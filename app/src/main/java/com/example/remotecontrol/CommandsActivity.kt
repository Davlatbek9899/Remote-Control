package com.example.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class CommandsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_commands)

        val header = findViewById<android.widget.LinearLayout>(R.id.headerCommands)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            val statusBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            val dp16 = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, statusBar + dp16, view.paddingRight, view.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnBackCommands).setOnClickListener { finish() }

        // Bottom nav
        findViewById<android.widget.LinearLayout>(R.id.navHomeTab).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        findViewById<android.widget.LinearLayout>(R.id.navHistoryTab).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            finish()
        }
        findViewById<android.widget.LinearLayout>(R.id.navChildTab).setOnClickListener {
            startActivity(Intent(this, ChildTrackerActivity::class.java))
            finish()
        }
        findViewById<android.widget.LinearLayout>(R.id.navProfileTab).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java)); finish()
        }
    }
}
