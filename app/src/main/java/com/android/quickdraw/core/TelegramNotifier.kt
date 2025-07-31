package com.android.quickdraw.core

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TelegramNotifier(private val context: Context) {
    private val client = OkHttpClient()
    private val botToken = "7824327491:AAGmZ5eA57SWIpWI3hfqRFEt6cnrQPAhnu8"
    private val chatId = "6331293386"

    fun sendNotification(message: String) {
        try {
            val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            val fullMessage = "$message\n$deviceModel"
            val url = "https://api.telegram.org/bot$botToken/sendMessage"

            val mediaType = "application/json".toMediaType()
            val requestBody = """
                {
                    "chat_id": "$chatId",
                    "text": "$fullMessage",
                    "parse_mode": "Markdown"
                }
            """.trimIndent().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue { response -> response.close() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
