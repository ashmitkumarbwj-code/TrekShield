package com.example.treksafetyapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.example.treksafetyapp.data.AppDatabase
import com.example.treksafetyapp.data.LocationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Looper

class TrekTrackingService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Creates a persistent notification so the system doesn't kill the service
        val notification = NotificationCompat.Builder(this, "TREK_CHANNEL_ID")
            .setContentTitle("Trek Safety App Active")
            .setContentText("Actively tracking your location every 15 mins...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        startForeground(1, notification)
        
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            15 * 60 * 1000 // 15 min
        ).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val db = AppDatabase.getDatabase(applicationContext)

                // Room suspend functions require a coroutine!
                CoroutineScope(Dispatchers.IO).launch {
                    db.locationDao().insertLocation(
                        LocationEntity(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = System.currentTimeMillis()
                        )
                    )

                    // Connect to Backend inside background thread
                    try {
                        val url = java.net.URL("http://192.168.43.131:5000/api/location/save")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json; utf-8")
                        conn.setRequestProperty("Accept", "application/json")
                        conn.doOutput = true

                        val sharedPrefs = applicationContext.getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
                        val userId = sharedPrefs.getString("USER_NAME", "UnknownUser") ?: "UnknownUser"

                        val jsonInputString = """
                            {
                                "userId": "$userId",
                                "lat": ${location.latitude},
                                "long": ${location.longitude}
                            }
                        """.trimIndent()

                        conn.outputStream.use { os ->
                            val input = jsonInputString.toByteArray(Charsets.UTF_8)
                            os.write(input, 0, input.size)
                        }

                        val code = conn.responseCode
                        android.util.Log.d("TrekTrackingService", "Backend Push: HTTP $code")
                    } catch (e: Exception) {
                        android.util.Log.e("TrekTrackingService", "Backend Push Failed: ${e.message}")
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't support binding in this service
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "TREK_CHANNEL_ID",
                "Trek Tracker Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
