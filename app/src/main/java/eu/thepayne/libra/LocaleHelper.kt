package eu.thepayne.libra

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

const val LANG_PREFS = "libra_lang"
const val LANG_KEY = "lang_code"

fun applyLocale(base: Context, langCode: String): Context {
    if (langCode.isEmpty()) return base
    val locale = Locale(langCode)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration).also { it.setLocale(locale) }
    return base.createConfigurationContext(config)
}
