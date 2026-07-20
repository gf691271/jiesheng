package com.frank.jiesheng

import java.util.Locale

object DurationText {
    fun format(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0) / 1_000).toInt()
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }
}
