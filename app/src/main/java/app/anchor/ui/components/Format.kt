package app.anchor.ui.components

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

fun formatDuration(ms: Long): String {
    val hours = ms / 3_600_000
    if (hours >= 48 && hours % 24 == 0L) return "${hours / 24} days"
    if (hours >= 1) return if (hours == 1L) "1 hour" else "$hours hours"
    val minutes = ms / 60_000
    return if (minutes == 1L) "1 minute" else "$minutes minutes"
}

fun formatTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

fun formatCount(n: Long): String = NumberFormat.getIntegerInstance().format(n)

fun formatCountdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
