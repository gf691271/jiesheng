package com.frank.jiesheng

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentMetadataReaderTest {
    @Test
    fun readsDisplayNameAndDurationFromSelectedAudio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = InstrumentationRegistry.getInstrumentation().context
        val file = File(context.cacheDir, "metadata-tone.wav")
        source.assets.open("tone-440.wav").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }

        val selected = DocumentMetadataReader(context).read(Uri.fromFile(file))

        assertEquals("metadata-tone.wav", selected.name)
        assertEquals(Uri.fromFile(file).toString(), selected.uri)
        assertTrue("unexpected duration: ${selected.durationMs}", abs(selected.durationMs - 400L) < 100L)
    }
}
