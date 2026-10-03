package com.del.bitsay.ui.theme

import android.content.Context
import android.content.res.Configuration

/**
 * Persists the colour-scheme choice. [ThemeMode.SYSTEM] is the default and is stored by simply
 * removing the key, which is also what "follow the phone" means.
 */
class ThemePrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun current(): ThemeMode {
        val stored = prefs.getString(KEY, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    fun set(mode: ThemeMode) {
        prefs.edit().apply {
            if (mode == ThemeMode.SYSTEM) remove(KEY) else putString(KEY, mode.name)
        }.apply()
    }

    companion object {
        const val FILE = "bitsay_theme"
        private const val KEY = "mode"

        /**
         * Resolves [mode] for code that has no Compose or Activity around it — above all the
         * widget, which renders from a broadcast receiver and must follow the *app's* choice
         * rather than the launcher's own night mode.
         */
        fun isDark(context: Context, mode: ThemeMode = ThemePrefs(context).current()): Boolean =
            when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> {
                    val night = context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                    night == Configuration.UI_MODE_NIGHT_YES
                }
            }
    }
}
