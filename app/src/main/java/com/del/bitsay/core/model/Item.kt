package com.del.bitsay.core.model

/**
 * A note and a todo have exactly the same shape: one blob of plain text plus timestamps.
 * They are therefore stored in a single table with a [Kind] discriminator, which keeps the
 * DAO, the repository, the backup format and the widget query path to one implementation.
 */
data class Item(
    val id: Long = 0L,
    val kind: Kind = Kind.NOTE,
    val text: String = "",
    val done: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val doneAt: Long? = null,
) {
    val isNote: Boolean get() = kind == Kind.NOTE
    val isTodo: Boolean get() = kind == Kind.TODO
}

enum class Kind(val code: Int) {
    NOTE(0),
    TODO(1),
    ;

    val other: Kind get() = if (this == NOTE) TODO else NOTE

    companion object {
        fun ofCode(code: Int): Kind = if (code == TODO.code) TODO else NOTE
    }
}
