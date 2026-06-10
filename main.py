# -*- coding: utf-8 -*-
import json
import requests
import os
import shutil
import whisper
import subprocess
import logging
import sys
from datetime import datetime
from fastapi import FastAPI, UploadFile, File
from pydantic import BaseModel
import paho.mqtt.client as mqtt

# ============= FIX UNICODE ENCODING TRÊN WINDOWS =============
class UTF8StreamHandler(logging.StreamHandler):
    def __init__(self, stream=None):
        super().__init__(stream)
        self.stream = stream or sys.stdout
    
    def emit(self, record):
        try:
            msg = self.format(record)
            if isinstance(msg, str):
                msg = msg.encode('utf-8', errors='ignore').decode('utf-8')
            stream = self.stream
            stream.write(msg + '\n')
            stream.flush()
        except Exception:
            self.handleError(record)

# Console: INFO only (faster)
console_handler = UTF8StreamHandler(sys.stdout)
console_handler.setLevel(logging.INFO)
console_handler.setFormatter(logging.Formatter('[%(asctime)s] %(levelname)s - %(message)s', datefmt='%H:%M:%S'))

# File: All DEBUG logs (UTF-8)
file_handler = logging.FileHandler('debug.log', encoding='utf-8')
file_handler.setLevel(logging.DEBUG)
file_handler.setFormatter(logging.Formatter('[%(asctime)s] %(levelname)s - %(message)s', datefmt='%Y-%m-%d %H:%M:%S'))

logger = logging.getLogger(__name__)
logger.setLevel(logging.DEBUG)
logger.addHandler(console_handler)
logger.addHandler(file_handler)

app = FastAPI()

def get_tailscale_ip():
    try:
        result = subprocess.run(['tailscale', 'ip', '-4'], capture_output=True, text=True, check=True, cwd=None)
        return result.stdout.strip()
    except Exception:
        logger.warning("Tailscale not available, using localhost")
        return '127.0.0.1'

LOCAL_IP = get_tailscale_ip()
print("-" * 50)
print(f"Server: http://{LOCAL_IP}:8000/api/voice")
print("-" * 50)

logger.info("Loading Whisper model...")
try:
    stt_model = whisper.load_model("base", device="cuda")
    logger.info("Using CUDA")
except:
    stt_model = whisper.load_model("base", device="cpu")
    logger.info("Using CPU")

logger.info("Server ready!")

@app.post("/api/voice")
async def process_voice_command(file: UploadFile = File(...)):
    temp_file_path = f"temp_{file.filename}"
    logger.info(f"Received: {file.filename}")
    
    try:
        # Save audio file
        with open(temp_file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
            
        # Transcribe with optimizations
        logger.info("Transcribing...")
        result = stt_model.transcribe(
            temp_file_path, 
            verbose=False,
            fp16=True,
            initial_prompt="bật đèn pin, flash on, tắt đèn pin, flash off, bật camera, open camera, bắt đầu ghi âm, record, hẹn giờ, timer, đếm ngược giây.",
            condition_on_previous_text=False,
            no_speech_threshold=0.6
        )
        user_text = result["text"].strip()
        logger.info(f"Text: {user_text}")
        
        if not user_text:
            logger.warning("Empty result")
            return {
                "action": "non-op function", 
                "reply": "Không nhận diện được lệnh, vui lòng thử lại",
                "recognized_text": ""
            }
        
        # Process with Qwen2.5
        final_result = process_command(CommandRequest(text=user_text))
        logger.info(f"Action: {final_result['action']}")
        final_result["recognized_text"] = user_text
        return final_result
        
    except Exception as e:
        logger.error(f"Error: {e}")
        return {
            "action": "non-op function",
            "reply": "Có lỗi xảy ra, vui lòng thử lại",
            "recognized_text": ""
        }
    finally:
        try:
            if os.path.exists(temp_file_path):
                os.remove(temp_file_path)
        except:
            pass

class CommandRequest(BaseModel):
    text: str

@app.post("/api/nlp")
def process_command(request: CommandRequest):
    input_text = request.text
    
    prompt = f"""Analyze this command and return JSON with 'action', 'duration' (if timer), and 'reply'.
Actions: flash_on, flash_off, record, timer, cam, non-op function
Rules for actions:
- If the user wants to turn ON/open the flashlight/flash, use 'flash_on'.
- If the user wants to turn OFF/close the flashlight/flash, use 'flash_off'.
- 'reply': Write a short confirmation message (below 10 words). Importance: the confirmation message must be concise and clear, match with the language of the input, directly related to the action, and should not include any additional information or explanations. It should be a simple acknowledgment of the action taken.
Command: {input_text}
Return ONLY JSON."""
    
    try:
        response = requests.post(
            "http://localhost:11434/api/generate",
            json={
                "model": "qwen2.5:1.5b",
                "prompt": prompt,
                "stream": False,
                "format": "json",
            },
            timeout=8
        )
        
        result_json = json.loads(response.json()["response"])
        action_code = result_json.get("action", "non-op function")
        duration = result_json.get("duration", 0)
        reply_text = result_json.get("reply", "Đã xử lý / Processed")
        
        final_dict = {
            "action": action_code,
            "reply": reply_text,
            "duration": duration
        }  
        
        # Publish to MQTT bằng kết nối persistent có sẵn
        try:
            payload_str = json.dumps(final_dict, ensure_ascii=False)
            mqtt_client.publish("audio/response", payload_str)
            logger.info(f"MQTT Published: {payload_str}")
        except Exception as e:
            logger.error(f"MQTT error: {e}")      
            
        return final_dict
        
    except Exception as e:
        logger.error(f"AI error: {e}")
        return {
            "action": "non-op function",
            "reply": "System error. Please try again.",
            "duration": 0
        }

# ========================================================
# KHỞI TẠO VÀ DUY TRÌ KẾT NỐI PERSISTENT MQTT (MOSQUITTO)
# ========================================================
mqtt_client = mqtt.Client()

def on_mqtt_connect(client, userdata, flags, rc):
    if rc == 0:
        logger.info("✅ MQTT connected to private broker successfully")
    else:
        logger.error(f"❌ MQTT connection failed with code: {rc}")

def on_mqtt_disconnect(client, userdata, rc):
    if rc != 0:
        logger.warning(f"⚠️ Unexpected MQTT disconnection: {rc}")

mqtt_client.on_connect = on_mqtt_connect
mqtt_client.on_disconnect = on_mqtt_disconnect

try:
    # Vì Mosquitto chạy chung máy với FastAPI nên trỏ thẳng về localhost (127.0.0.1) là chuẩn nhất
    mqtt_client.connect("broker.hivemq.com", 1883, keepalive=60)
    mqtt_client.loop_start()  # Bật luồng ngầm duy trì kết nối liên tục
    logger.info("MQTT client persistent loop started.")
except Exception as e:
    logger.error(f"Failed to initialize MQTT connection: {e}")

@app.on_event("shutdown")
async def shutdown_event():
    """Đóng kết nối an toàn khi tắt server"""
    mqtt_client.loop_stop()
    mqtt_client.disconnect()
    logger.info("MQTT connection gracefully closed.")