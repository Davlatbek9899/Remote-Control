package com.example.remotecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat

class BatteryMonitorService : Service() {

    private var receiver: BroadcastReceiver? = null
    private var lowSmsSent = false
    private var fullSmsSent = false

    override fun onCreate() {
        super.onCreate()
        startForeground(2002, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun buildNotification() = NotificationCompat.Builder(this, createChannel())
        .setContentTitle("Battery Monitor")
        .setContentText("Quvvat kuzatilmoqda")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()

    private fun createChannel(): String {
        val id = "battery_monitor"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(id, "Battery Monitor", NotificationManager.IMPORTANCE_LOW)
        )
        return id
    }

    private fun startMonitoring() {
        // Eski receiver ni o'chirib yangi boshlaymiz
        receiver?.let { unregisterReceiver(it) }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val prefs = getSharedPreferences("rc_auto_sms", Context.MODE_PRIVATE)
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percent = if (scale > 0) (level * 100 / scale) else return
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                val phone = prefs.getString("alert_phone", "") ?: ""

                if (phone.isEmpty()) {
                    Log.w("BatteryMonitor", "Raqam kiritilmagan!")
                    return
                }

                // QUVVAT TOLDI
                if (prefs.getBoolean("battery_full_enabled", false)) {
                    if (isCharging && percent >= 100 && !fullSmsSent) {
                        fullSmsSent = true
                        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                        sendSms(phone, "✅ Telefon toʻliq zaryadlandi!\n🔋 Quvvat: 100%\n🕐 Vaqt: $time\nZaryadni uzishingiz mumkin.")
                        Log.d("BatteryMonitor", "Full SMS yuborildi")
                    }
                    if (!isCharging) fullSmsSent = false
                }

                // QUVVAT KAM
                if (prefs.getBoolean("battery_low_enabled", false)) {
                    val threshold = prefs.getInt("battery_low_threshold", 19)
                    if (!isCharging && percent <= threshold && !lowSmsSent) {
                        lowSmsSent = true
                        val time2 = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                        sendSms(phone, "⚠️ Telefon quvvati kam!\n🔋 Quvvat: $percent%\n🕐 Vaqt: $time2\nZaryadga ulang.")
                        Log.d("BatteryMonitor", "Low SMS yuborildi: $percent%")
                    }
                    if (isCharging) lowSmsSent = false
                }
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        Log.d("BatteryMonitor", "Monitoring boshlandi")
    }

    private fun sendSms(phone: String, message: String) {
        try {
            val prefs = getSharedPreferences("rc_auto_sms", Context.MODE_PRIVATE)
            val simSlot = prefs.getInt("sim_slot", 0)
            val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subs = sm.activeSubscriptionInfoList
            val smsManager = if (subs != null && subs.size > simSlot)
                SmsManager.getSmsManagerForSubscriptionId(subs[simSlot].subscriptionId)
            else SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            Log.d("BatteryMonitor", "SMS yuborildi: $phone")
        } catch (e: Exception) {
            Log.e("BatteryMonitor", "SMS xato: ${e.message}")
        }
    }

    override fun onDestroy() {
        receiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}