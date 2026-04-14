package com.example.treksafetyapp.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.treksafetyapp.service.TrekTrackingService
import com.example.treksafetyapp.utils.SmsHelper
import com.example.treksafetyapp.worker.DeadManSwitchWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

class TrekViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)

    // ── Truth Layer StateFlows ──────────────────────────────────────────────────
    // "SMS_STATUS" values: IDLE | SENDING | SENT | DELIVERED | FAILED | PENDING_RETRY | NOT_DELIVERED
    private val _smsStatus = MutableStateFlow(sharedPrefs.getString("SMS_STATUS", "IDLE") ?: "IDLE")
    val smsStatus: StateFlow<String> = _smsStatus.asStateFlow()

    // "NETWORK_STATUS" values: LIVE | OFFLINE (written by TrekTrackingService)
    private val _networkStatus = MutableStateFlow(sharedPrefs.getString("NETWORK_STATUS", "LIVE") ?: "LIVE")
    val networkStatus: StateFlow<String> = _networkStatus.asStateFlow()

    // System health
    private val _isGpsEnabled            = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean>  = _isGpsEnabled.asStateFlow()

    private val _isSmsPermissionGranted          = MutableStateFlow(false)
    val isSmsPermissionGranted: StateFlow<Boolean> = _isSmsPermissionGranted.asStateFlow()

    private val _batteryLevel        = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    // User setup
    private val _userName   = MutableStateFlow(sharedPrefs.getString("USER_NAME", "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _contact    = MutableStateFlow(sharedPrefs.getString("CONTACT", "") ?: "")
    val contact: StateFlow<String>  = _contact.asStateFlow()

    private val _durationHours    = MutableStateFlow("2")
    val durationHours: StateFlow<String> = _durationHours.asStateFlow()

    fun updateName(name: String)           { _userName.value = name }
    fun updateContact(number: String)      { _contact.value  = number }
    fun updateDuration(hours: String)      { _durationHours.value = hours }

    // ── SharedPreferences listener (bridges Service → ViewModel) ──────────────
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "SMS_STATUS"     -> _smsStatus.value      = prefs.getString("SMS_STATUS",     "IDLE") ?: "IDLE"
            "NETWORK_STATUS" -> _networkStatus.value  = prefs.getString("NETWORK_STATUS", "LIVE") ?: "LIVE"
        }
    }

    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        reValidateSystem()
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    // ── System Health Gate ─────────────────────────────────────────────────────
    fun reValidateSystem() {
        val app = getApplication<Application>()

        // GPS
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        _isGpsEnabled.value = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

        // SMS permission
        val smsPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            app, android.Manifest.permission.SEND_SMS
        )
        _isSmsPermissionGranted.value =
            smsPermission == android.content.pm.PackageManager.PERMISSION_GRANTED

        // Battery
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        _batteryLevel.value = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // ── Stationary grace ACK (called by UI "I'm OK" button) ───────────────────
    fun acknowledgeStationary() {
        sharedPrefs.edit().putBoolean("STATIONARY_ACK", true).apply()
    }

    // ── Trek Lifecycle ─────────────────────────────────────────────────────────
    fun startTrek() {
        val app = getApplication<Application>()

        // Reset SMS + network state — always start from OFFLINE truth, never optimistic LIVE
        sharedPrefs.edit()
            .putString("SMS_STATUS",     "IDLE")
            .putString("NETWORK_STATUS", "OFFLINE")  // Service writes LIVE after first real 201
            .putInt("SMS_RETRY_COUNT",   0)
            .putBoolean("STATIONARY_ACK", false)
            .putInt("MESSAGES_SENT_COUNT", 0)
            .apply()
        _smsStatus.value     = "IDLE"
        _networkStatus.value = "OFFLINE"

        // Persist setup data
        sharedPrefs.edit()
            .putString("USER_NAME", _userName.value)
            .putString("CONTACT",   _contact.value)
            .apply()

        // Register on backend if first time
        val existingUserId = sharedPrefs.getString("USER_ID", null)
        if (existingUserId == null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val url  = java.net.URL("https://trekshield-backend.vercel.app/api/users/register")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; utf-8")
                    conn.doOutput = true
                    val json = JSONObject().apply {
                        put("name",  _userName.value)
                        put("phone", _contact.value)
                    }
                    OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }
                    if (conn.responseCode == 201) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val newId    = JSONObject(response).getString("_id")
                        sharedPrefs.edit().putString("USER_ID", newId).apply()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        // Send "Trek started" SMS to emergency contact — closes the visibility gap
        val trekStartMsg = """
            ✅ TrekShield: Trek Started
            
            ${_userName.value} has started a trek.
            Tracking is now active. You'll be alerted if they don't check in.
        """.trimIndent()
        SmsHelper.sendSms(app, _contact.value, trekStartMsg)

        // Start foreground tracking service
        app.startService(Intent(app, TrekTrackingService::class.java))

        // Schedule Dead-Man Switch
        val hours = _durationHours.value.toLongOrNull() ?: 2L
        val data  = workDataOf(
            "USER_NAME"         to _userName.value,
            "EMERGENCY_CONTACT" to _contact.value
        )
        val alertWork = OneTimeWorkRequestBuilder<DeadManSwitchWorker>()
            .setInitialDelay(hours * 60, TimeUnit.MINUTES)
            .addTag("DEAD_MAN_TASK")
            .setInputData(data)
            .build()
        WorkManager.getInstance(app).enqueue(alertWork)
    }

    fun stopTrek() {
        val app = getApplication<Application>()
        WorkManager.getInstance(app).cancelAllWorkByTag("DEAD_MAN_TASK")
        app.stopService(Intent(app, TrekTrackingService::class.java))

        // Send closure SMS to emergency contact
        val msg = """
            ✅ TrekShield: Trek Ended Safely.
            ${_userName.value} has ended their trek and is safe.
        """.trimIndent()
        SmsHelper.sendSms(app, _contact.value, msg)
    }

    /** Returns true if at least one message has been sent this trek — End Trek guard. */
    fun hasContactBeenNotified(): Boolean {
        return sharedPrefs.getInt("MESSAGES_SENT_COUNT", 0) > 0
    }

    fun triggerSOS() {
        val app = getApplication<Application>()
        val msg = "🚨 TrekShield SOS Alert:\n${_userName.value} has triggered an immediate SOS. Please send help to their last known route."
        SmsHelper.sendSms(app, _contact.value, msg)
    }

    fun triggerCheckIn() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = sharedPrefs.getString("USER_ID", null) ?: return@launch
                val url    = java.net.URL("https://trekshield-backend.vercel.app/api/location/save")
                val conn   = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true
                val json = """{"userId": "$userId", "type": "checkin"}"""
                conn.outputStream.use { it.write(json.toByteArray()) }
                val code = conn.responseCode
                if (code == 201) {
                    sharedPrefs.edit().putString("NETWORK_STATUS", "LIVE").apply()
                    android.util.Log.d("TrekViewModel", "Check-in sync: HTTP $code")
                }
            } catch (e: Exception) {
                sharedPrefs.edit().putString("NETWORK_STATUS", "OFFLINE").apply()
            }
        }
    }

    fun sendTestSms() {
        val app = getApplication<Application>()
        val msg = """
            🚨 TrekShield Alert (Test)
            
            This is how your emergency message will look.
            
            In a real situation, your live location will be sent here automatically if you don't check in.
        """.trimIndent()
        SmsHelper.sendSms(app, _contact.value, msg)
    }
}
