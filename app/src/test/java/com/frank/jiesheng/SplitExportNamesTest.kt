package com.frank.jiesheng

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitExportNamesTest {
    @Test
    fun `names remove only the final extension and use two-digit order`() {
        assertEquals(
            listOf(
                "会议.录音_01.m4a",
                "会议.录音_02.m4a",
                "会议.录音_03.m4a",
            ),
            SplitExportNames.forSource("会议.录音.mp3", 3),
        )
    }

    @Test
    fun `empty source base falls back to audio`() {
        assertEquals(listOf("音频_01.m4a"), SplitExportNames.forSource(".wav", 1))
    }

    @Test
    fun `twenty-one segments end with order twenty-one`() {
        val names = SplitExportNames.forSource("访谈", 21)

        assertEquals("访谈_01.m4a", names.first())
        assertEquals("访谈_21.m4a", names.last())
        assertEquals(21, names.size)
    }
}
