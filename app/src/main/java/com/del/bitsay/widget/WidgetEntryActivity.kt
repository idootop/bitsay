package com.del.bitsay.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.del.bitsay.BitSayApp
import com.del.bitsay.core.model.Kind
import com.del.bitsay.ui.AppViewModel
import com.del.bitsay.ui.BitSayRoot
import com.del.bitsay.ui.theme.BitSayTheme

/**
 * The window the home-screen widget opens to write or read one entry.
 *
 * It renders **exactly the same screen as the app** — same [BitSayRoot], same [com.del.bitsay.ui.
 * screen.EditorScreen], same background, no scrim and no rounded card — because a second,
 * slightly different editor would be a second set of bugs. Two things are different, and only
 * two:
 *
 *  * it lives in its own task (`taskAffinity`) with `excludeFromRecents` + `noHistory`, so it
 *    never drags the app's main task to the front — which used to paint the app's list for a
 *    frame before the editor appeared — and it never leaves a window behind in the background;
 *  * leaving the editor dismisses the window instead of falling back to the app's list
 *    (`AppViewModel.leaveEditor(fromWidget = true)`).
 */
class WidgetEntryActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as BitSayApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // On a configuration change the ViewModel survives with the draft intact, so the request
        // must only be honoured for a genuinely new window.
        if (savedInstanceState == null && !openRequestedEntry(intent)) {
            finish()
            return
        }

        setContent {
            BitSayTheme {
                BitSayRoot(viewModel, onExit = { finish() })
            }
        }
    }

    private fun openRequestedEntry(intent: Intent?): Boolean {
        val kind = Kind.ofCode(intent?.getIntExtra(WidgetContract.EXTRA_KIND, Kind.NOTE.code) ?: 0)
        return when (intent?.action) {
            WidgetContract.ACTION_NEW_ITEM -> {
                viewModel.startNew(kind, fromWidget = true)
                true
            }

            WidgetContract.ACTION_OPEN_ITEM -> {
                val id = intent.getLongExtra(WidgetContract.EXTRA_ITEM_ID, -1L)
                if (id <= 0L) {
                    false
                } else {
                    viewModel.openItem(id, fromWidget = true)
                    true
                }
            }

            else -> false
        }
    }
}
