package com.example.remotecontrol

import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var replyTo: String

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        replyTo = intent?.getStringExtra("reply_to") ?: ""
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        getLocation()
        return START_NOT_STICKY
    }

    private fun getLocation() {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val lat = location.latitude
                        val lng = location.longitude
                        val accuracy = location.accuracy
                        val googleMapsLink = "https://maps.google.com/?q=$lat,$lng"
                        val message = "📍 Joylashuv:\nKenglik: $lat\nUzunlik: $lng\nAniqlik: ${accuracy}m\n🗺️ $googleMapsLink"
                        // Telegram orqali kelgan bo'lsa — native location yuborish
                        val telegramService = com.example.remotecontrol.TelegramService.instance
                        if (telegramService != null && replyTo.all { it.isDigit() || it == '-' }) {
                            telegramService.sendTelegramLocation(replyTo, lat, lng)
                            telegramService.sendTelegram(replyTo, "📍 Aniqlik: ${accuracy.toInt()}m")
                        } else {
                            CommandHandler(this).sendSms(replyTo, message)
                        }
                        Log.d("LocationService", "Joylashuv yuborildi: $lat, $lng")
                    } else {
                        // Yangi joylashuvni so'rash
                        requestNewLocation()
                    }
                    stopSelf()
                }
                .addOnFailureListener { e ->
                    CommandHandler(this).sendSms(replyTo, "❌ Joylashuvni aniqlashda xato: ${e.message}")
                    stopSelf()
                }
        } catch (e: SecurityException) {
            Log.e("LocationService", "GPS ruxsati yo'q: ${e.message}")
            stopSelf()
        }
    }

    private fun requestNewLocation() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).setMaxUpdates(1).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val lat = location.latitude
                val lng = location.longitude
                val link = "https://maps.google.com/?q=$lat,$lng"
                CommandHandler(this@LocationService).sendSms(
                    replyTo,
                    "📍 Joylashuv:\n$lat, $lng\n🗺️ $link"
                )
                fusedLocationClient.removeLocationUpdates(this)
                stopSelf()
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, mainLooper)
        } catch (e: SecurityException) {
            Log.e("LocationService", "GPS ruxsati yo'q")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
