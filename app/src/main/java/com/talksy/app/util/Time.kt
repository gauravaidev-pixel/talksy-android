package com.talksy.app.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Parse the backend's ISO-8601 UTC timestamps and render short, human labels.
 * Fast on purpose: it runs for every visible row on every recomposition, so parsing is cached and the
 * formatters are created once (the old version allocated a SimpleDateFormat per call).
 */
object Time {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val parsed = ConcurrentHashMap<String, Long>()
    private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun millis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        parsed[value]?.let { return it }
        val ms = runCatching {
            LocalDateTime.parse(value.substringBefore('.').removeSuffix("Z")).toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull() ?: return null
        if (parsed.size > 4000) parsed.clear()
        parsed[value] = ms
        return ms
    }

    fun parse(value: String?): Date? = millis(value)?.let { Date(it) }

    private fun startOfDay(ms: Long): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    private const val DAY = 24L * 60 * 60 * 1000

    fun todayKey(): Long = startOfDay(System.currentTimeMillis())

    fun nowClock(): String = clockFmt.format(Instant.now().atZone(zone))

    /** Stable key for "which calendar day (local time) is this message on". */
    fun dayKey(value: String?): Long = millis(value)?.let { startOfDay(it) } ?: 0L

    /** "Today" / "Yesterday" / "12 October" / "12 October 2025" for date separators. */
    fun dayLabel(dayKey: Long): String {
        if (dayKey == 0L) return ""
        val today = startOfDay(System.currentTimeMillis())
        return when {
            dayKey == today -> "Today"
            dayKey == today - DAY -> "Yesterday"
            else -> {
                val date = Instant.ofEpochMilli(dayKey).atZone(zone).toLocalDate()
                val sameYear = date.year == Instant.now().atZone(zone).year
                DateTimeFormatter.ofPattern(if (sameYear) "d MMMM" else "d MMMM yyyy", Locale.getDefault()).format(date)
            }
        }
    }

    /** Chat-list timestamp: 14:05 today, "Yesterday", weekday this week, else "12 Oct". */
    fun listLabel(value: String?): String {
        val ms = millis(value) ?: return ""
        val today = startOfDay(System.currentTimeMillis())
        val day = startOfDay(ms)
        return when {
            day == today -> clock(value)
            day == today - DAY -> "Yesterday"
            today - day < 7 * DAY ->
                DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))
            else -> DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))
        }
    }

    fun short(value: String?): String = listLabel(value)

    fun clock(value: String?): String {
        val ms = millis(value) ?: return ""
        return clockFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    }

    /** "online" / "last seen just now" / "last seen 14:05" / "last seen yesterday" ... */
    fun lastSeen(online: Boolean, value: String?): String {
        if (online) return "online"
        val ms = millis(value) ?: return "last seen recently"
        val now = System.currentTimeMillis()
        val diff = now - ms
        val today = startOfDay(now)
        return when {
            diff < 60_000L -> "last seen just now"
            diff < 3_600_000L -> "last seen ${diff / 60_000L} min ago"
            startOfDay(ms) == today -> "last seen today at ${clock(value)}"
            startOfDay(ms) == today - DAY -> "last seen yesterday at ${clock(value)}"
            else -> "last seen " + DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))
        }
    }
}

object Initials {
    fun of(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }
}
