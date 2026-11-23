package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups == 0) {
            "$bytes B"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        }
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatDateTime(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        val sdf = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatDateShort(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatTimeoutLabel(seconds: Int): String = when (seconds) {
        0 -> "Immediately"
        15 -> "After 15 seconds"
        30 -> "After 30 seconds"
        60 -> "After 1 minute"
        300 -> "After 5 minutes"
        else -> "$seconds seconds"
    }
}
