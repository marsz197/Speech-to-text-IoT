package com.example.iotspeechtotext
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttManager(
    private val serverUri: String = "tcp://broker.hivemq.com:1883",
    private val topic: String = "audio/chunks/stt",
    private val onMessage: (String) -> Unit,
    private val onStatusChange: (Boolean) -> Unit
) {
    private var client: MqttClient? = null
    private val gson = Gson()

    fun connect() {
        try {
            val clientId = MqttClient.generateClientId()
            client = MqttClient(serverUri, clientId, MemoryPersistence())
            
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 60
            }

            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    onStatusChange(false)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.toString() ?: return
                    try {
                        onMessage(payload)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            Thread {
                try {
                    client?.connect(options)
                    client?.subscribe(topic)
                    onStatusChange(true)
                } catch (e: MqttException) {
                    e.printStackTrace()
                    onStatusChange(false)
                }
            }.start()
        } catch (e: MqttException) {
            e.printStackTrace()
            onStatusChange(false)
        }
    }

    fun disconnect() {
        try {
            client?.setCallback(null)
            if (client?.isConnected == true) {
                client?.disconnect()
            }
            client?.close()
            client = null
            onStatusChange(false)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}
