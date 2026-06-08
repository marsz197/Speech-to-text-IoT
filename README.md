# 🧠 AIoT Edge Server - Phone Voice Controller

Chào team, đây là tài liệu hướng dẫn giao tiếp với **Local AI Server** (do Trường phụ trách). Server này đóng vai trò là "bộ não" của hệ thống, thực hiện song song 2 nhiệm vụ:

1. **Nghe (Speech-to-Text):** Dùng AI Whisper dịch file âm thanh từ thiết bị của Minh/App của Quân thành văn bản tiếng Việt.
2. **Hiểu (NLP):** Dùng mô hình Qwen2.5 (chạy offline) để phân loại ý định lệnh và trả về hành động chính xác.

---

## 🚀 1. Luồng Hoạt Động Tổng Thể

**Quy trình chung:**

- **Minh:** Thu âm thanh từ Mic → Gửi qua IC & Bluetooth → App Android
- **Quân:** Nhận luồng âm thanh → Lưu thành file audio tạm → Bắn API gửi file đó lên Server của Trường
- **Trường (Server):** Phân tích file audio → Trả về JSON chứa lệnh (`flash`, `record`, `cam`) → App Android đọc JSON và bật/tắt phần cứng tương ứng trên điện thoại

---

## 📡 2. Thông Tin Kết Nối (Base URL)

Server chạy trực tiếp trên laptop của Trường. Khi Trường bật server, màn hình Console sẽ in ra một địa chỉ IP nội bộ.

- **Base URL mẫu:** `http://<IP_CỦA_TRƯỜNG>:8000`
  - *Ví dụ:* `http://192.168.1.5:8000`

### ⚠️ Lưu ý sống còn
Cả laptop của Trường và điện thoại Android của Quân **BẮT BUỘC** phải bắt chung một mạng Wi-Fi. Khuyến cáo dùng điện thoại thứ 3 phát Hotspot để mạng ổn định nhất khi demo.

---

## 🛠 3. Tài Liệu API (Dành cho Quân)

Hệ thống cung cấp 2 cổng API. Quân chủ yếu sử dụng **API số 1** để truyền file âm thanh thật. API số 2 dùng để test dự phòng.

### API 1: Xử lý file âm thanh (Chính)

| Thuộc tính | Giá trị |
|-----------|--------|
| **Endpoint** | `/api/voice` |
| **Method** | `POST` |
| **Content-Type** | `multipart/form-data` |

**Mô tả:** Nhận file âm thanh, tự động dịch ra chữ và phân tích ý định lệnh.

**Cách gửi Request Body:**

Cần đính kèm file âm thanh (định dạng `.wav`, `.m4a` hoặc `.mp3`) với key bắt buộc là `file`.

**Kết quả trả về (Response - JSON):**

Server sẽ luôn trả về HTTP Status `200 OK` kèm JSON sau:

```json
{
  "action": "flash",
  "reply": "Đèn pin đã được bật.",
  "recognized_text": "bật cái đèn pin lên đi tối quá"
}
```

**💡 Gợi ý cho Quân:** 
- Trên giao diện App Android (Dashboard), lấy giá trị `recognized_text` in ra màn hình (để chứng minh AI nghe đúng chữ Minh nói)
- Dùng logic `if/switch` trên trường `action` để gọi hàm hệ thống của điện thoại

---

### API 2: Xử lý văn bản thuần (Dùng để test nhanh)

| Thuộc tính | Giá trị |
|-----------|--------|
| **Endpoint** | `/api/nlp` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |

**Mô tả:** Nhận câu lệnh bằng chữ (bỏ qua khâu dịch âm thanh) và phân tích ý định.

**Request Body (JSON):**

```json
{
  "text": "bật camera lên chụp ảnh nào"
}
```

**Kết quả trả về (Response - JSON):**

```json
{
  "action": "cam",
  "reply": "Camera đã được bật."
}
```

---

## 📋 4. Danh sách các Action (Lệnh) được hỗ trợ

Ứng dụng của Quân chỉ cần bắt đúng 4 trường hợp (case) của key `action` trả về từ server:

| Giá trị `action` | Khi nào AI trả về? | Quân cần làm gì trên App? |
|-----------------|------------------|--------------------------|
| `"flash"` | Lệnh liên quan đến: bật đèn, mở flash, soi đèn... | Dùng `CameraManager` bật Flashlight |
| `"cam"` | Lệnh liên quan đến: chụp ảnh, mở camera, máy ảnh... | Dùng Intent gọi mở ứng dụng Camera |
| `"record"` | Lệnh liên quan đến: thu âm, ghi âm... | Dùng `MediaRecorder` để bắt đầu thu âm |
| `"non-op function"` | Các câu chào hỏi hoặc câu lệnh rác không có nghĩa | Không làm gì cả, chỉ in trường `reply` ra màn hình (Ví dụ: "Chào bạn") |

---

## 🐛 5. Hướng dẫn Test API bằng Postman/Trình duyệt

1. Trường khởi động Server
2. Truy cập vào: `http://<IP_CỦA_TRƯỜNG>:8000/docs`
3. Mở mục **POST /api/voice**
4. Bấm **Try it out** → Chọn một file `.wav` ghi âm sẵn trên máy → Bấm **Execute**
5. Chờ khoảng 1-2 giây để xem kết quả JSON trả về

---

## 📚 Tài liệu tham khảo

- **Whisper (Speech-to-Text):** Mô hình AI nghe nhạc
- **Qwen2.5 (NLP):** Mô hình phân loại ý định lệnh (chạy offline)
- **Framework:** FastAPI (chạy trên cổng 8000)

---

**Phiên bản:** v1.0  
**Cập nhật lần cuối:** 2024