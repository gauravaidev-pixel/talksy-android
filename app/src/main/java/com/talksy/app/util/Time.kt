package com.talksy.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Parse the backend's ISO-8601 UTC timestamps and render short, human labels. */
object Time {
    private fun utcFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parse(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.substringBefore('.').removeSuffix("Z")
        return runCatching { utcFormat().parse(cleaned) }.getOrNull()
    }

    private fun startOfDay(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private const val DAY = 24L * 60 * 60 * 1000

    fun todayKey(): Long = startOfDay(System.currentTimeMillis())

    fun nowClock(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    /** Stable key for "which calendar day (local time) is this message on". */
    fun dayKey(value: String?): Long = parse(value)?.let { startOfDay(it.time) } ?: 0L

    /** "Today" / "Yesterday" / "12 October" / "12 October 2025" for date separators. */
    fun dayLabel(dayKey: Long): String {
        if (dayKey == 0L) return ""
        val today = startOfDay(System.currentTimeMillis())
        return when {
            dayKey == today -> "Today"
            dayKey == today - DAY -> "Yesterday"
            else -> {
                val sameYear = Calendar.getInstance().apply { timeInMillis = dayKey }.get(Calendar.YEAR) ==
                    Calendar.getInstance().get(Calendar.YEAR)
                SimpleDateFormat(if (sameYear) "d MMMM" else "d MMMM yyyy", Locale.getDefault()).format(Date(dayKey))
            }
        }
    }

    /** Chat-list timestamp: 14:05 today, "Yesterday", weekday this week, else "12 Oct". */
    fun listLabel(value: String?): String {
        val date = parse(value) ?: return ""
        val today = startOfDay(System.currentTimeMillis())
        val day = startOfDay(date.time)
        return when {
            day == today -> clock(value)
            day == today - DAY -> "Yesterday"
            today - day < 7 * DAY -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
        }
    }

    fun short(value: String?): String = listLabel(value)

    fun clock(value: String?): String {
        val date = parse(value) ?: return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    /** "online" / "last seen just now" / "last seen 14:05" / "last seen yesterday" ... */
    fun lastSeen(online: Boolean, value: String?): String {
        if (online) return "online"
        val date = parse(value) ?: return "last seen recently"
        val diff = System.currentTimeMillis() - date.time
        return when {
            diff < 60_000L -> "last seen just now"
            diff < 3_600_000L -> "last seen ${diff / 60_000L} min ago"
            startOfDay(date.time) == startOfDay(System.currentTimeMillis()) -> "last seen today at ${clock(value)}"
            startOfDay(date.time) == startOfDay(System.currentTimeMillis()) - DAY -> "last seen yesterday at ${clock(value)}"
            else -> "last seen " + SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
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
