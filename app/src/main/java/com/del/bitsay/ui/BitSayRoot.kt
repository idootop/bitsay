package com.del.bitsay.ui

import androidx.compose.runtime.getValue
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.del.bitsay.i18n.backupErrorMessage
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
import com.del.bitsay.ui.screen.SearchScreen
import com.del.bitsay.ui.screen.SettingsScreen

@Composable
fun BitSayRoot(
    viewModel: AppViewModel,
    onExit: () -> Unit,
    onRelaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFiles.MIME),
    ) { uri -> if (uri != null) viewModel.writeExport(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.loadImport(uri) }

    // A widget-launched session ends by getting out of the way, back to the home screen.
    LaunchedEffect(viewModel) {
        viewModel.exit.collect { onExit() }
    }
    LaunchedEffect(viewModel) {
        viewModel.relaunch.collect { onRelaunch() }
    }

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
        is Notice.Failed -> when {
            // The wording lives in resources, so the mapping from a failure to a sentence
            // happens here rather than in the state layer.
            notice.staleExport -> context.getString(R.string.backup_error_stale)
            notice.error != null -> context.backupErrorMessage(notice.error, notice.schema)
            else -> context.getString(R.string.op_fail, notice.detail.orEmpty())
        }
        Notice.Empty -> stringResource(R.string.nothing_to_save)
        Notice.WidgetPinRequested -> stringResource(R.string.widget_pin_requested)
        Notice.WidgetPinUnsupported -> stringResource(R.string.widget_pin_unsupported)
    }
    LaunchedEffect(state.notice) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    // Enabled on the list too when the session came from the widget: back should leave the app,
    // not silently reveal an app screen the user never asked for.
    BackHandler(enabled = state.screen != Screen.List || state.fromWidget || state.inSelectionMode) {
        when {
            // Backing out of a selection should only leave selection mode, not the screen.
            state.inSelectionMode -> viewModel.clearSelection()
            state.screen is Screen.Editor -> viewModel.saveDraft()
            state.screen is Screen.Search -> viewModel.closeSearch()
            state.screen is Screen.Settings -> viewModel.openList()
            else -> onExit()
        }
    }

    // Whatever is half-typed is written before the app stops being visible, and the cache is
    // re-read whenever the app comes back (cheap insurance against any out-of-band write).
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flushDraft() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    // Ids each list has already shown, **one set per kind**. Not state: mutating it must not
    // recompose anything. Per-kind matters — with a single shared set the notes page and the todos
    // page (both alive inside the pager) overwrote each other every pass, so each one saw the
    // other's ids as brand new and replayed its whole entrance.
    val seenItemIds = remember { mutableMapOf<Kind, MutableSet<Long>>() }

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
                    // Hoisted above the screen switch on purpose. ListScreen leaves the composition
                    // whenever the editor or settings opens, taking any `remember` inside it with
                    // it — and then coming back would replay the entrance for the whole list
                    // instead of only for what actually changed.
                    seenItemIds = seenItemIds,
                    onSelectTab = viewModel::selectTab,
                    onOpenSearch = viewModel::openSearch,
                    onOpenSettings = viewModel::openSettings,
                    onOpenItem = viewModel::openItem,
                    onToggleDone = viewModel::toggleDone,
                    onNew = viewModel::startNewAsCurrentTab,
                    onBeginSelection = viewModel::beginSelection,
                    onToggleSelection = viewModel::toggleSelection,
                    onClearSelection = viewModel::clearSelection,
                    onDeleteSelected = viewModel::deleteSelected,
                )

                is Screen.Editor -> EditorScreen(
                    state = state,
                    onDraftChange = viewModel::setDraft,
                    onBack = viewModel::saveDraft,
                    onDelete = viewModel::deleteCurrent,
                )

                Screen.Search -> SearchScreen(
                    state = state,
                    onQuery = viewModel::setQuery,
                    onSelectTab = viewModel::selectSearchTab,
                    onBack = viewModel::closeSearch,
                    onOpenItem = viewModel::openSearchResult,
                    onToggleDone = viewModel::toggleDone,
                )

                Screen.Settings -> SettingsScreen(
                    language = state.language,
                    onLanguage = viewModel::setLanguage,
                    themeMode = state.themeMode,
                    onThemeMode = viewModel::setThemeMode,
                    onBack = viewModel::openList,
                    onExport = { viewModel.prepareExport { name -> exportLauncher.launch(name) } },
                    onImport = { importLauncher.launch(BackupFiles.IMPORT_MIME_TYPES) },
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
