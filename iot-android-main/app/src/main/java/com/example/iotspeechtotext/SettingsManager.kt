package com.example.iotspeechtotext

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "iot_settings",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_MQTT_URI = "mqtt_uri"
        private const val KEY_REST_URL = "rest_url"
        // Standard HiveMQ Broker for commands
        private const val DEFAULT_MQTT_URI = "tcp://broker.hivemq.com:1883"
        // Placeholder for YOUR FastAPI server (e.g. http://192.168.1.5:8000)
        private const val DEFAULT_REST_URL = "http://100.93.8.106:8000"
    }

    fun getMqttUri(): String = prefs.getString(KEY_MQTT_URI, DEFAULT_MQTT_URI) ?: DEFAULT_MQTT_URI
    fun getRestUrl(): String = prefs.getString(KEY_REST_URL, DEFAULT_REST_URL) ?: DEFAULT_REST_URL

    fun saveMqttUri(uri: String) {
        prefs.edit().putString(KEY_MQTT_URI, uri).apply()
    }

    fun saveRestUrl(url: String) {
        prefs.edit().putString(KEY_REST_URL, url).apply()
    }

    fun saveAll(mqttUri: String, restUrl: String) {
        prefs.edit().apply {
            putString(KEY_MQTT_URI, mqttUri)
            putString(KEY_REST_URL, restUrl)
        }.apply()
    }
}
