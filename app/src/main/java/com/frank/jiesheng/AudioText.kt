package com.frank.jiesheng

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AudioText {
    fun detail(item: SelectedAudio): String =
        "${item.formatLabel}${if (item.sourceType == SourceType.VIDEO) "（取音频）" else ""}· ${DurationText.format(item.durationMs)}"

    fun modified(epochMs: Long?, zoneId: ZoneId): String {
        if (epochMs == null) return "修改时间未知"
        val time = Instant.ofEpochMilli(epochMs).atZone(zoneId)
        return "修改于 ${MODIFIED_FORMATTER.format(time)}"
    }

    private val MODIFIED_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
}
