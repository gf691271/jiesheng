package com.frank.jiesheng

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SplitCompositionFactoryTest {
    @Test
    fun `clip keeps audio and applies the exact interval`() {
        val item = SplitCompositionFactory.audioClip(
            Uri.parse("content://audio/1"),
            SplitSegment(60_000L, 180_000L),
        )

        assertEquals(60_000L, item.mediaItem.clippingConfiguration.startPositionMs)
        assertEquals(180_000L, item.mediaItem.clippingConfiguration.endPositionMs)
        assertTrue(item.removeVideo)
    }

    @Test
    fun `clip rejects an empty or negative interval`() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitCompositionFactory.audioClip(
                Uri.parse("content://audio/1"),
                SplitSegment(60_000L, 60_000L),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SplitCompositionFactory.audioClip(
                Uri.parse("content://audio/1"),
                SplitSegment(-1L, 60_000L),
            )
        }
    }
}
