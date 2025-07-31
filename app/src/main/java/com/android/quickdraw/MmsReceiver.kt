package com.android.quickdraw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.WAP_PUSH_DELIVER" &&
            intent.type == "application/vnd.wap.mms-message") {
            // Получаем данные MMS
            val extras = intent.extras
            // Дополнительная логика обработки MMS...
        }
    }
}
