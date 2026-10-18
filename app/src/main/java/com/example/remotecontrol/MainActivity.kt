package com.example.remotecontrol

import android.animation.*
import android.app.Dialog
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 100
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE,
        Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.READ_MEDIA_IMAGES
    )

    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Placeholder WebView
        val placeholderWeb = findViewById<android.webkit.WebView>(R.id.btnTestPlaceholder)
        placeholderWeb.settings.javaScriptEnabled = true
        placeholderWeb.setBackgroundColor(0x00000000)
        placeholderWeb.loadUrl("file:///android_asset/test_button.html")
        // Haqiqiy WebView
        val webViewEarly = findViewById<android.webkit.WebView>(R.id.btnTest)
        webViewEarly.settings.javaScriptEnabled = true
        webViewEarly.settings.cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
        webViewEarly.setBackgroundColor(0x00000000)
        webViewEarly.loadUrl("file:///android_asset/test_button.html")
        // 600ms dan keyin placeholder ni sezdirmay yashiramiz
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val placeholder = findViewById<android.webkit.WebView>(R.id.btnTestPlaceholder)
            // WebView ni ko'rsatamiz
            webViewEarly.alpha = 0f
            webViewEarly.visibility = android.view.View.VISIBLE
            // Fade in/out animatsiya
            webViewEarly.animate().alpha(1f).setDuration(300).start()
            placeholder.animate().alpha(0f).setDuration(300).withEndAction {
                placeholder.visibility = android.view.View.GONE
            }.start()
        }, 600)
        checkAndRequestPermissions()
        checkOverlayPermission()
        setupUI()
        animateEntrance()
    }

    private fun setupSimCard() {
        val btnSimSelect = findViewById<android.widget.LinearLayout>(R.id.btnSimSelect)
        val tvSimCardStatus = findViewById<android.widget.TextView>(R.id.tvSimCardStatus)

        // Saqlangan SIM ni ko'rsatish
        val savedSubId = getSharedPreferences("rc_settings", MODE_PRIVATE).getInt("sim_subscription_id", -1)
        updateSimCardUI(savedSubId)

        btnSimSelect.setOnClickListener {
            showSimSelectDialog()
        }
    }

    private fun updateSimCardUI(subId: Int) {
        val tvSimCardStatus = findViewById<android.widget.TextView>(R.id.tvSimCardStatus)
        if (subId == -1) {
            tvSimCardStatus.text = "Default"
        } else {
            try {
                val subManager = getSystemService(android.telephony.SubscriptionManager::class.java)
                val info = subManager.getActiveSubscriptionInfo(subId)
                tvSimCardStatus.text = info?.displayName ?: "SIM $subId"
            } catch (e: Exception) {
                tvSimCardStatus.text = "SIM $subId"
            }
        }
    }

    private fun showSimSelectDialog() {
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.READ_PHONE_NUMBERS), 200)
            return
        }
        try {
            val subManager = getSystemService(android.telephony.SubscriptionManager::class.java)
            val sims = subManager.activeSubscriptionInfoList
            if (sims == null || sims.isEmpty()) {
                android.widget.Toast.makeText(this, "SIM karta topilmadi", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            val dialog = android.app.Dialog(this, R.style.Theme_RemoteControl_Dialog)
            dialog.setContentView(R.layout.dialog_sim_select)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val llSimOptions = dialog.findViewById<LinearLayout>(R.id.llSimOptions)
            val savedSubId = getSharedPreferences("rc_settings", MODE_PRIVATE).getInt("sim_subscription_id", -1)

            // Default item
            val defaultView = layoutInflater.inflate(R.layout.item_sim_card, llSimOptions, false)
            defaultView.findViewById<android.widget.TextView>(R.id.tvSimIcon).text = "🔄"
            defaultView.findViewById<android.widget.TextView>(R.id.tvSimName).text = "Default"
            defaultView.findViewById<android.widget.TextView>(R.id.tvSimNumber).text = "Tizim tanloviga"
            if (savedSubId == -1) defaultView.findViewById<android.widget.ImageView>(R.id.ivSimCheck).visibility = android.view.View.VISIBLE
            defaultView.setOnClickListener {
                getSharedPreferences("rc_settings", MODE_PRIVATE).edit().putInt("sim_subscription_id", -1).apply()
                updateSimCardUI(-1)
                dialog.dismiss()
            }
            llSimOptions.addView(defaultView)

            // SIM kartalar
            sims.forEach { sim ->
                val simView = layoutInflater.inflate(R.layout.item_sim_card, llSimOptions, false)
                simView.findViewById<android.widget.TextView>(R.id.tvSimName).text = sim.displayName
                simView.findViewById<android.widget.TextView>(R.id.tvSimNumber).text = sim.number ?: "SIM ${sim.simSlotIndex + 1}"
                if (savedSubId == sim.subscriptionId) simView.findViewById<android.widget.ImageView>(R.id.ivSimCheck).visibility = android.view.View.VISIBLE
                simView.setOnClickListener {
                    getSharedPreferences("rc_settings", MODE_PRIVATE).edit().putInt("sim_subscription_id", sim.subscriptionId).apply()
                    updateSimCardUI(sim.subscriptionId)
                    dialog.dismiss()
                }
                llSimOptions.addView(simView)
            }

            dialog.findViewById<android.widget.Button>(R.id.btnSimCancel).setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Xatolik: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUI() {
        // TelegramService ishga tushirish
        startService(Intent(this, TelegramService::class.java))
        val tvStatus = findViewById<TextView>(R.id.tvPowerStatus)
        val btnTest = findViewById<WebView>(R.id.btnTest)
        val btnPassword = findViewById<Button>(R.id.btnPassword)
        val btnBlock = findViewById<Button>(R.id.btnBlock)
        val navHistory = findViewById<LinearLayout>(R.id.navHistory)
        val navCommands = findViewById<LinearLayout>(R.id.navCommands)
        setupQuickCards()
        setupSimCard()
        refreshRecentCommands()

        // Servis holati
        isServiceRunning = true
        tvStatus.text = "Faol"
        tvStatus.setTextColor(Color.parseColor("#2DC96B"))

        btnTest.settings.javaScriptEnabled = true
        btnTest.setBackgroundColor(0x00000000)
        btnTest.webViewClient = WebViewClient()
        class AndroidBridge {
            @android.webkit.JavascriptInterface
            fun onTestClick() {
                runOnUiThread {
                    val phone = SecurityManager.getCommandSenders(this@MainActivity).firstOrNull() ?: ""
                    if (phone.isNotEmpty()) {
                        CommandHandler(this@MainActivity).sendSms(phone, "✅ Tizim ishlayapti!")
                    }
                    Toast.makeText(this@MainActivity, "Test xabari yuborilmoqda...", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnTest.addJavascriptInterface(AndroidBridge(), "Android")
        // loadUrl bu yerda shart emas - onCreate da yuklanadi

        btnPassword.setOnClickListener {
            animateCard(btnPassword)
            showPasswordDialog()
        }

        btnBlock.setOnClickListener {
            animateCard(btnBlock)
            showBlockDialog()
        }

        navHistory.setOnClickListener {
            animateCard(navHistory)
            startActivity(Intent(this, HistoryActivity::class.java))
            overridePendingTransition(R.anim.slide_up, R.anim.fade_in)
        }

        navCommands.setOnClickListener {
            animateCard(navCommands)
            startActivity(Intent(this, CommandsActivity::class.java))
            overridePendingTransition(R.anim.slide_up, R.anim.fade_in)
        }

        val navChild = findViewById<LinearLayout>(R.id.navChild)
        val navProfile = findViewById<LinearLayout>(R.id.navProfile)

        navChild.setOnClickListener {
            animateCard(navChild)
            startActivity(Intent(this, ChildTrackerActivity::class.java))
            overridePendingTransition(R.anim.slide_up, R.anim.fade_in)
        }

        navProfile.setOnClickListener {
            animateCard(navProfile)
            startActivity(Intent(this, ProfileActivity::class.java)); finish()
        }



    }

    private fun setupQuickCards() {
        val cardSleepTimer = findViewById<LinearLayout>(R.id.cardSleepTimer)
        val cardReminder = findViewById<LinearLayout>(R.id.cardReminder)
        val cardAppTimer = findViewById<LinearLayout>(R.id.cardAppTimer)
        val cardMore = findViewById<LinearLayout>(R.id.cardMore)

        cardSleepTimer.setOnClickListener {
            animateCard(cardSleepTimer)
            showSleepTimerDialog()
        }
        cardReminder.setOnClickListener {
            animateCard(cardReminder)
            Toast.makeText(this, "Tez orada!", Toast.LENGTH_SHORT).show()
        }
        cardAppTimer.setOnClickListener {
            animateCard(cardAppTimer)
            Toast.makeText(this, "Tez orada!", Toast.LENGTH_SHORT).show()
        }
        cardMore.setOnClickListener {
            animateCard(cardMore)
            showMoreDialog()
        }
    }

    private fun showBatteryFullDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_battery_full, null)
        val switchFull = view.findViewById<android.widget.Switch>(R.id.switchBatteryFull)
        val tvPhone = view.findViewById<TextView>(R.id.tvAlertPhoneFull)
        val btnPhone = view.findViewById<Button>(R.id.btnSetPhoneFull)
        val btnSim = view.findViewById<Button>(R.id.btnSimFull)

        switchFull.isChecked = AutoSmsManager.isBatteryFullEnabled(this)
        val phone = AutoSmsManager.getAlertPhone(this)
        tvPhone.text = if (phone.isEmpty()) "Raqam kiritilmagan" else phone

        switchFull.setOnCheckedChangeListener { _, checked ->
            AutoSmsManager.setBatteryFullEnabled(this, checked)
            if (checked) startBatteryMonitor()
        }
        btnPhone.setOnClickListener { showPhoneInputDialog { tvPhone.text = it } }
        btnSim.setOnClickListener { showSimDialog() }

        android.app.AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showBatteryLowDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_battery_low, null)
        val switchLow = view.findViewById<android.widget.Switch>(R.id.switchBatteryLow)
        val seekBar = view.findViewById<android.widget.SeekBar>(R.id.seekBatteryThreshold)
        val tvThreshold = view.findViewById<TextView>(R.id.tvThresholdValue)
        val tvPhone = view.findViewById<TextView>(R.id.tvAlertPhoneLow)
        val btnPhone = view.findViewById<Button>(R.id.btnSetPhoneLow)
        val btnSim = view.findViewById<Button>(R.id.btnSimLow)
        val layoutSeek = view.findViewById<LinearLayout>(R.id.layoutThreshold)

        switchLow.isChecked = AutoSmsManager.isBatteryLowEnabled(this)
        seekBar.progress = AutoSmsManager.getBatteryLowThreshold(this)
        tvThreshold.text = "${seekBar.progress}%"
        layoutSeek.visibility = if (switchLow.isChecked) View.VISIBLE else View.GONE
        val phone = AutoSmsManager.getAlertPhone(this)
        tvPhone.text = if (phone.isEmpty()) "Raqam kiritilmagan" else phone

        switchLow.setOnCheckedChangeListener { _, checked ->
            AutoSmsManager.setBatteryLowEnabled(this, checked)
            layoutSeek.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) startBatteryMonitor()
        }
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, f: Boolean) {
                val v = p.coerceAtLeast(5)
                tvThreshold.text = "$v%"
                AutoSmsManager.setBatteryLowThreshold(this@MainActivity, v)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        btnPhone.setOnClickListener { showPhoneInputDialog { tvPhone.text = it } }
        btnSim.setOnClickListener { showSimDialog() }

        android.app.AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .show()
    }



    private var sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepDialogMins: TextView? = null
    private var sleepDialogSub: TextView? = null
    private var sleepDialogTimer: CircularTimerView? = null
    private var sleepCountdown: Runnable? = null
    private var sleepRunning = false
    private var sleepMinutes = 30
    private var sleepRemaining = 0

    private fun showSleepTimerDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_sleep_timer)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.decorView?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.dialog_in))

        val timerView = dialog.findViewById<CircularTimerView>(R.id.circularTimerDialog)
        val tvMins = dialog.findViewById<TextView>(R.id.tvTimerMins)
        val tvSub = dialog.findViewById<TextView>(R.id.tvTimerSub)
        val btnStartStop = dialog.findViewById<Button>(R.id.btnTimerStart)
        val btnClose = dialog.findViewById<android.widget.ImageButton>(R.id.btnCloseTimer)
        val pulseDot = dialog.findViewById<android.view.View>(R.id.viewPulseDot)

        // Dialog ochilishi bilanoq referenslarni yangilash
        sleepDialogMins = tvMins
        sleepDialogSub = tvSub
        sleepDialogTimer = timerView

        // Joriy holatni darhol ko'rsatish
        if (sleepRunning) {
            val m = sleepRemaining / 60
            val s = sleepRemaining % 60
            tvMins.text = String.format("%02d:%02d", m, s)
            tvSub.text = "qoldi"
            val prog = sleepRemaining.toFloat() / (sleepMinutes * 60f)
            timerView.setProgress(prog)
            timerView.setDraggable(false)
            timerView.visibility = android.view.View.INVISIBLE
            btnStartStop.text = "TO'XTATISH"
            btnStartStop.setBackgroundResource(R.drawable.btn_red)
            // Pulse nuqtani yoqish
            pulseDot.visibility = android.view.View.VISIBLE
            val pulseAnim = android.animation.ObjectAnimator.ofFloat(pulseDot, "alpha", 1f, 0f).apply {
                duration = 900
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            pulseAnim.start()
            pulseDot.tag = pulseAnim
        } else {
            timerView.setProgress(sleepMinutes / 90f)
            tvMins.text = sleepMinutes.toString()
            tvSub.text = "daqiqa"
            btnStartStop.text = "BOSHLASH"
            btnStartStop.setBackgroundResource(R.drawable.btn_blue)
            timerView.setDraggable(true)
        }

        timerView.setOnAngleChangeListener { angle ->
            if (!sleepRunning) {
                sleepMinutes = ((angle / 360f) * 90).toInt().coerceIn(0, 90)
                tvMins.text = String.format("%02d:00", sleepMinutes)
                tvSub.text = "daqiqa"
                if (sleepMinutes == 0) {
                    btnStartStop.isEnabled = false
                    btnStartStop.alpha = 0.4f
                } else {
                    btnStartStop.isEnabled = true
                    btnStartStop.alpha = 1f
                }
            }
        }

        btnStartStop.setOnClickListener {
            if (sleepRunning) {
                // To'xtatish
                sleepRunning = false
                sleepCountdown?.let { r -> sleepTimerHandler.removeCallbacks(r) }
                sleepRemaining = 0
                // Pulse nuqtani o'chirish
                (pulseDot.tag as? android.animation.ObjectAnimator)?.cancel()
                pulseDot.visibility = android.view.View.GONE
                btnStartStop.text = "BOSHLASH"
                btnStartStop.setBackgroundResource(R.drawable.btn_blue)
                tvMins.text = String.format("%02d:00", sleepMinutes)
                tvSub.text = "daqiqa"
                timerView.setProgress(sleepMinutes / 90f)
                timerView.setDraggable(true)
                timerView.visibility = android.view.View.VISIBLE
                // Card ni qaytarish
                findViewById<TextView>(R.id.tvSleepStatus).text = "Sozlash"
                findViewById<TextView>(R.id.tvSleepStatus).setTextColor(
                    android.graphics.Color.parseColor("#1B8EF8"))
            } else {
                // Boshlash
                sleepRunning = true
                sleepRemaining = sleepMinutes * 60
                timerView.setDraggable(false)
                timerView.visibility = android.view.View.INVISIBLE
                btnStartStop.text = "TO'XTATISH"
                btnStartStop.setBackgroundResource(R.drawable.btn_red)
                // Pulse nuqtani yoqish
                pulseDot.visibility = android.view.View.VISIBLE
                val pulseAnim = android.animation.ObjectAnimator.ofFloat(pulseDot, "alpha", 1f, 0f).apply {
                    duration = 900
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                pulseAnim.start()
                pulseDot.tag = pulseAnim

                // Card da "Timer ketmoqda" ko'rsatish
                findViewById<TextView>(R.id.tvSleepStatus).text = "Timer ketmoqda"
                findViewById<TextView>(R.id.tvSleepStatus).setTextColor(
                    android.graphics.Color.parseColor("#FF4B4B"))

                sleepCountdown = object : Runnable {
                    override fun run() {
                        if (sleepRemaining <= 0) {
                            // Musiqani to'xtatish
                            try {
                                val audio = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
                                sendBroadcast(Intent("com.android.music.musicservicecommand").apply {
                                    putExtra("command", "pause")
                                })
                            } catch (e: Exception) {}
                            sleepRunning = false
                            sleepRemaining = 0
                            sleepMinutes = 30
                            sleepDialogMins?.text = String.format("%02d:00", sleepMinutes)
                            sleepDialogSub?.text = "daqiqa"
                            sleepDialogTimer?.setProgress(sleepMinutes / 90f)
                            sleepDialogTimer?.setDraggable(true)
                            sleepDialogTimer?.visibility = android.view.View.VISIBLE
                            // Tugmani BOSHLASH ga qaytarish
                            btnStartStop.text = "BOSHLASH"
                            btnStartStop.setBackgroundResource(R.drawable.btn_blue)
                            // Pulse nuqtani o'chirish
                            (pulseDot.tag as? android.animation.ObjectAnimator)?.cancel()
                            pulseDot.visibility = android.view.View.GONE
                            Toast.makeText(this@MainActivity, "🎵 Musiqa to'xtatildi!", Toast.LENGTH_LONG).show()
                            findViewById<TextView>(R.id.tvSleepStatus).text = "Sozlash"
                            findViewById<TextView>(R.id.tvSleepStatus).setTextColor(
                                android.graphics.Color.parseColor("#1B8EF8"))
                            return
                        }
                        sleepRemaining--
                        val m = sleepRemaining / 60
                        val s = sleepRemaining % 60
                        val timeStr = String.format("%02d:%02d", m, s)
                        val prog = sleepRemaining.toFloat() / (sleepMinutes * 60f)
                        // Dialog ochiq bo'lsa yangilash
                        sleepDialogMins?.text = timeStr
                        sleepDialogSub?.text = "qoldi"
                        sleepDialogTimer?.setProgress(prog)
                        sleepTimerHandler.postDelayed(this, 1000)
                    }
                }
                sleepTimerHandler.post(sleepCountdown!!)
            }
        }

        // X tugmasi — faqat yopadi, referenslarni tozalaydi
        btnClose.setOnClickListener {
            sleepDialogMins = null
            sleepDialogSub = null
            sleepDialogTimer = null
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            sleepDialogMins = null
            sleepDialogSub = null
            sleepDialogTimer = null
        }

        dialog.show()
    }

    private fun showMoreDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_more)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.decorView?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.dialog_in))

        val switchFull = dialog.findViewById<android.widget.Switch>(R.id.switchBatteryFull)
        val switchLow = dialog.findViewById<android.widget.Switch>(R.id.switchBatteryLow)
        val tvPhone = dialog.findViewById<TextView>(R.id.tvMorePhone)
        val btnPhone = dialog.findViewById<Button>(R.id.btnMorePhone)
        val btnSim = dialog.findViewById<Button>(R.id.btnMoreSim)
        val btnClose = dialog.findViewById<android.widget.ImageButton>(R.id.btnCloseMore)

        switchFull.isChecked = AutoSmsManager.isBatteryFullEnabled(this)
        switchLow.isChecked = AutoSmsManager.isBatteryLowEnabled(this)
        val phone = AutoSmsManager.getAlertPhone(this)
        tvPhone.text = if (phone.isEmpty()) "Raqam kiritilmagan" else phone

        switchFull.setOnCheckedChangeListener { _, checked ->
            AutoSmsManager.setBatteryFullEnabled(this, checked)
            if (checked || switchLow.isChecked) startBatteryMonitor()
        }
        switchLow.setOnCheckedChangeListener { _, checked ->
            AutoSmsManager.setBatteryLowEnabled(this, checked)
            if (checked || switchFull.isChecked) startBatteryMonitor()
        }
        btnPhone.setOnClickListener {
            showPhoneInputDialog { tvPhone.text = it }
        }
        btnSim.setOnClickListener { showSimDialog() }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPhoneInputDialog(onSave: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            hint = "+998XXXXXXXXX"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(AutoSmsManager.getAlertPhone(this@MainActivity))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("SMS raqami")
            .setView(input)
            .setPositiveButton("Saqlash") { _, _ ->
                val num = input.text.toString().trim()
                AutoSmsManager.setAlertPhone(this, num)
                onSave(if (num.isEmpty()) "Raqam kiritilmagan" else num)
            }
            .setNegativeButton("Bekor") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showSimDialog() {
        val simNames = AutoSmsManager.getSimNames(this).toTypedArray()
        val current = AutoSmsManager.getSimSlot(this)
        android.app.AlertDialog.Builder(this)
            .setTitle("SIM karta")
            .setSingleChoiceItems(simNames, current) { dialog, which ->
                AutoSmsManager.setSimSlot(this, which)
                Toast.makeText(this, "✅ ${simNames[which]} tanlandi", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    private fun startBatteryMonitor() {
        startService(Intent(this, BatteryMonitorService::class.java))
    }

    private fun startTelegramService() {
        startService(Intent(this, TelegramService::class.java))
    }





    private fun animateCard(view: View) {
        val scaleDown = ScaleAnimation(1f, 0.95f, 1f, 0.95f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply { duration = 80 }
        val scaleUp = ScaleAnimation(0.95f, 1f, 0.95f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 150; startOffset = 80
            interpolator = OvershootInterpolator()
        }
        val set = AnimationSet(true).apply { addAnimation(scaleDown); addAnimation(scaleUp) }
        view.startAnimation(set)
    }

    private fun animateEntrance() {
        val scrollView = findViewById<ScrollView>(findViewById<View>(R.id.bottomNav).id - 1)
        val bottomNav = findViewById<LinearLayout>(R.id.bottomNav)

        // Bottom nav pastdan
        bottomNav.translationY = 200f
        bottomNav.alpha = 0f
        bottomNav.animate().translationY(0f).alpha(1f).setDuration(500).setStartDelay(300).start()
    }

    private fun refreshRecentCommands() {
        val lvRecent = findViewById<ListView>(R.id.lvRecentCommands)
        val tvEmpty = findViewById<TextView>(R.id.tvRecentEmpty)
        val logs = CommandLogger.getLogs(this).takeLast(5).reversed()

        if (logs.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            lvRecent.visibility = View.GONE
            return
        }
        tvEmpty.visibility = View.GONE
        lvRecent.visibility = View.VISIBLE

        val adapter = object : ArrayAdapter<CommandLogger.LogEntry>(this, 0, logs) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_recent_command, parent, false)
                val entry = getItem(pos)!!
                view.findViewById<TextView>(R.id.tvRecentSender).text = entry.sender
                view.findViewById<TextView>(R.id.tvRecentCommand).text = entry.command
                view.findViewById<TextView>(R.id.tvRecentTime).text = entry.time
                return view
            }
        }
        lvRecent.adapter = adapter
    }

    private var pulseAnimator: android.animation.AnimatorSet? = null

    private var isPulsing = false


    private fun startPulseLoop(ring: View) {
        if (!isPulsing) return
        ring.scaleX = 1f
        ring.scaleY = 1f
        ring.alpha = 0.7f

        // 1.25f — ramkadan chiqmasin, alpha esa oldin yo'qoladi
        val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.25f).apply { duration = 3500 }
        val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.25f).apply { duration = 3500 }
        val alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.6f, 0f).apply { duration = 8000 }

        pulseAnimator = android.animation.AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            interpolator = android.view.animation.DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    ring.postDelayed({
                        if (isPulsing) startPulseLoop(ring)
                    }, 50)
                }
            })
            start()
        }
    }

    private fun showPasswordDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_password)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.decorView?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.dialog_in))

        val tvStatus = dialog.findViewById<TextView>(R.id.tvPasswordStatus)
        val etPass = dialog.findViewById<EditText>(R.id.etDialogPassword)
        val btnSave = dialog.findViewById<Button>(R.id.btnSavePassword)
        val btnRemove = dialog.findViewById<Button>(R.id.btnRemovePassword)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseDialog)

        val slots = listOf(
            dialog.findViewById<TextView>(R.id.tvSlot0),
            dialog.findViewById<TextView>(R.id.tvSlot1),
            dialog.findViewById<TextView>(R.id.tvSlot2),
            dialog.findViewById<TextView>(R.id.tvSlot3)
        )

        val current = SecurityManager.getPassword(this)
        tvStatus.text = if (current.isNullOrEmpty()) "Parol o'rnatilmagan" else "Parol: ****"

        fun updateSlots(text: String) {
            for (i in 0..3) {
                if (i < text.length) {
                    slots[i].text = text[i].toString()
                    slots[i].setTextColor(Color.parseColor("#1A1A2E"))
                } else {
                    slots[i].text = "*"
                    slots[i].setTextColor(Color.parseColor("#1B8EF8"))
                }
            }
        }
        updateSlots("")

        etPass.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { updateSlots(s?.toString() ?: "") }
        })

        dialog.findViewById<LinearLayout>(R.id.llPasswordDisplay).setOnClickListener { etPass.requestFocus() }

        btnSave.setOnClickListener {
            val pass = etPass.text?.toString() ?: ""
            if (pass.length == 4 && pass.all { it.isDigit() }) {
                SecurityManager.setPassword(this, pass)
                tvStatus.text = "Parol o'rnatildi ✅"
                etPass.text?.clear()
                updateSlots("")
                Toast.makeText(this, "Saqlandi!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                val shake = TranslateAnimation(-10f, 10f, 0f, 0f).apply {
                    duration = 50; repeatCount = 5; repeatMode = Animation.REVERSE
                }
                dialog.findViewById<LinearLayout>(R.id.llPasswordDisplay).startAnimation(shake)
                Toast.makeText(this, "4 ta raqam kiriting!", Toast.LENGTH_SHORT).show()
            }
        }
        btnRemove.setOnClickListener {
            SecurityManager.setPassword(this, "")
            tvStatus.text = "Parol o'chirildi"
            etPass.text?.clear()
            updateSlots("")
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
        etPass.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        etPass.postDelayed({ imm.showSoftInput(etPass, android.view.inputmethod.InputMethodManager.SHOW_FORCED) }, 200)
    }

    private fun showBlockDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_block)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.decorView?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.dialog_in))

        val etNumber = dialog.findViewById<EditText>(R.id.etBlockNumber)
        val btnAdd = dialog.findViewById<Button>(R.id.btnBlockAdd)
        val lvBlocked = dialog.findViewById<ListView>(R.id.lvBlockedNumbers)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseBlock)

        fun refresh() {
            val numbers = SecurityManager.getBlockedNumbers(this).toList()
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, numbers) {
                override fun getView(pos: Int, v: View?, parent: ViewGroup): View {
                    val view = super.getView(pos, v, parent) as TextView
                    view.setTextColor(Color.parseColor("#FF4B4B"))
                    view.setBackgroundColor(Color.TRANSPARENT)
                    return view
                }
            }
            lvBlocked.adapter = adapter
        }
        refresh()

        btnAdd.setOnClickListener {
            val num = etNumber.text.toString().trim()
            if (num.isNotEmpty()) {
                SecurityManager.blockNumber(this, num)
                etNumber.text.clear()
                refresh()
            }
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        refreshRecentCommands()
    }

    private fun checkOverlayPermission() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")))
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        }
    }
}
