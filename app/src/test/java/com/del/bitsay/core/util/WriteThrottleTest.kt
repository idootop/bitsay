package com.del.bitsay.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WriteThrottleTest {

    private val throttle = WriteThrottle(windowMs = 400)

    @Test
    fun `the very first write goes through immediately`() {
        assertEquals(0L, throttle.delayBeforeNextWrite(now = 1_000))
    }

    @Test
    fun `a keystroke right after a write waits out the whole window`() {
        throttle.onWritten(now = 1_000)
        assertEquals(400L, throttle.delayBeforeNextWrite(now = 1_000))
    }

    @Test
    fun `a keystroke mid-window only waits the remainder`() {
        throttle.onWritten(now = 1_000)
        assertEquals(250L, throttle.delayBeforeNextWrite(now = 1_150))
        assertEquals(1L, throttle.delayBeforeNextWrite(now = 1_399))
    }

    @Test
    fun `once the window has passed the write is immediate again`() {
        throttle.onWritten(now = 1_000)
        assertEquals(0L, throttle.delayBeforeNextWrite(now = 1_400))
        assertEquals(0L, throttle.delayBeforeNextWrite(now = 9_999))
    }

    @Test
    fun `reset makes the next write immediate`() {
        throttle.onWritten(now = 1_000)
        throttle.reset()
        assertEquals(0L, throttle.delayBeforeNextWrite(now = 1_000))
    }

    @Test
    fun `a single keystroke is persisted at once`() {
        assertEquals(listOf(0L), writeTimesOf(listOf(0L)))
    }

    @Test
    fun `the first character of a new thought is never held back`() {
        // Two keystrokes 50 ms apart: one write immediately, one trailing writes the final text.
        assertEquals(listOf(0L, 400L), writeTimesOf(listOf(0L, 50L)))
    }

    @Test
    fun `a long burst writes about once per window and always finishes with a trailing write`() {
        val keystrokes = (0L..450L step 50L).toList()

        val writes = writeTimesOf(keystrokes)

        assertEquals(listOf(0L, 400L, 800L), writes)
        // The trailing write lands after the last keystroke, so nothing is left unsaved.
        assertEquals(true, writes.last() > keystrokes.last())
    }

    @Test
    fun `a pause longer than the window makes the next keystroke immediate again`() {
        // 0 and 50 form one burst; 900 is a fresh burst far outside the window.
        assertEquals(listOf(0L, 400L, 900L), writeTimesOf(listOf(0L, 50L, 900L)))
    }

    /**
     * Replays a keystroke schedule exactly the way the ViewModel does: every keystroke cancels
     * the pending write and re-schedules it, and a write that comes due fires before the next
     * keystroke is handled.
     */
    private fun writeTimesOf(keystrokes: List<Long>, windowMs: Long = 400): List<Long> {
        val policy = WriteThrottle(windowMs)
        val writes = mutableListOf<Long>()
        var pendingAt: Long? = null
        for (keystrokeAt in keystrokes) {
            while (pendingAt != null && pendingAt <= keystrokeAt) {
                writes += pendingAt
                policy.onWritten(pendingAt)
                pendingAt = null
            }
            pendingAt = keystrokeAt + policy.delayBeforeNextWrite(keystrokeAt)
        }
        pendingAt?.let { writes += it }
        return writes
    }
}
