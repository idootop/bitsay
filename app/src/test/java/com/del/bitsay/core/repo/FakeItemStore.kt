package com.del.bitsay.core.repo

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind

/**
 * In-memory [ItemStore] used by the domain tests. Mirrors the SQLite ordering exactly
 * (`done ASC, created_at DESC, id DESC`) so tests exercise the same list ordering the app shows.
 */
class FakeItemStore(seed: List<Item> = emptyList()) : ItemStore {

    private val rows = LinkedHashMap<Long, Item>()
    private var nextId = 1L

    init {
        seed.forEach { insert(it) }
    }

    override fun list(kind: Kind): List<Item> = rows.values
        .filter { it.kind == kind }
        .sortedWith(compareBy({ it.done }, { -it.createdAt }, { -it.id }))

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

    override fun replaceAll(items: List<Item>) {
        rows.clear()
        nextId = 1L
        items.forEach { insert(it) }
    }

    override fun count(kind: Kind): Int = rows.values.count { it.kind == kind }

    fun snapshot(): List<Item> = rows.values.toList()
}
