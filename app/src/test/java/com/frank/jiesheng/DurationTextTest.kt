package com.frank.jiesheng

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTextTest {
    @Test
    fun `formats short and hour-long durations`() {
        assertEquals("0:00", DurationText.format(0))
        assertEquals("1:05", DurationText.format(65_000))
        assertEquals("1:00:00", DurationText.format(3_600_000))
    }
}
