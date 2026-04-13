package com.example.treksafetyapp.worker

import android.content.Context
import android.media.RingtoneManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.treksafetyapp.data.AppDatabase
import com.example.treksafetyapp.utils.SmsHelper
import kotlinx.coroutines.delay

class DeadManSwitchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("DeadManWorker", "Safety deadline triggered!")
        
        // Step 1: Warning notification 15 mins prior (Handled by another alarm/worker in practice)
        // Assume this worker triggers exactly AT the deadline.
        
        // Step 2: Critical Alert (Play loud alarm for 2 mins)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
        ringtone.play()

        // Give the user 2 minutes to respond (Cancel the alarm manually in the app which cancels this worker)
        delay(120_000L) // 2 minutes

        // If the worker was cancelled (user checked in), stop the alarm and succeed
        if (isStopped) {
            ringtone.stop()
            return Result.success()
        }

        // Step 3: Auto-SMS if no check-in
        ringtone.stop()
        
        val db = AppDatabase.getDatabase(applicationContext)
        val lastLocation = db.locationDao().getLastKnownLocation()

        val emergencyContact = inputData.getString("EMERGENCY_CONTACT") ?: ""
        val username = inputData.getString("USER_NAME") ?: "User"

        // Format and send the SMS
        val message = if (lastLocation != null) {
            "🚨 TrekShield Alert:\n$username is overdue.\n\nLast location:\nhttps://maps.google.com/?q=${lastLocation.latitude},${lastLocation.longitude}"
        } else {
            "🚨 TrekShield Alert:\n$username is overdue.\n\nLocation unavailable."
        }

        if (emergencyContact.isNotEmpty()) {
            SmsHelper.sendSms(emergencyContact, message)
            Log.d("DeadManWorker", "Emergency SMS sent to $emergencyContact")
        } else {
            Log.e("DeadManWorker", "No emergency contact provided")
        }

        return Result.success()
    }
}
