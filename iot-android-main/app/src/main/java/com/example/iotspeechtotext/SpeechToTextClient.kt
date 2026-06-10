package com.example.iotspeechtotext

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.File

class SpeechToTextClient(
    private val serverUrl: String,
    private val onResponse: (JsonObject) -> Unit,
    private val onError: (String) -> Unit
) {
    private val client = OkHttpClient()
    private val gson = Gson()

    fun sendAudioFile(audioFile: File) {
        try {
            Log.d("STT", "📤 Gửi file audio: ${audioFile.name} (${audioFile.length()} bytes)")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    RequestBody.create("audio/*".toMediaType(), audioFile)
                )
                .build()

            val request = Request.Builder()
                .url("$serverUrl/api/voice")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    val errorMsg = "❌ Lỗi gửi file: ${e.message}"
                    Log.e("STT", errorMsg)
                    onError(errorMsg)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: ""
                            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                            
                            val action = jsonObject.get("action")?.asString ?: "non-op function"
                            val reply = jsonObject.get("reply")?.asString ?: "Không có phản hồi"
                            val recognizedText = jsonObject.get("recognized_text")?.asString ?: ""
                            
                            Log.d("STT", "✅ Phản hồi: action=$action, recognized='$recognizedText'")
                            
                            onResponse(jsonObject)
                        } else {
                            val errorMsg = "❌ Lỗi server: ${response.code}"
                            Log.e("STT", errorMsg)
                            onError(errorMsg)
                        }
                    } catch (e: Exception) {
                        val errorMsg = "❌ Lỗi parse response: ${e.message}"
                        Log.e("STT", errorMsg)
                        onError(errorMsg)
                    }
                }
            })
        } catch (e: Exception) {
            val errorMsg = "❌ Lỗi tạo request: ${e.message}"
            Log.e("STT", errorMsg)
            onError(errorMsg)
        }
    }
}
