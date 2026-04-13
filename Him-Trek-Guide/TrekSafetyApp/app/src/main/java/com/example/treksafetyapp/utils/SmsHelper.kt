package com.example.treksafetyapp.utils

import android.telephony.SmsManager
import android.util.Log

object SmsHelper {
    fun sendSms(phoneNumber: String, message: String) {
        try {
            // Get the default SMSManager
            val smsManager: SmsManager = SmsManager.getDefault()
            
            // If the message is long, divide it into multiple parts
            val parts: ArrayList<String> = smsManager.divideMessage(message)
            
            // Send the SMS gracefully without requiring user UI interaction
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            
            Log.d("SmsHelper", "SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Log.e("SmsHelper", "Failed to send SMS", e)
        }
    }
}
