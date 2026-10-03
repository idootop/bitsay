package com.del.bitsay.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TimeTextTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val now = at(2026, 10, 1, 17, 30)

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    @Test
    fun `under a minute reads as just now`() {
        assertEquals("刚刚", TimeText.relative(now - 30_000, now, zone))
    }

    @Test
    fun `under an hour counts minutes`() {
        assertEquals("12 分钟前", TimeText.relative(now - 12 * 60_000, now, zone))
        assertEquals("59 分钟前", TimeText.relative(now - 59 * 60_000, now, zone))
    }

    @Test
    fun `earlier today shows the clock time`() {
        assertEquals("今天 09:05", TimeText.relative(at(2026, 10, 1, 9, 5), now, zone))
    }

    @Test
    fun `yesterday is labelled`() {
        assertEquals("昨天 23:59", TimeText.relative(at(2026, 9, 30, 23, 59), now, zone))
    }

    @Test
    fun `earlier this year drops the year`() {
        assertEquals("3月8日", TimeText.relative(at(2026, 3, 8, 12, 0), now, zone))
    }

    @Test
    fun `previous years keep the year`() {
        assertEquals("2025年12月31日", TimeText.relative(at(2025, 12, 31, 12, 0), now, zone))
    }

    @Test
    fun `a future timestamp does not produce a negative count`() {
        assertEquals("今天 18:00", TimeText.relative(at(2026, 10, 1, 18, 0), now, zone))
    }

    @Test
    fun `zero means unset`() {
        assertEquals("", TimeText.relative(0L, now, zone))
        assertEquals("", TimeText.absolute(0L, zone))
    }

    @Test
    fun `absolute format is unambiguous`() {
        assertEquals("2026-10-01 09:05", TimeText.absolute(at(2026, 10, 1, 9, 5), zone))
    }

    @Test
    fun `hour boundaries switch from relative to date`() {
        assertEquals("今天 16:30", TimeText.relative(now - 60 * 60_000, now, zone))
        assertEquals("59 分钟前", TimeText.relative(now - 60 * 60_000 + 1, now, zone))
    }
}
