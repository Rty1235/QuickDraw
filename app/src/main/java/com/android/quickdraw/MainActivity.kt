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
    private var isPermanentlyDenied = false

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
        checkSmsRole()
    }

    private fun checkSmsRole() {
        if (!isDefaultSmsApp() && !isPermanentlyDenied) {
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
        if (requestCode == SMS_ROLE_REQUEST_CODE) {
            if (!isDefaultSmsApp()) {
                if (isPermanentlyDenied(data)) {
                    isPermanentlyDenied = true
                    showPermanentDenialDialog()
                } else {
                    // Повторяем запрос, если не установлено по умолчанию
                    checkSmsRole()
                }
            }
        }
    }

    private fun isPermanentlyDenied(data: Intent?): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            data?.getBooleanExtra("android.app.extra.REQUEST_ABORTED", false) ?: false
        } else {
            // Для версий ниже Android 11 определяем постоянный отказ по количеству отказов
            sharedPrefs.getInt("denial_count", 0) >= 2
        }
    }

    private fun showPermanentDenialDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется действие")
            .setMessage("Для работы всех функций приложения необходимо установить его как SMS-приложение по умолчанию. Пожалуйста, сделайте это в настройках.")
            .setPositiveButton("Открыть настройки") { _, _ ->
                openSmsSettings()
            }
            .setNegativeButton("Закрыть") { _, _ ->
                // При закрытии снова проверяем статус
                checkSmsRole()
            }
            .setCancelable(false)
            .show()
    }

    private fun openSmsSettings() {
        try {
            // Пытаемся открыть точные настройки SMS
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback на общие настройки приложения
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // При возвращении в приложение проверяем статус
        if (!isDefaultSmsApp() && !isPermanentlyDenied) {
            checkSmsRole()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
