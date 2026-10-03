package com.del.bitsay.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.del.bitsay.AppContainer
import com.del.bitsay.core.backup.BackupFiles
import com.del.bitsay.core.backup.BackupManager
import com.del.bitsay.core.backup.ExportPayload
import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind
import com.del.bitsay.core.repo.ImportResult
import com.del.bitsay.core.repo.ItemRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which full-screen surface is on top. Deliberately hand-rolled: three screens do not
 *  justify a navigation library plus its serialization dependency. */
sealed interface Screen {
    data object List : Screen
    data class Editor(val id: Long, val kind: Kind) : Screen
    data object Settings : Screen
}

/** One-shot user feedback. Kept as data so the UI owns all wording. */
sealed interface Notice {
    data class Exported(val count: Int) : Notice
    data class Failed(val reason: String) : Notice
    data class Imported(val result: ImportResult) : Notice
    data object Empty : Notice
}

/** A backup file that has been read and is waiting for the user to pick a restore mode. */
data class PendingImport(val json: String, val count: Int)

data class AppUiState(
    val tab: Kind = Kind.NOTE,
    val query: String = "",
    val searchOpen: Boolean = false,
    val notes: List<Item> = emptyList(),
    val todos: List<Item> = emptyList(),
    val screen: Screen = Screen.List,
    val draft: String = "",
    val editingId: Long = 0L,
    val editingKind: Kind = Kind.NOTE,
    val editingCreatedAt: Long = 0L,
    val editingUpdatedAt: Long = 0L,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val notice: Notice? = null,
    val pendingImport: PendingImport? = null,
) {
    val current: List<Item> get() = if (tab == Kind.NOTE) notes else todos

    val visible: List<Item> get() {
        if (query.isBlank()) return current
        val needle = query.trim()
        return current.filter { it.text.contains(needle, ignoreCase = true) }
    }

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

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repository.reload() }
        viewModelScope.launch {
            repository.notes.collect { list -> _state.update { it.copy(notes = list) } }
        }
        viewModelScope.launch {
            repository.todos.collect { list -> _state.update { it.copy(todos = list) } }
        }
    }

    // ------------------------------------------------------------------ navigation

    fun selectTab(kind: Kind) = _state.update { it.copy(tab = kind, query = "") }

    fun toggleSearch() = _state.update {
        it.copy(searchOpen = !it.searchOpen, query = if (it.searchOpen) "" else it.query)
    }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun openSettings() = _state.update { it.copy(screen = Screen.Settings) }

    fun openList() = _state.update { it.copy(screen = Screen.List, query = "") }

    fun startNew(kind: Kind) {
        cancelAutoSave()
        createdInThisSession = false
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
            )
        }
    }

    fun startNewAsCurrentTab() = startNew(_state.value.tab)

    fun openItem(id: Long) {
        cancelAutoSave()
        createdInThisSession = false
        viewModelScope.launch {
            val item = repository.findById(id) ?: return@launch
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

    /**
     * Every keystroke reaches the database after a short pause, so a thought survives a crash,
     * a swipe-away, or simply forgetting to press save. Typing the first character of a new
     * note creates the row right away — nothing is ever held only in memory.
     */
    fun setDraft(value: String) {
        _state.update { it.copy(draft = value, dirty = true) }
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            persistDraft()
        }
    }

    /** Writes pending keystrokes immediately — used when the app leaves the foreground. */
    fun flushDraft() {
        if (!_state.value.dirty) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch { persistDraft() }
    }

    // ------------------------------------------------------------------ mutations

    /**
     * Leaves the editor. The text is already in the database, so this only flushes the last few
     * milliseconds and cleans up a brand-new entry the user emptied out before leaving.
     */
    fun saveDraft() {
        cancelAutoSave()
        viewModelScope.launch {
            persistDraft()
            val snapshot = _state.value
            val emptiedNewEntry = createdInThisSession && snapshot.draft.isBlank()
            if (emptiedNewEntry && snapshot.editingId > 0L) {
                repository.delete(snapshot.editingId)
            } else if (!createdInThisSession && snapshot.draft.isBlank()) {
                // Editing an existing entry down to nothing: keep the last real content and say so.
                if (snapshot.editingId > 0L) _state.update { it.copy(notice = Notice.Empty) }
            }
            closeEditor()
        }
    }

    fun deleteCurrent() {
        cancelAutoSave()
        val id = _state.value.editingId
        viewModelScope.launch {
            if (id > 0L) repository.delete(id)
            closeEditor()
        }
    }

    fun toggleDone(id: Long) {
        viewModelScope.launch { repository.toggleDone(id) }
    }

    /** Re-reads both lists from disk. Cheap, and keeps the UI honest after any external write. */
    fun refresh() {
        viewModelScope.launch { repository.reload() }
    }

    // ------------------------------------------------------------------ internals

    /**
     * Writes [AppUiState.draft] and adopts the row id when this was the first keystroke of a new
     * entry, so later keystrokes update instead of inserting again.
     */
    private suspend fun persistDraft() {
        val snapshot = _state.value
        val id = repository.saveDraft(snapshot.editingId, snapshot.editingKind, snapshot.draft)
        if (id == 0L) {
            _state.update { it.copy(dirty = snapshot.draft.isNotBlank()) }
            return
        }
        if (snapshot.editingId == 0L) createdInThisSession = true
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

    private fun closeEditor() = _state.update {
        it.copy(screen = Screen.List, draft = "", editingId = 0L, dirty = false)
    }

    private fun cancelAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        createdInThisSession = false
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
                    _state.update { it.copy(notice = Notice.Failed(error.readable())) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    fun writeExport(uri: Uri) {
        val payload = exportPayload
        if (payload == null) {
            _state.update { it.copy(notice = Notice.Failed("导出内容已失效，请重试")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { BackupFiles.write(container.context, uri, payload.json) }
                .onSuccess { _state.update { it.copy(notice = Notice.Exported(payload.count)) } }
                .onFailure { error -> _state.update { it.copy(notice = Notice.Failed(error.readable())) } }
            exportPayload = null
            _state.update { it.copy(busy = false) }
        }
    }

    /** Reads + validates a chosen backup file and asks the user how to apply it. */
    fun loadImport(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val json = BackupFiles.read(container.context, uri)
                PendingImport(json = json, count = backup.peek(json).items.size)
            }
                .onSuccess { pending -> _state.update { it.copy(pendingImport = pending) } }
                .onFailure { error -> _state.update { it.copy(notice = Notice.Failed(error.readable())) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun applyImport(replace: Boolean) {
        val pending = _state.value.pendingImport ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, pendingImport = null) }
            runCatching { backup.import(pending.json, replace) }
                .onSuccess { result -> _state.update { it.copy(notice = Notice.Imported(result)) } }
                .onFailure { error -> _state.update { it.copy(notice = Notice.Failed(error.readable())) } }
            _state.update { it.copy(busy = false) }
        }
    }

    fun cancelImport() = _state.update { it.copy(pendingImport = null) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    // ------------------------------------------------------------------ internals

    private var exportPayload: ExportPayload? = null
    private var autoSaveJob: Job? = null

    /** True when the entry now in the editor did not exist until this editing session. */
    private var createdInThisSession = false

    private fun Throwable.readable(): String = message ?: this::class.java.simpleName

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(container) as T
        }
    }

    companion object {
        /** Short enough to feel instant, long enough to avoid a write per keystroke. */
        const val AUTO_SAVE_DEBOUNCE_MS = 600L
    }
}
