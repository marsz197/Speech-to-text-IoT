package com.example.iotspeechtotext
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.PI
import kotlin.time.Duration.Companion.milliseconds
import android.provider.AlarmClock

class ActionExecutor(private val context: Context, private val scope: CoroutineScope) {
    private var isFlashOn = false

    fun execute(command: String, duration: Int = 0) {
        when (command) {
            "flash" -> toggleFlash()
            "cam" -> openCamera()
            "record" -> openRecorder()
            "timer" -> setTimer(duration)
            "non-op function" -> playBeepBeep()
            else -> playBeepBeep()
        }
    }

    private fun toggleFlash() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            isFlashOn = !isFlashOn
            cameraManager.setTorchMode(cameraId, isFlashOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openRecorder() {
        try {
            val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setTimer(duration: Int) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, "Smart AI Timer")
                putExtra(AlarmClock.EXTRA_LENGTH, duration)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playBeepBeep() {
        val sampleRate = 44100
        val durationMs = 500L
        val frequency = 440.0
        val numSamples = (durationMs * sampleRate / 1000).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            samples[i] = (sin(2.0 * PI * i / (sampleRate / frequency)) * Short.MAX_VALUE).toInt().toShort()
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(samples, 0, samples.size)

        scope.launch {
            try {
                repeat(2) {
                    audioTrack.play()
                    delay((durationMs + 100).milliseconds)
                    audioTrack.stop()
                    audioTrack.reloadStaticData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                audioTrack.release()
            }
        }
    }
}
