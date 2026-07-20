package com.frank.jiesheng

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
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
    fun preservesMediaStoreDisplayNameWhenDocumentColumnsAreUnsupported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedName = "完整可读文件名-${UUID.randomUUID().toString().take(8)}.wav"
        val uri = insertMediaStoreAudio(expectedName)

        try {
            val selected = DocumentMetadataReader(context).read(uri)

            assertEquals(expectedName, selected.name)
            assertEquals("WAV", selected.formatLabel)
            assertTrue("missing MediaStore modification time", selected.lastModifiedEpochMs != null)
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
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

    private fun insertMediaStoreAudio(displayName: String): Uri {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = requireNotNull(
            context.contentResolver.insert(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/JieshengMetadataTest/")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                },
            ),
        )
        InstrumentationRegistry.getInstrumentation().context.assets.open("tone-440.wav").use { input ->
            context.contentResolver.openOutputStream(uri).use { output ->
                requireNotNull(output)
                input.copyTo(output)
            }
        }
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
            null,
            null,
        )
        return uri
    }
}
