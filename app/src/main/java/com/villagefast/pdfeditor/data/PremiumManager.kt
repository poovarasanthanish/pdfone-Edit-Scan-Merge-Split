package com.villagefast.pdfeditor.data

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

class PremiumManager(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("pdf_editor_prefs", Context.MODE_PRIVATE)
    
    private val _isPremium = mutableStateOf(sharedPrefs.getBoolean("is_premium", false))
    val isPremium: State<Boolean> = _isPremium

    fun setPremium(value: Boolean) {
        _isPremium.value = value
        sharedPrefs.edit().putBoolean("is_premium", value).apply()
    }
}
