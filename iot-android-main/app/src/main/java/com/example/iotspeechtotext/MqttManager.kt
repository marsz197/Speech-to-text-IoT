package com.example.iotspeechtotext
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI

class MqttManager(
    private val serverUri: String = "tcp://broker.hivemq.com:1883",
    private val topic: String = "audio/response",
    private val onMessage: (String) -> Unit,
    private val onStatusChange: (Boolean) -> Unit
) {
    init {
        // Force IPv4 as early as possible
        try {
            System.setProperty("java.net.preferIPv4Stack", "true")
            System.setProperty("java.net.preferIPv6Addresses", "false")
            Log.d("MQTT", "🔧 IPv4 preference set to TRUE")
        } catch (e: Exception) {
            Log.e("MQTT", "❌ Failed to set IP preferences", e)
        }
    }

    private var client: MqttClient? = null
    private val gson = Gson()

    private fun resolveToIPv4(uri: String): String {
        return try {
            val parsedUri = URI(uri)
            val host = parsedUri.host ?: return uri
            val scheme = parsedUri.scheme ?: "tcp"
            val port = if (parsedUri.port != -1) parsedUri.port else {
                if (scheme == "ssl") 8883 else 1883
            }

            Log.d("MQTT", "🔍 Resolving host: $host")
            val addresses = InetAddress.getAllByName(host)
            val ipv4 = addresses.firstOrNull { it is Inet4Address }?.hostAddress
            
            if (ipv4 != null) {
                val newUri = "$scheme://$ipv4:$port"
                Log.d("MQTT", "✅ Resolved $host to IPv4: $ipv4. New URI: $newUri")
                newUri
            } else {
                Log.w("MQTT", "⚠️ No IPv4 found for $host, using original URI")
                uri
            }
        } catch (e: Exception) {
            Log.e("MQTT", "❌ DNS resolution failed: ${e.message}")
            uri
        }
    }

    fun connect() {
        Thread {
            try {
                val initialUri = if (!serverUri.contains("://")) {
                    "tcp://$serverUri"
                } else {
                    serverUri
                }

                // Hard force IPv4 resolution
                val formattedUri = resolveToIPv4(initialUri)

                Log.d("MQTT", "📡 ATTEMPTING CONNECTION TO: $formattedUri")
                val clientId = MqttClient.generateClientId()
                client = MqttClient(formattedUri, clientId, MemoryPersistence())
                
                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 30
                    keepAliveInterval = 60
                    isAutomaticReconnect = true
                    
                    if (formattedUri.startsWith("ssl://") || formattedUri.startsWith("wss://")) {
                        socketFactory = javax.net.ssl.SSLSocketFactory.getDefault()
                    }
                }

                client?.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.i("MQTT", "✅ Connection complete. Reconnect: $reconnect")
                        try {
                            client?.subscribe(topic)
                            Log.d("MQTT", "📡 Subscribed successfully to: $topic")
                            onStatusChange(true)
                        } catch (e: MqttException) {
                            Log.e("MQTT", "❌ Subscribe failed: ${e.message}")
                        }
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.w("MQTT", "⚠️ Connection lost: ${cause?.message}")
                        onStatusChange(false)
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.toString() ?: return
                        Log.d("MQTT", "📨 Message from [$topic]: $payload")
                        try {
                            onMessage(payload)
                        } catch (e: Exception) {
                            Log.e("MQTT", "❌ Parse error: ${e.message}", e)
                            e.printStackTrace()
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                client?.connect(options)
                Log.d("MQTT", "✅ Connected to: $formattedUri")
            } catch (e: Exception) {
                Log.e("MQTT", "❌ Connection error: ${e.message}")
                e.printStackTrace()
                onStatusChange(false)
            }
        }.start()
    }

    fun disconnect() {
        try {
            client?.setCallback(null)
            if (client?.isConnected == true) {
                client?.disconnect()
            }
            client?.close()
            client = null
            Log.d("MQTT", "🔌 Disconnected")
            onStatusChange(false)
        } catch (e: MqttException) {
            Log.e("MQTT", "❌ Disconnect error: ${e.message}", e)
            e.printStackTrace()
        }
    }

    fun publish(topic: String, payload: String) {
        try {
            if (client?.isConnected == true) {
                client?.publish(topic, payload.toByteArray(), 1, false)
                Log.d("MQTT", "📤 Published to [$topic]: $payload")
            } else {
                Log.w("MQTT", "⚠️ Not connected, cannot publish")
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "❌ Publish error: ${e.message}", e)
            e.printStackTrace()
        }
    }
}