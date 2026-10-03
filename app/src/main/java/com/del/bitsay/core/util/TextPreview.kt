package com.del.bitsay.core.util

/**
 * Flattens an entry into the single line every list preview shows — both the app's card list and
 * the home-screen widget. Newlines become spaces so a multi-line note previews as much of its
 * opening as fits, instead of being cut off at the first line break.
 */
object TextPreview {

    /**
     * Java's `\s` is ASCII-only, which would leave a Chinese full-width space (U+3000) or a
     * non-breaking space in the middle of the row. This is the Unicode White_Space set instead.
     */
    private val WHITESPACE =
        Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+")

    fun singleLine(text: String): String = text.replace(WHITESPACE, " ").trim()
}
