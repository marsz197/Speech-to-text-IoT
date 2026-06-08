import json
import requests
import os
import shutil
import whisper
import socket
from fastapi import FastAPI, UploadFile, File
from pydantic import BaseModel

app = FastAPI()
def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # Kết nối giả lập để kích hoạt card mạng lấy IP thực tế (không tốn lưu lượng mạng)
        s.connect(('8.8.8.8', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

LOCAL_IP = get_local_ip()
# 1. TẢI MÔ HÌNH WHISPER VÀO RAM NGAY KHI BẬT SERVER
print("Đang nạp mô hình Whisper (Speech-to-Text) vào RAM...")
# Bản 'small' cân bằng tốt nhất giữa tốc độ và độ chuẩn xác tiếng Việt
stt_model = whisper.load_model("small") 
print("Tải xong! Server đã sẵn sàng nhận lệnh.")
print(f"Server đang chạy tại địa chỉ: {LOCAL_IP}")


@app.post("/api/voice")
# 2. NHẬN FILE ÂM THANH TỪ ANDROID GỬI LÊN, DÙNG WHISPER DỊCH RA TEXT, RỒI TRUYỀN CHO QWEN2.5 XỬ LÝ
async def process_voice_command(file: UploadFile = File(...)):
    temp_file_path = f"temp_{file.filename}"
    
    try:
        # Lưu file âm thanh từ Android gửi lên vào máy tính của bạn
        with open(temp_file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
            
        # Dùng Whisper dịch âm thanh ra Text (ép nhận diện tiếng Việt)
        print("\nĐang dịch âm thanh...")
        result = stt_model.transcribe(temp_file_path)
        user_text = result["text"].strip()
        print(f"Người dùng vừa nói: {user_text}")
        
        # Nếu đoạn ghi âm bị rỗng hoặc có tạp âm không ra chữ
        if not user_text:
             return {
                 "action": "non-op function", 
                 "reply": "Tôi không nghe rõ bạn nói gì.",
                 "recognized_text": ""
             }
             
        # Truyền Text vừa dịch được cho Qwen2.5 xử lý
        final_result = process_command(CommandRequest(text=user_text))
        
        # Nhét thêm trường 'recognized_text' để Quân in ra màn hình Dashboard
        final_result["recognized_text"] = user_text
        return final_result
    finally:
        if os.path.exists(temp_file_path):
            os.remove(temp_file_path)
class CommandRequest(BaseModel):
    text: str 
@app.post("/api/nlp")
def process_command(request: CommandRequest):
    input_text = request.text
    #prompting the model
    system_instruction = (
        "Bạn là bộ não NLP đa ngôn ngữ của hệ thống điều khiển điện thoại AIoT.\n"
        "Hãy phân tích câu lệnh của người dùng và trả về DUY NHẤT một chuỗi JSON hợp lệ "
        "với cấu trúc: {\"action\": \"...\"}"
        "Các giá trị 'action' bắt buộc phải là 1 trong 4 trường hợp sau:\n"
        "- 'flash': nếu người dùng muốn bật/mở đèn pin, đèn flash.\n"
        "- 'record': nếu người dùng muốn thu âm, ghi âm.\n"
        "- 'cam': nếu người dùng muốn bật/mở camera, máy ảnh, chụp hình.\n"
        "- 'non-op function': dành cho các câu chào hỏi hoặc các chức năng không nằm trong 3 lệnh trên.\n"
        "Không được phép trả về bất kỳ giá trị nào khác ngoài 4 giá trị 'action' đã nêu trên.\n"
        "Tuyệt đối không viết thêm bất kỳ lời giải thích nào khác ngoài chuỗi JSON."
    )
    prompt = f"{system_instruction}\n\nCâu lệnh của người dùng: {input_text}"

    #gọi API của mô hình ngôn ngữ
    try : 
        response = requests.post(
            "http://localhost:11434/api/generate",
            json={
                "model": "qwen2.5:1.5b",
                "prompt": prompt,
                "stream": False,
                "format": "json",
            },
            timeout =10 #đặt timeout để tránh treo server nếu mô hình không phản hồi kịp thời
        ) 
        result_json = json.loads(response.json()["response"])
        action_code = result_json.get("action", "non-op function")
        reply_dict = {
            "flash": "Đèn pin đã được bật.",
            "record": "Bắt đầu thu âm.",
            "cam": "Camera đã được bật.",
            "non-op function": "Chào bạn, lệnh này hiện tại chưa được hỗ trợ."
        }
        return {
            "action": action_code,
            "reply": reply_dict.get(action_code, reply_dict["non-op function"])
        }
    except Exception as e:
        print(e)
        return{
            "action": "non-op function",
            "reply": "Xin lỗi, đã có lỗi xảy ra khi xử lý yêu cầu của bạn."
        }