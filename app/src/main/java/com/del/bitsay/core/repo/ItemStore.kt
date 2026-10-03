package com.del.bitsay.core.repo

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind

/**
 * Storage contract. The repository (business logic) only ever talks to this interface, which
 * keeps the domain layer free of Android types and lets unit tests run on a plain in-memory fake.
 *
 * **`text` from [list] and [search] is a preview, not the whole note.** A list of 10 000 entries
 * must not pull every full note into memory, and the UI only ever renders one flattened line.
 * Anything that needs the real text — the editor, the backup — goes through [findById] or
 * [listAll], which return untruncated rows.
 */
interface ItemStore {

    /** Items of one [kind], already in display order, with `text` truncated to a preview. */
    fun list(kind: Kind): List<Item>

    /**
     * Full-text search over the *complete* text (not just the preview), newest first.
     *
     * This lives in SQL rather than in the UI: the in-memory list only holds previews, so
     * filtering it would silently miss matches deep inside a long note.
     */
    fun search(kind: Kind, needle: String): List<Item>

    /** Everything in the database with full text, oldest row first (stable export order). */
    fun listAll(): List<Item>

    /** The full row, including the complete text. */
    fun findById(id: Long): Item?

    /** @return the new row id. */
    fun insert(item: Item): Long

    /** @return true when a row was actually changed. */
    fun update(item: Item): Boolean

    /** @return true when a row was actually removed. */
    fun delete(id: Long): Boolean

    /** Bulk delete in one transaction. @return how many rows went away. */
    fun deleteMany(ids: Collection<Long>): Int

    /** Bulk tick/untick in one transaction. @return how many rows changed. */
    fun setDoneMany(ids: Collection<Long>, done: Boolean, now: Long): Int

    /** Wipes the table and inserts [items] verbatim (ids included). */
    fun replaceAll(items: List<Item>)

    fun count(kind: Kind): Int

    companion object {
        /**
         * How much of `text` a list row carries. The widest preview in the app or the widget is
         * about 30 characters, so this is pure headroom; it exists to bound worst-case memory,
         * not to fit the screen.
         */
        const val PREVIEW_CHARS = 200
    }
}
