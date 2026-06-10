package com.example.iotspeechtotext

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
            val context = LocalContext.current
            var status by remember { mutableStateOf("Disconnected") }
            var lastAction by remember { mutableStateOf("None") }
            var lastRecognizedText by remember { mutableStateOf("") }
            // Load từ SharedPreferences
            var serverUri by remember { mutableStateOf(settingsManager.getMqttUri()) }
            var serverRestUrl by remember { mutableStateOf(settingsManager.getRestUrl()) }
            var showDialog by remember { mutableStateOf(false) }
            var isRecording by remember { mutableStateOf(false) }
            var recordingIndicator by remember { mutableStateOf("") }
            var isWaitingForResponse by remember { mutableStateOf(false) }
            var responseTimeout by remember { mutableStateOf(false) }

            // Permission Launcher
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
                val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
                if (!recordGranted) {
                    lastRecognizedText = "❌ Quyền ghi âm bị từ chối"
                }
            }

            // Function to check and request permissions
            val checkAndRequestPermissions = {
                val permissionsToRequest = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.CAMERA)
                }

                if (permissionsToRequest.isNotEmpty()) {
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    false
                } else {
                    true
                }
            }

            // Timeout handler for MQTT response (30 seconds)
            LaunchedEffect(isWaitingForResponse) {
                if (isWaitingForResponse) {
                    kotlinx.coroutines.delay(30000) // 30 seconds
                    isWaitingForResponse = false
                    responseTimeout = true
                    lastRecognizedText = "⏱️ Timeout! Server không trả về kết quả sau 30s"
                }
            }

            // Initialize MQTT Manager
            DisposableEffect(serverUri) {
                val manager = MqttManager(
                    serverUri = serverUri,
                    onMessage = { cmd ->
                        runOnUiThread {
                            try{
                                Log.d("MainActivity", "📨 MQTT message received: $cmd")
                                val json = JSONObject(cmd)
                                
                                // Clear timeout if waiting for response
                                if (isWaitingForResponse) {
                                    isWaitingForResponse = false
                                    responseTimeout = false
                                    lastRecognizedText = "" // Clear error messages
                                    Log.d("MainActivity", "✅ Response received, timeout cleared")
                                }
                                val duration = json.optInt("duration", 0)
                                val action = json.optString("action", "non-op function")
                                val recognized = json.optString("recognized_text", "")
                                val reply = json.optString("reply", "")
                                lastAction = when(action) {
                                    "flash_on" -> "Turn On Flash"
                                    "flash_off" -> "Turn Off Flash"
                                    "cam" -> "Open Camera"
                                    "record" -> "Open Recorder"
                                    "timer" -> "Set Timer ($duration seconds)"
                                    else -> "Unknown Action"
                                }
                                // Cập nhật UI với MQTT response
                                lastRecognizedText = buildString {
                                    if (recognized.isNotEmpty()) append("Recognized: $recognized\n")
                                    if (reply.isNotEmpty()) append("Reply: $reply")
                                }.trim()
                                
                                Log.d("MainActivity", "✅ Action=$action, Reply=$reply")
                                actionExecutor.execute(action, duration)
                            }
                            catch(e: Exception){
                                Log.e("MainActivity", "❌ MQTT parse error: ${e.message}", e)
                                actionExecutor.execute("non-op function", 0)
                                e.printStackTrace()
                            }
                        }
                    },
                    onStatusChange = { isConnected ->
                        Log.d("MainActivity", "📡 MQTT connection status changed: ${if (isConnected) "Connected" else "Disconnected"}")
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
                    onSendSuccess = {
                        runOnUiThread {
                            Log.d("MainActivity", "✅ Audio file sent successfully, waiting for MQTT response...")
                            lastRecognizedText = "⏳ Đã gửi file, chờ kết quả từ MQTT..."
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            Log.e("MainActivity", "❌ Send error: $error")
                            lastRecognizedText = error
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
                                    if (checkAndRequestPermissions()) {
                                        if (audioRecorder.startRecording()) {
                                            isRecording = true
                                            recordingIndicator = "●"
                                            Log.d("MainActivity", "🎤 Bắt đầu ghi âm")
                                        } else {
                                            lastRecognizedText = "❌ Không thể bắt đầu ghi âm"
                                        }
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
                                        Log.d("MainActivity", "📤 Sending audio file (${audioFile.length()} bytes) to $serverRestUrl/api/voice")
                                        lastRecognizedText = "📡 Gửi audio via REST API, chờ kết quả từ MQTT..."
                                        responseTimeout = false
                                        isWaitingForResponse = true
                                        Log.d("MainActivity", "⏱️ Timeout timer started (30s)")
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
