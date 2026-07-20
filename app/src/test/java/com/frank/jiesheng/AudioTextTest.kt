package com.frank.jiesheng

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTextTest {
    @Test
    fun `labels include source duration and exact local time`() {
        val modified = Instant.parse("2026-07-20T14:36:08Z").toEpochMilli()
        val video = SelectedAudio("u", "clip.mp4", 756_000, "MP4", SourceType.VIDEO, modified)

        assertEquals("MP4（取音频）· 12:36", AudioText.detail(video))
        assertEquals("修改于 2026-07-20 14:36:08", AudioText.modified(video.lastModifiedEpochMs, ZoneId.of("UTC")))
        assertEquals("修改时间未知", AudioText.modified(null, ZoneId.of("UTC")))
    }
}
