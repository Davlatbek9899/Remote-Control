package com.example.remotecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver

// Musiqani fon rejimida, YouTube ilovasini ochmasdan ijro qilish uchun xizmat.
// YouTube'ning rasmiy IFrame Player API'si ko'rinmas (1x1) overlay oynada ishlaydi.
// Ijro rejimlari: SINGLE (bitta qo'shiq), LOOP (takrorlash), CONTINUOUS (o'xshash
// qo'shiqlarga avtomatik o'tish), ARTIST (faqat bitta ijrochi doirasida aylanish).
class YouTubeAudioService : Service() {

    private enum class PlaybackMode { SINGLE, LOOP, CONTINUOUS, ARTIST }

    private var webView: WebView? = null
    private var windowManager: WindowManager? = null
    private var pageLoaded = false
    private var isPlaying = false
    private var currentTitle = "Musiqa"
    private var currentPositionMs = 0L
    private var currentDurationMs = 0L
    private var albumArt: android.graphics.Bitmap? = null

    private var mode = PlaybackMode.SINGLE
    private var artistSeed = "" // "continuous"/"artist" rejimida keyingi qo'shiqni qidirish uchun ism

    // Ijro navbati: (videoId, title, channelTitle). queueIndex joriy pozitsiyani bildiradi —
    // Next/Previous shu ro'yxat bo'ylab yuradi; ro'yxat oxiriga yetganda Next yangi qo'shiq qidiradi.
    private val queue = mutableListOf<Triple<String, String, String>>()
    private var queueIndex = -1
    private val playedVideoIds = mutableSetOf<String>()

    private lateinit var mediaSession: MediaSessionCompat

    // O'zimizning domenimiz — YouTube'ning o'zini (youtube.com) asos URL sifatida ko'rsatish
    // "self-embedding" deb hisoblanib, 152-xato bilan rad etiladi. Shuning uchun haqiqiy,
    // bizga tegishli domenni ishlatamiz.
    private val embedOrigin = "https://remotecontrol.13-60-189-220.sslip.io"

    override fun onCreate() {
        super.onCreate()
        setupMediaSession()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        val videoId = intent?.getStringExtra("video_id")
        if (videoId.isNullOrBlank()) {
            return START_STICKY
        }
        val title = intent.getStringExtra("title") ?: "Musiqa"
        val modeStr = intent.getStringExtra("mode") ?: "single"
        val artist = intent.getStringExtra("artist") ?: ""
        val fresh = intent.getBooleanExtra("fresh", true)

        mode = when (modeStr) {
            "loop" -> PlaybackMode.LOOP
            "continuous" -> PlaybackMode.CONTINUOUS
            "artist" -> PlaybackMode.ARTIST
            else -> PlaybackMode.SINGLE
        }
        artistSeed = artist

        if (fresh) {
            // Yangi, foydalanuvchi aytgan buyruq — navbatni boshidan boshlaymiz
            queue.clear()
            playedVideoIds.clear()
            queueIndex = -1
        }
        enqueueAndPlay(videoId, title, artist)
        return START_STICKY
    }

    private fun enqueueAndPlay(videoId: String, title: String, channelTitle: String) {
        // Agar oldinga qaytib (previous) keyin yangi yo'nalishga ketilsa, eski "oldinga" tarixini tashlaymiz
        if (queueIndex < queue.size - 1) {
            while (queue.size > queueIndex + 1) queue.removeAt(queue.size - 1)
        }
        queue.add(Triple(videoId, title, channelTitle))
        queueIndex = queue.size - 1
        playedVideoIds.add(videoId)

        currentTitle = title
        albumArt = null
        fetchThumbnail(videoId)
        playVideo(videoId)
    }

    // Next tugmasi yoki qo'shiq tabiiy tugaganda (continuous/artist rejimlarida) chaqiriladi.
    private fun advanceToNext() {
        if (queueIndex < queue.size - 1) {
            // Foydalanuvchi avval "orqaga" bosgan edi — navbatdagi (allaqachon topilgan) qo'shiqni ijro qilamiz
            queueIndex++
            val (vid, title, _) = queue[queueIndex]
            currentTitle = title
            albumArt = null
            fetchThumbnail(vid)
            playVideo(vid)
            return
        }
        // Yagona rejimda Next bosilsa ham, shu zahoti "davom etish" rejimiga o'tkazamiz
        if (mode == PlaybackMode.SINGLE) mode = PlaybackMode.CONTINUOUS

        val seed = if (artistSeed.isNotBlank()) artistSeed else currentTitle
        Thread {
            val found = JarvisCommandExecutor.searchYoutubeFull(BuildConfig.YOUTUBE_API_KEY, seed)
            android.os.Handler(mainLooper).post {
                if (found != null) {
                    val (vid, title, channel) = found
                    if (vid !in playedVideoIds) {
                        if (artistSeed.isBlank()) artistSeed = channel
                        enqueueAndPlay(vid, title, channel)
                    }
                }
            }
        }.start()
    }

    private fun goToPrevious() {
        if (queueIndex > 0) {
            queueIndex--
            val (vid, title, _) = queue[queueIndex]
            currentTitle = title
            albumArt = null
            fetchThumbnail(vid)
            playVideo(vid)
        }
    }

    private fun fetchThumbnail(videoId: String) {
        Thread {
            try {
                val url = java.net.URL("https://img.youtube.com/vi/$videoId/hqdefault.jpg")
                val bitmap = android.graphics.BitmapFactory.decodeStream(url.openStream())
                albumArt = bitmap
                android.os.Handler(mainLooper).post {
                    updatePlaybackState()
                    updateNotification()
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeAudio", "Rasm olishda xato: ${e.message}")
            }
        }.start()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "YouTubeAudioService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    webView?.evaluateJavascript("if (player) player.playVideo();", null)
                }

                override fun onPause() {
                    webView?.evaluateJavascript("if (player) player.pauseVideo();", null)
                }

                override fun onStop() {
                    stopSelf()
                }

                override fun onRewind() {
                    webView?.evaluateJavascript(
                        "if (player) player.seekTo(Math.max(0, player.getCurrentTime() - 10), true);", null
                    )
                }

                override fun onFastForward() {
                    webView?.evaluateJavascript(
                        "if (player) player.seekTo(player.getCurrentTime() + 10, true);", null
                    )
                }

                override fun onSeekTo(pos: Long) {
                    webView?.evaluateJavascript("if (player) player.seekTo(${pos / 1000}, true);", null)
                }

                override fun onSkipToNext() {
                    advanceToNext()
                }

                override fun onSkipToPrevious() {
                    goToPrevious()
                }
            })
            isActive = true
        }
    }

    // JS tomondan (IFrame Player holati o'zgarganda) chaqiriladi — bildirishnoma va
    // MediaSession holatini (play/pause belgisi, pozitsiya) shunga mos yangilaydi.
    inner class PlayerBridge {
        @JavascriptInterface
        fun onStateChange(state: Int) {
            // YT.PlayerState: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued
            isPlaying = (state == 1)
            android.os.Handler(mainLooper).post {
                updatePlaybackState()
                updateNotification()
                if (state == 0) { // qo'shiq tugadi
                    when (mode) {
                        PlaybackMode.SINGLE -> stopSelf()
                        PlaybackMode.LOOP -> webView?.evaluateJavascript(
                            "if (player) { player.seekTo(0, true); player.playVideo(); }", null
                        )
                        PlaybackMode.CONTINUOUS, PlaybackMode.ARTIST -> advanceToNext()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onTimeUpdate(currentSeconds: Double, durationSeconds: Double) {
            currentPositionMs = (currentSeconds * 1000).toLong()
            currentDurationMs = (durationSeconds * 1000).toLong()
            android.os.Handler(mainLooper).post { updatePlaybackState() }
        }
    }

    private fun updatePlaybackState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, currentPositionMs, 1f)
                .build()
        )
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "YouTube")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDurationMs)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .build()
        )
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
        val notification = buildNotification(channelId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(5, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(5, notification)
        }
    }

    private fun buildNotification(channelId: String): android.app.Notification {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pauza",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Ijro",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)
            )
        }
        val rewindAction = NotificationCompat.Action(
            android.R.drawable.ic_media_rew, "-10s",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND)
        )
        val forwardAction = NotificationCompat.Action(
            android.R.drawable.ic_media_ff, "+10s",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_FAST_FORWARD)
        )
        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Oldingi",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        )
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Keyingi",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(currentTitle)
            .setContentText(if (isPlaying) "🎵 Ijro etilmoqda" else "⏸ Pauza qilingan")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .apply { albumArt?.let { setLargeIcon(it) } }
            .addAction(prevAction)
            .addAction(rewindAction)
            .addAction(playPauseAction)
            .addAction(forwardAction)
            .addAction(nextAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 2, 4)
            )
            .setOngoing(isPlaying)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(5, buildNotification("youtube_audio_service"))
    }

    private fun playVideo(videoId: String) {
        if (webView == null) {
            setupWebView()
        }
        if (!pageLoaded) {
            val html = buildHtml(videoId)
            webView?.loadDataWithBaseURL(embedOrigin, html, "text/html", "utf-8", null)
            pageLoaded = true
        } else {
            // Sahifani qayta yuklamasdan, mavjud pleyerga yangi videoni yuklaymiz —
            // bu "avtomatik ovozli ijro" ruxsatini qayta so'rashning oldini oladi.
            webView?.evaluateJavascript("playVideoId('$videoId');", null)
        }
    }

    private fun setupWebView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            addJavascriptInterface(PlayerBridge(), "AndroidPlayer")
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.d("YouTubeAudio", "Console: ${message.message()}")
                    return true
                }
            }
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

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
            android.util.Log.e("YouTubeAudio", "Overlay oynasini qo'sha olmadi: ${e.message}")
        }
    }

    private fun buildHtml(initialVideoId: String): String = """
        <!DOCTYPE html>
        <html><body style="margin:0;padding:0;background:transparent;">
        <div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
            var player;
            var pendingVideoId = '$initialVideoId';
            function onYouTubeIframeAPIReady() {
                player = new YT.Player('player', {
                    height: '1',
                    width: '1',
                    playerVars: { 'autoplay': 1, 'controls': 0, 'playsinline': 1, 'origin': '$embedOrigin' },
                    events: {
                        'onReady': function(event) {
                            if (pendingVideoId) {
                                event.target.loadVideoById(pendingVideoId);
                                pendingVideoId = null;
                            } else {
                                event.target.playVideo();
                            }
                        },
                        'onStateChange': function(event) {
                            AndroidPlayer.onStateChange(event.data);
                        },
                        'onError': function(event) {
                            console.log('XATO: ' + event.data);
                        }
                    }
                });
                setInterval(function() {
                    if (player && typeof player.getCurrentTime === 'function') {
                        var dur = player.getDuration() || 0;
                        if (dur > 0) {
                            AndroidPlayer.onTimeUpdate(player.getCurrentTime(), dur);
                        }
                    }
                }, 1000);
            }
            function playVideoId(id) {
                if (player && typeof player.loadVideoById === 'function') {
                    player.loadVideoById(id);
                } else {
                    pendingVideoId = id;
                }
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
        pageLoaded = false
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun stop(context: Context) {
            context.stopService(Intent(context, YouTubeAudioService::class.java))
        }
    }
}
