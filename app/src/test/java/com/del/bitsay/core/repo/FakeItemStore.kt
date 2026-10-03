package com.del.bitsay.core.repo

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind

/**
 * In-memory [ItemStore] used by the domain tests. Mirrors the SQLite behaviour that matters:
 * the same display order (`done ASC, created_at DESC, id DESC`), the same stable `id ASC` export
 * order, and the same "list rows carry only a preview of the text" truncation.
 */
class FakeItemStore(seed: List<Item> = emptyList()) : ItemStore {

    private val rows = LinkedHashMap<Long, Item>()
    private var nextId = 1L

    init {
        seed.forEach { insert(it) }
    }

    override fun list(kind: Kind): List<Item> =
        ordered(kind).map(::preview)

    override fun search(kind: Kind, needle: String): List<Item> =
        ordered(kind).filter { it.text.contains(needle, ignoreCase = true) }.map(::preview)

    override fun listAll(): List<Item> = rows.values.sortedBy { it.id }

    override fun findById(id: Long): Item? = rows[id]

    override fun insert(item: Item): Long {
        val id = if (item.id > 0L) item.id else nextId++
        nextId = maxOf(nextId, id + 1)
        rows[id] = item.copy(id = id)
        return id
    }

    override fun update(item: Item): Boolean {
        if (!rows.containsKey(item.id)) return false
        rows[item.id] = item
        return true
    }

    override fun delete(id: Long): Boolean = rows.remove(id) != null

    override fun deleteMany(ids: Collection<Long>): Int = ids.count { rows.remove(it) != null }


    override fun replaceAll(items: List<Item>) {
        rows.clear()
        nextId = 1L
        items.forEach { insert(it) }
    }

    override fun count(kind: Kind): Int = rows.values.count { it.kind == kind }

    fun snapshot(): List<Item> = rows.values.toList()

    private fun ordered(kind: Kind): List<Item> = rows.values
        .filter { it.kind == kind }
        .sortedWith(compareBy({ it.done }, { -it.createdAt }, { -it.id }))

    private fun preview(item: Item): Item =
        if (item.text.length <= ItemStore.PREVIEW_CHARS) {
            item
        } else {
            item.copy(text = item.text.take(ItemStore.PREVIEW_CHARS))
        }
}
