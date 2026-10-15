package com.example.remotecontrol

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

// Ilovaning "YouTube" bo'limi — haqiqiy YouTube veb-saytini (m.youtube.com) to'liq,
// ko'rinadigan WebView ichida ochadi. Musiqa bildirishnomasi (banner) bosilganda shu
// ekran ochiladi va fon pleyeri qoldirgan video/soniyadan davom etadi.
class YouTubeActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var rootLayout: FrameLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoId = intent.getStringExtra("video_id")
        val positionMs = intent.getLongExtra("position_ms", 0L)

        // Fon pleyerini to'xtatamiz — endi shu ekranning o'zi ijro qiladi, ikkala
        // joyda bir vaqtda ovoz chiqib ketmasligi uchun.
        YouTubeAudioService.stop(this)

        rootLayout = FrameLayout(this)
        setContentView(rootLayout)

        webView = WebView(this)
        rootLayout.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            @Suppress("DEPRECATION")
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            // YouTube'ning "to'liq ekran" (fullscreen video) tugmasi shu orqali ishlaydi
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                rootLayout.addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                ))
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                customView?.let { rootLayout.removeView(it) }
                customView = null
                webView.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
            }
        }

        val startSeconds = positionMs / 1000
        val url = if (!videoId.isNullOrBlank()) {
            "https://m.youtube.com/watch?v=$videoId&t=${startSeconds}s"
        } else {
            "https://m.youtube.com"
        }
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        rootLayout.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}
