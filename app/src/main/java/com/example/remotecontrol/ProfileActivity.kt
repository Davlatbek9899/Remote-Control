package com.example.remotecontrol

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        setupBottomNav()
        loadProfileData()
        setupLogout()
    }

    private fun setupLogout() {
        findViewById<android.widget.LinearLayout>(R.id.btnLogout).setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showLogoutDialog() {
        val dialog = android.app.Dialog(this, R.style.Theme_RemoteControl_Dialog)
        dialog.setContentView(R.layout.dialog_logout)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<android.widget.Button>(R.id.btnDialogCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<android.widget.Button>(R.id.btnDialogLogout).setOnClickListener {
            getSharedPreferences("rc_auth", MODE_PRIVATE).edit().clear().apply()
            getSharedPreferences("rc_pin", MODE_PRIVATE).edit().clear().apply()
            startActivity(android.content.Intent(this, AuthActivity::class.java))
            finish()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun loadProfileData() {
        findViewById<android.widget.TextView>(R.id.tvProfileName).text = "Davlatbek"
        findViewById<android.widget.TextView>(R.id.tvProfilePhone).text = "+998 93 221 98 99"
        findViewById<android.widget.ImageView>(R.id.ivProfilePicture).setImageResource(R.drawable.profile_picture)
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
        findViewById<LinearLayout>(R.id.navChildTab).setOnClickListener {
            startActivity(Intent(this, ChildTrackerActivity::class.java)); finish()
        }
    }
}