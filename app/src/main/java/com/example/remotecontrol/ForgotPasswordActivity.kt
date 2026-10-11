package com.example.remotecontrol

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ForgotPasswordActivity : AppCompatActivity() {

    private var email = ""
    private var verifiedCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        setupStep1()
        setupCodeBoxes()

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupStep1() {
        findViewById<Button>(R.id.btnSendCode).setOnClickListener {
            email = findViewById<EditText>(R.id.etForgotEmail).text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Emailni kiriting", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val btn = it as Button
            btn.isEnabled = false
            btn.text = "Yuklanmoqda..."

            ApiClient.forgotPassword(email) { success, message ->
                runOnUiThread {
                    btn.isEnabled = true
                    btn.text = "Kod yuborish"
                    if (success) {
                        findViewById<TextView>(R.id.tvSentTo).text = "Kod yuborildi: $email"
                        showStep(2)
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
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

        startResendTimer()

        findViewById<Button>(R.id.btnVerifyCode).setOnClickListener {
            val code = boxes.joinToString("") { it.text.toString() }
            if (code.length < 4) {
                Toast.makeText(this, "Kodni to'liq kiriting", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifiedCode = code
            showStep(3)
        }
    }

    private fun startResendTimer() {
        val tvResend = findViewById<TextView>(R.id.tvResendCode)
        tvResend.isEnabled = false
        tvResend.setTextColor(android.graphics.Color.parseColor("#AAAAAA"))

        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                tvResend.text = "$sec  Kodni qayta yuborish"
            }
            override fun onFinish() {
                tvResend.text = "Kodni qayta yuborish"
                tvResend.isEnabled = true
                tvResend.setTextColor(android.graphics.Color.parseColor("#1B8EF8"))
                tvResend.setOnClickListener {
                    tvResend.isEnabled = false
                    ApiClient.forgotPassword(email) { success, _ ->
                        runOnUiThread {
                            if (success) {
                                Toast.makeText(this@ForgotPasswordActivity, "Yangi kod yuborildi!", Toast.LENGTH_SHORT).show()
                                startResendTimer()
                            }
                        }
                    }
                }
            }
        }.start()
    }

    private fun showStep(step: Int) {
        findViewById<View>(R.id.layoutStep1).visibility = if (step == 1) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutStep2).visibility = if (step == 2) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutStep3).visibility = if (step == 3) View.VISIBLE else View.GONE

        if (step == 3) {
            findViewById<Button>(R.id.btnResetPassword).setOnClickListener {
                val newPass = findViewById<EditText>(R.id.etNewPassword).text.toString()
                val confirm = findViewById<EditText>(R.id.etConfirmNewPassword).text.toString()
                if (newPass.isEmpty() || confirm.isEmpty()) {
                    Toast.makeText(this, "Barcha maydonlarni to'ldiring", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newPass != confirm) {
                    Toast.makeText(this, "Parollar mos kelmaydi", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val btn = it as Button
                btn.isEnabled = false
                btn.text = "Yuklanmoqda..."

                ApiClient.resetPassword(email, verifiedCode, newPass) { success, message ->
                    runOnUiThread {
                        btn.isEnabled = true
                        btn.text = "Parolni o'zgartirish"
                        if (success) {
                            Toast.makeText(this, "Parol muvaffaqiyatli o'zgartirildi!", Toast.LENGTH_LONG).show()
                            finish()
                        } else {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}