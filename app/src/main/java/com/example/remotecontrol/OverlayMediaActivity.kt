package com.example.remotecontrol

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class OverlayMediaActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.apply {
            addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
            attributes = attributes.also { it.dimAmount = 0.75f }
        }

        val filePath = intent.getStringExtra("file_path") ?: ""
        val isVideo = intent.getBooleanExtra("is_video", false)
        val caption = intent.getStringExtra("caption") ?: ""

        val screenWidth = resources.displayMetrics.widthPixels
        val cardWidth = screenWidth - 64 // 32dp har tomonda

        // Root
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }

        // Card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 48f
                setColor(Color.parseColor("#1A2535"))
            }
            elevation = 32f
            clipToOutline = true
            setOnClickListener { }
        }

        val displayText = intent.getStringExtra("display_text") ?: ""

        if (displayText.isNotEmpty()) {
            // TEXT rejimi - oq card
            card.background = GradientDrawable().apply {
                cornerRadius = 48f
                setColor(Color.WHITE)
            }
            val textSize = when {
                displayText.length < 30 -> 32f
                displayText.length < 60 -> 26f
                displayText.length < 100 -> 22f
                displayText.length < 200 -> 18f
                else -> 15f
            }
            val tv = TextView(this).apply {
                text = displayText
                setTextColor(Color.parseColor("#1A1A2E"))
                this.textSize = textSize
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(56, 64, 56, 64)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            card.addView(tv)

            // Chip
            val chipsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(40, 8, 40, 16)
                gravity = Gravity.CENTER
            }
            val chipView = TextView(this).apply {
                text = "💬 Xabar"
                setTextColor(Color.parseColor("#1A1A2E"))
                this.textSize = 12f
                setPadding(20, 8, 20, 8)
                background = GradientDrawable().apply {
                    cornerRadius = 32f
                    setColor(Color.parseColor("#F0F4FF"))
                }
            }
            chipsRow.addView(chipView)
            card.addView(chipsRow)

            val closeBtn = TextView(this).apply {
                text = "✕   Yopish"
                setTextColor(Color.parseColor("#1A1A2E"))
                this.textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 24)
                background = GradientDrawable().apply {
                    cornerRadius = 40f
                    setColor(Color.parseColor("#2563EB"))
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(40, 0, 40, 40)
                layoutParams = lp
                setOnClickListener { finish() }
            }
            card.addView(closeBtn)

            val cardLp = FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            rootLayout.addView(card, cardLp)
            setContentView(rootLayout)
            Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 30000)
            return
        }

        if (isVideo) {
            // Video proporsiyasini hisoblash
            var mediaW = 9
            var mediaH = 16
            try {
                val ret = MediaMetadataRetriever()
                ret.setDataSource(filePath)
                val w = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 9
                val h = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 16
                val rot = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                ret.release()
                // Rotation 90 yoki 270 bo'lsa swap
                if (rot == 90 || rot == 270) { mediaW = h; mediaH = w }
                else { mediaW = w; mediaH = h }
            } catch (e: Exception) { }

            // Video height = cardWidth * (mediaH / mediaW)
            val videoHeight = (cardWidth * mediaH.toFloat() / mediaW.toFloat()).toInt()
                .coerceIn(300, (resources.displayMetrics.heightPixels * 0.8f).toInt())

            val videoContainer = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, videoHeight
                )
                background = GradientDrawable().apply {
                    cornerRadii = floatArrayOf(48f, 48f, 48f, 48f, 0f, 0f, 0f, 0f)
                }
                clipToOutline = true
            }
            val videoView = VideoView(this).apply {
                setVideoURI(Uri.parse(filePath))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            videoContainer.addView(videoView)
            card.addView(videoContainer)
            videoView.setOnPreparedListener { mp ->
                mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                mp.start()
            }
            videoView.setOnCompletionListener { finish() }

        } else {
            val bitmap = BitmapFactory.decodeFile(filePath)
            if (bitmap != null) {
                val imgHeight = (cardWidth * bitmap.height.toFloat() / bitmap.width.toFloat()).toInt()
                    .coerceIn(200, (resources.displayMetrics.heightPixels * 0.8f).toInt())
                val imageView = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, imgHeight
                    )
                    clipToOutline = true
                    background = GradientDrawable().apply {
                        cornerRadii = floatArrayOf(48f, 48f, 48f, 48f, 0f, 0f, 0f, 0f)
                    }
                }
                card.addView(imageView)
            }
        }

        // Caption
        if (caption.isNotEmpty()) {
            val tv = TextView(this).apply {
                text = caption
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(40, 20, 40, 8)
            }
            card.addView(tv)
        }

        // Chip
        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 10, 40, 16)
        }
        val chipText = if (isVideo) "🎬 Video" else "🖼 Rasm"
        val chipView = TextView(this).apply {
            text = chipText
            setTextColor(Color.parseColor("#1A2535"))
            textSize = 12f
            setPadding(20, 8, 20, 8)
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(Color.parseColor("#F0F4FF"))
            }
        }
        chipsRow.addView(chipView)
        card.addView(chipsRow)

        // Yopish
        val closeBtn = TextView(this).apply {
            text = "✕   Yopish"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#2563EB"))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(40, 0, 40, 40)
            layoutParams = lp
            setOnClickListener { finish() }
        }
        card.addView(closeBtn)

        val cardLp = FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        rootLayout.addView(card, cardLp)
        setContentView(rootLayout)

        // Faqat rasm uchun 30 sekund timer, video o'zi tugaganda yopiladi
        if (!isVideo) {
            Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 30000)
        }
    }
}
