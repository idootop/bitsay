package com.del.bitsay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.del.bitsay.ui.AppViewModel
import com.del.bitsay.ui.BitSayRoot
import com.del.bitsay.ui.theme.BitSayTheme
import com.del.bitsay.widget.WidgetContract

/**
 * The app itself: single activity, three Compose screens.
 *
 * Writing and reading a single entry from the home screen does **not** come through here — that
 * is [com.del.bitsay.widget.WidgetEntryActivity], a separate floating window in its own task.
 * The widget only opens this activity through its "open app" button, which lands on the list.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as BitSayApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            BitSayTheme {
                BitSayRoot(viewModel, onExit = { finish() })
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
}
