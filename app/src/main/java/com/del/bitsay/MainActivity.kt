package com.del.bitsay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.del.bitsay.core.model.Kind
import com.del.bitsay.ui.AppViewModel
import com.del.bitsay.ui.BitSayRoot
import com.del.bitsay.ui.theme.BitSayTheme
import com.del.bitsay.widget.WidgetContract

/**
 * Single activity, three Compose screens. The widget opens it with an action so a desktop tap
 * lands directly in the right place:
 *  * [ACTION_NEW]  — blank editor for the kind the widget was showing;
 *  * [ACTION_OPEN] — editor for one specific note/todo.
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
                BitSayRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_NEW -> viewModel.startNew(
                Kind.ofCode(intent.getIntExtra(WidgetContract.EXTRA_KIND, Kind.NOTE.code)),
            )

            ACTION_OPEN -> {
                val id = intent.getLongExtra(WidgetContract.EXTRA_ITEM_ID, -1L)
                if (id > 0L) viewModel.openItem(id)
            }
        }
    }

    companion object {
        const val ACTION_NEW = "com.del.bitsay.action.NEW"
        const val ACTION_OPEN = "com.del.bitsay.action.OPEN"
    }
}
