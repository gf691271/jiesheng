package com.frank.jiesheng

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ExportNames {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    fun m4a(instant: Instant, zoneId: ZoneId): String {
        return "接声_${formatter.withZone(zoneId).format(instant)}.m4a"
    }
}
