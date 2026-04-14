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

    private var lastLocation: android.location.Location? = null
    private var lastMovementTime  = System.currentTimeMillis()
    private var lastLocationTime  = System.currentTimeMillis() // updated on every GPS fix
    private var isSurvivalMode    = false
    private var fallbackCount     = 0
    private var lastFallbackTime  = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())

        // Truth Layer: never lie about network at start
        getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
            .edit().putString("NETWORK_STATUS", "OFFLINE").apply()

        applyAdaptiveIntervals()
        startGpsHealthMonitor()   // 🔴 Test 4: GPS Kill — this is what fires fallback SMS
        startHeartbeatTimer()
        startStationaryWatchdog()

        return START_STICKY
    }

    private fun applyAdaptiveIntervals() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        isSurvivalMode = batteryLevel < 10
        
        val interval = when {
            batteryLevel > 50 -> 10 * 60 * 1000L
            batteryLevel > 20 -> 20 * 60 * 1000L
            else -> 30 * 60 * 1000L
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            interval
        ).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                // Track movement for stationary watchdog
                lastLocation?.let {
                    if (location.distanceTo(it) > 20f && location.accuracy < 20f) {
                        lastMovementTime = System.currentTimeMillis()
                    }
                }
                lastLocation     = location
                lastLocationTime = System.currentTimeMillis() // GPS health monitor reads this

                val db = AppDatabase.getDatabase(applicationContext)
                CoroutineScope(Dispatchers.IO).launch {
                    db.locationDao().insertLocation(
                        LocationEntity(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = System.currentTimeMillis(),
                            isSynced = false
                        )
                    )

                    // ALWAYS try to flush priority-pending SMS regardless of survival mode.
                    // A queued SOS must send the moment signal returns — even at 5% battery.
                    com.example.treksafetyapp.utils.SmsHelper.flushPendingSmsIfAny(applicationContext)

                    // Network sync only if NOT in survival mode (save battery)
                    if (!isSurvivalMode) {
                        syncLatestLocation(location)
                    } else {
                        // In survival mode, still mark offline so UI is honest
                        getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
                            .edit().putString("NETWORK_STATUS", "OFFLINE").apply()
                    }
                }
            }
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    // ── GPS Health Monitor ──────────────────────────────────────────────────────
    // Checks every 60s. If no GPS fix for 5 mins → sends last-known-location SMS.
    // Repeats at most 3 times, with a 30-min gap between each, to avoid spam.
    private fun startGpsHealthMonitor() {
        val timer = java.util.Timer()
        timer.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                val now           = System.currentTimeMillis()
                val noGpsDuration = now - lastLocationTime
                val timeSinceLast = now - lastFallbackTime

                val gpsLostLongEnough  = noGpsDuration  > 5  * 60_000L
                val gapBetweenRepeats  = lastFallbackTime == 0L || timeSinceLast > 30 * 60_000L
                val underRepeatCap     = fallbackCount < 3

                if (gpsLostLongEnough && gapBetweenRepeats && underRepeatCap) {
                    android.util.Log.w("TrekTrackingService",
                        "GPS lost for ${noGpsDuration / 60000}m. Sending fallback SMS (${ fallbackCount + 1}/3).")
                    triggerGpsFallbackSms()
                }
            }
        }, 60_000L, 60_000L)   // check every 1 minute
    }

    private fun startHeartbeatTimer() {
        val timer = java.util.Timer()
        timer.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                val sharedPrefs = getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
                val userId = sharedPrefs.getString("USER_ID", null) ?: return
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val url = java.net.URL("https://trekshield-backend.vercel.app/api/location/save")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json; utf-8")
                        conn.doOutput = true
                        
                        val json = """{"userId": "$userId", "type": "heartbeat", "status": "alive"}"""
                        conn.outputStream.use { it.write(json.toByteArray()) }
                        android.util.Log.d("TrekTrackingService", "Heartbeat Sent: ${conn.responseCode}")
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }, 30 * 60000L, 30 * 60000L)
    }

    private fun startStationaryWatchdog() {
        val timer = java.util.Timer()
        timer.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                if (System.currentTimeMillis() - lastMovementTime > 30 * 60000L && !isSurvivalMode) {
                    showStationaryGracePrompt()
                }
            }
        }, 60000L, 5 * 60000L) // Check every 5 mins
    }

    private fun showStationaryGracePrompt() {
        // BroadCast to UI to show "Are you okay?" prompt
        val intent = Intent("com.example.treksafetyapp.STATIONARY_WARNING")
        sendBroadcast(intent)
        
        // Wait 2 minutes for response from ViewModel via SharedPrefs
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            val sharedPrefs = getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
            val isOk = sharedPrefs.getBoolean("STATIONARY_ACK", false)
            if (!isOk) {
                triggerStationaryAlertSms()
            }
            sharedPrefs.edit().putBoolean("STATIONARY_ACK", false).apply()
        }, 2 * 60000L)
    }

    private fun triggerStationaryAlertSms() {
        val sharedPrefs = getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
        val contact = sharedPrefs.getString("CONTACT", null) ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            val lastLoc = AppDatabase.getDatabase(applicationContext).locationDao().getLastKnownLocation()
            val locUrl = lastLoc?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" } ?: "Unknown"
            
            val msg = """
                🚨 TrekShield Alert:
                No movement detected for over 30 minutes.
                
                User may be injured or unable to respond.
                
                Last known location:
                $locUrl
            """.trimIndent()
            
            com.example.treksafetyapp.utils.SmsHelper.sendSms(applicationContext, contact, msg)
        }
    }

    private fun triggerGpsFallbackSms() {
        val sharedPrefs = getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
        val contact     = sharedPrefs.getString("CONTACT", null) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val lastLoc = AppDatabase.getDatabase(applicationContext).locationDao().getLastKnownLocation()
            val locUrl  = lastLoc?.let {
                "https://maps.google.com/?q=${it.latitude},${it.longitude}"
            } ?: "Unknown"

            // Approved SMS copy (matches spec exactly)
            val msg = """
                ⚠️ TrekShield Alert:
                Battery is critically low and GPS signal is weak.
                
                Sending last known location:
                $locUrl
            """.trimIndent()

            com.example.treksafetyapp.utils.SmsHelper.sendSms(applicationContext, contact, msg)
            fallbackCount++
            lastFallbackTime = System.currentTimeMillis()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(applicationContext, this.javaClass)
        restartIntent.setPackage(packageName)
        val restartPendingIntent = PendingIntent.getService(
            applicationContext, 
            1, 
            restartIntent, 
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            android.app.AlarmManager.RTC, 
            System.currentTimeMillis() + 5000, 
            restartPendingIntent
        )
    }

    private fun buildNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, "TREK_CHANNEL_ID")
            .setContentTitle("TrekShield Active")
            .setContentText("Emergency tracking active. Stay safe.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }

    private fun syncLatestLocation(location: android.location.Location) {
        val sharedPrefs = getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
        val userId = sharedPrefs.getString("USER_ID", null) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = java.net.URL("https://trekshield-backend.vercel.app/api/location/save")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true

                val json = """{"userId": "$userId", "lat": ${location.latitude}, "long": ${location.longitude}}"""
                conn.outputStream.use { os -> os.write(json.toByteArray()) }

                if (conn.responseCode == 201) {
                    android.util.Log.d("TrekTrackingService", "Latest location synced.")
                    // Mark network as live — ViewModel reads this for Truth Label
                    sharedPrefs.edit().putString("NETWORK_STATUS", "LIVE").apply()
                    // Flush any priority-pending SMS now that signal is back
                    com.example.treksafetyapp.utils.SmsHelper.flushPendingSmsIfAny(applicationContext)
                    batchSyncUnsynced(userId)
                } else {
                    sharedPrefs.edit().putString("NETWORK_STATUS", "OFFLINE").apply()
                }
            } catch (e: Exception) {
                android.util.Log.e("TrekTrackingService", "Sync failed: ${e.message}")
                sharedPrefs.edit().putString("NETWORK_STATUS", "OFFLINE").apply()
            }
        }
    }

    private suspend fun batchSyncUnsynced(userId: String) {
        val db = AppDatabase.getDatabase(applicationContext)
        val unsynced = db.locationDao().getUnsyncedLocations() // I need to add this to DAO
        
        for (loc in unsynced) {
            try {
                val url = java.net.URL("https://trekshield-backend.vercel.app/api/location/save")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true

                val json = """{"userId": "$userId", "lat": ${loc.latitude}, "long": ${loc.longitude}, "timestamp": ${loc.timestamp}}"""
                conn.outputStream.use { os -> os.write(json.toByteArray()) }

                if (conn.responseCode == 201) {
                    db.locationDao().markAsSynced(loc.id) // I need to add this to DAO
                }
            } catch (e: Exception) { break } // Stop batch if network fails again
        }
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
