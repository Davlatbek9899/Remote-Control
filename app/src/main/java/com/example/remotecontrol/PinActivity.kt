package com.example.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class PinActivity : AppCompatActivity() {

    private val pin = StringBuilder()
    private var mode = "verify"
    private var firstPin = ""
    private var setupStep = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)
        mode = intent.getStringExtra("mode") ?: "verify"
        updateTitle()
        setupNumpad()
        setupFingerprint()
        if (mode == "verify") {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showFingerprintPrompt()
            }, 300)
        }
    }

    private fun updateTitle() {
        val tv = findViewById<TextView>(R.id.tvPinUsername)
        tv.text = when {
            mode == "setup" && setupStep == 1 -> "Parol yarating"
            mode == "setup" && setupStep == 2 -> "Parolni tasdiqlang"
            else -> "Parolni tasdiqlang"
        }
    }

    private fun showFingerprintPrompt() {
        try {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { goToMain() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                override fun onAuthenticationFailed() {}
            })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Barmoq izi bilan kirish")
                .setSubtitle("Tezroq kirish uchun barmoq izingizni tasdiqlang")
                .setNegativeButtonText("PIN bilan kirish")
                .build()
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {}
    }

    private fun dots() = listOf(
        findViewById<android.view.View>(R.id.dot1),
        findViewById<android.view.View>(R.id.dot2),
        findViewById<android.view.View>(R.id.dot3),
        findViewById<android.view.View>(R.id.dot4)
    )

    private fun setupNumpad() {
        val numButtons = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )
        numButtons.forEach { (id, digit) ->
            findViewById<Button>(id).setOnClickListener {
                if (pin.length < 4) {
                    pin.append(digit)
                    updateDots()
                    if (pin.length == 4) {
                        if (mode == "setup") handleSetup()
                        else handleVerify()
                    }
                }
            }
        }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            if (pin.isNotEmpty()) {
                pin.deleteCharAt(pin.length - 1)
                updateDots()
            }
        }
    }

    private fun handleSetup() {
        if (setupStep == 1) {
            firstPin = pin.toString()
            pin.clear()
            setupStep = 2
            updateDots()
            updateTitle()
        } else {
            if (firstPin == pin.toString()) {
                getSharedPreferences("rc_pin", MODE_PRIVATE)
                    .edit().putString("pin", pin.toString()).apply()
                Toast.makeText(this, "Parol o'rnatildi!", Toast.LENGTH_SHORT).show()
                goToMain()
            } else {
                Toast.makeText(this, "Parollar mos kelmadi, qayta urinib ko'ring", Toast.LENGTH_SHORT).show()
                pin.clear()
                firstPin = ""
                setupStep = 1
                updateDots()
                updateTitle()
            }
        }
    }

    private fun handleVerify() {
        val savedPin = getSharedPreferences("rc_pin", MODE_PRIVATE).getString("pin", null)
        if (savedPin == pin.toString()) {
            goToMain()
        } else {
            Toast.makeText(this, "Noto'g'ri parol", Toast.LENGTH_SHORT).show()
            pin.clear()
            updateDots()
        }
    }

    private fun updateDots() {
        dots().forEachIndexed { i, dot ->
            dot.setBackgroundResource(
                if (i < pin.length) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }
    }

    private fun setupFingerprint() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { goToMain() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            override fun onAuthenticationFailed() {
                Toast.makeText(this@PinActivity, "Barmoq izi tasdiqlanmadi", Toast.LENGTH_SHORT).show()
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Barmoq izi bilan kirish")
            .setNegativeButtonText("PIN bilan kirish")
            .build()
        findViewById<android.widget.LinearLayout>(R.id.btnFingerprint).setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}