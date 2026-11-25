package com.example.remotecontrol

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

// Jarvis orqali "ertaga soat 8da SMS yoz" kabi vaqt belgilangan xabarlarni amalga oshiradi.
// AlarmManager orqali belgilangan vaqtda ishga tushadi (ilova yopiq bo'lsa ham ishlaydi).
class ScheduledSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: return
        val text = intent.getStringExtra("text") ?: return
        CommandHandler(context).sendSms(number, text)
    }

    companion object {
        fun schedule(context: Context, number: String, text: String, delaySeconds: Long) {
            // Har bir rejalashtirilgan SMS o'ziga xos so'rov kodi oladi — bir nechtasi
            // bir-birini bosib yubormasligi uchun.
            val requestCode = (number + text + System.currentTimeMillis()).hashCode()
            val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
                putExtra("number", number)
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
                    // Aniq vaqt ruxsati yo'q bo'lsa, taxminiy (inexact) alarm bilan davom etamiz
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
