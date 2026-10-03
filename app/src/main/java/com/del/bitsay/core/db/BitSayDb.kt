package com.del.bitsay.core.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Schema for `bitsay.db`.
 *
 * Version history
 * ---------------
 * 1 — initial: single `items` table holding both notes and todos.
 */
internal class BitSayDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    init {
        // WAL: readers (the widget) never block the writer (the app) and vice versa.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ITEMS)
        db.execSQL(SQL_INDEX_ITEMS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 is the first release; future migrations are appended here.
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // A user restoring an older APK should not crash: keep whatever is readable.
    }

    companion object {
        const val NAME = "bitsay.db"
        const val VERSION = 1

        const val T_ITEMS = "items"
        const val C_ID = "id"
        const val C_KIND = "kind"
        const val C_TEXT = "text"
        const val C_DONE = "done"
        const val C_CREATED_AT = "created_at"
        const val C_UPDATED_AT = "updated_at"
        const val C_DONE_AT = "done_at"

        private const val SQL_CREATE_ITEMS = """
            CREATE TABLE $T_ITEMS (
                $C_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
                $C_KIND        INTEGER NOT NULL,
                $C_TEXT        TEXT    NOT NULL,
                $C_DONE        INTEGER NOT NULL DEFAULT 0,
                $C_CREATED_AT  INTEGER NOT NULL,
                $C_UPDATED_AT  INTEGER NOT NULL,
                $C_DONE_AT     INTEGER
            )
        """

        private const val SQL_INDEX_ITEMS =
            "CREATE INDEX idx_items_kind ON $T_ITEMS ($C_KIND, $C_DONE, $C_UPDATED_AT DESC)"
    }
}
