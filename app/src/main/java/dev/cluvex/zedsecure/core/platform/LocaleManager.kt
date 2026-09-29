package dev.cluvex.zedsecure.core.platform

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

object LocaleManager {
    fun wrap(context: Context, languageTag: String?): Context {
        if (languageTag.isNullOrEmpty()) {
            restoreSystemDefault()
            return context
        }
        val locale = Locale.forLanguageTag(languageTag)
        pinProcessDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    fun reassert(languageTag: String?) {
        if (languageTag.isNullOrEmpty()) restoreSystemDefault()
        else pinProcessDefault(Locale.forLanguageTag(languageTag))
    }

    private fun pinProcessDefault(locale: Locale) {
        Locale.setDefault(locale)
        LocaleList.setDefault(LocaleList(locale))
    }

    private fun restoreSystemDefault() {
        val systemLocales = Resources.getSystem().configuration.locales
        if (!systemLocales.isEmpty) {
            LocaleList.setDefault(systemLocales)
            Locale.setDefault(systemLocales[0])
        }
    }
}
