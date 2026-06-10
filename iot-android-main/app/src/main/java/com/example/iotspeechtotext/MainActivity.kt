package com.example.iotspeechtotext

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    private var mqttManager: MqttManager? = null
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var settingsManager: SettingsManager
    private var speechToTextClient: SpeechToTextClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionExecutor = ActionExecutor(this, lifecycleScope)
        audioRecorder = AudioRecorder(this)
        settingsManager = SettingsManager(this)
        
        setContent {
            var status by remember { mutableStateOf("Disconnected") }
            var lastAction by remember { mutableStateOf("None") }
            var lastRecognizedText by remember { mutableStateOf("") }
            // Load từ SharedPreferences
            var serverUri by remember { mutableStateOf(settingsManager.getMqttUri()) }
            var serverRestUrl by remember { mutableStateOf(settingsManager.getRestUrl()) }
            var showDialog by remember { mutableStateOf(false) }
            var isRecording by remember { mutableStateOf(false) }
            var recordingIndicator by remember { mutableStateOf("") }

            // Initialize MQTT Manager
            DisposableEffect(serverUri) {
                val manager = MqttManager(
                    serverUri = serverUri,
                    onMessage = { cmd ->
                        runOnUiThread {
                            try{
                                val json = JSONObject(cmd)
                                lastAction = cmd
                                val duration = json.optInt("duration", 0)
                                actionExecutor.execute(cmd, duration)
                            }
                            catch(e: Exception){
                                lastAction = cmd
                                actionExecutor.execute(cmd, 0)
                                e.printStackTrace()
                            }
                        }
                    },
                    onStatusChange = { isConnected ->
                        runOnUiThread {
                            status = if (isConnected) "Connected" else "Disconnected"
                        }
                    }
                )
                manager.connect()
                mqttManager = manager
                
                onDispose {
                    manager.disconnect()
                }
            }

            // Initialize Speech-to-Text Client
            LaunchedEffect(serverRestUrl) {
                speechToTextClient = SpeechToTextClient(
                    serverUrl = serverRestUrl,
                    onResponse = { response ->
                        val action = response.get("action")?.asString ?: "non-op function"
                        val reply = response.get("reply")?.asString ?: ""
                        val recognized = response.get("recognized_text")?.asString ?: ""
                        
                        runOnUiThread {
                            lastAction = " $action"
                            lastRecognizedText = "Recognized: $recognized\nReply: $reply"
                            
                            // Thực thi lệnh từ server
                            try {
                                val json = JSONObject()
                                json.put("action", action)
                                val duration = response.get("duration")?.asInt ?: 0
                                actionExecutor.execute(json.toString(), duration)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            lastRecognizedText = error
                            Log.e("MainActivity", error)
                        }
                    }
                )
            }

            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🎤 IoT Speech-to-Text",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // MQTT Status
                        Text(text = "📡 MQTT: $status", color = Color.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Recording Status
                        if (isRecording) {
                            Text(
                                text = "🔴 REC $recordingIndicator",
                                color = Color.Red,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            Text(text = "⚫ Stopped", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Last Action
                        Text(text = "Last Action: $lastAction", color = Color.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Recognized Text
                        Text(
                            text = lastRecognizedText,
                            color = Color.DarkGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Record Button
                        if (!isRecording) {
                            Button(
                                onClick = {
                                    if (audioRecorder.startRecording()) {
                                        isRecording = true
                                        recordingIndicator = "●"
                                        Log.d("MainActivity", "🎤 Bắt đầu ghi âm")
                                    } else {
                                        lastRecognizedText = "❌ Không thể bắt đầu ghi âm"
                                    }
                                },
                                modifier = Modifier.width(200.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = "Record", tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Record", color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val audioFile = audioRecorder.stopRecording()
                                    isRecording = false
                                    recordingIndicator = ""
                                    Log.d("MainActivity", "⏹️ Dừng ghi âm")
                                    
                                    if (audioFile != null && audioFile.exists()) {
                                        lastRecognizedText = "✅ Ghi âm xong, đang gửi..."
                                        speechToTextClient?.sendAudioFile(audioFile)
                                    }
                                },
                                modifier = Modifier.width(200.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop & Send", color = Color.White)
                            }
                        }

                        Button(
                            onClick = { showDialog = true },
                            modifier = Modifier.width(200.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text("⚙️ Settings", color = Color.White)
                        }
                    }

                    if (showDialog) {
                        var tempMqttUri by remember { mutableStateOf(serverUri) }
                        var tempRestUrl by remember { mutableStateOf(serverRestUrl) }
                        
                        AlertDialog(
                            onDismissRequest = { showDialog = false },
                            title = { Text("⚙️ Settings") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("MQTT Broker URI:")
                                    TextField(
                                        value = tempMqttUri,
                                        onValueChange = { tempMqttUri = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("REST Server URL:")
                                    TextField(
                                        value = tempRestUrl,
                                        onValueChange = { tempRestUrl = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    serverUri = tempMqttUri
                                    serverRestUrl = tempRestUrl
                                    // Lưu vào SharedPreferences
                                    settingsManager.saveAll(tempMqttUri, tempRestUrl)
                                    Log.d("MainActivity", "✅ Settings đã lưu")
                                    showDialog = false
                                }) { Text("Save") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }
    }
}
