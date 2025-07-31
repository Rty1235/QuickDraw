package com.android.quickdraw

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        
        // Настройка WebView
        with(webView.settings) {
            javaScriptEnabled = true // Включение JavaScript
            domStorageEnabled = true // Включение DOM Storage
            loadWithOverviewMode = true // Оптимизация под размер экрана
            useWideViewPort = true // Использование широкого viewport
        }

        // Установка WebViewClient для обработки навигации
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return false
            }
        }

        // Загрузка страницы
        webView.loadUrl("https://www.example.com") // Замените на нужный URL
    }

    // Обработка кнопки "Назад" для навигации в WebView
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
