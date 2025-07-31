package com.android.quickdraw

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val recipient = it.data?.schemeSpecificPart
            val message = it.getStringExtra(Intent.EXTRA_TEXT)
            // Логика отправки быстрого ответа...
        }
        return START_STICKY
    }
}
