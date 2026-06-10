package com.example.iotspeechtotext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var mqttManager: MqttManager? = null
    private lateinit var actionExecutor: ActionExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionExecutor = ActionExecutor(this, lifecycleScope)
        
        setContent {
            var status by remember { mutableStateOf("Disconnected") }
            var lastAction by remember { mutableStateOf("None") }
            var serverUri by remember { mutableStateOf("tcp://broker.hivemc.com:1883") }
            var showDialog by remember { mutableStateOf(false) }

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

            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "IoT Actuator",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(text = "Status: $status", color = Color.Black)
                        Text(text = "Last Action: $lastAction", color = Color.Black)
                    }

                    Button(
                        onClick = { showDialog = true },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                    ) {
                        Text("Settings", color = Color.White)
                    }

                    if (showDialog) {
                        var tempUri by remember { mutableStateOf(serverUri) }
                        AlertDialog(
                            onDismissRequest = { showDialog = false },
                            title = { Text("MQTT Settings") },
                            text = {
                                TextField(
                                    value = tempUri,
                                    onValueChange = { tempUri = it },
                                    label = { Text("Server URI") }
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    serverUri = tempUri
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
