package com.example.remotecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class CallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra("number") ?: run { finish(); return }

        android.util.Log.d("CallActivity", "Raqam: $number")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("CallActivity", "Ruxsat bor, qo'ng'iroq qilinmoqda")
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
            }
            startActivity(callIntent)
        } else {
            android.util.Log.d("CallActivity", "Ruxsat yo'q, so'ralmoqda")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
        }

        // finish() ni kechiktirish — qo'ng'iroq boshlashga vaqt berish
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 3000)
    }
}
