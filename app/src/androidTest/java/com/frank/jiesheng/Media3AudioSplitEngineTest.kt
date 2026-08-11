package com.frank.jiesheng

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3AudioSplitEngineTest {
    @Test
    fun splitCreatesSeparateSingleTrackClipsWithRequestedDurations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val source = copyAsset(testContext, context, "tone-440.wav")

        val first = exportClip(context, source, SplitSegment(0L, 200L), "split-first.m4a")
        val second = exportClip(context, source, SplitSegment(200L, 400L), "split-second.m4a")

        assertSingleAudioTrackNear(first, 200_000L)
        assertSingleAudioTrackNear(second, 200_000L)
        assertTrue(source.isFile)
        assertEquals(35_358L, source.length())
    }

    private fun exportClip(
        context: Context,
        source: File,
        segment: SplitSegment,
        outputName: String,
    ): File {
        val output = File(context.cacheDir, outputName).apply { delete() }
        val completion = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val engine = Media3AudioSplitEngine(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            engine.split(
                Uri.fromFile(source),
                segment,
                output,
                object : SplitListener {
                    override fun onProgress(percent: Int) = Unit

                    override fun onCompleted() {
                        completion.countDown()
                    }

                    override fun onError(error: Throwable) {
                        failure.set(error)
                        completion.countDown()
                    }
                },
            )
        }
        assertTrue("split timed out", completion.await(30, TimeUnit.SECONDS))
        assertNull(failure.get()?.stackTraceToString(), failure.get())
        assertTrue(output.isFile)
        return output
    }

    private fun assertSingleAudioTrackNear(file: File, expectedDurationUs: Long) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val audioDurations = (0 until extractor.trackCount).mapNotNull { index ->
            extractor.getTrackFormat(index).takeIf { format ->
                format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }?.getLong(MediaFormat.KEY_DURATION)
        }
        extractor.release()

        assertEquals(1, audioDurations.size)
        assertTrue(
            "unexpected duration: ${audioDurations.single()}",
            abs(audioDurations.single() - expectedDurationUs) < 160_000L,
        )
    }

    private fun copyAsset(sourceContext: Context, destinationContext: Context, name: String): File {
        val destination = File(destinationContext.cacheDir, name)
        sourceContext.assets.open(name).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }
}
