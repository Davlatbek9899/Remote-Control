package com.example.remotecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RCAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RCAccessibilityService? = null
    }

    private var tapScheduled = false

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val action = pendingAction ?: return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.android.settings") return
        if (tapScheduled) return  // Allaqachon rejalashtirilgan

        tapScheduled = true
        pendingAction = null  // Darhol null qilamiz

        Handler(Looper.getMainLooper()).postDelayed({
            android.util.Log.d("RCAccess", "Tap bajarilmoqda: $action")
            when (action) {
                "wifi" -> performTap(1058f, 546f)
                "bluetooth" -> performTap(1058f, 546f)
                "gps_on", "gps_off" -> performTap(1058f, 574f)
            }
            tapScheduled = false

            // Settings ni yopish
            Handler(Looper.getMainLooper()).postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 600)
        }, 800)
    }

    override fun onInterrupt() {}

    fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}

var pendingAction: String? = null
