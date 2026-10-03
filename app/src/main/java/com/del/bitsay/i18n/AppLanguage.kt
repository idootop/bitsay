package com.del.bitsay.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The language the app is displayed in.
 *
 * [SYSTEM] is the default and means "whatever the phone is set to". The two explicit choices exist
 * because a bilingual user often wants the app in one language while the system is in the other.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    CHINESE("zh"),
    ENGLISH("en"),
    ;

    companion object {
        fun ofTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Persists the choice. Deliberately a plain `SharedPreferences` rather than
 * `AppCompatDelegate.setApplicationLocales`: that API needs AppCompat activities and a support
 * library dependency, and everything it does on API < 33 is [withAppLanguage] anyway.
 */
class LanguagePrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun current(): AppLanguage = AppLanguage.ofTag(prefs.getString(KEY, null))

    fun set(language: AppLanguage) {
        prefs.edit().apply {
            if (language.tag == null) remove(KEY) else putString(KEY, language.tag)
        }.apply()
    }

    companion object {
        const val FILE = "bitsay_settings"
        private const val KEY = "language"
    }
}

/**
 * Returns a context pinned to the chosen language, or `this` when the choice is "follow system".
 *
 * Call it from `attachBaseContext` in every activity, and anywhere outside an activity that reads
 * strings for display — most importantly the widget, which renders from a broadcast receiver and
 * would otherwise always use the system language.
 */
fun Context.withAppLanguage(): Context {
    val language = LanguagePrefs(this).current()
    val locale = language.tag?.let(Locale::forLanguageTag) ?: return this

    // Date formatting goes through java.time, which reads the process default rather than the
    // context configuration; both have to be moved together or the UI would mix two languages.
    Locale.setDefault(locale)

    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return createConfigurationContext(configuration)
}
