package com.example.treksafetyapp.ui

import android.app.Application
import android.content.Context
import android.content.Intent
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
import java.util.concurrent.TimeUnit

class TrekViewModel(application: Application) : AndroidViewModel(application) {
    
    private val sharedPrefs = application.getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)

    private val _userName = MutableStateFlow(sharedPrefs.getString("USER_NAME", "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _contact = MutableStateFlow(sharedPrefs.getString("CONTACT", "") ?: "")
    val contact: StateFlow<String> = _contact.asStateFlow()

    private val _durationHours = MutableStateFlow("2")
    val durationHours: StateFlow<String> = _durationHours.asStateFlow()

    fun updateName(name: String) { _userName.value = name }
    fun updateContact(contactNumber: String) { _contact.value = contactNumber }
    fun updateDuration(hours: String) { _durationHours.value = hours }

    fun startTrek() {
        val app = getApplication<Application>()
        
        sharedPrefs.edit()
            .putString("USER_NAME", _userName.value)
            .putString("CONTACT", _contact.value)
            .apply()

        // 1. Start foreground service
        val intent = Intent(app, TrekTrackingService::class.java)
        app.startService(intent)

        // 2. Schedule Worker
        val hours = _durationHours.value.toLongOrNull() ?: 2L
        val timeMinutes = hours * 60
        val data = workDataOf("USER_NAME" to _userName.value, "EMERGENCY_CONTACT" to _contact.value)
        val alertWork = OneTimeWorkRequestBuilder<DeadManSwitchWorker>()
            .setInitialDelay(timeMinutes, TimeUnit.MINUTES)
            .addTag("DEAD_MAN_TASK")
            .setInputData(data)
            .build()
        WorkManager.getInstance(app).enqueue(alertWork)
    }

    fun stopTrek() {
        val app = getApplication<Application>()
        
        WorkManager.getInstance(app).cancelAllWorkByTag("DEAD_MAN_TASK")
        
        val intent = Intent(app, TrekTrackingService::class.java)
        app.stopService(intent)
    }

    fun triggerSOS() {
        val msg = "🚨 TrekShield SOS Alert:\n${_userName.value} has triggered an immediate SOS. Please send help to their last known route."
        SmsHelper.sendSms(_contact.value, msg)
    }
}
