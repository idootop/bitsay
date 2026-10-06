package com.del.bitsay.ui

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.del.bitsay.AppContainer
import com.del.bitsay.core.backup.BackupArchive
import com.del.bitsay.core.backup.BackupError
import com.del.bitsay.core.backup.BackupException
import com.del.bitsay.core.backup.BackupFiles
import com.del.bitsay.core.backup.BackupManager
import com.del.bitsay.core.backup.ExportPayload
import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind
import com.del.bitsay.core.repo.ImportResult
import com.del.bitsay.core.repo.ItemRepository
import com.del.bitsay.core.util.WriteThrottle
import com.del.bitsay.i18n.AppLanguage
import com.del.bitsay.ui.theme.ThemeMode
import com.del.bitsay.widget.WidgetPinner
import com.del.bitsay.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which full-screen surface is on top. Deliberately hand-rolled: three screens do not
 *  justify a navigation library plus its serialization dependency. */
sealed interface Screen {
    data object List : Screen
    data class Editor(val id: Long, val kind: Kind) : Screen
    data object Settings : Screen

    /** Its own page rather than a field that expands inside the list: search has its own scope
     *  (which list you are looking in), its own empty state and its own back behaviour. */
    data object Search : Screen
}

/** One-shot user feedback. Kept as data so the UI owns all wording. */
sealed interface Notice {
    data class Exported(val count: Int) : Notice
    /**
     * [error] is set when the failure is one we can name; [detail] carries anything else. The
     * wording lives in resources, not here, so this layer stays language-agnostic.
     */
    data class Failed(
        val error: BackupError? = null,
        val detail: String? = null,
        val schema: Int = 0,
        /** The in-memory export expired before the user picked a file — not a damaged backup. */
        val staleExport: Boolean = false,
    ) : Notice
    data class Imported(val result: ImportResult) : Notice
    data object Empty : Notice
    data object WidgetPinRequested : Notice
    data object WidgetPinUnsupported : Notice
}

/** A backup file that has been read and is waiting for the user to pick a restore mode. */
data class PendingImport(val payload: ByteArray, val count: Int)

data class AppUiState(
    val tab: Kind = Kind.NOTE,
    val query: String = "",
    val notes: List<Item> = emptyList(),
    val todos: List<Item> = emptyList(),
    val screen: Screen = Screen.List,
    val draft: String = "",
    val editingId: Long = 0L,
    val editingKind: Kind = Kind.NOTE,
    val editingCreatedAt: Long = 0L,
    val editingUpdatedAt: Long = 0L,
    /** Keystrokes typed but not written to the database yet. */
    val dirty: Boolean = false,
    /**
     * The editing session was opened from the home-screen widget, so finishing it must drop the
     * user back on the launcher instead of showing the app's own list.
     */
    val fromWidget: Boolean = false,
    /**
     * Opened from the widget's `+`: a blank quick-capture editor with no save button — the text
     * is already being written as it is typed, so the way out is simply "back".
     */
    val quickCapture: Boolean = false,
    /** Whether this launcher supports "pin widget to home screen" from inside the app. */
    val canPinWidget: Boolean = false,
    /** The language the app is displayed in; [AppLanguage.SYSTEM] means "follow the phone". */
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Light/dark choice; [ThemeMode.SYSTEM] means "follow the phone". */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Ids ticked for a batch operation. Empty means "not in selection mode" — there is no
     * separate boolean to keep in sync.
     */
    val selection: Set<Long> = emptySet(),
    /**
     * Rows matching [query], fetched from SQL. The in-memory lists only hold previews of the
     * text, so searching them would silently miss matches deep inside a long note.
     */
    val searchResults: List<Item> = emptyList(),
    /**
     * Raise the keyboard as soon as the editor appears. True only when the editor was opened to
     * **write something new** (the FAB, or the widget's `+`); opening an existing entry is a
     * "look at it" gesture and should not have half the screen covered by an IME.
     */
    val autoFocusEditor: Boolean = false,
    /**
     * Increments every time an editor session starts. It is the key the editor uses to run its
     * one-shot focus effect, so that a second "new entry" arriving while the editor is already
     * open (e.g. the widget's `+` while the app sits behind it) still focuses the field.
     */
    val editorSession: Long = 0L,
    val busy: Boolean = false,
    val notice: Notice? = null,
    val pendingImport: PendingImport? = null,
) {
    val current: List<Item> get() = if (tab == Kind.NOTE) notes else todos

    val visible: List<Item> get() = if (query.isBlank()) current else searchResults

    val inSelectionMode: Boolean get() = selection.isNotEmpty()

    val noteCount: Int get() = notes.size
    val todoCount: Int get() = todos.size
    val openTodoCount: Int get() = todos.count { !it.done }
}

/**
 * The UI state layer. Holds no business rules — every mutation is delegated to
 * [ItemRepository] / [BackupManager], which are pure enough to be unit tested on their own.
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val repository: ItemRepository = container.repository
    private val backup: BackupManager = container.backup

    private val _state = MutableStateFlow(
        AppUiState(
            canPinWidget = WidgetPinner.isSupported(container.context),
            language = container.languagePrefs.current(),
            themeMode = container.themePrefs.current(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    /**
     * Fired when the user is done with a widget-launched session and the Activity should get out
     * of the way. An event rather than state, because it is consumed exactly once.
     */
    private val _exit = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exit: SharedFlow<Unit> = _exit.asSharedFlow()

    /**
     * Fired when the language changed. Every string on screen — and every date formatter — was
     * built from the old configuration, so the only honest way to apply it is to rebuild the
     * screen. `recreate()` re-runs `attachBaseContext`, which is where the locale is applied.
     */
    private val _relaunch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val relaunch: SharedFlow<Unit> = _relaunch.asSharedFlow()

    init {
        viewModelScope.launch { repository.reload() }
        viewModelScope.launch {
            repository.notes.collect { list -> _state.update { it.copy(notes = list) } }
        }
        viewModelScope.launch {
            repository.todos.collect { list -> _state.update { it.copy(todos = list) } }
        }
        // Search hits live outside the in-memory lists, so a write has to re-run them.
        viewModelScope.launch {
            repository.change.drop(1).collect {
                if (_state.value.query.isNotBlank()) runSearch()
            }
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch()
        }
    }

    private suspend fun runSearch() {
        val (kind, needle) = _state.value.let { it.tab to it.query }
        val results = repository.search(kind, needle)
        // Drop the result if the user typed on while the query was running.
        if (_state.value.query == needle && _state.value.tab == kind) {
            _state.update { it.copy(searchResults = results) }
        }
    }

    // ------------------------------------------------------------------ navigation

    fun selectTab(kind: Kind) = _state.update {
        it.copy(tab = kind, query = "", searchResults = emptyList(), selection = emptySet())
    }

    /**
     * The search page's tab switch: same keyword, other kind.
     *
     * It must not go through [selectTab], which drops the query — there, "notes / todos" picks
     * which list the app shows, here it narrows the search that is already on screen. The new
     * results are fetched straight away rather than through the typing debounce: a tab tap is a
     * single deliberate action, not a keystroke in a burst. The previous kind's rows stay up
     * until the new ones land, which is a few milliseconds of a `LIKE` scan.
     */
    fun selectSearchTab(kind: Kind) {
        if (_state.value.tab == kind) return
        _state.update { it.copy(tab = kind) }
        if (_state.value.query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch() }
    }

    /**
     * @param fromWidget true when the widget's search button opened the floating window: leaving
     *   search then dismisses that window instead of landing on the app's list.
     */
    fun openSearch(fromWidget: Boolean = false) = _state.update {
        it.copy(screen = Screen.Search, query = "", searchResults = emptyList(), fromWidget = fromWidget)
    }

    /**
     * Leaving search drops the query: coming back to a stale result list would be confusing.
     *
     * In the widget's floating window there is nothing to go *back* to, and switching to the list
     * first would paint the app's list inside the window for a frame — the same flicker
     * [leaveEditor] avoids. So the window just goes away.
     */
    fun closeSearch() {
        if (_state.value.fromWidget) {
            exitToLauncher()
            return
        }
        _state.update { it.copy(screen = Screen.List, query = "", searchResults = emptyList()) }
    }

    /**
     * Opening a result has to keep the origin it was found from: a hit opened inside the widget's
     * window must back out to the home screen, not to the app's list.
     */
    fun openSearchResult(id: Long) = openItem(id, fromWidget = _state.value.fromWidget)

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        scheduleSearch()
    }

    // ------------------------------------------------------------------ batch selection

    /** Long-press on a row: enter selection mode with that row ticked. */
    fun beginSelection(id: Long) = _state.update { it.copy(selection = setOf(id)) }

    fun toggleSelection(id: Long) = _state.update {
        it.copy(selection = if (id in it.selection) it.selection - id else it.selection + id)
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun deleteSelected() {
        val ids = _state.value.selection
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteMany(ids)
            _state.update { it.copy(selection = emptySet()) }
        }
    }


    fun openSettings() = _state.update { it.copy(screen = Screen.Settings) }

    fun openList() = _state.update { it.copy(screen = Screen.List, query = "") }

    /**
     * @param fromWidget true when the widget's `+` opened the app: the editor runs in quick
     *   capture mode and backing out returns to the home screen.
     */
    fun startNew(kind: Kind, fromWidget: Boolean = false) {
        cancelAutoSave()
        _state.update {
            it.copy(
                screen = Screen.Editor(id = 0L, kind = kind),
                tab = kind,
                draft = "",
                editingId = 0L,
                editingKind = kind,
                editingCreatedAt = 0L,
                editingUpdatedAt = 0L,
                dirty = false,
                fromWidget = fromWidget,
                quickCapture = fromWidget,
                autoFocusEditor = true,
                editorSession = it.editorSession + 1L,
            )
        }
    }

    fun startNewAsCurrentTab() = startNew(_state.value.tab)

    fun openItem(id: Long, fromWidget: Boolean = false) {
        cancelAutoSave()
        _state.update {
            it.copy(
                fromWidget = fromWidget,
                quickCapture = false,
                // Looking at an existing entry: show it, do not shove a keyboard in front of it.
                autoFocusEditor = false,
                editorSession = it.editorSession + 1L,
            )
        }
        viewModelScope.launch {
            val item = repository.findById(id)
            if (item == null) {
                // Deleted on another surface while the tap was in flight.
                if (fromWidget) exitToLauncher() else openList()
                return@launch
            }
            _state.update {
                it.copy(
                    screen = Screen.Editor(id = item.id, kind = item.kind),
                    tab = item.kind,
                    draft = item.text,
                    editingId = item.id,
                    editingKind = item.kind,
                    editingCreatedAt = item.createdAt,
                    editingUpdatedAt = item.updatedAt,
                    dirty = false,
                )
            }
        }
    }

    // ------------------------------------------------------------------ auto-save

    /**
     * Auto-save with a leading **and** trailing edge (see [WriteThrottle]): the first character
     * of a new thought hits the database immediately, continuous typing writes at most once per
     * [AUTO_SAVE_THROTTLE_MS], and the last character always gets its own write. A thought
     * therefore survives a crash, a swipe-away, or simply forgetting to press save.
     */
    fun setDraft(value: String) {
        _state.update { it.copy(draft = value, dirty = true) }
        scheduleAutoSave()
    }

    /** Writes pending keystrokes immediately — used when the app leaves the foreground. */
    fun flushDraft() {
        if (!_state.value.dirty) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch { persistDraft() }
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            val wait = autoSaveThrottle.delayBeforeNextWrite(SystemClock.elapsedRealtime())
            if (wait > 0L) delay(wait)
            persistDraft()
        }
    }

    // ------------------------------------------------------------------ mutations

    /**
     * Leaves the editor. The text is already in the database, so this only flushes the last few
     * milliseconds and removes a row the user emptied out.
     *
     * **An empty entry is not content.** The repository never inserts one for a blank draft, and a
     * row whose text has been cleared is deleted here — whether it was created a moment ago or has
     * been on the list for weeks. The old behaviour kept the previous text for an existing entry
     * and only showed a notice, which meant the words the user had just deleted came back the next
     * time they opened it.
     */
    fun saveDraft() {
        cancelAutoSave()
        viewModelScope.launch {
            persistDraft()
            val snapshot = _state.value
            val emptied = snapshot.draft.isBlank() && snapshot.editingId > 0L
            if (emptied) {
                repository.delete(snapshot.editingId)
                _state.update { it.copy(notice = Notice.Empty) }
            }
            leaveEditor(snapshot.fromWidget)
        }
    }

    fun deleteCurrent() {
        cancelAutoSave()
        val snapshot = _state.value
        viewModelScope.launch {
            if (snapshot.editingId > 0L) repository.delete(snapshot.editingId)
            leaveEditor(snapshot.fromWidget)
        }
    }

    fun toggleDone(id: Long) {
        viewModelScope.launch { repository.toggleDone(id) }
    }

    /** Re-reads both lists from disk. Cheap, and keeps the UI honest after any external write. */
    fun refresh() {
        viewModelScope.launch { repository.reload() }
    }

    fun setLanguage(language: AppLanguage) {
        if (_state.value.language == language) return
        container.languagePrefs.set(language)
        _state.update { it.copy(language = language) }
        // The widget renders its strings into RemoteViews, so it holds a copy of the old
        // language until something makes it render again — nothing else here would.
        viewModelScope.launch {
            withContext(Dispatchers.Default) { WidgetUpdater.refreshAll(container.context) }
        }
        _relaunch.tryEmit(Unit)
    }

    /**
     * Unlike the language, a theme change needs no activity restart: Compose simply recomposes
     * against the new palette. The widget still has to be pushed, because its colours were baked
     * into the RemoteViews it already handed to the launcher.
     */
    fun setThemeMode(mode: ThemeMode) {
        if (_state.value.themeMode == mode) return
        container.themePrefs.set(mode)
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            withContext(Dispatchers.Default) { WidgetUpdater.refreshAll(container.context) }
        }
    }

    /** Asks the launcher to drop a widget on the home screen. */
    fun addWidgetToHome() {
        val accepted = WidgetPinner.request(container.context)
        _state.update {
            it.copy(
                notice = if (accepted) Notice.WidgetPinRequested else Notice.WidgetPinUnsupported,
            )
        }
    }

    // ------------------------------------------------------------------ internals

    /**
     * Writes [AppUiState.draft] and adopts the row id when this was the first keystroke of a new
     * entry, so later keystrokes update instead of inserting again.
     */
    private suspend fun persistDraft() {
        val snapshot = _state.value
        // reload = false: the list is behind the editor, so re-reading every row after every
        // keystroke-batch buys nothing. leaveEditor() reloads once, when it becomes visible again.
        val id = repository.saveDraft(
            id = snapshot.editingId,
            kind = snapshot.editingKind,
            text = snapshot.draft,
            reload = false,
        )
        autoSaveThrottle.onWritten(SystemClock.elapsedRealtime())
        if (id == 0L) {
            _state.update { it.copy(dirty = snapshot.draft.isNotBlank()) }
            return
        }
        val stored = repository.findById(id)
        _state.update {
            it.copy(
                editingId = id,
                screen = Screen.Editor(id = id, kind = it.editingKind),
                editingCreatedAt = stored?.createdAt ?: it.editingCreatedAt,
                editingUpdatedAt = stored?.updatedAt ?: it.editingUpdatedAt,
                dirty = false,
            )
        }
    }

    /**
     * Closes the editor, either back into the app or all the way out to the home screen.
     *
     * In the widget's floating window there is nothing to navigate *to*: switching to the list
     * first would paint it for a frame before `finish()` landed, which is exactly the flicker the
     * separate window exists to avoid. So the screen is left alone and the window just goes away.
     */
    private suspend fun leaveEditor(fromWidget: Boolean) {
        // One refresh for the whole editing session, instead of one per keystroke-batch.
        repository.reload()
        if (fromWidget) exitToLauncher() else closeEditor()
    }

    private fun exitToLauncher() {
        _exit.tryEmit(Unit)
    }

    private fun closeEditor() = _state.update {
        it.copy(
            screen = Screen.List,
            draft = "",
            editingId = 0L,
            dirty = false,
            fromWidget = false,
            quickCapture = false,
            // The editor can be opened from a search hit, and it always returns to the list — so
            // the query has to go with it. Leaving it set made the home list silently render
            // `searchResults` instead of the real list: count the header said "4 todos" while the
            // body showed the empty state. Same reasoning as closeSearch().
            query = "",
            searchResults = emptyList(),
        )
    }

    private fun cancelAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        autoSaveThrottle.reset()
    }

    // ------------------------------------------------------------------ backup

    /**
     * Builds the document first (cheap, in memory), then hands the suggested file name to the
     * caller so it can open the system "create document" picker.
     */
    fun prepareExport(onReady: (fileName: String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { backup.suggestedFileName() to backup.export() }
                .onSuccess { (name, payload) ->
                    exportPayload = payload
                    onReady(name)
                }
                .onFailure { error ->
                    _state.update { it.copy(notice = error.toFailure()) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    fun writeExport(uri: Uri) {
        val payload = exportPayload
        if (payload == null) {
            _state.update { it.copy(notice = Notice.Failed(staleExport = true)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { BackupFiles.write(container.context, uri, BackupArchive.compress(payload.payload)) }
                .onSuccess { _state.update { it.copy(notice = Notice.Exported(payload.count)) } }
                .onFailure { error -> _state.update { it.copy(notice = error.toFailure()) } }
            exportPayload = null
            _state.update { it.copy(busy = false) }
        }
    }

    /** Reads + validates a chosen backup file and asks the user how to apply it. */
    fun loadImport(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val payload = BackupArchive.decompress(BackupFiles.read(container.context, uri))
                PendingImport(payload = payload, count = backup.peek(payload).items.size)
            }
                .onSuccess { pending -> _state.update { it.copy(pendingImport = pending) } }
                .onFailure { error -> _state.update { it.copy(notice = error.toFailure()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun applyImport(replace: Boolean) {
        val pending = _state.value.pendingImport ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, pendingImport = null) }
            runCatching { backup.import(pending.payload, replace) }
                .onSuccess { result -> _state.update { it.copy(notice = Notice.Imported(result)) } }
                .onFailure { error -> _state.update { it.copy(notice = error.toFailure()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun cancelImport() = _state.update { it.copy(pendingImport = null) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    // ------------------------------------------------------------------ state

    private var exportPayload: ExportPayload? = null
    private var autoSaveJob: Job? = null
    private var searchJob: Job? = null
    private val autoSaveThrottle = WriteThrottle(AUTO_SAVE_THROTTLE_MS)

    /** True when the entry now in the editor did not exist until this editing session. */

    /** Turns any throwable into something the UI can put into a sentence. */
    private fun Throwable.toFailure(): Notice.Failed = when (this) {
        is BackupException -> Notice.Failed(error = error, schema = schema)
        else -> Notice.Failed(detail = message ?: this::class.java.simpleName)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(container) as T
        }
    }

    companion object {
        /**
         * Upper bound on how long a keystroke can sit unsaved. The *first* keystroke of a burst
         * is written immediately, so this only bounds the middle of a long burst.
         */
        const val AUTO_SAVE_THROTTLE_MS = 400L

        /** Long enough to coalesce a burst of typing, short enough to feel live. */
        const val SEARCH_DEBOUNCE_MS = 180L
    }
}
