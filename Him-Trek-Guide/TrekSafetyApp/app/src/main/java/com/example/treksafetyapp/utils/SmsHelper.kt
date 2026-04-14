package com.example.treksafetyapp.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

const val SMS_SENT = "SMS_SENT_ACTION"
const val SMS_DELIVERED = "SMS_DELIVERED_ACTION"

object SmsHelper {

    /**
     * Primary send — stores a pending copy for auto-retry before dispatching.
     */
    fun sendSms(context: Context, phoneNumber: String, message: String) {
        if (phoneNumber.isBlank()) {
            Log.e("SmsHelper", "No phone number configured.")
            return
        }

        val prefs = context.getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)

        // Store as pending BEFORE sending — so if the app dies mid-send we can retry
        prefs.edit()
            .putString("PENDING_SMS_BODY", message)
            .putString("PENDING_SMS_TO", phoneNumber)
            .putString("SMS_STATUS", "SENDING")
            .putInt("SMS_RETRY_COUNT", 0)
            .apply()

        dispatchSms(context, phoneNumber, message)
    }

    /**
     * Called by TrekTrackingService on reconnect to flush any priority-pending SMS.
     */
    fun flushPendingSmsIfAny(context: Context) {
        val prefs = context.getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
        val status = prefs.getString("SMS_STATUS", "") ?: ""

        if (status == "PENDING_RETRY") {
            val body = prefs.getString("PENDING_SMS_BODY", null) ?: return
            val to   = prefs.getString("PENDING_SMS_TO",   null) ?: return
            Log.d("SmsHelper", "Signal restored — flushing priority pending SMS.")
            // Reset retry so it gets a clean shot
            prefs.edit().putInt("SMS_RETRY_COUNT", 0).apply()
            dispatchSms(context, to, body)
        }
    }

    private fun dispatchSms(context: Context, phoneNumber: String, message: String) {
        try {
            val smsManager: SmsManager = SmsManager.getDefault()

            val sentPI = PendingIntent.getBroadcast(
                context, 0, Intent(SMS_SENT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val deliveredPI = PendingIntent.getBroadcast(
                context, 0, Intent(SMS_DELIVERED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val parts: ArrayList<String> = smsManager.divideMessage(message)
            if (parts.size > 1) {
                val sentIntents     = ArrayList<PendingIntent>(parts.map { sentPI })
                val deliveredIntents = ArrayList<PendingIntent>(parts.map { deliveredPI })
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveredIntents)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
            }

            Log.d("SmsHelper", "SMS dispatched to $phoneNumber")
        } catch (e: Exception) {
            Log.e("SmsHelper", "Dispatch failed: ${e.message}")
            val prefs = context.getSharedPreferences("TrekSafetyPrefs", Context.MODE_PRIVATE)
            prefs.edit().putString("SMS_STATUS", "FAILED").apply()
        }
    }
}
