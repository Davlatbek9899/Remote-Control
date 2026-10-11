package com.example.remotecontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class AudioPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private val CHANNEL_ID = "audio_player"
    private val NOTIF_ID = 3
    private var audioName = "Audio"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("AudioPlayer", "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            "STOP" -> { android.util.Log.d("AudioPlayer", "STOP qabul qilindi"); stopPlayback(); return START_NOT_STICKY }
            "PAUSE" -> { android.util.Log.d("AudioPlayer", "PAUSE qabul qilindi"); mediaPlayer?.pause(); updatePlaybackState(false); return START_NOT_STICKY }
            "RESUME" -> { android.util.Log.d("AudioPlayer", "RESUME qabul qilindi"); mediaPlayer?.start(); updatePlaybackState(true); return START_NOT_STICKY }
        }
        val audioPath = intent?.getStringExtra("audio_path") ?: return START_NOT_STICKY
        audioName = intent.getStringExtra("audio_name") ?: "Audio"
        val isUrl = intent.getBooleanExtra("is_url", false)
        createNotificationChannel()
        setupMediaSession()
        showNotification(true)
        playAudio(audioPath, isUrl)
        return START_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "AudioPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { mediaPlayer?.start(); updatePlaybackState(true) }
                override fun onPause() { mediaPlayer?.pause(); updatePlaybackState(false) }
                override fun onStop() { stopPlayback() }
                override fun onSeekTo(pos: Long) {
                    mediaPlayer?.seekTo(pos.toInt())
                    updatePlaybackState(mediaPlayer?.isPlaying == true)
                }
            })
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, audioName)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Remote Control")
                    .build()
            )
            isActive = true
        }
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val duration = mediaPlayer?.duration?.toLong() ?: 0L
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, position, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, audioName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Remote Control")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .build()
        )
        showNotification(isPlaying)
    }

    private fun playAudio(path: String, isUrl: Boolean = false) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                if (isUrl) {
                    setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                    setDataSource(path)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        mp.start()
                        updatePlaybackState(true)
                        Log.d("AudioPlayer", "URL ijro boshlandi: $audioName")
                    }
                } else {
                    setDataSource(path)
                    prepare()
                    start()
                    updatePlaybackState(true)
                    Log.d("AudioPlayer", "Ijro boshlandi: $audioName")
                }
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "Xato: what=$what extra=$extra")
                    stopSelf()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Xato: ${e.message}")
            stopSelf()
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaSession?.isActive = false
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Audio Player", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun pendingIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, AudioPlayerService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun showNotification(isPlaying: Boolean) {
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseAction = if (isPlaying) "PAUSE" else "RESUME"
        val playPauseLabel = if (isPlaying) "Pauza" else "Davom"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(audioName)
            .setContentText("Remote Control")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(playPauseIcon, playPauseLabel, pendingIntent(playPauseAction))
            .addAction(android.R.drawable.ic_delete, "To'xtatish", pendingIntent("STOP"))
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaSession?.release()
        mediaPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}