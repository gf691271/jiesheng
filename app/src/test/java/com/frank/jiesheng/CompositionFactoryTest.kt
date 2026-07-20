package com.frank.jiesheng

import android.net.Uri
import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompositionFactoryTest {
    @Test
    fun `audio composition requires at least two inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            CompositionFactory.audioOnly(listOf(Uri.parse("content://audio/1")))
        }
    }

    @Test
    fun `audio composition preserves order and excludes video`() {
        val inputs = listOf(
            Uri.parse("content://audio/1"),
            Uri.parse("content://audio/2"),
            Uri.parse("content://audio/3"),
        )

        val composition = CompositionFactory.audioOnly(inputs)
        val sequence = composition.sequences.single()

        assertEquals(setOf(C.TRACK_TYPE_AUDIO), sequence.trackTypes)
        assertEquals(
            inputs,
            sequence.editedMediaItems.map { it.mediaItem.localConfiguration!!.uri },
        )
        assertFalse(composition.transmuxAudio)
    }
}
