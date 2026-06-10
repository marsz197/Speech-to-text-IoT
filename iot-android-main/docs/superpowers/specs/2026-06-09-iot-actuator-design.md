# IoT Actuator Design Spec

## Goal
Lightweight Android app to receive MQTT signals and trigger hardware actions.

## Architecture
*   **UI:** Jetpack Compose.
*   **MQTT:** Paho Java Client (low overhead).
*   **State:** Local Activity state (zero persistence).
*   **Lifecycle:** Connection active while app is in foreground/recent tasks. Disconnect on kill.

## UI Requirements
*   **Background:** Plain White.
*   **Title:** "IoT Actuator" (Black, upper middle).
*   **Status Text:** "Connected" or "Disconnected".
*   **Action Text:** "Last Action: [action]".

## MQTT Configuration
*   **Broker:** `broker.hivemc.com`
*   **Port:** `1883`
*   **Topic:** `audio/chunks/stt` (should be easy to read and changed later on)
*   **Format:** JSON (e.g., `{"command": "flash"}`) (should be easy to read and changed later on)

## Action Mapping
| Command | Action | Implementation |
| :--- | :--- | :--- |
| `flash` | Toggle Flash | `CameraManager.setTorchMode` (no UI change) |
| `cam` | Open Camera | `MediaStore.ACTION_IMAGE_CAPTURE` Intent |
| `record` | Start Recording | `MediaStore.Audio.Media.RECORD_SOUND_ACTION` Intent |
| `non-op function` | Beep-Beep | `AudioTrack` sine wave 440Hz, 500ms x2 |

## Success Criteria
*   Fast startup (<2s).
*   Memory usage <100MB.
*   Immediate response to MQTT signals.
