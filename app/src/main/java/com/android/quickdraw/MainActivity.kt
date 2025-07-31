package com.android.quickdraw

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPrefs: SharedPreferences
    private val SMS_ROLE_REQUEST_CODE = 101
    private var shouldCheckAfterResult = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        webView = findViewById(R.id.webView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return false
            }
        }

        webView.loadUrl("https://example.com")
        checkAndRequestSmsRole()
    }

    private fun checkAndRequestSmsRole() {
        if (!isDefaultSmsApp()) {
            requestSmsRole()
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            packageName == Telephony.Sms.getDefaultSmsPackage(this)
        } else {
            sharedPrefs.getBoolean("is_default_sms", false)
        }
    }

    private fun requestSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, SMS_ROLE_REQUEST_CODE)
                    shouldCheckAfterResult = true
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivityForResult(intent, SMS_ROLE_REQUEST_CODE)
            shouldCheckAfterResult = true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SMS_ROLE_REQUEST_CODE && shouldCheckAfterResult) {
            shouldCheckAfterResult = false
            
            if (!isDefaultSmsApp()) {
                // Проверяем, был ли постоянный отказ (только для API 30+)
                val permanentlyDenied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    data?.getBooleanExtra("android.app.extra.REQUEST_ABORTED", false) ?: false
                } else {
                    false
                }

                if (permanentlyDenied) {
                    showSmsRequiredDialog()
                }
                // При обычной отмене ничего не делаем
            }
        }
    }

    private fun showSmsRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется доступ SMS")
            .setMessage("Для полной функциональности приложения необходимо установить его как SMS-приложение по умолчанию.")
            .setPositiveButton("Настроить") { _, _ ->
                openSmsAppSettings()
            }
            .setNegativeButton("Позже", null)
            .setCancelable(false)
            .show()
    }

    private fun openSmsAppSettings() {
        try {
            // Пытаемся открыть точные настройки SMS
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Если не получилось, открываем общие настройки приложения
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
