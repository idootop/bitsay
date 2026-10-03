package com.del.bitsay.core.util

/**
 * Leading-edge + trailing throttle, used by the editor's auto-save.
 *
 * Plain debounce ("write 600 ms after the last keystroke") has two problems: the very first
 * character of a new thought sits in memory for 600 ms, and continuous typing never writes at
 * all until the user stops. This policy instead:
 *
 *  * writes the **first** event immediately (leading edge) — a new note reaches the database on
 *    the first keypress;
 *  * then allows at most one write per [windowMs];
 *  * and because every event re-schedules, the **last** event of a burst always gets its own
 *    write (trailing edge) — the final text is never left unsaved.
 *
 * Worst case a keystroke waits [windowMs]; best case (the first one) it waits nothing.
 *
 * Pure Kotlin with an explicit `now`, so the timing policy is unit tested without a real clock.
 */
class WriteThrottle(private val windowMs: Long) {

    private var lastWriteAt: Long? = null

    /** @return milliseconds the caller should wait before writing; `0` means "write right now". */
    fun delayBeforeNextWrite(now: Long): Long {
        val last = lastWriteAt ?: return 0L
        return (last + windowMs - now).coerceAtLeast(0L)
    }

    /** Call immediately after a write actually happened. */
    fun onWritten(now: Long) {
        lastWriteAt = now
    }

    /** Forget the history — e.g. when a different entry is opened in the editor. */
    fun reset() {
        lastWriteAt = null
    }
}
