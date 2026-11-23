package com.tarek.whtranslator

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "whtranslator_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_SOURCE_LANG = "source_lang"
    private const val KEY_TARGET_LANG = "target_lang"
    private const val KEY_MODEL = "model"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getApiKey(context: Context) = prefs(context).getString(KEY_API_KEY, "") ?: ""
    fun getSourceLang(context: Context) = prefs(context).getString(KEY_SOURCE_LANG, "auto") ?: "auto"
    fun getTargetLang(context: Context) = prefs(context).getString(KEY_TARGET_LANG, "Español") ?: "Español"
    fun getModel(context: Context) = prefs(context).getString(KEY_MODEL, "gemini-1.5-flash") ?: "gemini-1.5-flash"

    fun save(context: Context, apiKey: String, sourceLang: String, targetLang: String, model: String) {
        prefs(context).edit().apply {
            putString(KEY_API_KEY, apiKey)
            putString(KEY_SOURCE_LANG, sourceLang)
            putString(KEY_TARGET_LANG, targetLang)
            putString(KEY_MODEL, model)
            apply()
        }
    }

    val LANGUAGES = listOf(
        "auto (detectar)", "Español", "Inglés", "Italiano", "Francés",
        "Alemán", "Portugués", "Chino", "Japonés", "Árabe",
        "Ruso", "Holandés", "Polaco", "Turco", "Coreano"
    )

    val MODELS = listOf(
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.0-flash",
        "gemini-2.0-flash-thinking-exp"
    )

    fun langDisplayToApi(lang: String): String {
        return if (lang.startsWith("auto")) "auto" else lang
    }
}
