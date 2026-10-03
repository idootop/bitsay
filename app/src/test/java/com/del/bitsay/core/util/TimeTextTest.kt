package com.del.bitsay.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class TimeTextTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val now = at(2026, 10, 1, 17, 30)

    private val zh = TimeWording(
        justNow = "刚刚",
        minutesAgo = "%1\$d 分钟前",
        today = "今天 %1\$s",
        yesterday = "昨天 %1\$s",
        monthDayPattern = "M月d日",
        yearMonthDayPattern = "yyyy年M月d日",
        fullPattern = "yyyy-MM-dd HH:mm",
    )

    private val en = TimeWording(
        justNow = "Just now",
        minutesAgo = "%1\$d min ago",
        today = "Today %1\$s",
        yesterday = "Yesterday %1\$s",
        monthDayPattern = "MMM d",
        yearMonthDayPattern = "MMM d, yyyy",
        fullPattern = "yyyy-MM-dd HH:mm",
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun zhRelative(timestamp: Long) =
        TimeText.relative(timestamp, zh, now, zone, Locale.SIMPLIFIED_CHINESE)

    private fun enRelative(timestamp: Long) =
        TimeText.relative(timestamp, en, now, zone, Locale.US)

    // ------------------------------------------------------------------ Chinese

    @Test
    fun `under a minute reads as just now`() {
        assertEquals("刚刚", zhRelative(now - 30_000))
    }

    @Test
    fun `under an hour counts minutes`() {
        assertEquals("12 分钟前", zhRelative(now - 12 * 60_000))
        assertEquals("59 分钟前", zhRelative(now - 59 * 60_000))
    }

    @Test
    fun `earlier today shows the clock time`() {
        assertEquals("今天 09:05", zhRelative(at(2026, 10, 1, 9, 5)))
    }

    @Test
    fun `yesterday is labelled`() {
        assertEquals("昨天 23:59", zhRelative(at(2026, 9, 30, 23, 59)))
    }

    @Test
    fun `earlier this year drops the year`() {
        assertEquals("3月8日", zhRelative(at(2026, 3, 8, 12, 0)))
    }

    @Test
    fun `previous years keep the year`() {
        assertEquals("2025年12月31日", zhRelative(at(2025, 12, 31, 12, 0)))
    }

    // ------------------------------------------------------------------ English

    @Test
    fun `english wording is used verbatim`() {
        assertEquals("Just now", enRelative(now - 30_000))
        assertEquals("12 min ago", enRelative(now - 12 * 60_000))
        assertEquals("Today 09:05", enRelative(at(2026, 10, 1, 9, 5)))
        assertEquals("Yesterday 23:59", enRelative(at(2026, 9, 30, 23, 59)))
    }

    @Test
    fun `english month names come from the locale, not from the pattern alone`() {
        // "MMM d" must render as an English month for en-US and a Chinese one for zh-CN.
        assertEquals("Mar 8", enRelative(at(2026, 3, 8, 12, 0)))
        assertEquals("Dec 31, 2025", enRelative(at(2025, 12, 31, 12, 0)))
    }

    // ------------------------------------------------------------------ shared rules

    @Test
    fun `a future timestamp does not produce a negative count`() {
        assertEquals("今天 18:00", zhRelative(at(2026, 10, 1, 18, 0)))
        assertEquals("Today 18:00", enRelative(at(2026, 10, 1, 18, 0)))
    }

    @Test
    fun `zero means unset`() {
        assertEquals("", zhRelative(0L))
        assertEquals("", TimeText.absolute(0L, zh, zone, Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `absolute format is unambiguous in both languages`() {
        val timestamp = at(2026, 10, 1, 9, 5)

        assertEquals("2026-10-01 09:05", TimeText.absolute(timestamp, zh, zone, Locale.SIMPLIFIED_CHINESE))
        assertEquals("2026-10-01 09:05", TimeText.absolute(timestamp, en, zone, Locale.US))
    }

    @Test
    fun `hour boundaries switch from relative to date`() {
        assertEquals("今天 16:30", zhRelative(now - 60 * 60_000))
        assertEquals("59 分钟前", zhRelative(now - 60 * 60_000 + 1))
    }
}
