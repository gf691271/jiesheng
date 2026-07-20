package com.frank.jiesheng

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentMetadataReaderTest {
    @Test
    fun readsDisplayNameAndDurationFromSelectedAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = copyAssetToCache("tone-440.wav", "metadata-tone.wav")

        val selected = DocumentMetadataReader(context).read(Uri.fromFile(file))

        assertEquals("metadata-tone.wav", selected.name)
        assertEquals(Uri.fromFile(file).toString(), selected.uri)
        assertTrue("unexpected duration: ${selected.durationMs}", abs(selected.durationMs - 400L) < 100L)
    }

    @Test
    fun readsVideoMetadataWhenVideoContainsAnAudioTrack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = copyAssetToCache("tone-video.mp4", "metadata-tone-video.mp4")

        val selected = DocumentMetadataReader(context).read(Uri.fromFile(file), SourceType.VIDEO)

        assertEquals(SourceType.VIDEO, selected.sourceType)
        assertEquals("MP4", selected.formatLabel)
        assertTrue("unexpected duration: ${selected.durationMs}", selected.durationMs > 0)
    }

    @Test
    fun rejectsVideoWithoutAnAudioTrack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = copyAssetToCache("silent-video.mp4", "metadata-silent-video.mp4")

        assertThrows(NoAudioTrackException::class.java) {
            DocumentMetadataReader(context).read(Uri.fromFile(file), SourceType.VIDEO)
        }
    }

    private fun copyAssetToCache(assetName: String, destinationName: String): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = InstrumentationRegistry.getInstrumentation().context
        return File(context.cacheDir, destinationName).also { file ->
            source.assets.open(assetName).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
