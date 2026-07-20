package com.frank.jiesheng

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportNamesTest {
    @Test
    fun `M4A name uses local minute in a stable format`() {
        val name = ExportNames.m4a(
            Instant.parse("2026-07-20T22:30:00Z"),
            ZoneId.of("America/Los_Angeles"),
        )

        assertEquals("接声_20260720_1530.m4a", name)
    }
}
