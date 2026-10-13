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

    // Har bir provayder o'z turi, keyi va (OpenAI-uslubidagilar uchun) modeliga ega.
    // Tartib: avval Groq keylar (tez va bepul), keyin OpenRouter, eng oxirida Gemini (zaxira).
    private sealed class AiProvider(val key: String) {
        class Groq(key: String) : AiProvider(key)
        class OpenRouter(key: String) : AiProvider(key)
        class Gemini(key: String) : AiProvider(key)
    }

    private val providers: List<AiProvider> by lazy {
        val list = mutableListOf<AiProvider>()
        listOf(
            BuildConfig.GROQ_KEY_1, BuildConfig.GROQ_KEY_2, BuildConfig.GROQ_KEY_3,
            BuildConfig.GROQ_KEY_4, BuildConfig.GROQ_KEY_5, BuildConfig.GROQ_KEY_6
        ).filter { it.isNotBlank() }.forEach { list.add(AiProvider.Groq(it)) }
        if (BuildConfig.OPENROUTER_KEY.isNotBlank()) list.add(AiProvider.OpenRouter(BuildConfig.OPENROUTER_KEY))
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) list.add(AiProvider.Gemini(BuildConfig.GEMINI_API_KEY))
        list
    }

    // Oxirgi ishlagan provayderdan boshlaymiz — keyingi xabarlar ham shu yerdan davom etadi,
    // har safar o'lik keylarni qayta sinab vaqt yo'qotmaslik uchun.
    private var currentProviderIndex = 0

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
Qo'ng'iroq (aniq raqam aytilgan bo'lsa): {"type":"command","cmd":"#call:+998901234567","message":"Qo'ng'iroq qilinmoqda ☎️"} (raqamni foydalanuvchi aytgan holida, + bilan yoki yo'q, o'zgartirmasdan yoz)
Qo'ng'iroq (ism aytilgan bo'lsa, raqam emas — masalan "Sardorga qo'ng'iroq qil"): {"type":"contact_call","query":"Sardor","message":"Qidirilmoqda..."} — ismni aynan foydalanuvchi aytganidek yoz, o'zgartirma
Kontaktlar ro'yxati (masalan "kontaktlarni ko'rsat", "telefonimdagi kontaktlarni ko'rsat"): {"type":"list_contacts","message":"Kontaktlar ro'yxati:"}
Raqamning oxiri bilan qo'ng'iroq (masalan "9899 raqami bilan tugaydigan raqamga qo'ng'iroq qil"): {"type":"call_by_digits","query":"9899","message":"Qidirilmoqda..."} — query maydoniga FAQAT raqamlarni yoz, boshqa hech narsa qo'shma
Kontaktning raqamini so'rash (qo'ng'iroq qilmasdan, faqat ko'rsatish — masalan "To'lqinning nomeri nechi edi?", "Sardorning raqamini ayt"): {"type":"contact_number","query":"To'lqin","message":"Qidirilmoqda..."} — ismni aynan foydalanuvchi aytganidek yoz
MUHIM: "Qayerdasan?", "Sen qayerdasan?", "Hozir qayerdasan?" kabi savollar JARVIS haqida emas — bular doim QURILMANING joylashuvini so'rash degani. Bunday savolga HAR DOIM #location buyrug'ini qaytar, o'zing haqingda ("men sun'iy intellektman" va h.k.) hech qachon javob berma.
Musiqa: {"type":"music","query":"qo'shiq nomi","message":"Ijro 🎵"}
YouTube: {"type":"youtube","query":"qo'shiq nomi","message":"YouTube 🎬"}
MUHIM (musiqa/YouTube tanlovi): STANDART holat — "music" (fon rejimi, YouTube ilovasi ochilmaydi). FAQAT foydalanuvchi aniq "YouTube'ni och", "YouTube orqali", "YouTube ilovasida" kabi so'z bilan ilovani ochishni so'rasa — "youtube" turini tanla. Boshqa barcha holatda "music" ishlat.
MUHIM (qidiruv so'zini olish): "query" maydoniga FAQAT qo'shiqning/ijrochining HAQIQIY nomini yoz. Agar foydalanuvchi biror narsani RAD ETSA (masalan "...emas", "... bo'lmasin", "... bilan aralashtirma" kabi), o'sha rad etilgan qismni query ichiga HECH QACHON qo'shma — faqat tasdiqlangan (xohlangan) nomni yoz. Misol: "Doxximning Megaladon qo'shig'ini, Itlar qo'shig'ini emas" → query: "Doxxim Megaladon" (ASLO "Itlar" so'zini qo'shma).
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

    // Umumiy (provayderdan mustaqil) xabarlar ro'yxati: har biri role+content juftligi.
    private fun buildMessageHistory(context: Context, phoneNumber: String, userMessage: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val history = getHistory(context, phoneNumber)
        for (i in 0 until history.length()) {
            val h = history.getJSONObject(i)
            list.add(h.getString("role") to h.getString("content"))
        }
        list.add("user" to userMessage)
        return list
    }

    // OpenAI-uslubidagi provayderlar (Groq, OpenRouter) uchun so'rov.
    private fun buildOpenAiRequest(provider: AiProvider, history: List<Pair<String, String>>): Request {
        val url = when (provider) {
            is AiProvider.Groq -> "https://api.groq.com/openai/v1/chat/completions"
            is AiProvider.OpenRouter -> "https://openrouter.ai/api/v1/chat/completions"
            else -> error("OpenAI-uslubidagi provayder emas")
        }
        val model = when (provider) {
            is AiProvider.Groq -> "openai/gpt-oss-120b"
            is AiProvider.OpenRouter -> "meta-llama/llama-4-scout:free"
            else -> error("OpenAI-uslubidagi provayder emas")
        }
        val messages = JSONArray()
        messages.put(JSONObject().apply { put("role", "system"); put("content", SYSTEM_PROMPT) })
        history.forEach { (role, content) ->
            messages.put(JSONObject().apply { put("role", role); put("content", content) })
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 500)
            put("temperature", 0.9)
        }
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${provider.key}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    // Gemini butunlay boshqa so'rov/javob formatiga ega (contents + systemInstruction, role "model").
    private fun buildGeminiRequest(provider: AiProvider.Gemini, history: List<Pair<String, String>>): Request {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${provider.key}"
        val contents = JSONArray()
        history.forEach { (role, content) ->
            contents.put(JSONObject().apply {
                put("role", if (role == "assistant") "model" else "user")
                put("parts", JSONArray().put(JSONObject().apply { put("text", content) }))
            })
        }
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", SYSTEM_PROMPT) }))
            })
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 500)
                put("temperature", 0.9)
            })
        }
        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun extractText(provider: AiProvider, responseBody: String): String {
        val json = JSONObject(responseBody)
        return when (provider) {
            is AiProvider.Gemini -> json.getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0).getString("text")
            else -> json.getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    // "Band" deb hisoblanadigan kodlar — bular kelsa, foydalanuvchiga hech narsa ko'rsatmasdan
    // keyingi provayderga sezilmasdan o'tiladi.
    private fun isSwappableFailure(code: Int) = code == 429 || code == 401 || code == 403 || code >= 500

    fun sendMessage(context: Context, phoneNumber: String, userMessage: String, clearedHistoryOnce: Boolean = false): JarvisResponse {
        if (providers.isEmpty()) {
            return JarvisResponse(type = "text", message = "Hozir javob berolmayapman, birozdan keyin urinib ko'ring.")
        }

        val history = buildMessageHistory(context, phoneNumber, userMessage)

        repeat(providers.size) { attempt ->
            val index = (currentProviderIndex + attempt) % providers.size
            val provider = providers[index]
            try {
                val request = when (provider) {
                    is AiProvider.Gemini -> buildGeminiRequest(provider, history)
                    else -> buildOpenAiRequest(provider, history)
                }
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    android.util.Log.d("Jarvis", "Provayder #$index (${provider::class.simpleName}) xato: ${response.code} — keyingisiga o'tildi")

                    if (response.code == 400 && !clearedHistoryOnce) {
                        // Kontekst oynasi to'lib ketgan bo'lishi mumkin — tarixni tozalab, shu
                        // provayderdan boshlab yana bir bor (foydalanuvchiga sezdirmasdan) sinaymiz.
                        clearHistory(context, phoneNumber)
                        return sendMessage(context, phoneNumber, userMessage, clearedHistoryOnce = true)
                    }

                    if (isSwappableFailure(response.code) || response.code == 400) {
                        currentProviderIndex = (index + 1) % providers.size
                        return@repeat // keyingi provayderni sinaymiz
                    }
                }

                if (response.isSuccessful) {
                    val text = extractText(provider, responseBody)
                    currentProviderIndex = index // shu provayder ishladi — keyingi safar shundan boshlanadi
                    android.util.Log.d("Jarvis", "AI javobi (${provider::class.simpleName}): $text")
                    saveHistory(context, phoneNumber, "user", userMessage)
                    saveHistory(context, phoneNumber, "assistant", text)
                    return parseResponse(text)
                }
            } catch (e: Exception) {
                // Tarmoq xatosi va h.k. — bu ham sezilmasdan keyingi provayderga o'tish sababi
                android.util.Log.d("Jarvis", "Provayder #$index istisno: ${e.message} — keyingisiga o'tildi")
                currentProviderIndex = (index + 1) % providers.size
            }
        }

        // Hammasi urinib ko'rildi va birortasi ham javob bermadi
        return JarvisResponse(type = "text", message = "Hozir javob berolmayapman, birozdan keyin urinib ko'ring.")
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