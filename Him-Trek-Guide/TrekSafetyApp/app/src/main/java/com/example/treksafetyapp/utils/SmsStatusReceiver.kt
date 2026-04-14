package com.example.treksafetyapp.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        when (action) {
            SMS_SENT -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d("SmsStatusReceiver", "SMS Sent OK")
                    updateStatus(context, "SENT")
                    clearPendingRetry(context)
                } else {
                    Log.e("SmsStatusReceiver", "SMS Sent Failed: $resultCode")
                    handleSmsFailure(context)
                }
            }

            SMS_DELIVERED -> {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d("SmsStatusReceiver", "SMS Delivered OK")
                    updateStatus(context, "DELIVERED")
                } else {
                    Log.w("SmsStatusReceiver", "SMS Delivery Failed: $resultCode")
                    updateStatus(context, "NOT_DELIVERED")
                }
            }
        }
    }

    private fun handleSmsFailure(context: Context) {
        val prefs = context.getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
        val retryCount = prefs.getInt("SMS_RETRY_COUNT", 0) + 1
        prefs.edit().putInt("SMS_RETRY_COUNT", retryCount).apply()

        if (retryCount >= 2) {
            // Max retries reached — queue as PRIORITY PENDING for auto-send on signal recovery
            Log.e("SmsStatusReceiver", "SMS failed $retryCount times. Marking as PENDING_RETRY.")
            updateStatus(context, "PENDING_RETRY")
            // Pending message body stored in prefs["PENDING_SMS_BODY"]
            // TrekTrackingService auto-sends this when next sync succeeds
        } else {
            Log.w("SmsStatusReceiver", "SMS failed. Retry $retryCount/2.")
            updateStatus(context, "FAILED")
        }
    }

    private fun updateStatus(context: Context, status: String) {
        context.getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
            .edit().putString("SMS_STATUS", status).apply()
    }

    private fun clearPendingRetry(context: Context) {
        context.getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("SMS_RETRY_COUNT", 0)
            .remove("PENDING_SMS_BODY")
            .apply()
    }
}
