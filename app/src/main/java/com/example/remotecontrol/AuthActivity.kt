package com.example.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AuthActivity : AppCompatActivity() {

    private var currentEmail = ""
    private lateinit var wvLoginLoading: WebView
    private lateinit var wvRegisterLoading: WebView
    private lateinit var wvErrorDialog: WebView
    private var pendingRetryAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        setupLoadingViews()
        setupTabs()
    }

    private fun setupLoadingViews() {
        wvLoginLoading = findViewById(R.id.wvLoginLoading)
        wvLoginLoading.settings.javaScriptEnabled = true
        wvLoginLoading.setBackgroundColor(0x00000000)
        wvLoginLoading.loadUrl("file:///android_asset/loading.html")

        wvRegisterLoading = findViewById(R.id.wvRegisterLoading)
        wvRegisterLoading.settings.javaScriptEnabled = true
        wvRegisterLoading.setBackgroundColor(0x00000000)
        wvRegisterLoading.loadUrl("file:///android_asset/loading.html")

        wvErrorDialog = findViewById(R.id.wvErrorDialog)
        wvErrorDialog.settings.javaScriptEnabled = true
        wvErrorDialog.setBackgroundColor(0x00000000)
        wvErrorDialog.webViewClient = WebViewClient()
        class ErrorBridge {
            @JavascriptInterface
            fun onRetry() {
                runOnUiThread {
                    hideErrorDialog()
                    pendingRetryAction?.invoke()
                }
            }
            @JavascriptInterface
            fun onCancel() {
                runOnUiThread { hideErrorDialog() }
            }
        }
        wvErrorDialog.addJavascriptInterface(ErrorBridge(), "Android")
        wvErrorDialog.loadUrl("file:///android_asset/error_dialog.html")
    }

    private fun showErrorDialog(message: String, retryAction: () -> Unit) {
        pendingRetryAction = retryAction
        val overlay = findViewById<android.widget.FrameLayout>(R.id.errorDialogOverlay)
        wvErrorDialog.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {}
    }

    private fun hideErrorDialog() {
        val overlay = findViewById<android.widget.FrameLayout>(R.id.errorDialogOverlay)
        overlay.visibility = View.GONE
    }

    private fun showInlineError(message: String) {
        val tv = findViewById<TextView>(R.id.tvLoginError)
        tv.text = "⚠  $message"
        tv.visibility = View.VISIBLE
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        } catch (e: Exception) {}
    }

    private fun showLoginLoading(show: Boolean) {
        val btn = findViewById<Button>(R.id.btnLogin)
        if (show) {
            btn.visibility = View.INVISIBLE
            wvLoginLoading.visibility = View.VISIBLE
        } else {
            wvLoginLoading.visibility = View.GONE
            btn.visibility = View.VISIBLE
        }
    }

    private fun showRegisterLoading(show: Boolean) {
        val btn = findViewById<Button>(R.id.btnRegister)
        if (show) {
            btn.visibility = View.INVISIBLE
            wvRegisterLoading.visibility = View.VISIBLE
        } else {
            wvRegisterLoading.visibility = View.GONE
            btn.visibility = View.VISIBLE
        }
    }

    private fun performLogin(emailOrUsername: String, pass: String) {
        showLoginLoading(true)
        ApiClient.login(emailOrUsername, pass) { success, result ->
            runOnUiThread {
                showLoginLoading(false)
                android.util.Log.d("AuthDebug", "success=$success result=$result")
                if (success) {
                    getSharedPreferences("rc_auth", MODE_PRIVATE).edit().putString("token", result).apply()
                    getSharedPreferences("rc_pin", MODE_PRIVATE).edit().remove("pin").apply()
                    startActivity(Intent(this, PinActivity::class.java).putExtra("mode", "setup"))
                    finish()
                } else if (result == "Server bilan aloqa yo'q") {
                    showErrorDialog(result) { performLogin(emailOrUsername, pass) }
                } else {
                    showInlineError(result)
                }
            }
        }
    }

    private fun setupTabs() {
        val tabLogin = findViewById<TextView>(R.id.tabLogin)
        val tabRegister = findViewById<TextView>(R.id.tabRegister)
        val layoutLogin = findViewById<View>(R.id.layoutLogin)
        val layoutRegister = findViewById<View>(R.id.layoutRegister)

        tabLogin.setOnClickListener {
            tabLogin.setBackgroundResource(R.drawable.tab_selected)
            tabLogin.setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            tabRegister.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            tabRegister.setTextColor(android.graphics.Color.parseColor("#888888"))
            layoutLogin.visibility = View.VISIBLE
            layoutRegister.visibility = View.GONE
        }

        tabRegister.setOnClickListener {
            tabRegister.setBackgroundResource(R.drawable.tab_selected)
            tabRegister.setTextColor(android.graphics.Color.parseColor("#1A1A2E"))
            tabLogin.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            tabLogin.setTextColor(android.graphics.Color.parseColor("#888888"))
            layoutRegister.visibility = View.VISIBLE
            layoutLogin.visibility = View.GONE
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            startActivity(android.content.Intent(this, ForgotPasswordActivity::class.java))
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val emailOrUsername = findViewById<EditText>(R.id.etLoginEmail).text.toString().trim()
            val pass = findViewById<EditText>(R.id.etLoginPassword).text.toString()
            if (emailOrUsername.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Barcha maydonlarni to'ldiring", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findViewById<TextView>(R.id.tvLoginError).visibility = View.GONE
            performLogin(emailOrUsername, pass)
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            val username = findViewById<EditText>(R.id.etRegUsername).text.toString().trim()
            val email = findViewById<EditText>(R.id.etRegEmail).text.toString().trim()
            val pass = findViewById<EditText>(R.id.etRegPassword).text.toString()
            val confirm = findViewById<EditText>(R.id.etRegConfirmPassword).text.toString()

            if (username.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Barcha maydonlarni to'ldiring", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass != confirm) {
                Toast.makeText(this, "Parollar mos kelmaydi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showRegisterLoading(true)

            ApiClient.register(username, email, pass) { success, message ->
                runOnUiThread {
                    showRegisterLoading(false)
                    if (success) {
                        currentEmail = email
                        val intent = Intent(this, VerifyEmailActivity::class.java)
                        intent.putExtra("email", email)
                        startActivity(intent)
                    } else if (message == "Server bilan aloqa yo'q") {
                        showErrorDialog(message) {
                            showRegisterLoading(true)
                            ApiClient.register(username, email, pass) { s, m ->
                                runOnUiThread {
                                    showRegisterLoading(false)
                                    if (s) {
                                        currentEmail = email
                                        startActivity(Intent(this, VerifyEmailActivity::class.java).putExtra("email", email))
                                    } else {
                                        Toast.makeText(this, m, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}