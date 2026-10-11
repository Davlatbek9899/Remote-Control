package com.example.remotecontrol

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = getSharedPreferences("rc_auth", MODE_PRIVATE).getString("token", null)
        val pin = getSharedPreferences("rc_pin", MODE_PRIVATE).getString("pin", null)

        when {
            token == null -> {
                // Login sahifasi
                startActivity(Intent(this, AuthActivity::class.java))
            }
            pin == null -> {
                // PIN o'rnatish sahifasi
                val intent = Intent(this, PinActivity::class.java)
                intent.putExtra("mode", "setup")
                startActivity(intent)
            }
            else -> {
                // PIN kiritish sahifasi
                val intent = Intent(this, PinActivity::class.java)
                intent.putExtra("mode", "verify")
                startActivity(intent)
            }
        }
        finish()
    }
}