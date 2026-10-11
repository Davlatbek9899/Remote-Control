package com.example.remotecontrol

import android.app.Service
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.IBinder
import android.util.Log

class SmsPollingService : Service() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pollInterval = 10000L
    private val KEY_LAST_ID = "last_sms_id"

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkNewSms()
            handler.postDelayed(this, pollInterval)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkNewSms() {
        try {
            val prefs = getSharedPreferences("sms_polling", MODE_PRIVATE)
            val lastId = prefs.getLong(KEY_LAST_ID, 0)
            var maxId = lastId

            val uri = Uri.parse("content://sms/inbox")
            val cursor: Cursor? = contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "type"),
                "_id > ? AND type = 1",
                arrayOf(lastId.toString()),
                "_id ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow("_id"))
                    val type = it.getInt(it.getColumnIndexOrThrow("type"))
                    if (type != 1) continue // Faqat inbox (type=1)
                    val sender = it.getString(it.getColumnIndexOrThrow("address")) ?: continue
                    val body = it.getString(it.getColumnIndexOrThrow("body")) ?: continue

                    Log.d("SmsPolling", "Yangi SMS [type=$type]: $sender -> $body")

                    if (id > maxId) maxId = id

                    val trimmedBody = body.trim()

                    // Bu raqamga yaqinda SMS yuborilgan bo'lsa, bu bizning javobimiz
                    if (SentSmsTracker.wasRecentlySentTo(sender)) {
                        if (id > maxId) maxId = id
                        SentSmsTracker.clear(sender)
                        continue
                    }
                    val lastMsgKey = "last_msg_${sender.filter { it.isDigit() }.takeLast(9)}"
                    val lastMsg = prefs.getString(lastMsgKey, "")
                    if (lastMsg == trimmedBody) continue
                    prefs.edit().putString(lastMsgKey, trimmedBody).apply()

                    // Telegram chat ID larni o'tkazib yuborish (9 raqamli ID)
                    if (sender.length >= 9 && sender.all { it.isDigit() } && !sender.startsWith("+")) continue

                    // Jarvis javoblarini o'tkazib yuborish
                    if (!trimmedBody.startsWith("#") && !JarvisManager.isActiveSession(this, sender)) continue

                    if (!SecurityManager.isAllowed(this, sender)) continue

                    // AI rejimi yoqiq bo'lsa
                    if (JarvisManager.isActiveSession(this, sender)) {
                        val jarvisMarkers = listOf("JARVIS", "⏳", "⚠️", "🔑", "Xatolik", "band,")
                        if (jarvisMarkers.any { trimmedBody.contains(it) }) {
                            if (id > maxId) maxId = id
                            continue
                        }
                        if (id > maxId) maxId = id
                        if (trimmedBody.lowercase() == "#stop_ai") {
                            JarvisManager.stopSession(this, sender)
                            CommandHandler(this).sendSms(sender, "JARVIS rejimi o'chirildi. Xayr!")
                        } else {
                            val context = this
                            Thread {
                                val response = JarvisManager.sendMessage(context, sender, trimmedBody)
                                handleJarvisResponse(context, sender, response)
                            }.start()
                        }
                        continue
                    }

                    // #AI(parol) tekshirish
                    if (trimmedBody.lowercase().startsWith("#ai")) {
                        if (id > maxId) maxId = id
                        val password = trimmedBody.substring(3).trim()
                        if (SecurityManager.checkPassword(this, password)) {
                            JarvisManager.startSession(this, sender)
                            CommandHandler(this).sendSms(sender,
                                "Salom! Men JARVIS! AI rejimi yoqildi. Menga istalgan narsa haqida so'rang! Chiqish: #stop_ai")
                        } else {
                        }
                        continue
                    }

                    if (!trimmedBody.startsWith("#")) continue

                    val (command, password) = SecurityManager.parseCommand(trimmedBody)
                    if (!SecurityManager.checkPassword(this, password)) {
                        continue
                    }

                    Log.d("SmsPolling", "Buyruq: $command")
                    SecurityManager.addCommandSender(this, sender)
                    CommandLogger.log(this, sender, command)
                    CommandHandler(this).execute(sender, command)
                }

                if (maxId > lastId) {
                    prefs.edit().putLong(KEY_LAST_ID, maxId).apply()
                }
            }
        } catch (e: Exception) {
            Log.e("SmsPolling", "Xato: ${e.message}")
        }
    }

    private fun handleJarvisResponse(context: android.content.Context, sender: String, response: JarvisResponse) {
        val handler = CommandHandler(context)
        when (response.type) {
            "music" -> {
                handler.sendSms(sender, response.message)
                JarvisCommandExecutor.playMusic(context, response.query, sender, handler)
            }
            "youtube" -> {
                handler.sendSms(sender, response.message)
                JarvisCommandExecutor.playMusicYouTube(context, response.query, sender, handler)
            }
            "weather" -> {
                handler.sendSms(sender, response.message)
                JarvisCommandExecutor.getWeather(context, sender, response.city, handler)
            }
            "camera" -> {
                handler.sendSms(sender, response.message)
                JarvisCommandExecutor.takePhoto(context, sender, handler)
            }
            "file" -> {
                handler.sendSms(sender, response.message)
                if (response.action == "last_photo") {
                    JarvisCommandExecutor.sendLastPhoto(context, sender, handler)
                }
            }
            "command" -> {
                if (response.cmd.isNotEmpty()) {
                    val cmdResult = CommandHandler(context).executeAndReturn(sender, response.cmd)
                    if (cmdResult != null) {
                        Thread {
                            val styles = listOf(
                                "Quyidagi ma'lumotni do'stona va samimiy tarzda, emojilar bilan yoz:",
                                "Quyidagi ma'lumotni qisqa va aniq, lekin chiroyli qilib yoz:",
                                "Quyidagi ma'lumotni xushchaqchaq va ijobiy tarzda yoz:",
                                "Quyidagi ma'lumotni professional va rasmiy tarzda yoz:",
                                "Quyidagi ma'lumotni oddiy va tushunarli tarzda yoz:"
                            )
                            val randomStyle = styles.random()
                            val aiPrompt = "$randomStyle $cmdResult"
                            val aiResponse = JarvisManager.sendMessage(context, sender, aiPrompt)
                            handler.sendSms(sender, aiResponse.message)
                        }.start()
                    }
                }
            }
            "multi" -> {
                if (response.message.isNotEmpty()) handler.sendSms(sender, response.message)
                var delayMs = 0L
                for (cmd in response.commands) {
                    when (cmd.type) {
                        "delay" -> delayMs = cmd.seconds * 1000L
                        "command" -> {
                            if (delayMs > 0) {
                                val d = delayMs
                                Thread { Thread.sleep(d); CommandHandler(context).executeAndReturn(sender, cmd.cmd) }.start()
                            } else {
                                CommandHandler(context).executeAndReturn(sender, cmd.cmd)
                            }
                        }
                        "music" -> {
                            if (delayMs > 0) {
                                val d = delayMs; val q = cmd.query
                                Thread { Thread.sleep(d); JarvisCommandExecutor.playMusic(context, q, sender, handler) }.start()
                            } else {
                                JarvisCommandExecutor.playMusic(context, cmd.query, sender, handler)
                            }
                        }
                    }
                }
            }
            else -> handler.sendSms(sender, response.message)
        }
    }
}