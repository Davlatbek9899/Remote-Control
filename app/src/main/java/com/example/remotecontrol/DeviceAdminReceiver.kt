package com.example.remotecontrol

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.d("DeviceAdmin", "Admin huquqi berildi")
    }
    override fun onDisabled(context: Context, intent: Intent) {
        Log.d("DeviceAdmin", "Admin huquqi olindi")
    }
}
