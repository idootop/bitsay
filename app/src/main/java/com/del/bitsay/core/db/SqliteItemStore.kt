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
        orderBy = DISPLAY_ORDER,
        // Only a prefix of the text ever reaches the screen; pulling whole notes here is what
        // would make a large database blow up the app's heap.
        columns = PREVIEW_COLUMNS,
    )

    override fun search(kind: Kind, needle: String): List<Item> = query(
        // LIKE against the real column, not the truncated preview: a match deep inside a long
        // note must still be found.
        selection = "${BitSayDb.C_KIND} = ? AND ${BitSayDb.C_TEXT} LIKE ? ESCAPE '\\'",
        selectionArgs = arrayOf(kind.code.toString(), "%${escapeLike(needle)}%"),
        orderBy = DISPLAY_ORDER,
        columns = PREVIEW_COLUMNS,
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

    override fun deleteMany(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        val chunk = ids.toList()
        return db.inTransaction {
            // One statement per chunk of ids: SQLITE_MAX_VARIABLE_NUMBER is 999 on older builds,
            // so a "select all 10 000 rows" batch has to be split.
            chunk.chunked(SQL_VARIABLE_LIMIT).sumOf { part ->
                db.delete(BitSayDb.T_ITEMS, idInClause(part), idArgs(part))
            }
        }
    }


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
        columns: Array<String>? = null,
    ): List<Item> {
        val cursor = db.query(
            BitSayDb.T_ITEMS,
            columns,
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

    private fun <T> android.database.sqlite.SQLiteDatabase.inTransaction(body: () -> T): T {
        beginTransaction()
        return try {
            val result = body()
            setTransactionSuccessful()
            result
        } finally {
            endTransaction()
        }
    }

    private fun idInClause(ids: List<Long>): String =
        "${BitSayDb.C_ID} IN (${ids.joinToString(",") { "?" }})"

    private fun idArgs(ids: List<Long>): Array<String> = Array(ids.size) { ids[it].toString() }

    private fun Item.toValues(withId: Boolean): ContentValues = ContentValues(7).apply {
        if (withId) put(BitSayDb.C_ID, id)
        put(BitSayDb.C_KIND, kind.code)
        put(BitSayDb.C_TEXT, text)
        put(BitSayDb.C_DONE, if (done) 1 else 0)
        put(BitSayDb.C_CREATED_AT, createdAt)
        put(BitSayDb.C_UPDATED_AT, updatedAt)
        if (doneAt == null) putNull(BitSayDb.C_DONE_AT) else put(BitSayDb.C_DONE_AT, doneAt)
    }

    private companion object {
        /** Sorted by CREATION time: editing a note must not make the list jump under the finger. */
        const val DISPLAY_ORDER =
            "${BitSayDb.C_DONE} ASC, ${BitSayDb.C_CREATED_AT} DESC, ${BitSayDb.C_ID} DESC"

        /** Everything except the bulk of the text, which only the editor and the backup need. */
        val PREVIEW_COLUMNS = arrayOf(
            BitSayDb.C_ID,
            BitSayDb.C_KIND,
            "substr(${BitSayDb.C_TEXT}, 1, ${ItemStore.PREVIEW_CHARS}) AS ${BitSayDb.C_TEXT}",
            BitSayDb.C_DONE,
            BitSayDb.C_CREATED_AT,
            BitSayDb.C_UPDATED_AT,
            BitSayDb.C_DONE_AT,
        )

        /** SQLite's default SQLITE_MAX_VARIABLE_NUMBER is 999 on older Android builds. */
        const val SQL_VARIABLE_LIMIT = 500

        /**
         * Neutralises LIKE wildcards in user input, so searching for "50%" or "a_b" looks for
         * those literal characters instead of turning into a match-everything pattern.
         */
        fun escapeLike(needle: String): String = needle
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}
