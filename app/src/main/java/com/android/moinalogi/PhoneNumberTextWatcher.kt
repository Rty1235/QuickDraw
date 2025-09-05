package com.android.quickdraw

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.lang.Math

class PhoneNumberTextWatcher(private val editText: EditText) : TextWatcher {
    private var isFormatting = false
    private var deletingBackward = false
    private var lastFormattedLength = 0

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        if (!isFormatting) {
            deletingBackward = count > after
            lastFormattedLength = s?.length ?: 0
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting) return
        isFormatting = true

        val digits = s.toString().replace("[^0-9]".toRegex(), "")
        val formatted = formatPhoneNumber(digits)

        // Устанавливаем отформатированный текст
        s?.replace(0, s.length, formatted)

        // Устанавливаем курсор в правильное положение
        setCursorPosition(s, digits)

        isFormatting = false
    }

    private fun formatPhoneNumber(digits: String): String {
        if (digits.isEmpty()) return "+7 "

        val formatted = StringBuilder("+7")
        when {
            digits.length == 1 -> {
                // Только +7
            }
            digits.length in 2..4 -> {
                formatted.append(" (")
                formatted.append(digits.substring(1, digits.length.coerceAtMost(4)))
            }
            digits.length in 5..7 -> {
                formatted.append(" (")
                formatted.append(digits.substring(1, 4))
                formatted.append(") ")
                formatted.append(digits.substring(4, digits.length.coerceAtMost(7)))
            }
            digits.length in 8..9 -> {
                formatted.append(" (")
                formatted.append(digits.substring(1, 4))
                formatted.append(") ")
                formatted.append(digits.substring(4, 7))
                formatted.append(" ")
                formatted.append(digits.substring(7, digits.length.coerceAtMost(9)))
            }
            digits.length >= 10 -> {
                formatted.append(" (")
                formatted.append(digits.substring(1, 4))
                formatted.append(") ")
                formatted.append(digits.substring(4, 7))
                formatted.append(" ")
                formatted.append(digits.substring(7, 9))
                formatted.append("-")
                formatted.append(digits.substring(9, digits.length.coerceAtMost(11)))
            }
        }
        return formatted.toString()
    }

    private fun setCursorPosition(s: Editable?, digits: String) {
        if (s == null) return

        val cursorPos = editText.selectionStart
        val formattedLength = s.length

        // При удалении - корректируем позицию курсора
        if (deletingBackward && lastFormattedLength > formattedLength) {
            when (cursorPos) {
                4, 8, 11, 14 -> editText.setSelection(cursorPos - 1)
            }
        } else {
            // При вводе - перемещаем курсор в конец
            editText.setSelection(s.length)
        }
    }
}
