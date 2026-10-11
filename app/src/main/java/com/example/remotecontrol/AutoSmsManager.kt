package com.example.remotecontrol

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SubscriptionManager

object AutoSmsManager {

    private const val PREFS = "rc_auto_sms"

    fun isBatteryFullEnabled(context: Context) =
        getPrefs(context).getBoolean("battery_full_enabled", false)

    fun setBatteryFullEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean("battery_full_enabled", enabled).apply()

    fun isBatteryLowEnabled(context: Context) =
        getPrefs(context).getBoolean("battery_low_enabled", false)

    fun setBatteryLowEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean("battery_low_enabled", enabled).apply()

    fun getBatteryLowThreshold(context: Context) =
        getPrefs(context).getInt("battery_low_threshold", 20)

    fun setBatteryLowThreshold(context: Context, value: Int) =
        getPrefs(context).edit().putInt("battery_low_threshold", value).apply()

    fun getAlertPhone(context: Context) =
        getPrefs(context).getString("alert_phone", "") ?: ""

    fun setAlertPhone(context: Context, phone: String) =
        getPrefs(context).edit().putString("alert_phone", phone).apply()

    fun getSimSlot(context: Context) =
        getPrefs(context).getInt("sim_slot", 0)

    fun setSimSlot(context: Context, slot: Int) =
        getPrefs(context).edit().putInt("sim_slot", slot).apply()

    fun getSimCount(context: Context): Int {
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager
            sm.activeSubscriptionInfoList?.size ?: 1
        } catch (e: Exception) { 1 }
    }

    fun getSimNames(context: Context): List<String> {
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as SubscriptionManager
            sm.activeSubscriptionInfoList?.mapIndexed { i, sub ->
                "SIM ${i + 1}: ${sub.displayName}"
            } ?: listOf("SIM 1")
        } catch (e: Exception) { listOf("SIM 1") }
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
