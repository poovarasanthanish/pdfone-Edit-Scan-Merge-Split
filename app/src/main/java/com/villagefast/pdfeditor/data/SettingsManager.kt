package com.villagefast.pdfeditor.data

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

class SettingsManager(private val context: Context) {
    private val sharedPrefs = context.getSharedPreferences("pdf_editor_settings", Context.MODE_PRIVATE)

    // Theme Settings
    private val _themeMode = mutableStateOf(sharedPrefs.getString("theme_mode", "System Default") ?: "System Default")
    val themeMode: State<String> = _themeMode

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    // Language Settings
    private val _language = mutableStateOf(sharedPrefs.getString("language", "English") ?: "English")
    val language: State<String> = _language

    fun setLanguage(lang: String) {
        if (_language.value != lang) {
            _language.value = lang
            sharedPrefs.edit().putString("language", lang).apply()
            
            val locale = getLocale(lang)
            val config = context.resources.configuration
            config.setLocale(locale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)

            // Trigger activity recreation for language change to take full effect
            if (context is android.app.Activity) {
                context.recreate()
            }
        }
    }

    fun applyLanguage(lang: String) {
        val locale = getLocale(lang)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        
        // For older versions or specific cases where updateConfiguration is still needed
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun getLocale(lang: String): Locale {
        return when (lang) {
            "Spanish" -> Locale("es")
            "French" -> Locale("fr")
            "German" -> Locale("de")
            "Hindi" -> Locale("hi")
            "Tamil" -> Locale("ta")
            "Portuguese" -> Locale("pt", "BR")
            "Japanese" -> Locale("ja")
            "Arabic" -> Locale("ar")
            "Indonesian" -> Locale("in")
            "Korean" -> Locale("ko")
            "Vietnamese" -> Locale("vi")
            else -> Locale.ENGLISH
        }
    }

    // New PDF One Settings
    private val _defaultAuthor = mutableStateOf(sharedPrefs.getString("default_author", "") ?: "")
    val defaultAuthor: State<String> = _defaultAuthor

    fun setDefaultAuthor(author: String) {
        _defaultAuthor.value = author
        sharedPrefs.edit().putString("default_author", author).apply()
    }

    private val _historyRetention = mutableStateOf(sharedPrefs.getString("history_retention", "Unlimited") ?: "Unlimited")
    val historyRetention: State<String> = _historyRetention

    fun setHistoryRetention(days: String) {
        _historyRetention.value = days
        sharedPrefs.edit().putString("history_retention", days).apply()
    }
}
