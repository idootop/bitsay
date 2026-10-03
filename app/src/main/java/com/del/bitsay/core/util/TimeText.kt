package com.del.bitsay.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Timestamp rendering. Pure functions with an explicit `now` / [ZoneId] so they are unit
 * testable without freezing the system clock.
 */
object TimeText {

    private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")
    private val monthDay = DateTimeFormatter.ofPattern("M月d日")
    private val yearMonthDay = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val full = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** "刚刚" · "12 分钟前" · "今天 14:03" · "昨天 09:20" · "3月8日" · "2025年3月8日" */
    fun relative(
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (timestamp <= 0L) return ""
        val delta = now - timestamp
        if (delta < 0L) return format(timestamp, "今天 HH:mm", zone)
        if (delta < MINUTE) return "刚刚"
        if (delta < HOUR) return "${delta / MINUTE} 分钟前"

        val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = hourMinute.format(Instant.ofEpochMilli(timestamp).atZone(zone))
        return when {
            date == today -> "今天 $time"
            date == today.minusDays(1) -> "昨天 $time"
            date.year == today.year -> monthDay.format(date)
            else -> yearMonthDay.format(date)
        }
    }

    /** "2026-10-01 17:23" — used on the detail screen. */
    fun absolute(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        if (timestamp <= 0L) "" else full.format(Instant.ofEpochMilli(timestamp).atZone(zone))

    fun dateOnly(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    private fun format(timestamp: Long, pattern: String, zone: ZoneId): String =
        DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(timestamp).atZone(zone))

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
}
