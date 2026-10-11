package com.example.remotecontrol

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

object JarvisCommandExecutor {

    // Musiqa ijro qilish
    fun playMusic(context: Context, query: String, sender: String = "", handler: CommandHandler? = null) {
        Thread {
            try {
                // Backend orqali audio URL olish
                val serverUrl = "https://ungritty-tonisha-nonmedical.ngrok-free.dev/audio/stream?query=${Uri.encode(query)}"
                val conn = java.net.URL(serverUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 20000
                conn.setRequestProperty("ngrok-skip-browser-warning", "true")
                val responseText = conn.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(responseText)
                val audioUrl = json.getString("audio_url")
                val title = json.getString("title")
                Log.d("Jarvis", "Audio URL olindi: $title")
                val intent = android.content.Intent(context, AudioPlayerService::class.java).apply {
                    putExtra("audio_path", audioUrl)
                    putExtra("audio_name", title)
                    putExtra("is_url", true)
                }
                context.startService(intent)
                handler?.sendSms(sender, "🎵 Ijro qilinmoqda: $title")
            } catch (e: Exception) {
                Log.e("Jarvis", "Musiqa xatosi: ${e.message}")
                handler?.sendSms(sender, "❌ Musiqa topilmadi: ${e.message?.take(50)}")
            }
        }.start()
    }

    // YouTube ilovasi orqali musiqa ijro
    fun playMusicYouTube(context: Context, query: String, sender: String = "", handler: CommandHandler? = null) {
        Thread {
            try {
                val apiKey = BuildConfig.YOUTUBE_API_KEY
                val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=${Uri.encode(query)}&type=video&videoCategoryId=10&key=$apiKey&maxResults=1"
                val conn = java.net.URL(searchUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
                val items = json.getJSONArray("items")
                if (items.length() > 0) {
                    val videoId = items.getJSONObject(0).getJSONObject("id").getString("videoId")
                    val title = items.getJSONObject(0).getJSONObject("snippet").getString("title")
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
                    handler?.sendSms(sender, "🎬 YouTube da ijro qilinmoqda: $title")
                } else {
                    handler?.sendSms(sender, "❌ Topilmadi: $query")
                }
            } catch (e: Exception) {
                handler?.sendSms(sender, "❌ Xatolik: ${e.message?.take(30)}")
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
    fun sendLastPhoto(context: Context, sender: String, handler: CommandHandler) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
            )
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    handler.sendSms(sender, "📸 Oxirgi rasm topildi: $path\n(Telegram bot orqali yuborish tez orada qo'shiladi)")
                } else {
                    handler.sendSms(sender, "📭 Hech qanday rasm topilmadi")
                }
            }
        } catch (e: Exception) {
            handler.sendSms(sender, "⚠️ Rasmni topib bo'lmadi: ${e.message?.take(30)}")
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