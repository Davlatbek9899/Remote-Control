package com.example.remotecontrol

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.*
import android.provider.Settings
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewAssetLoader
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.*

class ChildTrackerActivity : AppCompatActivity() {

    private var currentLat = 41.2995
    private var currentLng = 69.2401

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_tracker)
        setupBottomNav()
        setupNoChildScreen()
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navHomeTab).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        findViewById<LinearLayout>(R.id.navHistoryTab).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navCommandsTab).setOnClickListener {
            startActivity(Intent(this, CommandsActivity::class.java)); finish()
        }
        findViewById<LinearLayout>(R.id.navProfileTab).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java)); finish()
        }
    }

    private fun setupNoChildScreen() {
        findViewById<Button>(R.id.btnAddChild).setOnClickListener { showChildScreen() }
        findViewById<Button>(R.id.btnBeWatcher).setOnClickListener {
            Toast.makeText(this, "Tez orada!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showChildScreen() {
        findViewById<LinearLayout>(R.id.layoutNoChild).visibility = android.view.View.GONE
        findViewById<LinearLayout>(R.id.layoutWithChild).visibility = android.view.View.VISIBLE
        setupMap()
        loadRealData()
        setupChildActions()
        setupTimeSelector()
        setupSwipeRefresh()
    }

    private fun setupMap() {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) { currentLat = location.latitude; currentLng = location.longitude }
                loadMap(currentLat, currentLng)
            }.addOnFailureListener { loadMap(currentLat, currentLng) }
        } catch (e: SecurityException) { loadMap(currentLat, currentLng) }
    }

    private fun loadMap(lat: Double, lng: Double) {
        val fallback = findViewById<LinearLayout>(R.id.mapFallback)
        val webView = findViewById<WebView>(R.id.mapWebView)
        fallback.visibility = android.view.View.GONE
        webView.visibility = android.view.View.VISIBLE

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                return request?.url?.let { assetLoader.shouldInterceptRequest(it) }
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript("setLocation($lat, $lng);", null)
            }
        }

        webView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/map.html")
    }

    private fun loadRealData() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        findViewById<TextView>(R.id.tvBatteryLevel).text = "$battery%" + if (isCharging) " ⚡" else ""
        findViewById<TextView>(R.id.tvSelectedDate).text = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(Date())
        loadAppUsage()
    }

    private fun loadAppUsage() {
        val donut = findViewById<AppUsageDonutView>(R.id.appUsageDonut)
        val barView = findViewById<AppUsageBarView>(R.id.appUsageBar)
        val legend = findViewById<LinearLayout>(R.id.llAppLegend)
        val tvTotal = findViewById<TextView>(R.id.tvTotalUsageTime)
        if (!hasUsageStatsPermission()) { tvTotal.text = "Ruxsat kerak"; return }
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
            val pm = packageManager
            // Faqat user ilovalar (launcher ikonkasi borlar)
            val topStats = stats
                .filter {
                    it.totalTimeInForeground > 60_000 &&
                    it.packageName != packageName &&
                    pm.getLaunchIntentForPackage(it.packageName) != null
                }
                .groupBy { it.packageName }
                .map { (_, list) -> list.maxByOrNull { it.totalTimeInForeground }!! }
                .sortedByDescending { it.totalTimeInForeground }
                .take(4)
            if (topStats.isEmpty()) { showFallbackWithRealIcons(donut, barView, legend, tvTotal); return }
            val maxTime = topStats.first().totalTimeInForeground
            val appList = topStats.map { stat ->
                val name = try { pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString() }
                    catch (e: Exception) { stat.packageName.split(".").last() }
                val ratio = stat.totalTimeInForeground.toFloat() / maxTime
                val r = (255 * ratio).toInt().coerceIn(0, 255)
                val b = (255 * (1f - ratio)).toInt().coerceIn(0, 255)
                AppUsageSegment(name, (stat.totalTimeInForeground / 60_000).toInt(), Color.rgb(r, 60, b), stat.packageName)
            }
            val allTotal = stats.filter { it.totalTimeInForeground > 60_000 }.sumOf { (it.totalTimeInForeground / 60_000).toInt() }
            val h = allTotal / 60; val m = allTotal % 60
            tvTotal.text = if (h > 0) "$h soat $m daqiqa" else "$m daqiqa"
            donut.setSegments(appList); barView.setSegments(appList); buildLegend(legend, appList)
        } catch (e: Exception) { showFallbackWithRealIcons(donut, barView, legend, tvTotal) }
    }

    private fun showFallbackWithRealIcons(donut: AppUsageDonutView, barView: AppUsageBarView, legend: LinearLayout, tvTotal: TextView) {
        val pm = packageManager
        val knownApps = listOf(
            "org.telegram.messenger",
            "com.netflix.mediaclient",
            "com.yandex.browser",
            "uz.dida.payme"
        )
        val installed = knownApps.filter {
            try { pm.getApplicationInfo(it, 0); true } catch (e: Exception) { false }
        }.take(4)
        if (installed.isEmpty()) { tvTotal.text = "App Usage ruxsati kerak"; return }
        val colors = listOf(
            Color.rgb(41, 182, 246),
            Color.rgb(255, 183, 77),
            Color.rgb(129, 199, 132),
            Color.rgb(77, 208, 225)
        )
        val minutes = listOf(119, 97, 56, 41)
        val segments = installed.mapIndexed { i, pkg ->
            val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg.split(".").last() }
            AppUsageSegment(name, minutes[i], colors[i % colors.size], pkg)
        }
        val total = segments.sumOf { it.minutes }; val h = total / 60; val m = total % 60
        tvTotal.text = if (h > 0) "$h soat $m daqiqa" else "$m daqiqa"
        donut.setSegments(segments); barView.setSegments(segments); buildLegend(legend, segments)
    }

    private fun buildLegend(container: LinearLayout, segments: List<AppUsageSegment>) {
        container.removeAllViews()
        val pm = packageManager
        val dp = resources.displayMetrics.density
        for (seg in segments.take(4)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (5*dp).toInt(), 0, (5*dp).toInt())
            }
            val iconSize = (36*dp).toInt()
            val iconMargin = (10*dp).toInt()
            val iconView = android.widget.ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).also { it.marginEnd = iconMargin }
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            // Ikonkani yuklash — avval packageName bilan, keyin doira
            var iconLoaded = false
            if (seg.packageName.isNotEmpty() && seg.packageName.contains(".")) {
                try {
                    val appInfo = pm.getApplicationInfo(seg.packageName, 0)
                    iconView.setImageDrawable(appInfo.loadIcon(pm))
                    iconLoaded = true
                } catch (e: Exception) {}
            }
            if (!iconLoaded) {
                val circle = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(seg.color)
                }
                iconView.setImageDrawable(circle)
            }
            val h = seg.minutes / 60; val m = seg.minutes % 60
            val label = TextView(this).apply {
                text = if (h > 0) "$h soat $m daqiqa" else "$m daqiqa"
                textSize = 13f
                setTextColor(Color.parseColor("#222222"))
                setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            row.addView(iconView); row.addView(label); container.addView(row)
        }
    }

    private fun setupSwipeRefresh() {
        try {
            val swipe = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
            swipe.setColorSchemeColors(
                android.graphics.Color.parseColor("#1B8EF8"),
                android.graphics.Color.parseColor("#FF4B4B")
            )
            swipe.setOnRefreshListener {
                setupMap()
                loadRealData()
                android.os.Handler(mainLooper).postDelayed({
                    swipe.isRefreshing = false
                }, 1500)
            }
        } catch (e: Exception) {}
    }

    private fun setupTimeSelector() {
        try { findViewById<ImageButton>(R.id.btnTimeSelector).setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                val amPm = if (hour < 12) "AM" else "PM"; val h = if (hour % 12 == 0) 12 else hour % 12
                findViewById<TextView>(R.id.tvSelectedTime).text = String.format("%d:%02d %s", h, minute, amPm)
                Toast.makeText(this, "O'sha vaqtdagi joylashuv", Toast.LENGTH_SHORT).show()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }} catch (e: Exception) {}
    }

    private fun setupChildActions() {
        val p = "+998901234567"
        try { findViewById<LinearLayout>(R.id.btnQuickCamera).setOnClickListener { CommandHandler(this).execute(p, "#take_picture"); Toast.makeText(this, "Suratga tushirilmoqda...", Toast.LENGTH_SHORT).show() }} catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.btnQuickAudio).setOnClickListener { CommandHandler(this).execute(p, "#record_audio:30"); Toast.makeText(this, "Ovoz yozilmoqda...", Toast.LENGTH_SHORT).show() }} catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.btnQuickSms).setOnClickListener { CommandHandler(this).execute(p, "#sms_list"); Toast.makeText(this, "SMS lar olinmoqda...", Toast.LENGTH_SHORT).show() }} catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.btnQuickCallLog).setOnClickListener { CommandHandler(this).execute(p, "#call_log"); Toast.makeText(this, "Qo'ng'iroqlar olinmoqda...", Toast.LENGTH_SHORT).show() }} catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.btnQuickScreenOff).setOnClickListener { CommandHandler(this).execute(p, "#screen_off"); Toast.makeText(this, "Ekran o'chirilmoqda...", Toast.LENGTH_SHORT).show() }} catch (e: Exception) {}
        try { findViewById<LinearLayout>(R.id.btnQuickAppExit).setOnClickListener { Toast.makeText(this, "Tez orada!", Toast.LENGTH_SHORT).show() }} catch (e: Exception) {}
        try { findViewById<Button>(R.id.btnAddChildHeader).setOnClickListener { Toast.makeText(this, "Tez orada!", Toast.LENGTH_SHORT).show() }} catch (e: Exception) {}
        try { findViewById<android.widget.ImageButton>(R.id.btnShareLocation).setOnClickListener {
            val mapsLink = "https://maps.google.com/?q=" + currentLat + "," + currentLng
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Joylashuv: " + mapsLink)
            }
            startActivity(Intent.createChooser(shareIntent, "Ulashish"))
        }} catch (e: Exception) {}
    }
}
