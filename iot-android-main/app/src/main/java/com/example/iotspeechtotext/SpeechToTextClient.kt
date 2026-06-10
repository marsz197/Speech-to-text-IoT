package com.example.iotspeechtotext

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.File

class SpeechToTextClient(
    private val serverUrl: String,
    private val onSendSuccess: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val client = OkHttpClient()

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
                            Log.d("STT", "✅ File được gửi thành công, chờ kết quả từ MQTT...")
                            onSendSuccess()
                        } else {
                            val errorMsg = "❌ Lỗi server: ${response.code}"
                            Log.e("STT", errorMsg)
                            onError(errorMsg)
                        }
                    } catch (e: Exception) {
                        val errorMsg = "❌ Lỗi: ${e.message}"
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
