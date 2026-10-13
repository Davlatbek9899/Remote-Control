package com.example.remotecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.app.NotificationCompat

// Musiqani fon rejimida, YouTube ilovasini ochmasdan ijro qilish uchun xizmat.
// Audio havolasini "o'g'irlash" (yt-dlp kabi) o'rniga, YouTube'ning RASMIY IFrame Player
// API'sini ko'rinmas (1x1 piksel) overlay oynada ishga tushiramiz — bu hech qanday
// bot-tekshiruviga tushmaydi, chunki bu YouTube'ning o'z pleyeri.
class YouTubeAudioService : Service() {

    private var webView: WebView? = null
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra("video_id")
        if (videoId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        playVideo(videoId)
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "youtube_audio_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Musiqa ijrosi", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Remote Control")
            .setContentText("🎵 Musiqa ijro etilmoqda")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(5, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(5, notification)
        }
    }

    // O'zimizning domenimiz — YouTube'ning o'zini (youtube.com) asos URL sifatida ko'rsatish
    // "self-embedding" deb hisoblanib, 152-xato bilan rad etiladi. Shuning uchun haqiqiy,
    // bizga tegishli domenni ishlatamiz.
    private val embedOrigin = "https://remotecontrol.13-60-189-220.sslip.io"

    private fun playVideo(videoId: String) {
        if (webView == null) {
            setupWebView()
        }
        val html = buildHtml(videoId)
        webView?.loadDataWithBaseURL(embedOrigin, html, "text/html", "utf-8", null)
    }

    private fun setupWebView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.d("YouTubeAudio", "Console: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // 1x1 piksel, teginib bo'lmaydigan, fokus olmaydigan oyna — ko'zga ko'rinmaydi,
        // lekin WebView "ekranga ulangan" bo'lgani uchun tizim uning JS/media ijrosini
        // to'xtatib qo'ymaydi (ko'rinmas service'ga osilgan WebView'lar odatda to'xtatiladi).
        val params = WindowManager.LayoutParams(
            1, 1,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager?.addView(webView, params)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeAudio", "Overlay oynasini qo'sha olmadi (SYSTEM_ALERT_WINDOW ruxsati kerak): ${e.message}")
        }
    }

    private fun buildHtml(videoId: String): String = """
        <!DOCTYPE html>
        <html><body style="margin:0;padding:0;background:transparent;">
        <div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
            console.log('Sahifa yuklandi, IFrame API kutilmoqda...');
            var player;
            function onYouTubeIframeAPIReady() {
                console.log('IFrame API tayyor, pleyer yaratilmoqda...');
                player = new YT.Player('player', {
                    height: '1',
                    width: '1',
                    videoId: '$videoId',
                    playerVars: { 'autoplay': 1, 'controls': 0, 'playsinline': 1, 'origin': '$embedOrigin' },
                    events: {
                        'onReady': function(event) {
                            console.log('Pleyer tayyor, ijro boshlanmoqda...');
                            event.target.playVideo();
                        },
                        'onStateChange': function(event) {
                            console.log('Holat o\'zgardi: ' + event.data);
                        },
                        'onError': function(event) {
                            console.log('XATO: ' + event.data);
                        }
                    }
                });
            }
        </script>
        </body></html>
    """.trimIndent()

    override fun onDestroy() {
        try {
            webView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            // oyna allaqachon olib tashlangan bo'lishi mumkin — e'tiborsiz qoldiramiz
        }
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun stop(context: Context) {
            context.stopService(Intent(context, YouTubeAudioService::class.java))
        }
    }
}
