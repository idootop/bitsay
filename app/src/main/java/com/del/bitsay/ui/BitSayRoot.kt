package com.del.bitsay.ui

import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.del.bitsay.i18n.backupErrorMessage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.del.bitsay.R
import com.del.bitsay.core.backup.BackupFiles
import com.del.bitsay.core.model.Kind
import com.del.bitsay.ui.components.BrandMark
import com.del.bitsay.ui.components.PaperBackground
import com.del.bitsay.ui.components.emphasized
import com.del.bitsay.ui.screen.EditorScreen
import com.del.bitsay.ui.screen.ListScreen
import com.del.bitsay.ui.screen.SearchScreen
import com.del.bitsay.ui.screen.SettingsScreen
import com.del.bitsay.ui.theme.Divider
import com.del.bitsay.ui.theme.InkFaint
import com.del.bitsay.ui.theme.MeasureWidth
import com.del.bitsay.ui.theme.WideBreakpoint
import com.del.bitsay.ui.theme.listPaneWidth

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
        // The one place that decides the *shape* of the app: a single column, or two.
        //
        // 600dp is where "a 344dp list plus a detail column that can still hold a sentence" stops
        // fitting. Everything narrower — every phone in portrait, a folded cover screen — takes the
        // branch below and is byte-for-byte what it always was; the wide branch is additive, it
        // does not retune the phone.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            // Read out here on purpose: inside the Row the RowScope becomes the implicit receiver
            // and `maxWidth` resolves against that instead.
            val windowWidth = maxWidth
            if (windowWidth >= WideBreakpoint) {
                Row(Modifier.fillMaxSize()) {
                    // The list never leaves a wide window. Opening an entry puts the editor *next
                    // to* the list rather than on top of it, so what you were reading stays as
                    // context while you write — and because this pane is never torn down, the
                    // entrance animation stays quiet for rows that were already on screen.
                    ListPane(
                        state = state,
                        viewModel = viewModel,
                        seenItemIds = seenItemIds,
                        modifier = Modifier.width(listPaneWidth(windowWidth)).fillMaxHeight(),
                    )

                    Box(Modifier.width(1.dp).fillMaxHeight().background(Divider))

                    // Search and settings ride in the detail pane as well: both are things you
                    // opened from this window, and the list behind them is still the list you were
                    // reading. On top of that sits the reading measure, so a 1400dp desktop window
                    // gets a centred column instead of a paragraph stretched across the screen.
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.widthIn(max = MeasureWidth).fillMaxSize()) {
                            when (val screen = state.screen) {
                                Screen.List -> WidePlaceholder()
                                is Screen.Editor -> EditorPane(state, viewModel)
                                Screen.Search -> SearchPane(state, viewModel)
                                Screen.Settings -> SettingsPane(
                                    state = state,
                                    viewModel = viewModel,
                                    onExport = {
                                        viewModel.prepareExport { name -> exportLauncher.launch(name) }
                                    },
                                    onImport = { importLauncher.launch(BackupFiles.IMPORT_MIME_TYPES) },
                                )
                            }
                        }
                    }
                }
            } else {
                when (val screen = state.screen) {
                    Screen.List -> ListPane(
                        state = state,
                        viewModel = viewModel,
                        seenItemIds = seenItemIds,
                    )

                    is Screen.Editor -> EditorPane(state, viewModel)

                    Screen.Search -> SearchPane(state, viewModel)

                    Screen.Settings -> SettingsPane(
                        state = state,
                        viewModel = viewModel,
                        onExport = { viewModel.prepareExport { name -> exportLauncher.launch(name) } },
                        onImport = { importLauncher.launch(BackupFiles.IMPORT_MIME_TYPES) },
                    )
                }
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

// ----------------------------------------------------------------------------
// The four screens, wired once.
//
// Both the narrow and the wide branch call these, so the callback list for a screen exists in
// exactly one place. The only difference between the two branches is *where* the pane lands.
// ----------------------------------------------------------------------------

/**
 * The master pane: the list, the tabs and the search/settings buttons. On a wide window it is
 * pinned to the left for as long as the app is open.
 */
@Composable
private fun ListPane(
    state: AppUiState,
    viewModel: AppViewModel,
    /**
     * Hoisted above the screen switch on purpose. The list pane is disposed whenever a narrow
     * window leaves it for the editor, taking any `remember` inside it along — and coming back
     * would then replay the entrance for the whole list instead of only for what actually changed.
     */
    seenItemIds: MutableMap<Kind, MutableSet<Long>>,
    modifier: Modifier = Modifier,
) {
    ListScreen(
        state = state,
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
        modifier = modifier,
    )
}

@Composable
private fun EditorPane(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    EditorScreen(
        state = state,
        onDraftChange = viewModel::setDraft,
        onBack = viewModel::saveDraft,
        onDelete = viewModel::deleteCurrent,
        modifier = modifier,
    )
}

@Composable
private fun SearchPane(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    SearchScreen(
        state = state,
        onQuery = viewModel::setQuery,
        onSelectTab = viewModel::selectSearchTab,
        onBack = viewModel::closeSearch,
        onOpenItem = viewModel::openSearchResult,
        onToggleDone = viewModel::toggleDone,
        modifier = modifier,
    )
}

@Composable
private fun SettingsPane(
    state: AppUiState,
    viewModel: AppViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScreen(
        language = state.language,
        onLanguage = viewModel::setLanguage,
        themeMode = state.themeMode,
        onThemeMode = viewModel::setThemeMode,
        onBack = viewModel::openList,
        onExport = onExport,
        onImport = onImport,
        modifier = modifier,
    )
}

/**
 * What the detail pane shows before anything is picked.
 *
 * A signpost, deliberately not a button: no fill, no shape, nothing that invites a tap it cannot
 * answer. The plant is here rather than a second empty-state headline because it is the app's one
 * established "nothing here yet" mark, and it is drawn at 46dp — a third of the empty state's
 * size — so it reads as a watermark, not as content.
 */
@Composable
private fun WidePlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(height = 46.dp)
        Text(
            text = emphasized(stringResource(R.string.wide_blank), InkFaint),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = InkFaint,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.wide_blank_hint),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = InkFaint,
            modifier = Modifier.padding(top = 6.dp).alpha(0.72f),
        )
    }
}
