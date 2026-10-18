package com.example.remotecontrol

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

object JarvisCommandExecutor {

    // Telegram orqali chaqirilganda handler har doim null bo'ladi — shuning uchun
    // natija xabari (muvaffaqiyat yoki xato) Telegram'ning o'ziga to'g'ridan-to'g'ri yuboriladi.
    private fun notify(sender: String, handler: CommandHandler?, message: String) {
        val telegramService = TelegramService.instance
        if (telegramService != null && sender.isNotEmpty() && sender.all { it.isDigit() || it == '-' }) {
            telegramService.sendTelegram(sender, message)
        } else {
            handler?.sendSms(sender, message)
        }
    }

    // YouTube Data API orqali qidirish — ikkala (fon va YouTube ilovasi) rejimda ham ishlatiladi
    private fun searchYoutube(apiKey: String, query: String): Pair<String, String>? {
        val found = searchYoutubeFull(apiKey, query) ?: return null
        return found.first to found.second
    }

    // "Keyingi" (Next) uchun: bir nechta natija so'rab, allaqachon ijro etilganlarini
    // chetlab, birinchi YANGI qo'shiqni qaytaradi (bitta natija bilan deyarli har doim
    // hozir ijro bo'layotgan qo'shiqning o'zi qaytib, hech narsa o'zgarmas edi).
    fun searchYoutubeExcluding(apiKey: String, query: String, excludeIds: Set<String>): Triple<String, String, String>? {
        val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=${Uri.encode(query)}&type=video&videoCategoryId=10&key=$apiKey&maxResults=10"
        val conn = java.net.URL(searchUrl).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
        val items = json.getJSONArray("items")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val videoId = item.getJSONObject("id").getString("videoId")
            if (videoId !in excludeIds) {
                val snippet = item.getJSONObject("snippet")
                return Triple(videoId, snippet.getString("title"), snippet.getString("channelTitle"))
            }
        }
        return null
    }

    // Ijrochi nomini (channelTitle) ham qaytaradigan to'liq versiya — "davom etish"/"ijrochi"
    // rejimlari uchun kerak (keyingi o'xshash qo'shiqni shu ijrochi bo'yicha qidirish uchun)
    fun searchYoutubeFull(apiKey: String, query: String): Triple<String, String, String>? {
        val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=${Uri.encode(query)}&type=video&videoCategoryId=10&key=$apiKey&maxResults=1"
        val conn = java.net.URL(searchUrl).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
        val items = json.getJSONArray("items")
        if (items.length() == 0) return null
        val snippet = items.getJSONObject(0).getJSONObject("snippet")
        val videoId = items.getJSONObject(0).getJSONObject("id").getString("videoId")
        return Triple(videoId, snippet.getString("title"), snippet.getString("channelTitle"))
    }

    // Musiqa ijro qilish — fon rejimida, YouTube ilovasini ochmasdan.
    // Audio havolasini "o'g'irlash" o'rniga, YouTube'ning rasmiy IFrame Player'ini
    // ko'rinmas oynada ishga tushiramiz (YouTubeAudioService) — bot-tekshiruvi yo'q.
    fun playMusic(context: Context, query: String, sender: String = "", handler: CommandHandler? = null, mode: String = "single") {
        Thread {
            try {
                // "artist" rejimida query — ijrochi nomining o'zi, shu bo'yicha birinchi qo'shiqni topamiz
                val found = searchYoutubeFull(BuildConfig.YOUTUBE_API_KEY, query)
                if (found == null) {
                    notify(sender, handler, "❌ \"$query\" bo'yicha video topilmadi, boshqacha nomlab qayta urinib ko'ring.")
                    return@Thread
                }
                val (videoId, title, channelTitle) = found
                val artistSeed = if (mode == "artist") query else channelTitle
                val intent = android.content.Intent(context, YouTubeAudioService::class.java).apply {
                    putExtra("video_id", videoId)
                    putExtra("title", title)
                    putExtra("mode", mode)
                    putExtra("artist", artistSeed)
                    putExtra("fresh", true)
                }
                context.startService(intent)
                notify(sender, handler, "🎵 Ijro qilinmoqda: $title")
            } catch (e: Exception) {
                Log.e("Jarvis", "Musiqa xatosi: ${e.message}")
                notify(sender, handler, "❌ Xatolik: ${e.message?.take(50)}")
            }
        }.start()
    }

    // Musiqani to'xtatish
    fun stopMusic(context: Context) {
        YouTubeAudioService.stop(context)
    }

    // YouTube ilovasi orqali musiqa ijro
    fun playMusicYouTube(context: Context, query: String, sender: String = "", handler: CommandHandler? = null) {
        Thread {
            try {
                val found = searchYoutube(BuildConfig.YOUTUBE_API_KEY, query)
                if (found != null) {
                    val (videoId, title) = found
                    val packages = listOf("com.android.youtube.premium", "com.google.android.youtube")
                    var opened = false
                    for (pkg in packages) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://www.youtube.com/watch?v=$videoId")
                                setPackage(pkg)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            opened = true
                            break
                        } catch (e: Exception) { continue }
                    }
                    if (!opened) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://www.youtube.com/watch?v=$videoId")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                    notify(sender, handler, "🎬 YouTube da ijro qilinmoqda: $title")
                } else {
                    notify(sender, handler, "❌ \"$query\" bo'yicha video topilmadi, boshqacha nomlab qayta urinib ko'ring.")
                }
            } catch (e: Exception) {
                notify(sender, handler, "❌ Xatolik: ${e.message?.take(30)}")
            }
        }.start()
    }

    // Ob-havo ma'lumotlari
    fun getWeather(context: Context, sender: String, city: String, handler: CommandHandler) {
        Thread {
            try {
                val url = java.net.URL("https://wttr.in/${Uri.encode(city)}?format=3")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().readText()
                handler.sendSms(sender, "🌤 $response")
            } catch (e: Exception) {
                handler.sendSms(sender, "⚠️ Ob-havo ma'lumotini ololmadim: ${e.message?.take(30)}")
            }
        }.start()
    }

    // Oxirgi rasmni yuborish
    // count=1 -> "oxirgi rasm", count=5 -> "oxirgi 5 ta rasm".
    // photoMode: "any" (har qanday), "camera" (faqat kamera, skrinshotlar chetlab o'tiladi),
    // "screenshot" (faqat skrinshotlar) — skrinshotlar nomi yoki saqlangan papkasi bo'yicha aniqlanadi.
    fun sendLastPhotos(context: Context, sender: String, handler: CommandHandler?, count: Int, photoMode: String) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val screenshotClause = "(${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?)"
            val selection = when (photoMode) {
                "screenshot" -> screenshotClause
                "camera" -> "NOT $screenshotClause"
                else -> null
            }
            val selectionArgs = if (photoMode == "screenshot" || photoMode == "camera") {
                arrayOf("%Screenshot%", "%Screenshot%")
            } else null

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            val paths = mutableListOf<String>()
            cursor?.use {
                while (it.moveToNext() && paths.size < count) {
                    paths.add(it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)))
                }
            }

            if (paths.isEmpty()) {
                notify(sender, handler, if (photoMode == "screenshot") "📭 Hech qanday skrinshot topilmadi" else "📭 Hech qanday rasm topilmadi")
                return
            }

            val telegramService = TelegramService.instance
            if (telegramService == null) {
                notify(sender, handler, "⚠️ Bu funksiya hozircha faqat Telegram orqali ishlaydi")
                return
            }
            paths.forEach { path -> telegramService.sendTelegramPhoto(sender, path) }
        } catch (e: Exception) {
            notify(sender, handler, "⚠️ Rasmlarni yuborishda xatolik: ${e.message?.take(50)}")
        }
    }

    // Rasm olish
    fun takePhoto(context: Context, sender: String, handler: CommandHandler) {
        try {
            val intent = Intent(context, CameraActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("sender", sender)
                putExtra("silent", true)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            handler.sendSms(sender, "⚠️ Kamera ishga tushmadi: ${e.message?.take(30)}")
        }
    }
}