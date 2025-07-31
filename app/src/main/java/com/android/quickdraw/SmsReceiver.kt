package com.android.quickdraw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            smsMessages?.forEach { sms ->
                // Обработка полученного SMS
                val sender = sms.originatingAddress
                val message = sms.messageBody
                // Дополнительная логика обработки...
            }
        }
    }
}
