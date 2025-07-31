package com.android.quickdraw.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import androidx.annotation.StringRes
import com.android.quickdraw.R

class NoInternetDialog(
    private val context: Context,
    private val onRetry: () -> Unit
) {
    fun show() {
        AlertDialog.Builder(context)
            .setTitle(R.string.no_internet_title)
            .setMessage(R.string.no_internet_message)
            .setPositiveButton(R.string.retry_button) { _, _ -> onRetry() }
            .setCancelable(false)
            .show()
    }
}
