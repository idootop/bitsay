package com.del.bitsay

import androidx.compose.runtime.getValue
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.del.bitsay.i18n.withAppLanguage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.del.bitsay.ui.AppViewModel
import com.del.bitsay.ui.BitSayRoot
import com.del.bitsay.ui.theme.BitSayTheme
import com.del.bitsay.ui.theme.ThemePrefs
import com.del.bitsay.widget.WidgetContract

/**
 * The app itself: single activity, three Compose screens.
 *
 * Writing and reading a single entry from the home screen does **not** come through here — that
 * is [com.del.bitsay.widget.WidgetEntryActivity], a separate floating window in its own task.
 * The widget only opens this activity through its "open app" button, which lands on the list.
 */
class MainActivity : ComponentActivity() {

    /**
     * Pins the activity to the language chosen in settings. Doing it here rather than through
     * `AppCompatDelegate` keeps the app free of the AppCompat dependency for one setting.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguage())
    }


    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as BitSayApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        // Paint the window in the resolved scheme before the first frame, otherwise a dark-theme
        // launch flashes the light `windowBackground` from the theme resource.
        window.setBackgroundDrawable(
            ColorDrawable(if (ThemePrefs.isDark(this)) DARK_WINDOW else LIGHT_WINDOW),
        )

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            BitSayTheme(mode = state.themeMode) {
                BitSayRoot(viewModel, onExit = { finish() }, onRelaunch = { recreate() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** The widget's app button always lands on the list, never on whatever was left open. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == WidgetContract.ACTION_SHOW_LIST) viewModel.openList()
    }

    private companion object {
        const val DARK_WINDOW = 0xFF211F1D.toInt()
        const val LIGHT_WINDOW = 0xFFFCF3E8.toInt()
    }
}
