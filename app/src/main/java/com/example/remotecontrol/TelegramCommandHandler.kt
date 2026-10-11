package com.example.remotecontrol

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection

class TelegramCommandHandler(
    context: Context,
    private val chatId: String
) : CommandHandler(context) {

    override fun sendSms(number: String, message: String) {
        Thread {
            try {
                val token = BuildConfig.TELEGRAM_BOT_TOKEN
                val url = "https://api.telegram.org/bot$token/sendMessage"
                val body = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", message)
                }.toString()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.write(body.toByteArray())
                connection.inputStream.bufferedReader().readText()
                Log.d("TelegramCH", "Yuborildi: $chatId -> $message")
            } catch (e: Exception) {
                Log.e("TelegramCH", "Xato: ${e.message}")
            }
        }.start()
    }

    fun executeAndReturn(command: String): String? {
        return when {
            command == "#battery" -> getBatteryInfo()
            command.startsWith("#volume:") -> { execute(chatId, command); "Ovoz o'rnatildi" }
            command == "#wifi_on" -> { execute(chatId, command); "WiFi yoqildi" }
            command == "#wifi_off" -> { execute(chatId, command); "WiFi o'chirildi" }
            command == "#bluetooth_on" -> { execute(chatId, command); "Bluetooth yoqildi" }
            command == "#bluetooth_off" -> { execute(chatId, command); "Bluetooth o'chirildi" }
            command == "#screen_off" -> { execute(chatId, command); null }
            command == "#screen_on" -> { execute(chatId, command); null }
            else -> { execute(chatId, command); null }
        }
    }
}