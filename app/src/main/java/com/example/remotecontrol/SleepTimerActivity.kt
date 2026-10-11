package com.example.remotecontrol

import android.content.Intent
import android.media.AudioManager
import android.os.*
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class SleepTimerActivity : AppCompatActivity() {

    private var selectedMinutes = 30
    private val maxMinutes = 90
    private var timerRunning = false
    private var remainingSeconds = 0L
    private var handler = Handler(Looper.getMainLooper())
    private lateinit var timerView: CircularTimerView
    private lateinit var tvMinutes: TextView
    private lateinit var tvLabel: TextView
    private lateinit var btnStart: TextView
    private lateinit var btnStartPlayer: TextView
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContentView(R.layout.activity_sleep_timer)

        timerView = findViewById(R.id.circularTimer)
        tvMinutes = findViewById(R.id.tvMinutes)
        tvLabel = findViewById(R.id.tvLabel)
        btnStart = findViewById(R.id.btnStart)
        btnStartPlayer = findViewById(R.id.btnStartPlayer)

        val btnBack = findViewById<android.widget.ImageButton>(R.id.btnBackTimer)
        btnBack.setOnClickListener { finish() }

        timerView.setOnAngleChangeListener { angle ->
            if (!timerRunning) {
                selectedMinutes = ((angle / 360f) * maxMinutes).toInt().coerceIn(1, maxMinutes)
                tvMinutes.text = selectedMinutes.toString()
            }
        }

        btnStart.setOnClickListener {
            if (timerRunning) {
                stopTimer()
            } else {
                startTimer(false)
            }
        }

        btnStartPlayer.setOnClickListener {
            if (timerRunning) {
                stopTimer()
            } else {
                startTimer(true)
            }
        }

        timerView.setProgress(selectedMinutes.toFloat() / maxMinutes)
        tvMinutes.text = selectedMinutes.toString()
    }

    private fun startTimer(withPlayer: Boolean) {
        timerRunning = true
        remainingSeconds = selectedMinutes * 60L
        tvLabel = findViewById(R.id.tvLabel)
        btnStart.text = "BEKOR QILISH"
        btnStartPlayer.text = "BEKOR QILISH"
        timerView.setDraggable(false)

        countdownRunnable = object : Runnable {
            override fun run() {
                if (remainingSeconds <= 0) {
                    stopMusic()
                    stopTimer()
                    return
                }
                remainingSeconds--
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                tvMinutes.text = String.format("%02d:%02d", mins, secs)
                tvLabel.text = "QOLDI"
                val progress = remainingSeconds.toFloat() / (selectedMinutes * 60f)
                timerView.setProgress(progress)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun stopTimer() {
        timerRunning = false
        countdownRunnable?.let { handler.removeCallbacks(it) }
        btnStart.text = "START"
        btnStartPlayer.text = "START & PLAYER"
        tvLabel.text = "MINUTES"
        tvMinutes.text = selectedMinutes.toString()
        timerView.setProgress(selectedMinutes.toFloat() / maxMinutes)
        timerView.setDraggable(true)
    }

    private fun stopMusic() {
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            val intent = Intent("com.android.music.musicservicecommand")
            intent.putExtra("command", "pause")
            sendBroadcast(intent)
            // Spotify, YouTube uchun
            val intentSpotify = Intent("com.spotify.mobile.android.ui.widget.PLAY")
            sendBroadcast(intentSpotify)
        } catch (e: Exception) {}

        android.app.AlertDialog.Builder(this)
            .setTitle("⏰ Taymer tugadi")
            .setMessage("Musiqa to'xtatildi!")
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
    }
}
