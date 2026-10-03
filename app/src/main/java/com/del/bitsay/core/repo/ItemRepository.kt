package com.del.bitsay.core.repo

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The whole business layer for notes & todos. No Android imports, no UI imports: it can be
 * driven from a unit test, from the widget, or from a future CLI.
 *
 * Rules enforced here (never in the UI):
 *  * text is trimmed, blank text is rejected;
 *  * `createdAt` is written once, `updatedAt` on every real change;
 *  * ticking a todo records `doneAt`, unticking clears it;
 *  * notes and todos never mix, even though they share one table.
 *
 * Reads are served from two in-memory [StateFlow]s that are refreshed after every mutation.
 * There is exactly one writer (this class, single process), so the cache cannot go stale.
 */
class ItemRepository(
    private val store: ItemStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val _notes = MutableStateFlow<List<Item>>(emptyList())
    private val _todos = MutableStateFlow<List<Item>>(emptyList())

    val notes: StateFlow<List<Item>> = _notes.asStateFlow()
    val todos: StateFlow<List<Item>> = _todos.asStateFlow()

    /**
     * Emitted after every successful write. The widget layer listens to it to re-render.
     *
     * [version] alone would not be enough: observers also need to know *what kind* of write
     * happened. A newest-first ListView re-anchors its scroll position to the previously-first
     * row whenever its data changes (`AbsListView.rememberSyncState`), so when a row is
     * **inserted** at the top the new entry ends up hidden just above the viewport. [insertedAt]
     * carries the [version] of the most recent insert so the widget can tell the two apart and
     * scroll back to the top only when there is something new to reveal.
     */
    data class DataChange(
        val version: Long = 0L,
        /** [version] at which a brand-new row appeared; 0 when nothing was ever inserted. */
        val insertedAt: Long = 0L,
    )

    private val _change = MutableStateFlow(DataChange())
    val change: StateFlow<DataChange> = _change.asStateFlow()

    fun items(kind: Kind): StateFlow<List<Item>> = if (kind == Kind.NOTE) _notes else _todos

    /** Loads both lists. Safe to call repeatedly (e.g. from `onStart`). */
    suspend fun reload() = withContext(dispatcher) {
        val notes = store.list(Kind.NOTE)
        val todos = store.list(Kind.TODO)
        _notes.value = notes
        _todos.value = todos
    }

    /** Blocking read for the widget, which runs on a binder thread and cannot await. */
    fun snapshot(kind: Kind): List<Item> = store.list(kind)

    // ------------------------------------------------------------------ mutations

    /** @return the new id, or `null` when [text] was blank. */
    suspend fun add(kind: Kind, text: String): Long? {
        val clean = normalize(text) ?: return null
        val now = clock()
        val id = withContext(dispatcher) {
            store.insert(Item(kind = kind, text = clean, createdAt = now, updatedAt = now))
        }
        afterWrite(inserted = true)
        return id
    }

    /** @return true when the stored text actually changed. */
    suspend fun updateText(id: Long, text: String): Boolean {
        val clean = normalize(text) ?: return false
        val changed = withContext(dispatcher) {
            val existing = store.findById(id) ?: return@withContext false
            if (existing.text == clean) return@withContext false
            store.update(existing.copy(text = clean, updatedAt = clock()))
        }
        if (changed) afterWrite()
        return changed
    }

    /** @return true when the todo was found and its state changed. */
    suspend fun setDone(id: Long, done: Boolean): Boolean {
        val changed = withContext(dispatcher) {
            val existing = store.findById(id) ?: return@withContext false
            if (existing.kind != Kind.TODO || existing.done == done) return@withContext false
            val now = clock()
            store.update(
                existing.copy(
                    done = done,
                    doneAt = if (done) now else null,
                    updatedAt = now,
                ),
            )
        }
        if (changed) afterWrite()
        return changed
    }

    suspend fun toggleDone(id: Long): Boolean {
        val current = withContext(dispatcher) { store.findById(id)?.done } ?: return false
        return setDone(id, !current)
    }

    suspend fun delete(id: Long): Boolean {
        val removed = withContext(dispatcher) { store.delete(id) }
        if (removed) afterWrite()
        return removed
    }

    /**
     * Idempotent "persist whatever the user has typed so far". This is what the editor calls on
     * every debounced keystroke, so a crash, a swipe-away or a battery pull can never lose a
     * thought. Calling it repeatedly with the same text is free (no write, no version bump).
     *
     * @param id the row being edited, or 0 when the editor is still blank/new.
     * @return the row id holding the text — the same [id] when it already existed, a fresh id
     *   when this call created the row, or 0 when there is nothing worth storing yet.
     */
    suspend fun saveDraft(id: Long, kind: Kind, text: String): Long {
        val clean = normalize(text) ?: return if (id > 0L) id else 0L
        val now = clock()
        var changed = true
        var inserted = false
        val resolved = withContext(dispatcher) {
            val existing = if (id > 0L) store.findById(id) else null
            when {
                existing == null -> {
                    inserted = true
                    store.insert(Item(kind = kind, text = clean, createdAt = now, updatedAt = now))
                }

                existing.text == clean -> {
                    changed = false
                    id
                }

                else -> {
                    store.update(existing.copy(text = clean, updatedAt = now))
                    id
                }
            }
        }
        if (changed) afterWrite(inserted)
        return resolved
    }

    suspend fun findById(id: Long): Item? = withContext(dispatcher) { store.findById(id) }

    fun counts(): Pair<Int, Int> = store.count(Kind.NOTE) to store.count(Kind.TODO)

    // ------------------------------------------------------------------ backup support

    /** Everything, newest first. Used by the backup encoder. */
    suspend fun all(): List<Item> = withContext(dispatcher) { store.listAll() }

    /**
     * Restores a backup.
     *
     * @param replace when true the database is wiped first, otherwise records are merged:
     *   a record whose id already exists is kept only if the local copy is newer.
     */
    suspend fun restore(incoming: List<Item>, replace: Boolean): ImportResult {
        val sanitized = incoming
            .filter { it.text.isNotBlank() }
            .map { it.copy(text = it.text.trim()) }

        val result = withContext(dispatcher) {
            if (replace) {
                store.replaceAll(sanitized)
                ImportResult(total = sanitized.size, inserted = sanitized.size, updated = 0)
            } else {
                var inserted = 0
                var updated = 0
                sanitized.forEach { candidate ->
                    val local = candidate.id.takeIf { it > 0L }?.let { store.findById(it) }
                    when {
                        local == null -> {
                            store.insert(candidate)
                            inserted++
                        }
                        candidate.updatedAt > local.updatedAt -> {
                            store.update(candidate)
                            updated++
                        }
                        // local copy is newer or identical -> nothing to do
                    }
                }
                ImportResult(total = sanitized.size, inserted = inserted, updated = updated)
            }
        }
        afterWrite(inserted = result.inserted > 0)
        return result
    }

    // ------------------------------------------------------------------ internals

    private suspend fun afterWrite(inserted: Boolean = false) {
        reload()
        _change.update { current ->
            val next = current.version + 1L
            current.copy(version = next, insertedAt = if (inserted) next else current.insertedAt)
        }
    }

    companion object {
        /** Guard against a runaway paste turning one note into a multi-megabyte row. */
        const val MAX_TEXT_LENGTH = 20_000

        /** Trim, drop blanks, cap the length. Returns null when there is nothing to store. */
        fun normalize(text: String): String? =
            text.trim().take(MAX_TEXT_LENGTH).ifEmpty { null }
    }
}

data class ImportResult(
    val total: Int,
    val inserted: Int,
    val updated: Int,
)
