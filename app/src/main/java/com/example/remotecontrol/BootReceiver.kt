package com.example.remotecontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d("BootReceiver", "Telefon yoqildi — RemoteControl ishga tushmoqda...")

            val serviceIntent = Intent(context, RemoteControlService::class.java)
            context.startForegroundService(serviceIntent)

            Log.d("BootReceiver", "RemoteControl Service ishga tushdi ✅")
        }
    }
}
