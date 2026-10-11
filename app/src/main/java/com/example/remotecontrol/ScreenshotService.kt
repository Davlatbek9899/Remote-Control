package com.example.remotecontrol

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenshotService : Service() {

    private var mediaProjection: MediaProjection? = null
    private lateinit var replyTo: String

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        replyTo = intent?.getStringExtra("reply_to") ?: ""
        val resultCode = intent?.getIntExtra("result_code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        startForegroundNotification()

        if (resultCode == Activity.RESULT_OK && data != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                captureScreen(resultCode, data)
            }, 500)
        } else {
            CommandHandler(this).sendSms(replyTo, "Screenshot ruxsat berilmadi.")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "screenshot_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(channelId, "Screenshot", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screenshot olinmoqda...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(2001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun captureScreen(resultCode: Int, data: Intent) {
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, data)

            // Callback ro'yxatdan o'tkazish (Android 14+ da majburiy)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d("ScreenshotService", "MediaProjection stopped")
                }
            }, Handler(Looper.getMainLooper()))

            val dm = resources.displayMetrics
            val width = dm.widthPixels
            val height = dm.heightPixels
            val density = dm.densityDpi

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)

            mediaProjection?.createVirtualDisplay(
                "Screenshot", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null
            )

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = imageReader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height, Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        val dcim = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DCIM)
                        val dir = File(dcim, "Screenshots")
                        if (!dir.exists()) dir.mkdirs()
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val file = File(dir, "RC_$timestamp.png")
                        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

                        android.media.MediaScannerConnection.scanFile(
                            this, arrayOf(file.absolutePath), arrayOf("image/png"), null
                        )
                        CommandHandler(this).sendSms(replyTo, "Screenshot olindi! DCIM/Screenshots/${file.name}")
                        Log.d("ScreenshotService", "Saqlandi: ${file.absolutePath}")
                    } else {
                        CommandHandler(this).sendSms(replyTo, "Screenshot olishda xato.")
                    }
                    mediaProjection?.stop()
                } catch (e: Exception) {
                    CommandHandler(this).sendSms(replyTo, "Xato: " + e.message)
                }
                stopSelf()
            }, 1000)
        } catch (e: Exception) {
            CommandHandler(this).sendSms(replyTo, "Xato: " + e.message)
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaProjection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
