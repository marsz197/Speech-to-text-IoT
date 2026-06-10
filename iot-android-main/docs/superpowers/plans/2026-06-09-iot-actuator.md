# IoT Actuator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight Android app that triggers hardware actions via MQTT signals.

**Architecture:** Simplified Activity-centric design using Jetpack Compose and Paho MQTT Java client. Hardware actions are triggered via `CameraManager`, `Intent`, and `AudioTrack`. Lifecycle hooks ensure MQTT only runs while app is active.

**Tech Stack:** Kotlin, Jetpack Compose, Paho MQTT, Android Hardware APIs.

---

### Task 1: Project Setup (Dependencies & Permissions)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add Paho MQTT and Gson to libs.versions.toml**
Add to `[versions]`:
```toml
paho = "1.2.5"
gson = "2.10.1"
composeBom = "2024.02.01"
```
Add to `[libraries]`:
```toml
mqtt-paho = { group = "org.eclipse.paho", name = "org.eclipse.paho.client.mqttv3", version.ref = "paho" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
```

- [ ] **Step 2: Enable Compose and add dependencies in app/build.gradle.kts**
```kotlin
android {
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8" // Adjust based on Kotlin version
    }
}
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.mqtt-paho)
    implementation(libs.gson)
}
```

- [ ] **Step 3: Add permissions to AndroidManifest.xml**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

- [ ] **Step 4: Commit**
```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "chore: setup dependencies and permissions"
```

---

### Task 2: Hardware Action Executor

**Files:**
- Create: `app/src/main/java/com/example/iotspeechtotext/ActionExecutor.kt`

- [ ] **Step 1: Implement ActionExecutor class**
```kotlin
class ActionExecutor(private val context: Context) {
    fun execute(command: String) {
        when (command) {
            "flash" -> toggleFlash()
            "cam" -> openCamera()
            "record" -> openRecorder()
            "non-op function" -> playBeepBeep()
        }
    }

    private fun toggleFlash() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]
        cameraManager.setTorchMode(cameraId, true) // For simplicity, just turn on
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun openRecorder() {
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun playBeepBeep() {
        // Logic for 440Hz beep 500ms twice
    }
}
```

- [ ] **Step 2: Commit**
```bash
git add app/src/main/java/com/example/iotspeechtotext/ActionExecutor.kt
git commit -m "feat: implement ActionExecutor for hardware triggers"
```

---

### Task 3: MQTT Manager

**Files:**
- Create: `app/src/main/java/com/example/iotspeechtotext/MqttManager.kt`

- [ ] **Step 1: Implement MqttManager with Paho**
```kotlin
class MqttManager(
    private val serverUri: String = "tcp://broker.hivemc.com:1883",
    private val topic: String = "audio/chunks/stt",
    private val onMessage: (String) -> Unit,
    private val onStatusChange: (Boolean) -> Unit
) {
    private var client: MqttClient? = null

    fun connect() {
        client = MqttClient(serverUri, MqttClient.generateClientId(), MemoryPersistence())
        client?.setCallback(object : MqttCallback {
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.toString() ?: return
                // Parse JSON {"command": "..."}
                onMessage(payload)
            }
            override fun connectionLost(cause: Throwable?) { onStatusChange(false) }
            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })
        client?.connect()
        client?.subscribe(topic)
        onStatusChange(true)
    }

    fun disconnect() {
        client?.disconnect()
        onStatusChange(false)
    }
}
```

- [ ] **Step 2: Commit**
```bash
git add app/src/main/java/com/example/iotspeechtotext/MqttManager.kt
git commit -m "feat: implement MqttManager with Paho MQTT"
```

---

### Task 4: UI, Settings & Lifecycle Integration

**Files:**
- Modify: `app/src/main/java/com/example/iotspeechtotext/MainActivity.kt`

- [ ] **Step 1: Implement Compose UI with Settings and MQTT Lifecycle**
```kotlin
class MainActivity : ComponentActivity() {
    private var mqttManager: MqttManager? = null
    private lateinit var actionExecutor: ActionExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionExecutor = ActionExecutor(this)
        setContent {
            var status by remember { mutableStateOf("Disconnected") }
            var lastAction by remember { mutableStateOf("None") }
            var showSettings by remember { mutableStateOf(false) }
            var serverUri by remember { mutableStateOf("tcp://broker.hivemc.com:1883") }

            mqttManager = MqttManager(
                serverUri = serverUri,
                onMessage = { json ->
                    val cmd = parseJson(json)
                    lastAction = cmd
                    actionExecutor.execute(cmd)
                },
                onStatusChange = { isConnected ->
                    status = if (isConnected) "Connected" else "Disconnected"
                }
            )

            // UI: Black text on White background
            Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp)) {
                    Text("IoT Actuator", color = Color.Black, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(20.dp))
                    Text("Status: $status", color = Color.Black)
                    Text("Last Action: $lastAction", color = Color.Black)
                }
                
                Button(
                    onClick = { showSettings = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp)
                ) {
                    Text("Settings")
                }

                if (showSettings) {
                    // Simple Dialog to change serverUri
                }
            }
        }
    }

    override fun onStart() { super.onStart(); mqttManager?.connect() }
    override fun onStop() { super.onStop(); mqttManager?.disconnect() }
}
```

- [ ] **Step 2: Commit**
```bash
git add app/src/main/java/com/example/iotspeechtotext/MainActivity.kt
git commit -m "feat: integrate MQTT with Compose UI, Settings, and Lifecycle"
```
