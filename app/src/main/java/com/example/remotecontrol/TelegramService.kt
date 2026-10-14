package com.example.remotecontrol

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection

class TelegramService : Service() {

    companion object {
        var instance: TelegramService? = null
    }

    private val TAG = "TelegramService"
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pollInterval = 1000L // 1 sekund

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkUpdates()
            handler.postDelayed(this, pollInterval)
        }
    }

    private var isPolling = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        Log.d(TAG, "TelegramService ishga tushdi! Server orqali polling.")
        startForeground()
        if (!isPolling) {
            isPolling = true
            handler.post(pollRunnable)
        }
        return START_STICKY
    }

    private fun startForeground() {
        val channelId = "telegram_service"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Remote Control", android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Remote Control")
            .setContentText("Faol")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(2, notification)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(pollRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Endi Telegram'ga bevosita ulanmaymiz — bot token endi faqat serverda saqlanadi.
    // Buning o'rniga, o'z parolimiz bilan serverdan "menga tegishli xabar bormi?" deb so'raymiz.
    // Bu bir nechta qurilma bir xil botni bevosita kuzatganda yuzaga kelgan ziddiyatni (boshqa
    // qurilmaning buyrug'i mening telefonimda bajarilib qolishi) butunlay bartaraf qiladi.
    private fun checkUpdates() {
        Log.e(TAG, "checkUpdates chaqirildi")
        Thread {
            try {
                val password = SecurityManager.getPassword(this) ?: return@Thread
                val encodedPassword = java.net.URLEncoder.encode(password, "UTF-8")
                val url = "https://remotecontrol.13-60-189-220.sslip.io/telegram/poll?password=$encodedPassword"

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                val response = connection.inputStream.bufferedReader().readText()

                val json = JSONObject(response)
                val messages = json.getJSONArray("messages")

                for (i in 0 until messages.length()) {
                    val msg = messages.getJSONObject(i)
                    val chatId = msg.getString("chat_id")
                    val msgType = msg.optString("type", "text")

                    when (msgType) {
                        "audio" -> {
                            val fileId = msg.getString("file_id")
                            if (CommandHandler.isDuplicate(chatId, "audio_$fileId")) continue
                            if (!JarvisManager.isActiveSession(this, chatId)) continue
                            val fileName = msg.optString("file_name", "audio.mp3")
                            // AI ga audio haqida xabar yuboramiz, ovoz buyrug'ini ham bajaradi
                            sendTelegram(chatId, "🎵 Audio qabul qilindi, yuklanmoqda...")
                            Thread {
                                val aiResp = JarvisManager.sendMessage(this@TelegramService, chatId,
                                    "Foydalanuvchi audio fayl yubordi: $fileName. Oldingi suhbatda ovoz darajasi haqida so'ralgan bo'lsa, avval ovoz buyrug'ini baj, keyin audio ijro boshlanadi deb ayt. Faqat JSON qaytarma, oddiy javob ber.")
                                if (aiResp.type == "command" && aiResp.cmd.isNotEmpty()) {
                                    TelegramCommandHandler(this@TelegramService, chatId).executeAndReturn(aiResp.cmd)
                                } else if (aiResp.type == "multi") {
                                    for (cmd in aiResp.commands) {
                                        if (cmd.type == "command") TelegramCommandHandler(this@TelegramService, chatId).executeAndReturn(cmd.cmd)
                                    }
                                }
                            }.start()
                            downloadAndPlayAudio(chatId, fileId, fileName)
                        }
                        "photo" -> {
                            val fileId = msg.getString("file_id")
                            val cap = msg.optString("caption", "")
                            val activeChat = JarvisManager.getActiveChatId(this)
                            if (activeChat != null && activeChat != chatId) continue
                            if (!CommandHandler.isDuplicate(chatId, "photo_$fileId")) {
                                downloadAndShowMedia(chatId, fileId, false, cap)
                            }
                        }
                        "video" -> {
                            val fileId = msg.getString("file_id")
                            val cap = msg.optString("caption", "")
                            val activeChatV = JarvisManager.getActiveChatId(this)
                            if (activeChatV != null && activeChatV != chatId) continue
                            if (!CommandHandler.isDuplicate(chatId, "video_$fileId")) {
                                downloadAndShowMedia(chatId, fileId, true, cap)
                            }
                        }
                        else -> {
                            val text = msg.optString("text", "")
                            if (text.isEmpty()) continue
                            Log.d(TAG, "Xabar: $chatId -> $text")
                            if (CommandHandler.isDuplicate(chatId, text)) continue
                            processMessage(chatId, text)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Poll xato: ${e.message}")
            }
        }.start()
    }

    private fun processMessage(chatId: String, text: String) {
        val trimmed = text.trim()

        // Ruxsat tekshirish
        if (!SecurityManager.isAllowed(this, chatId)) return

        // Bir nechta mos kontakt topilib, foydalanuvchidan tanlov so'ralgan bo'lsa —
        // keyingi xabarni avval shu tanlov sifatida tekshiramiz.
        PendingCallManager.getPending(chatId)?.let { pendingState ->
            val selected = PendingCallManager.resolveSelection(trimmed, pendingState.candidates)
            if (selected != null) {
                PendingCallManager.clear(chatId)
                when (pendingState.action) {
                    PendingCallManager.PendingAction.CALL -> {
                        sendTelegram(chatId, "📞 ${selected.first} ga qo'ng'iroq qilinmoqda...")
                        placeCall(selected.second)
                    }
                    PendingCallManager.PendingAction.SHOW_NUMBER -> {
                        sendTelegram(chatId, buildContactNumberMessage(selected.first, selected.second))
                    }
                }
                return
            }
        }

        // #AI(parol) tekshirish — AI rejimini boshlash
        if (trimmed.lowercase().startsWith("#ai")) {
            val password = trimmed.substring(3).trim()
            if (SecurityManager.checkPassword(this, password)) {
                // Agar boshqa chatId AI rejimida bo'lsa — uni o'chirib yangi chatId ga o'tamiz
                val prevChatId = JarvisManager.getActiveChatId(this)
                if (prevChatId != null && prevChatId != chatId) {
                    sendTelegram(prevChatId, "🔴 Boshqa qurilmadan ulanildi. JARVIS rejimi o'chirildi.")
                }
                JarvisManager.startSession(this, chatId)
                JarvisManager.clearHistory(this, chatId)
                sendTelegram(chatId, "🤖 Salom! Men JARVIS — sizning shaxsiy yordamchingizman!\n✅ AI rejimi yoqildi.\n💬 Menga istalgan narsa haqida so'rang!\n🔴 Chiqish uchun: #stop_ai")
            }
            return
        }

        // AI rejimi aktiv bo'lsa — faqat aktiv chatId dan kelgan xabarlarni qabul qilish
        if (JarvisManager.hasActiveSession(this)) {
            if (!JarvisManager.isActiveSession(this, chatId)) return // Boshqa chatId dan kelsa — e'tiborsiz
            if (trimmed.startsWith("#AI") || trimmed.startsWith("#ai")) return
            if (trimmed.lowercase() == "#stop_ai") {
                JarvisManager.stopSession(this, chatId)
                sendTelegram(chatId, "🔴 JARVIS rejimi o'chirildi. Xayr!")
            } else {
                Thread {
                    val response = JarvisManager.sendMessage(this, chatId, trimmed)
                    handleJarvisResponse(chatId, response)
                }.start()
            }
            return
        }

        // Oddiy buyruq rejimi — parolga qarab ishlaydi
        if (!trimmed.startsWith("#")) return

        val (command, password) = SecurityManager.parseCommand(trimmed)
        if (!SecurityManager.checkPassword(this, password)) {
            return
        }

        Log.d(TAG, "Buyruq: $command")
        SecurityManager.addCommandSender(this, chatId)
        CommandLogger.log(this, chatId, command)
        TelegramCommandHandler(this, chatId).execute(chatId, command)
    }

    private fun handleJarvisResponse(chatId: String, response: JarvisResponse) {
        when (response.type) {
            "music" -> {
                sendTelegram(chatId, response.message)
                val mode = response.mode.ifBlank { "single" }
                JarvisCommandExecutor.playMusic(this, response.query, chatId, null, mode)
            }
            "youtube" -> {
                sendTelegram(chatId, response.message)
                JarvisCommandExecutor.playMusicYouTube(this, response.query, chatId, null)
            }
            "weather" -> {
                sendTelegram(chatId, response.message)
                JarvisCommandExecutor.getWeather(this, chatId, response.city, CommandHandler(this))
            }
            "list_contacts" -> {
                Thread {
                    val contacts = ContactsHelper.getAllContacts(this)
                    if (contacts.isEmpty()) {
                        sendTelegram(chatId, "📇 Kontaktlar topilmadi.")
                    } else {
                        // Telegram xabar uzunligi cheklangani uchun katta ro'yxatni bo'laklarga bo'lib yuboramiz
                        contacts.map { it.first }.chunked(50).forEachIndexed { i, chunk ->
                            val header = if (i == 0) "📇 Kontaktlar (${contacts.size} ta):\n" else ""
                            sendTelegram(chatId, header + chunk.joinToString("\n"))
                        }
                    }
                }.start()
            }
            "call_by_digits" -> {
                val digits = response.query.filter { it.isDigit() }
                Thread {
                    val matches = PhoneLookupHelper.findByLastDigits(this, digits)
                    when {
                        matches.isEmpty() -> sendTelegram(chatId, "❌ $digits raqami bilan tugaydigan raqam topilmadi!")
                        matches.size == 1 -> {
                            val m = matches[0]
                            sendTelegram(chatId, buildCallMatchMessage(m))
                            placeCall(m.number)
                        }
                        else -> {
                            PendingCallManager.setPending(
                                chatId,
                                matches.map { PhoneLookupHelper.formatUzPhone(it.number) to it.number },
                                PendingCallManager.PendingAction.CALL
                            )
                            val list = matches.mapIndexed { i, m ->
                                "${i + 1}. 📱 ${PhoneLookupHelper.formatUzPhone(m.number)}"
                            }.joinToString("\n\n")
                            sendTelegram(
                                chatId,
                                "🔍 $digits raqami bilan tugaydigan ${matches.size} ta raqam topildi. Qaysi biriga qo'ng'iroq qilay:\n\n$list"
                            )
                        }
                    }
                }.start()
            }
            "contact_call" -> {
                val query = response.query
                Thread {
                    val matches = ContactsHelper.searchContacts(this, query)
                    when {
                        matches.isEmpty() -> sendTelegram(chatId, "❌ \"$query\" nomli kontakt topilmadi.")
                        matches.size == 1 -> {
                            val (name, number) = matches[0]
                            sendTelegram(chatId, "📞 $name ga qo'ng'iroq qilinmoqda...")
                            placeCall(number)
                        }
                        else -> {
                            PendingCallManager.setPending(chatId, matches, PendingCallManager.PendingAction.CALL)
                            val list = matches.mapIndexed { i, (name, _) -> "${i + 1}. $name" }.joinToString("\n")
                            sendTelegram(chatId, "🔎 Bir nechta mos kontakt topildi, qaysi biriga qo'ng'iroq qilay?\n$list")
                        }
                    }
                }.start()
            }
            "contact_number" -> {
                val query = response.query
                Thread {
                    val matches = ContactsHelper.searchContacts(this, query)
                    when {
                        matches.isEmpty() -> sendTelegram(chatId, "❌ \"$query\" nomli kontakt topilmadi.")
                        matches.size == 1 -> {
                            val (name, number) = matches[0]
                            sendTelegram(chatId, buildContactNumberMessage(name, number))
                        }
                        else -> {
                            PendingCallManager.setPending(chatId, matches, PendingCallManager.PendingAction.SHOW_NUMBER)
                            val list = matches.mapIndexed { i, (name, _) -> "${i + 1}. $name" }.joinToString("\n")
                            sendTelegram(chatId, "🔎 Bir nechta mos kontakt topildi, qaysi birini ko'rsataman?\n$list")
                        }
                    }
                }.start()
            }
            "command" -> {
                if (response.cmd.isNotEmpty()) {
                    val cmdResult = TelegramCommandHandler(this, chatId).executeAndReturn(response.cmd)
                    if (cmdResult != null) {
                        Thread {
                            val styles = listOf(
                                "Quyidagi ma'lumotni do'stona va samimiy tarzda, emojilar bilan yoz:",
                                "Quyidagi ma'lumotni qisqa va aniq, lekin chiroyli qilib yoz:",
                                "Quyidagi ma'lumotni xushchaqchaq va ijobiy tarzda yoz:",
                                "Quyidagi ma'lumotni professional va rasmiy tarzda yoz:"
                            )
                            val aiPrompt = "${styles.random()} $cmdResult"
                            val aiResponse = JarvisManager.sendMessage(this@TelegramService, chatId, aiPrompt)
                            sendTelegram(chatId, aiResponse.message)
                        }.start()
                    }
                }
            }
            "multi" -> {
                if (response.message.isNotEmpty()) sendTelegram(chatId, response.message)
                var delayMs = 0L
                for (cmd in response.commands) {
                    when (cmd.type) {
                        "delay" -> delayMs = cmd.seconds * 1000L
                        "command" -> {
                            if (delayMs > 0) {
                                val d = delayMs
                                Thread { Thread.sleep(d); TelegramCommandHandler(this, chatId).executeAndReturn(cmd.cmd) }.start()
                            } else {
                                TelegramCommandHandler(this, chatId).executeAndReturn(cmd.cmd)
                            }
                        }
                        "music" -> {
                            val m = cmd.mode.ifBlank { "single" }
                            if (delayMs > 0) {
                                val d = delayMs; val q = cmd.query
                                Thread { Thread.sleep(d); JarvisCommandExecutor.playMusic(this@TelegramService, q, chatId, null, m) }.start()
                            } else {
                                JarvisCommandExecutor.playMusic(this, cmd.query, chatId, null, m)
                            }
                        }
                    }
                }
            }
            else -> sendTelegram(chatId, response.message)
        }
    }

    private fun buildContactNumberMessage(name: String, rawNumber: String): String {
        val formatted = PhoneLookupHelper.formatUzPhone(rawNumber.filter { it.isDigit() })
        return "✅ Kontakt topildi:\n👤 Ism: $name\n📱 Raqam: $formatted"
    }

    private fun buildCallMatchMessage(m: PhoneLookupHelper.NumberMatch): String {
        val formatted = PhoneLookupHelper.formatUzPhone(m.number)
        val dateLine = m.lastCallMillis?.let {
            "🕐 Oxirgi marta qo'ng'iroq qilgan: ${PhoneLookupHelper.formatCallDate(it)}\n"
        } ?: ""
        return if (m.contactName != null) {
            "✅ Kontaktlar ichida topildi:\n👤 Ism: ${m.contactName}\n📱 Raqam: $formatted\n$dateLine" +
                    "📞 Qo'ng'iroq qilinmoqda....."
        } else {
            "🕓 Kontaktlar ichida topilmadi, qo'ng'iroq tarixida topildi:\n📱 Raqam: $formatted\n$dateLine" +
                    "📞 Qo'ng'iroq qilinmoqda....."
        }
    }

    private fun placeCall(number: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("number", number)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    fun sendTelegramLocation(chatId: String, lat: Double, lng: Double) {
        Thread {
            try {
                val url = "https://remotecontrol.13-60-189-220.sslip.io/telegram/send"
                val body = JSONObject().apply {
                    put("chat_id", chatId)
                    put("type", "location")
                    put("lat", lat)
                    put("lng", lng)
                }.toString()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.write(body.toByteArray())
                connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "Location yuborildi: $lat, $lng")
            } catch (e: Exception) {
                Log.e(TAG, "Location xato: ${e.message}")
            }
        }.start()
    }

    fun sendTelegram(chatId: String, text: String) {
        Thread {
            try {
                val url = "https://remotecontrol.13-60-189-220.sslip.io/telegram/send"
                val body = JSONObject().apply {
                    put("chat_id", chatId)
                    put("type", "text")
                    put("text", text)
                }.toString()

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.write(body.toByteArray())
                connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "Telegram xabar yuborildi: $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "Telegram yuborish xatosi: ${e.message}")
            }
        }.start()
    }

    private fun downloadAndShowMedia(chatId: String, fileId: String, isVideo: Boolean, caption: String) {
        Thread {
            try {
                val token = BuildConfig.TELEGRAM_BOT_TOKEN
                // File path olish
                val fileInfoUrl = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
                val conn1 = java.net.URL(fileInfoUrl).openConnection() as java.net.HttpURLConnection
                conn1.connectTimeout = 10000
                val fileInfo = org.json.JSONObject(conn1.inputStream.bufferedReader().readText())
                val filePath = fileInfo.getJSONObject("result").getString("file_path")
                val ext = if (isVideo) ".mp4" else ".jpg"
                val localFile = java.io.File(cacheDir, "media_${System.currentTimeMillis()}$ext")

                // Yuklab olish
                val downloadUrl = "https://api.telegram.org/file/bot$token/$filePath"
                val conn2 = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
                conn2.connectTimeout = 30000
                conn2.readTimeout = 30000
                localFile.outputStream().use { out ->
                    conn2.inputStream.copyTo(out)
                }

                android.util.Log.d(TAG, "Media yuklandi: ${localFile.absolutePath}")

                // OverlayMediaActivity ni ochish
                val intent = android.content.Intent(this, OverlayMediaActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("file_path", localFile.absolutePath)
                    putExtra("is_video", isVideo)
                    putExtra("caption", caption)
                }
                startActivity(intent)

                val type = if (isVideo) "🎬 Video" else "🖼 Rasm"
                sendTelegram(chatId, "$type telefoningizda ko'rsatilmoqda!")

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Media xato: ${e.message}")
                sendTelegram(chatId, "⚠️ Media xato: ${e.message?.take(50)}")
            }
        }.start()
    }

    private fun downloadAndPlayAudio(chatId: String, fileId: String, fileName: String) {
        Thread {
            try {
                val token = BuildConfig.TELEGRAM_BOT_TOKEN
                // 1. File path olish
                val fileInfoUrl = "https://api.telegram.org/bot$token/getFile?file_id=$fileId"
                val conn1 = java.net.URL(fileInfoUrl).openConnection() as java.net.HttpURLConnection
                conn1.connectTimeout = 10000
                val fileInfo = org.json.JSONObject(conn1.inputStream.bufferedReader().readText())
                val filePath = fileInfo.getJSONObject("result").getString("file_path")

                // 2. Faylni yuklab olish
                val downloadUrl = "https://api.telegram.org/file/bot$token/$filePath"
                val audioFile = java.io.File(cacheDir, fileName)
                val conn2 = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
                conn2.connectTimeout = 30000
                conn2.readTimeout = 60000
                audioFile.outputStream().use { out ->
                    conn2.inputStream.copyTo(out)
                }

                Log.d(TAG, "Audio yuklandi: ${audioFile.absolutePath}")

                // 3. AudioPlayerService ga yuborish
                val intent = Intent(this, AudioPlayerService::class.java).apply {
                    putExtra("audio_path", audioFile.absolutePath)
                    putExtra("audio_name", fileName)
                    putExtra("chat_id", chatId)
                }
                startService(intent)
                sendTelegram(chatId, "▶️ Ijro boshlandi: $fileName")

            } catch (e: Exception) {
                Log.e(TAG, "Audio xatosi: ${e.message}")
                sendTelegram(chatId, "❌ Audio yuklab bo'lmadi: ${e.message?.take(50)}")
            }
        }.start()
    }

    fun sendTelegramPhoto(chatId: String, photoPath: String, caption: String = "") {
        Thread {
            try {
                val url = "https://remotecontrol.13-60-189-220.sslip.io/telegram/send_photo"
                val file = java.io.File(photoPath)
                if (!file.exists()) {
                    sendTelegram(chatId, "❌ Fayl topilmadi")
                    return@Thread
                }

                val boundary = "----FormBoundary${System.currentTimeMillis()}"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.doOutput = true

                val output = connection.outputStream
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n".toByteArray())
                output.write("$chatId\r\n".toByteArray())
                if (caption.isNotEmpty()) {
                    output.write("--$boundary\r\n".toByteArray())
                    output.write("Content-Disposition: form-data; name=\"caption\"\r\n\r\n".toByteArray())
                    output.write("$caption\r\n".toByteArray())
                }
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"\r\n".toByteArray())
                output.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
                output.write(file.readBytes())
                output.write("\r\n--$boundary--\r\n".toByteArray())

                connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "Rasm yuborildi: $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "Rasm yuborish xatosi: ${e.message}")
                sendTelegram(chatId, "❌ Rasmni yuborishda xatolik")
            }
        }.start()
    }
}