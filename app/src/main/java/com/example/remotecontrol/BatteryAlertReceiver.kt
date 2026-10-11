package com.example.remotecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log

class BatteryAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("rc_auto_sms", Context.MODE_PRIVATE)

        when (intent.action) {
            Intent.ACTION_BATTERY_LOW -> {
                if (!prefs.getBoolean("battery_low_enabled", false)) return
                val level = getBatteryLevel(context)
                val phone = prefs.getString("alert_phone", "") ?: ""
                if (phone.isEmpty()) return
                sendSms(context, phone, "🔋 Diqqat! Batareya quvvati kam qoldi: $level%")
                Log.d("BatteryAlert", "Battery low SMS yuborildi")
            }
            Intent.ACTION_BATTERY_OKAY -> {
                // Bu ACTION faqat low dan normal ga qaytganda ishga tushadi
            }
            Intent.ACTION_POWER_CONNECTED -> {
                // Zaryadlash boshlanganda tekshirish uchun
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                // Zaryadlash to'xtaganda
            }
            "com.example.remotecontrol.BATTERY_FULL" -> {
                if (!prefs.getBoolean("battery_full_enabled", false)) return
                val phone = prefs.getString("alert_phone", "") ?: ""
                if (phone.isEmpty()) return
                sendSms(context, phone, "🔋 Batareya to'liq zaryadlandi! 100%")
                Log.d("BatteryAlert", "Battery full SMS yuborildi")
            }
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun sendSms(context: Context, phone: String, message: String) {
        try {
            val prefs = context.getSharedPreferences("rc_auto_sms", Context.MODE_PRIVATE)
            val simSlot = prefs.getInt("sim_slot", 0)

            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager
            val subscriptions = subscriptionManager.activeSubscriptionInfoList

            if (subscriptions != null && subscriptions.size > simSlot) {
                val subId = subscriptions[simSlot].subscriptionId
                val smsManager = SmsManager.getSmsManagerForSubscriptionId(subId)
                smsManager.sendTextMessage(phone, null, message, null, null)
            } else {
                SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
            }
        } catch (e: Exception) {
            Log.e("BatteryAlert", "SMS xato: ${e.message}")
        }
    }
}
