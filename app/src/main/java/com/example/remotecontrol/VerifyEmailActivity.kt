package com.example.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class VerifyEmailActivity : AppCompatActivity() {

    private var email = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        val email = intent.getStringExtra("email") ?: ""
        findViewById<TextView>(R.id.tvVerifyEmail).text = "Kod yuborildi: $email"

        setupCodeBoxes()

        findViewById<Button>(R.id.btnVerify).setOnClickListener {
            val code = listOf(
                findViewById<EditText>(R.id.etCode1).text.toString(),
                findViewById<EditText>(R.id.etCode2).text.toString(),
                findViewById<EditText>(R.id.etCode3).text.toString(),
                findViewById<EditText>(R.id.etCode4).text.toString()
            ).joinToString("")

            if (code.length < 4) {
                Toast.makeText(this, "Kodni to'liq kiriting", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val btn = it as Button
            btn.isEnabled = false
            btn.text = "Tekshirilmoqda..."

            ApiClient.verify(email, code) { success, result ->
                runOnUiThread {
                    btn.isEnabled = true
                    btn.text = "Tasdiqlash"
                    if (success) {
                        // Tokenni saqlash
                        getSharedPreferences("rc_auth", MODE_PRIVATE)
                            .edit().putString("token", result).apply()
                        Toast.makeText(this, "Email tasdiqlandi!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, PinActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
        startResendTimer()
    }

    private fun startResendTimer() {
        val tvResend = findViewById<TextView>(R.id.tvResendCode)
        tvResend.isEnabled = false
        tvResend.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))

        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                tvResend.text = "$sec  Kodni qayta yuborish"
                tvResend.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            }
            override fun onFinish() {
                tvResend.text = "Kodni qayta yuborish"
                tvResend.isEnabled = true
                tvResend.setTextColor(android.graphics.Color.parseColor("#1B8EF8"))
                tvResend.setOnClickListener {
                    tvResend.isEnabled = false
                    Toast.makeText(this@VerifyEmailActivity, "Kod qayta yuborilmoqda...", Toast.LENGTH_SHORT).show()
                    ApiClient.forgotPassword(email) { success, _ ->
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this@VerifyEmailActivity, "Yangi kod yuborildi!", Toast.LENGTH_SHORT).show()
                                startResendTimer()
                            }
                        }
                    }
                }
            }
        }.start()
    }

    private fun setupCodeBoxes() {
        val boxes = listOf(
            findViewById<EditText>(R.id.etCode1),
            findViewById<EditText>(R.id.etCode2),
            findViewById<EditText>(R.id.etCode3),
            findViewById<EditText>(R.id.etCode4)
        )
        boxes.forEachIndexed { i, box ->
            box.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && i < boxes.size - 1) boxes[i + 1].requestFocus()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        boxes[0].requestFocus()
    }
}