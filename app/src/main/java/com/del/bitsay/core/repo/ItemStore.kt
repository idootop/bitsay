package com.del.bitsay.core.repo

import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind

/**
 * Storage contract. The repository (business logic) only ever talks to this interface, which
 * keeps the domain layer free of Android types and lets unit tests run on a plain in-memory fake.
 */
interface ItemStore {

    /** Items of one [kind], already in display order. */
    fun list(kind: Kind): List<Item>

    /** Everything in the database, newest first. */
    fun listAll(): List<Item>

    fun findById(id: Long): Item?

    /** @return the new row id. */
    fun insert(item: Item): Long

    /** @return true when a row was actually changed. */
    fun update(item: Item): Boolean

    /** @return true when a row was actually removed. */
    fun delete(id: Long): Boolean

    /** Wipes the table and inserts [items] verbatim (ids included). */
    fun replaceAll(items: List<Item>)

    fun count(kind: Kind): Int
}
