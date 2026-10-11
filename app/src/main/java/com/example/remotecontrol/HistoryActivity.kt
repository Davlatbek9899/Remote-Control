package com.example.remotecontrol

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val header = findViewById<android.widget.LinearLayout>(R.id.headerHistory)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            val statusBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            val dp16 = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, statusBar + dp16, view.paddingRight, view.paddingBottom)
            insets
        }
        header.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_down))

        findViewById<android.widget.ImageButton>(R.id.btnBackHistory).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.slide_left)
        }
        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            CommandLogger.clearLog(this)
            refreshList()
            Toast.makeText(this, "Tarix tozalandi", Toast.LENGTH_SHORT).show()
        }

        // Bottom nav
        findViewById<android.widget.LinearLayout>(R.id.navHomeTab).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        findViewById<android.widget.LinearLayout>(R.id.navCommandsTab).setOnClickListener {
            startActivity(Intent(this, CommandsActivity::class.java))
            finish()
            overridePendingTransition(R.anim.slide_up, R.anim.fade_in)
        }
        findViewById<android.widget.LinearLayout>(R.id.navChildTab).setOnClickListener {
            startActivity(Intent(this, ChildTrackerActivity::class.java))
            finish()
        }
        findViewById<android.widget.LinearLayout>(R.id.navProfileTab).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java)); finish()
        }

        refreshList()
    }

    private fun refreshList() {
        val listView = findViewById<ListView>(R.id.lvHistory)
        val tvEmpty = findViewById<TextView>(R.id.tvHistoryEmpty)
        val logs = CommandLogger.getLogs(this)
        if (logs.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            listView.visibility = View.GONE
            return
        }
        tvEmpty.visibility = View.GONE
        listView.visibility = View.VISIBLE
        val adapter = object : ArrayAdapter<CommandLogger.LogEntry>(this, 0, logs.reversed()) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
                val entry = getItem(pos)!!
                view.findViewById<TextView>(R.id.tvLogSender).text = entry.sender
                view.findViewById<TextView>(R.id.tvLogCommand).text = entry.command
                view.findViewById<TextView>(R.id.tvLogTime).text = entry.time
                return view
            }
        }
        listView.adapter = adapter
    }
}
