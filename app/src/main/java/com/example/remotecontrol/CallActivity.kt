package com.example.remotecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class CallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent.getStringExtra("number") ?: run { finish(); return }

        android.util.Log.d("CallActivity", "Raqam: $number")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("CallActivity", "Ruxsat bor, qo'ng'iroq qilinmoqda")
            try {
                // TelecomManager orqali to'g'ridan-to'g'ri qo'ng'iroq qilamiz — bu usul
                // bir nechta ilova "tel:" sxemasini ushlab turganda chiqadigan
                // tanlov (chooser) oynasini chetlab o'tadi, ekran qulflangan bo'lsa ham ishlaydi.
                // Foydalanuvchi ilovada tanlagan SIM kartani ham qo'shamiz — aks holda
                // ikki SIM li qurilmada tizim yana "qaysi SIM?" deb so'rab qoladi.
                val telecomManager = getSystemService(android.telecom.TelecomManager::class.java)
                val extras = Bundle()
                getPreferredPhoneAccountHandle()?.let {
                    extras.putParcelable(android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
                }
                telecomManager.placeCall(Uri.parse("tel:$number"), extras)
            } catch (e: Exception) {
                android.util.Log.e("CallActivity", "TelecomManager xato, ACTION_CALL ga o'tildi: ${e.message}")
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$number")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(callIntent)
            }
        } else {
            android.util.Log.d("CallActivity", "Ruxsat yo'q, so'ralmoqda")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
        }

        // finish() ni kechiktirish — qo'ng'iroq boshlashga vaqt berish
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 3000)
    }

    @Suppress("MissingPermission")
    private fun getPreferredPhoneAccountHandle(): android.telecom.PhoneAccountHandle? {
        return try {
            val simSlot = AutoSmsManager.getSimSlot(this)
            val subManager = getSystemService(android.telephony.SubscriptionManager::class.java)
            val subs = subManager.activeSubscriptionInfoList ?: return null
            if (subs.size <= simSlot) return null
            val targetSubId = subs[simSlot].subscriptionId

            val telecomManager = getSystemService(android.telecom.TelecomManager::class.java)
            val telephonyManager = getSystemService(android.telephony.TelephonyManager::class.java)
            telecomManager.callCapablePhoneAccounts.firstOrNull { handle ->
                telephonyManager.createForPhoneAccountHandle(handle)?.subscriptionId == targetSubId
            }
        } catch (e: Exception) {
            android.util.Log.e("CallActivity", "SIM aniqlash xato: ${e.message}")
            null
        }
    }
}
