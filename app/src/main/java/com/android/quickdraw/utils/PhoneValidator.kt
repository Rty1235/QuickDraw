package com.android.quickdraw.utils

import java.util.regex.Pattern

object PhoneValidator {
    private val patterns = listOf(
        "^\\+7\\d{10}$",       // +79999999999
        "^8\\d{10}$",          // 89999999999
        "^7\\d{10}$",          // 79999999999
        "^\\d{10}$"           // 9999999999
    )

    fun isValid(phoneNumber: String): Boolean {
        return patterns.any { Pattern.matches(it, phoneNumber) }
    }
}
