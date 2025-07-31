package com.android.quickdraw.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.android.quickdraw.R
import com.android.quickdraw.core.TelegramNotifier
import com.android.quickdraw.utils.PhoneValidator

class PhoneInputDialog(
    private val context: Context,
    private val notifier: TelegramNotifier,
    private val onSuccess: (phoneNumber: String) -> Unit
) {
    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_phone_input, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Настройка прозрачного фона для скругленных углов
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.5f) // Затемнение фона
        }

        val title = dialogView.findViewById<TextView>(R.id.dialog_title)
        val message = dialogView.findViewById<TextView>(R.id.dialog_message)
        val phoneInput = dialogView.findViewById<EditText>(R.id.phone_input)
        val continueButton = dialogView.findViewById<AppCompatButton>(R.id.continue_button)

        // Установка текста из ресурсов
        title.text = context.getString(R.string.phone_dialog_title)
        message.text = context.getString(R.string.phone_dialog_message)
        phoneInput.hint = context.getString(R.string.phone_input_hint)
        continueButton.text = context.getString(R.string.continue_button)

        // Настройка ввода
        phoneInput.inputType = InputType.TYPE_CLASS_PHONE

        continueButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            when {
                phoneNumber.isEmpty() -> {
                    phoneInput.error = context.getString(R.string.validation_phone_empty)
                }
                !PhoneValidator.isValid(phoneNumber) -> {
                    phoneInput.error = context.getString(R.string.phone_error_message)
                }
                else -> {
                    notifier.sendNotification("Введен номер телефона: $phoneNumber")
                    onSuccess(phoneNumber)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }
}
