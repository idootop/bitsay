package com.del.bitsay.core.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.del.bitsay.core.model.Item
import com.del.bitsay.core.model.Kind
import com.del.bitsay.core.repo.ItemStore

/**
 * SQLite implementation of [ItemStore]. Deliberately hand written instead of Room:
 * two columns of business data do not justify an annotation processor, and this keeps the
 * APK free of `room-runtime` + `sqlite-framework` (~1 MB) and the build free of KSP.
 *
 * Every method blocks — callers are responsible for being on a background thread.
 */
class SqliteItemStore(context: Context) : ItemStore {

    private val helper = BitSayDb(context)

    private val db get() = helper.writableDatabase

    override fun list(kind: Kind): List<Item> = query(
        selection = "${BitSayDb.C_KIND} = ?",
        selectionArgs = arrayOf(kind.code.toString()),
        // Sorted by CREATION time, not update time: editing a note (or ticking a todo) must not
        // make the list jump around under the user's finger. Undone todos still sink to the
        // bottom, and `id` breaks ties when two rows share a millisecond.
        orderBy = "${BitSayDb.C_DONE} ASC, ${BitSayDb.C_CREATED_AT} DESC, ${BitSayDb.C_ID} DESC",
    )

    override fun listAll(): List<Item> = query(
        // `id ASC` keeps exports deterministic: two backups of unchanged data are byte-identical.
        orderBy = "${BitSayDb.C_ID} ASC",
    )

    override fun findById(id: Long): Item? =
        query(selection = "${BitSayDb.C_ID} = ?", selectionArgs = arrayOf(id.toString()), limit = "1")
            .firstOrNull()

    override fun insert(item: Item): Long {
        val values = item.toValues(withId = item.id > 0L)
        return db.insert(BitSayDb.T_ITEMS, null, values)
    }

    override fun update(item: Item): Boolean {
        val values = item.toValues(withId = false)
        val rows = db.update(
            BitSayDb.T_ITEMS,
            values,
            "${BitSayDb.C_ID} = ?",
            arrayOf(item.id.toString()),
        )
        return rows > 0
    }

    override fun delete(id: Long): Boolean =
        db.delete(BitSayDb.T_ITEMS, "${BitSayDb.C_ID} = ?", arrayOf(id.toString())) > 0

    override fun replaceAll(items: List<Item>) {
        db.beginTransaction()
        try {
            db.delete(BitSayDb.T_ITEMS, null, null)
            items.forEach { db.insert(BitSayDb.T_ITEMS, null, it.toValues(withId = it.id > 0L)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun count(kind: Kind): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM ${BitSayDb.T_ITEMS} WHERE ${BitSayDb.C_KIND} = ?",
            arrayOf(kind.code.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ---------------------------------------------------------------- internals

    private fun query(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        orderBy: String? = null,
        limit: String? = null,
    ): List<Item> {
        val cursor = db.query(
            BitSayDb.T_ITEMS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
            limit,
        )
        return cursor.use { c ->
            val out = ArrayList<Item>(c.count)
            while (c.moveToNext()) out.add(c.toItem())
            out
        }
    }

    private fun Cursor.toItem(): Item {
        val doneAtIndex = getColumnIndexOrThrow(BitSayDb.C_DONE_AT)
        return Item(
            id = getLong(getColumnIndexOrThrow(BitSayDb.C_ID)),
            kind = Kind.ofCode(getInt(getColumnIndexOrThrow(BitSayDb.C_KIND))),
            text = getString(getColumnIndexOrThrow(BitSayDb.C_TEXT)),
            done = getInt(getColumnIndexOrThrow(BitSayDb.C_DONE)) != 0,
            createdAt = getLong(getColumnIndexOrThrow(BitSayDb.C_CREATED_AT)),
            updatedAt = getLong(getColumnIndexOrThrow(BitSayDb.C_UPDATED_AT)),
            doneAt = if (isNull(doneAtIndex)) null else getLong(doneAtIndex),
        )
    }

    private fun Item.toValues(withId: Boolean): ContentValues = ContentValues(7).apply {
        if (withId) put(BitSayDb.C_ID, id)
        put(BitSayDb.C_KIND, kind.code)
        put(BitSayDb.C_TEXT, text)
        put(BitSayDb.C_DONE, if (done) 1 else 0)
        put(BitSayDb.C_CREATED_AT, createdAt)
        put(BitSayDb.C_UPDATED_AT, updatedAt)
        if (doneAt == null) putNull(BitSayDb.C_DONE_AT) else put(BitSayDb.C_DONE_AT, doneAt)
    }
}
