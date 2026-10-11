package com.example.remotecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = listOf(
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
            "android.provider.Telephony.SMS_DELIVER"
        )
        if (intent.action !in validActions) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (message in messages) {
            val sender = message.originatingAddress ?: "Unknown"
            val body = message.messageBody ?: continue

            Log.d("SmsReceiver", "SMS keldi | Kimdan: $sender | Matn: $body")

            val trimmedBody = body.trim()

            // Oxirgi yuborilgan SMS bilan taqqoslash
            val prefs = context.getSharedPreferences("sms_sent_track", Context.MODE_PRIVATE)
            val lastSent = prefs.getString("last_sent_to_${sender.filter{it.isDigit()}.takeLast(9)}", "")
            if (lastSent == trimmedBody) {
                Log.d("SmsReceiver", "O'z SMS o'tkazib yuborildi")
                continue
            }

            // Raqam bloklangan?
            if (!SecurityManager.isAllowed(context, sender)) {
                Log.w("SmsReceiver", "Bloklangan raqam: $sender")
                continue
            }

            // AI rejimi yoqiq bo'lsa — AI ga yuborish
            if (JarvisManager.isActiveSession(context, sender)) {
                // JARVIS ning o'z javoblarini o'tkazib yuborish
                val jarvisMarkers = listOf("JARVIS", "⏳", "⚠️", "🔑", "Xatolik", "band,")
                if (jarvisMarkers.any { trimmedBody.contains(it) }) continue
                // #stop_ai buyrug'i bilan AI rejimini o'chirish
                if (trimmedBody.lowercase() == "#stop_ai") {
                    JarvisManager.stopSession(context, sender)
                    CommandHandler(context).sendSms(sender, "🔴 JARVIS rejimi o'chirildi. Xayr!")
                    continue
                }

                // AI ga xabar yuborish
                Thread {
                    val response = JarvisManager.sendMessage(context, sender, trimmedBody)
                    handleJarvisResponse(context, sender, response)
                }.start()
                continue
            }

            // #AI(parol) formatini tekshirish
            if (trimmedBody.lowercase().startsWith("#ai")) {
                val password = trimmedBody.substring(3).trim()
                if (SecurityManager.checkPassword(context, password)) {
                    JarvisManager.startSession(context, sender)
                    CommandHandler(context).sendSms(sender,
                        "🤖 Salom! Men JARVIS — sizning shaxsiy yordamchingizman!\n" +
                        "✅ AI rejimi yoqildi.\n" +
                        "💬 Menga istalgan narsa haqida so'rang!\n" +
                        "🔴 Chiqish uchun: #stop_ai")
                } else {
                }
                continue
            }

            // Oddiy buyruqlar (#ping, #flash_on va boshqalar)
            if (!trimmedBody.startsWith("#")) continue

            val (command, password) = SecurityManager.parseCommand(trimmedBody)

            if (!SecurityManager.checkPassword(context, password)) {
                Log.w("SmsReceiver", "Noto'g'ri parol: $password")
                continue
            }

            Log.d("SmsReceiver", "Buyruq qabul qilindi: $command")
            SecurityManager.addCommandSender(context, sender)
            CommandLogger.log(context, sender, command)
            CommandHandler(context).execute(sender, command)
        }
    }

    private fun handleJarvisResponse(context: Context, sender: String, response: JarvisResponse) {
        val handler = CommandHandler(context)

        when (response.type) {
            "text" -> {
                handler.sendSms(sender, response.message)
            }
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
                    // Buyruqni bajarib natijani AI orqali chiroyli yuborish
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
            else -> {
                handler.sendSms(sender, response.message)
            }
        }
    }
}