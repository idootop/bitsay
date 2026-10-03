package com.del.bitsay.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.del.bitsay.R
import com.del.bitsay.core.backup.BackupFiles
import com.del.bitsay.core.model.Kind
import com.del.bitsay.ui.components.PaperBackground
import com.del.bitsay.ui.screen.EditorScreen
import com.del.bitsay.ui.screen.ListScreen
import com.del.bitsay.ui.screen.SettingsScreen

@Composable
fun BitSayRoot(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFiles.MIME),
    ) { uri -> if (uri != null) viewModel.writeExport(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.loadImport(uri) }

    // ---- one-shot feedback -------------------------------------------------
    val message: String? = when (val notice = state.notice) {
        null -> null
        is Notice.Exported -> stringResource(R.string.export_ok, notice.count)
        is Notice.Imported -> stringResource(
            R.string.import_ok,
            notice.result.total,
            notice.result.inserted,
            notice.result.updated,
        )
        is Notice.Failed -> stringResource(R.string.op_fail, notice.reason)
        Notice.Empty -> stringResource(R.string.nothing_to_save)
    }
    LaunchedEffect(state.notice) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    BackHandler(enabled = state.screen != Screen.List) {
        when (state.screen) {
            is Screen.Editor -> viewModel.saveDraft()
            Screen.Settings -> viewModel.openList()
            Screen.List -> Unit
        }
    }

    // Whatever is half-typed is written before the app stops being visible, and the cache is
    // re-read whenever the app comes back (cheap insurance against any out-of-band write).
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flushDraft() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    PaperBackground(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            when (val screen = state.screen) {
                Screen.List -> ListScreen(
                    state = state,
                    onSelectTab = viewModel::selectTab,
                    onToggleSearch = viewModel::toggleSearch,
                    onQuery = viewModel::setQuery,
                    onOpenSettings = viewModel::openSettings,
                    onOpenItem = viewModel::openItem,
                    onToggleDone = viewModel::toggleDone,
                    onNew = viewModel::startNewAsCurrentTab,
                )

                is Screen.Editor -> EditorScreen(
                    state = state,
                    onDraftChange = viewModel::setDraft,
                    onBack = viewModel::saveDraft,
                    onSave = viewModel::saveDraft,
                    onDelete = viewModel::deleteCurrent,
                )

                Screen.Settings -> SettingsScreen(
                    noteCount = state.noteCount,
                    todoCount = state.todoCount,
                    openTodoCount = state.openTodoCount,
                    onBack = viewModel::openList,
                    onExport = { viewModel.prepareExport { name -> exportLauncher.launch(name) } },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
            }

            if (state.busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }

    val pending = state.pendingImport
    if (pending != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_msg, pending.count)) },
            confirmButton = {
                TextButton(onClick = { viewModel.applyImport(replace = false) }) {
                    Text(stringResource(R.string.import_mode_merge))
                }
            },
            dismissButton = {
                Box {
                    TextButton(onClick = { viewModel.applyImport(replace = true) }) {
                        Text(
                            text = stringResource(R.string.import_mode_replace),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
    }
}

/** Convenience for the widget config screen. */
internal fun kindLabelRes(kind: Kind): Int =
    if (kind == Kind.NOTE) R.string.tab_notes else R.string.tab_todos
