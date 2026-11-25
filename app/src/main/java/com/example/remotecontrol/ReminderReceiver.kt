package com.example.remotecontrol

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

// Jarvis orqali "2 soatdan keyin onamga qo'ng'iroq qilishim kerak, meni eslat" kabi
// eslatmalarni amalga oshiradi. Belgilangan vaqt kelganda IKKALA usul bilan birdan eslatadi:
// 1) Telegram botdan xabar, 2) telefon ekranida (hatto o'chiq bo'lsa ham) to'liq ekranli karta.
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra("chat_id") ?: ""
        val text = intent.getStringExtra("text") ?: return

        // 1) Telegram orqali
        if (chatId.isNotEmpty()) {
            TelegramService.instance?.sendTelegram(chatId, "⏰ Eslatma: $text")
        }

        // 2) Ekranda (overlay) — hatto ekran o'chiq/qulflangan bo'lsa ham ko'rinadi
        val overlayIntent = Intent(context, OverlayMediaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("display_text", text)
            putExtra("chip_label", "⏰ Eslatma")
        }
        context.startActivity(overlayIntent)
    }

    companion object {
        fun schedule(context: Context, chatId: String, text: String, delaySeconds: Long) {
            val requestCode = (chatId + text + System.currentTimeMillis()).hashCode()
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("text", text)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = SystemClock.elapsedRealtime() + delaySeconds * 1000
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: Exception) {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        }
    }
}
