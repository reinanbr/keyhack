package com.reinanbr.keyhack

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** Whether KeyHack is enabled in system settings and currently selected. */
data class ImeStatus(val enabled: Boolean, val selected: Boolean) {
    companion object {
        fun read(context: Context): ImeStatus {
            val imm = context.getSystemService(InputMethodManager::class.java)
            val pkg = context.packageName
            val enabled = imm.enabledInputMethodList.any { it.packageName == pkg }
            val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            return ImeStatus(enabled = enabled, selected = enabled && current?.startsWith("$pkg/") == true)
        }
    }
}
