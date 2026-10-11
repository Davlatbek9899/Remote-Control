package com.example.remotecontrol

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object JarvisManager {

    private const val PREFS_NAME = "jarvis_sessions"
    private const val MAX_HISTORY = 6
    // Faqat bitta aktiv chatId — SharedPreferences da saqlanadi (har telefonda alohida)
    private const val KEY_ACTIVE_CHAT = "active_chat_id"

    private fun getActiveChatIdFromPrefs(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_CHAT, null)
    }

    private fun setActiveChatIdToPrefs(context: Context, chatId: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE_CHAT, chatId).apply()
    }

    private val apiKeys: List<String> by lazy {
        listOf(
            BuildConfig.GROQ_KEY_1,
            BuildConfig.GROQ_KEY_2,
            BuildConfig.GROQ_KEY_3,
            BuildConfig.GROQ_KEY_4,
            BuildConfig.GROQ_KEY_5,
            BuildConfig.GROQ_KEY_6,
            BuildConfig.GEMINI_API_KEY
        ).filter { it.isNotEmpty() }
    }
    private var currentKeyIndex = 0

    private fun getNextKey(): String {
        if (apiKeys.isEmpty()) return BuildConfig.GEMINI_API_KEY
        val key = apiKeys[currentKeyIndex % apiKeys.size]
        currentKeyIndex++
        return key
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """Sen JARVIS — Android telefon yordamchisi. Buyruq so'ralganda FAQAT JSON qaytar, boshqa hech narsa yozma. Oddiy savol bo'lsa oddiy matn yoz.

BUYRUQLAR (JSON format):
Chiroq: {"type":"command","cmd":"#flash_on","message":"Chiroq yoqildi 🔦"} / {"type":"command","cmd":"#flash_off","message":"Chiroq o'chirildi"}
Ekran: {"type":"command","cmd":"#screen_off","message":"Ekran o'chirildi 📵"} / {"type":"command","cmd":"#screen_on","message":"Ekran yoqildi"}
Ovoz: {"type":"command","cmd":"#volume:80","message":"Ovoz 80% 🔊"} / {"type":"command","cmd":"#mute","message":"Ovozsiz 🔇"} / {"type":"command","cmd":"#unmute","message":"Ovoz yoqildi"}
WiFi: {"type":"command","cmd":"#wifi_on","message":"WiFi yoqildi 📶"} / {"type":"command","cmd":"#wifi_off","message":"WiFi o'chirildi"}
Bluetooth: {"type":"command","cmd":"#bluetooth_on","message":"BT yoqildi 🔵"} / {"type":"command","cmd":"#bluetooth_off","message":"BT o'chirildi"}
Mobil internet: {"type":"command","cmd":"#mobile_on","message":"Internet yoqildi 📱"} / {"type":"command","cmd":"#mobile_off","message":"Internet o'chirildi"}
GPS: {"type":"command","cmd":"#gps_on","message":"GPS yoqildi 📍"} / {"type":"command","cmd":"#gps_off","message":"GPS o'chirildi"}
Yorug'lik: {"type":"command","cmd":"#brightness:50","message":"Yorug'lik 50% 💡"}
Batareya: {"type":"command","cmd":"#battery","message":"Batareya 🔋"}
Joylashuv: {"type":"command","cmd":"#location","message":"Joylashuv 📍"}
Musiqa: {"type":"music","query":"qo'shiq nomi","message":"Ijro 🎵"}
YouTube: {"type":"youtube","query":"qo'shiq nomi","message":"YouTube 🎬"}
Ilova: {"type":"command","cmd":"#open_app:telegram","message":"Ochildi 📱"}
Matn: {"type":"command","cmd":"#show_text:matn","message":"Ko'rsatildi 💬"}
To'xtat: {"type":"command","cmd":"#stop_audio","message":"To'xtatildi ⏹"}
Ko'p buyruq: {"type":"multi","commands":[...],"message":"Bajarildi"}

Misol: "chiroqni yoq" → {"type":"command","cmd":"#flash_on","message":"Chiroq yoqildi 🔦"}
Misol: "salom" → Salom! Qanday yordam bera olaman? 😊"""

    fun isActiveSession(context: Context, chatId: String) = getActiveChatIdFromPrefs(context) == chatId
    fun hasActiveSession(context: Context) = getActiveChatIdFromPrefs(context) != null
    fun startSession(context: Context, chatId: String) { setActiveChatIdToPrefs(context, chatId) }
    fun stopSession(context: Context, chatId: String) { 
        if (getActiveChatIdFromPrefs(context) == chatId) setActiveChatIdToPrefs(context, null) 
    }
    fun getActiveChatId(context: Context) = getActiveChatIdFromPrefs(context)

    private fun saveHistory(context: Context, phoneNumber: String, role: String, message: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "history_$phoneNumber"
        val history = try { JSONArray(prefs.getString(key, "[]")) } catch (e: Exception) { JSONArray() }
        history.put(JSONObject().apply {
            put("role", role)
            put("content", message)
        })
        while (history.length() > MAX_HISTORY) history.remove(0)
        prefs.edit().putString(key, history.toString()).apply()
    }

    private fun getHistory(context: Context, phoneNumber: String): JSONArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try { JSONArray(prefs.getString("history_$phoneNumber", "[]")) } catch (e: Exception) { JSONArray() }
    }

    fun clearHistory(context: Context, phoneNumber: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove("history_$phoneNumber").apply()
    }

    fun sendMessage(context: Context, phoneNumber: String, userMessage: String, retryCount: Int = 0): JarvisResponse {
        return try {
            val messages = JSONArray()

            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })

            // Tarix
            val history = getHistory(context, phoneNumber)
            for (i in 0 until history.length()) {
                messages.put(history.getJSONObject(i))
            }

            // Yangi xabar
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            val requestBody = JSONObject().apply {
                put("model", "meta-llama/llama-4-scout:free")
                put("messages", messages)
                put("max_tokens", 500)
                put("temperature", 0.9)
            }

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${BuildConfig.OPENROUTER_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            android.util.Log.d("Jarvis", "Response code: ${response.code}")
            if (!response.isSuccessful) {
                android.util.Log.e("Jarvis", "Error body: ${responseBody.take(500)}")
                return when (response.code) {
                    429 -> {
                                currentKeyIndex++
                                if (retryCount < apiKeys.size) {
                                    android.util.Log.d("Jarvis", "429 - keyingi keyga o'tildi: $currentKeyIndex")
                                    return sendMessage(context, phoneNumber, userMessage, retryCount + 1)
                                } else {
                                    JarvisResponse(type = "text", message = "⏳ Barcha API keylar band, biroz kuting!")
                                }
                            }
                    400 -> {
                                // Context window to'lib ketgan — tarixni tozalab qayta urinish
                                clearHistory(context, phoneNumber)
                                if (retryCount < 1) {
                                    return sendMessage(context, phoneNumber, userMessage, retryCount + 1)
                                }
                                JarvisResponse(type = "text", message = "⚠️ Xabar uzun edi, qayta yuboring!")
                            }
                    401, 403 -> JarvisResponse(type = "text", message = "🔑 API xatolik!")
                    else -> JarvisResponse(type = "text", message = "⚠️ Xatolik ${response.code}, qayta urinib ko'ring!")
                }
            }

            val text = JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            android.util.Log.d("Jarvis", "AI javobi: $text")
            saveHistory(context, phoneNumber, "user", userMessage)
            saveHistory(context, phoneNumber, "assistant", text)

            parseResponse(text)

        } catch (e: Exception) {
            JarvisResponse(type = "text", message = "⚠️ Xatolik: ${e.message?.take(50)}")
        }
    }

    private fun parseResponse(text: String): JarvisResponse {
        return try {
            val clean = text.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            if (clean.startsWith("{")) {
                val json = JSONObject(clean)
                val cmdsArray = json.optJSONArray("commands")
                val cmdsList = if (cmdsArray != null) {
                    (0 until cmdsArray.length()).map { i ->
                        val c = cmdsArray.getJSONObject(i)
                        JarvisResponse(
                            type = c.optString("type", "text"),
                            message = c.optString("message", ""),
                            query = c.optString("query", ""),
                            city = c.optString("city", ""),
                            action = c.optString("action", ""),
                            cmd = c.optString("cmd", ""),
                            seconds = c.optInt("seconds", 0)
                        )
                    }
                } else emptyList()

                JarvisResponse(
                    type = json.optString("type", "text"),
                    message = json.optString("message", text),
                    query = json.optString("query", ""),
                    city = json.optString("city", ""),
                    action = json.optString("action", ""),
                    cmd = json.optString("cmd", ""),
                    commands = cmdsList,
                    seconds = json.optInt("seconds", 0)
                )
            } else {
                JarvisResponse(type = "text", message = text)
            }
        } catch (e: Exception) {
            JarvisResponse(type = "text", message = text)
        }
    }
}

data class JarvisResponse(
    val type: String,
    val message: String,
    val query: String = "",
    val city: String = "",
    val action: String = "",
    val cmd: String = "",
    val commands: List<JarvisResponse> = emptyList(),
    val seconds: Int = 0
)