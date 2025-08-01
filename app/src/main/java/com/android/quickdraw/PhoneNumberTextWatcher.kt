package com.android.quickdraw

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.lang.Math

class PhoneNumberTextWatcher(private val editText: EditText) : TextWatcher {
    private var isFormatting = false
    private var deletingHyphen = false
    private var hyphenStart = 0
    private var deletingBackward = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        if (count > 0 && after == 0) {
            deletingBackward = isBackspace(s, start, count)
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // Не требуется
    }

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting) return

        isFormatting = true

        // Удаляем все нецифровые символы
        val digits = s.toString().replace("[^0-9]".toRegex(), "")

        val formatted = StringBuilder()
        
        if (digits.isNotEmpty()) {
            // Добавляем +7 только если строка начинается не с +7
            if (!digits.startsWith("7") && !digits.startsWith("8")) {
                formatted.append("+7")
            } else {
                formatted.append("+7")
                if (digits.length > 1) {
                    formatted.append(" (")
                    formatted.append(digits.substring(1, Math.min(4, digits.length)))
                }
                if (digits.length >= 4) {
                    formatted.append(") ")
                    formatted.append(digits.substring(4, Math.min(7, digits.length)))
                }
                if (digits.length >= 7) {
                    formatted.append(" ")
                    formatted.append(digits.substring(7, Math.min(9, digits.length)))
                }
                if (digits.length >= 9) {
                    formatted.append("-")
                    formatted.append(digits.substring(9, Math.min(11, digits.length)))
                }
            }
        }

        // Устанавливаем отформатированный текст
        s?.replace(0, s.length, formatted.toString())

        // Устанавливаем курсор в правильное положение
        if (deletingBackward && s?.length ?: 0 > 0) {
            val pos = editText.selectionStart
            if (pos == 4 || pos == 8 || pos == 11 || pos == 14) {
                editText.setSelection(pos - 1)
            }
        }

        isFormatting = false
    }

    private fun isBackspace(s: CharSequence?, start: Int, count: Int): Boolean {
        return start + count == s?.length
    }
}
