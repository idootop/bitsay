package com.del.bitsay.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The locale-specific bits of relative-time rendering, handed in by the caller.
 *
 * Keeping them out of [TimeText] is what lets the rules below stay pure Kotlin: Android resources
 * cannot be read from a JVM unit test, and a time formatter with a hard-coded language is exactly
 * the kind of thing that quietly survives an i18n pass.
 */
data class TimeWording(
    val justNow: String,
    /** `%1$d` is the number of minutes. */
    val minutesAgo: String,
    /** `%1$s` is a clock time, e.g. `HH:mm`. */
    val today: String,
    /** `%1$s` is a clock time. */
    val yesterday: String,
    val monthDayPattern: String,
    val yearMonthDayPattern: String,
    val fullPattern: String,
)

/**
 * Timestamp rendering. Pure functions with an explicit wording, `now`, [ZoneId] and [Locale], so
 * every rule is testable without a real clock.
 */
object TimeText {

    /** "Just now" · "12 min ago" · "Today 14:03" · "Yesterday 09:20" · "Mar 8" · "Mar 8, 2025" */
    fun relative(
        timestamp: Long,
        wording: TimeWording,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        if (timestamp <= 0L) return ""
        val delta = now - timestamp
        val clock = clock(timestamp, zone, locale)
        if (delta < 0L) return String.format(locale, wording.today, clock)
        if (delta < MINUTE) return wording.justNow
        if (delta < HOUR) return String.format(locale, wording.minutesAgo, delta / MINUTE)

        val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            date == today -> String.format(locale, wording.today, clock)
            date == today.minusDays(1) -> String.format(locale, wording.yesterday, clock)
            date.year == today.year -> format(timestamp, wording.monthDayPattern, zone, locale)
            else -> format(timestamp, wording.yearMonthDayPattern, zone, locale)
        }
    }

    /** "2026-10-01 17:23" — used on the editor footer. */
    fun absolute(
        timestamp: Long,
        wording: TimeWording,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String =
        if (timestamp <= 0L) "" else format(timestamp, wording.fullPattern, zone, locale)

    // ------------------------------------------------------------------ internals

    private fun format(timestamp: Long, pattern: String, zone: ZoneId, locale: Locale): String =
        pattern(pattern, zone, locale).format(Instant.ofEpochMilli(timestamp).atZone(zone))

    private fun clock(timestamp: Long, zone: ZoneId, locale: Locale): String =
        format(timestamp, CLOCK, zone, locale)

    /**
     * Cached per (pattern, zone, locale): building a [DateTimeFormatter] is not cheap and a list
     * asks for one per row. Concurrent because the widget renders on a binder thread.
     */
    private val formatters = ConcurrentHashMap<Triple<String, ZoneId, Locale>, DateTimeFormatter>()

    private fun pattern(pattern: String, zone: ZoneId, locale: Locale): DateTimeFormatter =
        formatters.getOrPut(Triple(pattern, zone, locale)) {
            DateTimeFormatter.ofPattern(pattern, locale).withZone(zone)
        }

    private const val CLOCK = "HH:mm"
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
}
