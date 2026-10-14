package com.talksy.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Parse the backend's ISO-8601 UTC timestamps and render short, human labels. */
object Time {
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parse(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.substringBefore('.').removeSuffix("Z")
        return runCatching { iso.parse(cleaned) }.getOrNull()
    }

    fun short(value: String?): String {
        val date = parse(value) ?: return ""
        val now = System.currentTimeMillis()
        val diff = now - date.time
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        return when {
            diff < minute -> "now"
            diff < hour -> "${diff / minute}m"
            diff < day -> "${diff / hour}h"
            diff < 7 * day -> "${diff / day}d"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
        }
    }

    fun clock(value: String?): String {
        val date = parse(value) ?: return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
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
