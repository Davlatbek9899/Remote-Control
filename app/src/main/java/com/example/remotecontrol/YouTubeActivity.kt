package com.example.remotecontrol

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Ilovaning "YouTube" bo'limi. Interfeys (dizayn) ilovaning o'zida, mahalliy
// assets/youtube_ui.html fayli sifatida — internetga bog'liq emas, darhol yuklanadi.
// Faqat haqiqiy ma'lumotlar (videolar, nomlar, ko'rishlar) YouTube Data API orqali olinadi.
class YouTubeActivity : Activity() {

    private lateinit var webView: WebView
    private var pendingVideoId: String? = null
    private var pendingPositionMs: Long = 0
    private var accessToken: String? = null
    private val youtubeScope = Scope("https://www.googleapis.com/auth/youtube.readonly")
    private val RC_SIGN_IN = 9001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingVideoId = intent.getStringExtra("video_id")
        pendingPositionMs = intent.getLongExtra("position_ms", 0L)

        // Fon pleyerini to'xtatamiz — endi shu ekranning o'zi ijro qiladi
        YouTubeAudioService.stop(this)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!pendingVideoId.isNullOrBlank()) {
                    val startSec = pendingPositionMs / 1000
                    webView.evaluateJavascript(
                        "openVideo('$pendingVideoId','Musiqa','YouTube',$startSec);", null
                    )
                    pendingVideoId = null
                } else {
                    checkSignInState()
                }
            }
        }
        webView.webChromeClient = WebChromeClient()

        // "file://" asos URL'i YouTube'ning IFrame Player'i tomonidan 152/153-xato bilan rad
        // etiladi (haqiqiy domen emas deb). Shuning uchun HTML'ni matn sifatida o'qib,
        // o'zimizning domenimizni asos URL qilib beramiz — YouTube uni qonuniy manba deb qabul qiladi.
        val html = assets.open("youtube_ui.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL(
            "https://remotecontrol.13-60-189-220.sslip.io", html, "text/html", "utf-8", null
        )
    }

    // Avtomatik oyna ochmaymiz — agar hisob hali bog'lanmagan bo'lsa, foydalanuvchiga
    // aniq tugma ko'rsatamiz; bosilganda esa haqiqatan hisob tanlash oynasi ochiladi.
    private fun checkSignInState() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && GoogleSignIn.hasPermissions(account, youtubeScope)) {
            fetchAccessTokenAndLoadHome(account)
        } else {
            webView.evaluateJavascript("showConnectButton();", null)
        }
    }

    private fun startSignIn() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(youtubeScope)
                .build()
            val client = GoogleSignIn.getClient(this, gso)
            @Suppress("DEPRECATION")
            startActivityForResult(client.signInIntent, RC_SIGN_IN)
        } catch (e: Exception) {
            Log.e("YouTubeActivity", "signInIntent ishga tushmadi: ${e.message}", e)
            android.widget.Toast.makeText(this, "Google hisobga ulanishda xatolik: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                fetchAccessTokenAndLoadHome(account)
            } catch (e: ApiException) {
                Log.e("YouTubeActivity", "Kirish xato, statusCode=${e.statusCode}: ${e.message}", e)
                android.widget.Toast.makeText(
                    this, "Xato kodi: ${e.statusCode} — ${e.message}", android.widget.Toast.LENGTH_LONG
                ).show()
                webView.evaluateJavascript("showConnectButton();", null)
            }
        }
    }

    private fun fetchAccessTokenAndLoadHome(account: GoogleSignInAccount) {
        Thread {
            try {
                val scopeStr = "oauth2:https://www.googleapis.com/auth/youtube.readonly"
                accessToken = GoogleAuthUtil.getToken(this, account.account!!, scopeStr)
            } catch (e: Exception) {
                Log.e("YouTubeActivity", "Token olishda xato: ${e.message}")
            }
            runOnUiThread { loadHome() }
        }.start()
    }

    inner class Bridge {
        @JavascriptInterface
        fun onHome() {
            runOnUiThread { webView.evaluateJavascript("document.getElementById('feed').innerHTML='<div id=\"loading\">Yuklanmoqda...</div>';", null) }
            loadHome()
        }

        @JavascriptInterface
        fun onSearch(query: String) {
            loadSearch(query)
        }

        @JavascriptInterface
        fun onConnectAccount() {
            runOnUiThread { startSignIn() }
        }
    }

    private fun loadHome() {
        Thread {
            try {
                val token = accessToken
                val resultArray = if (token != null) {
                    loadPersonalizedHome(token)
                } else {
                    loadTrendingHome()
                }
                pushToWebView(resultArray)
            } catch (e: Exception) {
                Log.e("YouTubeActivity", "Bosh sahifa xatosi: ${e.message}")
                pushToWebView(JSONArray())
            }
        }.start()
    }

    // Hisobsiz holat uchun — YouTube'ning ommaviy "trend" ro'yxati
    private fun loadTrendingHome(): JSONArray {
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics,contentDetails" +
                "&chart=mostPopular&maxResults=20&key=$apiKey"
        val json = fetchJson(url)
        val items = json.getJSONArray("items")
        val resultArray = JSONArray()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val snippet = item.getJSONObject("snippet")
            val stats = item.optJSONObject("statistics")
            val content = item.getJSONObject("contentDetails")
            resultArray.put(JSONObject().apply {
                put("id", item.getString("id"))
                put("title", snippet.getString("title"))
                put("channel", snippet.getString("channelTitle"))
                put("thumb", snippet.getJSONObject("thumbnails").getJSONObject("medium").getString("url"))
                put("duration", parseIsoDuration(content.getString("duration")))
                put("views", formatCount(stats?.optString("viewCount", "0") ?: "0"))
                put("ago", formatRelativeTime(snippet.getString("publishedAt")))
            })
        }
        return resultArray
    }

    // YouTube'ning "siz uchun tavsiyalar" algoritmi ochiq API orqali berilmaydi (Google buni
    // maxfiy saqlaydi). Shuning uchun haqiqiy va olish mumkin bo'lgan shaxsiylashtirish yo'li —
    // foydalanuvchi OBUNA bo'lgan kanallardan so'nggi videolarni yig'ib ko'rsatish.
    private fun loadPersonalizedHome(token: String): JSONArray {
        // 1) Obuna bo'lgan kanallar ro'yxati
        val subsJson = fetchJson(
            "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=15&order=alphabetical",
            token
        )
        val subItems = subsJson.getJSONArray("items")
        if (subItems.length() == 0) return loadTrendingHome()

        val channelIds = StringBuilder()
        for (i in 0 until subItems.length()) {
            val cid = subItems.getJSONObject(i).getJSONObject("snippet")
                .getJSONObject("resourceId").getString("channelId")
            if (channelIds.isNotEmpty()) channelIds.append(",")
            channelIds.append(cid)
        }

        // 2) Har bir kanalning "uploads" pleylist ID'sini bitta so'rovda olamiz
        val channelsJson = fetchJson(
            "https://www.googleapis.com/youtube/v3/channels?part=contentDetails&id=$channelIds", token
        )
        val channelItems = channelsJson.getJSONArray("items")

        // 3) Har bir kanaldan so'nggi 2 tadan videoni yig'amiz
        val collected = mutableListOf<Pair<String, JSONObject>>() // videoId, snippet
        for (i in 0 until channelItems.length()) {
            val uploadsPlaylistId = channelItems.getJSONObject(i)
                .getJSONObject("contentDetails").getJSONObject("relatedPlaylists")
                .optString("uploads", null) ?: continue
            try {
                val plJson = fetchJson(
                    "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&playlistId=$uploadsPlaylistId&maxResults=2",
                    token
                )
                val plItems = plJson.getJSONArray("items")
                for (j in 0 until plItems.length()) {
                    val snippet = plItems.getJSONObject(j).getJSONObject("snippet")
                    val vid = snippet.getJSONObject("resourceId").optString("videoId", null) ?: continue
                    collected.add(vid to snippet)
                }
            } catch (e: Exception) {
                // bitta kanalda xato bo'lsa, qolganlarini davom ettiramiz
            }
        }
        if (collected.isEmpty()) return loadTrendingHome()

        // Eng yangi videolar tepada bo'lishi uchun saralaymiz
        collected.sortByDescending { it.second.getString("publishedAt") }
        val top = collected.take(20)

        // 4) Davomiylik/ko'rishlar sonini olish uchun bitta umumiy so'rov
        val ids = top.joinToString(",") { it.first }
        val detailsJson = fetchJson(
            "https://www.googleapis.com/youtube/v3/videos?part=statistics,contentDetails&id=$ids", token
        )
        val detailsItems = detailsJson.getJSONArray("items")
        val detailsMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until detailsItems.length()) {
            val d = detailsItems.getJSONObject(i)
            detailsMap[d.getString("id")] = d
        }

        val resultArray = JSONArray()
        for ((vid, snippet) in top) {
            val details = detailsMap[vid]
            resultArray.put(JSONObject().apply {
                put("id", vid)
                put("title", snippet.getString("title"))
                put("channel", snippet.optString("channelTitle", snippet.optString("videoOwnerChannelTitle", "")))
                put("thumb", snippet.getJSONObject("thumbnails").getJSONObject("medium").getString("url"))
                put("duration", details?.optJSONObject("contentDetails")?.optString("duration")?.let { parseIsoDuration(it) } ?: "")
                put("views", formatCount(details?.optJSONObject("statistics")?.optString("viewCount", "0") ?: "0"))
                put("ago", formatRelativeTime(snippet.getString("publishedAt")))
            })
        }
        return resultArray
    }

    private fun loadSearch(query: String) {
        Thread {
            try {
                val apiKey = BuildConfig.YOUTUBE_API_KEY
                val encoded = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=$encoded" +
                        "&type=video&maxResults=20&key=$apiKey"
                val searchJson = fetchJson(searchUrl)
                val items = searchJson.getJSONArray("items")
                val ids = StringBuilder()
                val metaList = mutableListOf<Pair<String, JSONObject>>()
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val vid = item.getJSONObject("id").getString("videoId")
                    if (ids.isNotEmpty()) ids.append(",")
                    ids.append(vid)
                    metaList.add(vid to item.getJSONObject("snippet"))
                }
                if (ids.isEmpty()) {
                    pushToWebView(JSONArray())
                    return@Thread
                }
                // statistics/duration olish uchun videos.list orqali qo'shimcha so'rov
                val detailsUrl = "https://www.googleapis.com/youtube/v3/videos?part=statistics,contentDetails&id=$ids&key=$apiKey"
                val detailsJson = fetchJson(detailsUrl)
                val detailsItems = detailsJson.getJSONArray("items")
                val detailsMap = mutableMapOf<String, JSONObject>()
                for (i in 0 until detailsItems.length()) {
                    val d = detailsItems.getJSONObject(i)
                    detailsMap[d.getString("id")] = d
                }

                val resultArray = JSONArray()
                for ((vid, snippet) in metaList) {
                    val details = detailsMap[vid]
                    resultArray.put(JSONObject().apply {
                        put("id", vid)
                        put("title", snippet.getString("title"))
                        put("channel", snippet.getString("channelTitle"))
                        put("thumb", snippet.getJSONObject("thumbnails").getJSONObject("medium").getString("url"))
                        put("duration", details?.optJSONObject("contentDetails")?.optString("duration")?.let { parseIsoDuration(it) } ?: "")
                        put("views", formatCount(details?.optJSONObject("statistics")?.optString("viewCount", "0") ?: "0"))
                        put("ago", formatRelativeTime(snippet.getString("publishedAt")))
                    })
                }
                pushToWebView(resultArray)
            } catch (e: Exception) {
                android.util.Log.e("YouTubeActivity", "Qidiruv xatosi: ${e.message}")
                pushToWebView(JSONArray())
            }
        }.start()
    }

    private fun pushToWebView(array: JSONArray) {
        val jsonStr = array.toString().replace("\\", "\\\\").replace("'", "\\'")
        runOnUiThread {
            webView.evaluateJavascript("renderVideos('$jsonStr');", null)
        }
    }

    private fun fetchJson(url: String, token: String? = null): JSONObject {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        val text = conn.inputStream.bufferedReader().readText()
        return JSONObject(text)
    }

    // "PT14M20S" -> "14:20"
    private fun parseIsoDuration(iso: String): String {
        val regex = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
        val match = regex.find(iso) ?: return "0:00"
        val h = match.groupValues[1].toIntOrNull() ?: 0
        val m = match.groupValues[2].toIntOrNull() ?: 0
        val s = match.groupValues[3].toIntOrNull() ?: 0
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }

    // 1200000 -> "1.2 mln", 4500 -> "4.5 ming"
    private fun formatCount(countStr: String): String {
        val count = countStr.toLongOrNull() ?: 0L
        return when {
            count >= 1_000_000 -> String.format("%.1f mln", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1f ming", count / 1_000.0)
            else -> count.toString()
        }
    }

    private fun formatRelativeTime(isoDate: String): String {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val date = fmt.parse(isoDate) ?: return ""
            val diffMs = Date().time - date.time
            val days = diffMs / (1000 * 60 * 60 * 24)
            when {
                days < 1 -> "bugun"
                days < 7 -> "$days kun oldin"
                days < 30 -> "${days / 7} hafta oldin"
                days < 365 -> "${days / 30} oy oldin"
                else -> "${days / 365} yil oldin"
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun onBackPressed() {
        webView.evaluateJavascript(
            "(function(){ var p=document.getElementById('playerView'); " +
                    "if (p.classList.contains('show')) { closePlayer(); return true; } return false; })();"
        ) { result ->
            if (result != "true") {
                runOnUiThread { super.onBackPressed() }
            }
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
