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
    private var userDeniedPermanently = false

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
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivityForResult(intent, SMS_ROLE_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SMS_ROLE_REQUEST_CODE && !isDefaultSmsApp()) {
            // Для API 30+ проверяем постоянный отказ
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (data?.getBooleanExtra("android.app.extra.REQUEST_ABORTED", false) == true) {
                    userDeniedPermanently = true
                    showSmsRequiredDialog()
                    return
                }
            }
            // Для других версий просто показываем диалог
            showSmsRequiredDialog()
        }
    }

    private fun showSmsRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется доступ SMS")
            .setMessage("Для работы приложения необходимо быть SMS-приложением по умолчанию. Хотите открыть настройки?")
            .setPositiveButton("Открыть настройки") { _, _ ->
                openDefaultSmsAppSettings()
            }
            .setNegativeButton("Отмена", null)
            .setCancelable(false)
            .show()
    }

    private fun openDefaultSmsAppSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
