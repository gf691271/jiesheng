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
class Media3AudioMergeEngineTest {
    @Test
    fun mergeCreatesOneAudioTrackWithCombinedDuration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val first = copyAsset(testContext, context, "tone-440.wav")
        val second = copyAsset(testContext, context, "tone-660.m4a")
        val third = copyAsset(testContext, context, "tone-880.mp3")
        val output = File(context.cacheDir, "merged-test.m4a").apply { delete() }
        val completion = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val engine = Media3AudioMergeEngine(context)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            engine.merge(
                listOf(Uri.fromFile(first), Uri.fromFile(second), Uri.fromFile(third)),
                output,
                object : MergeListener {
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

        assertTrue("merge timed out", completion.await(30, TimeUnit.SECONDS))
        assertNull(failure.get()?.stackTraceToString(), failure.get())
        assertTrue(output.isFile)

        val extractor = MediaExtractor()
        extractor.setDataSource(output.absolutePath)
        var audioTrackCount = 0
        var durationUs = 0L
        repeat(extractor.trackCount) { index ->
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackCount += 1
                durationUs = format.getLong(MediaFormat.KEY_DURATION)
            }
        }
        extractor.release()

        assertEquals(1, audioTrackCount)
        assertTrue("unexpected duration: $durationUs", abs(durationUs - 1_200_000L) < 350_000L)
    }

    @Test
    fun mergeExtractsVideoAudioAndCombinesItWithM4a() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val video = copyAsset(testContext, context, "tone-video.mp4")
        val audio = copyAsset(testContext, context, "tone-660.m4a")
        val output = File(context.cacheDir, "merged-video-test.m4a").apply { delete() }
        val completion = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val engine = Media3AudioMergeEngine(context)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            engine.merge(
                listOf(Uri.fromFile(video), Uri.fromFile(audio)),
                output,
                object : MergeListener {
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

        assertTrue("merge timed out", completion.await(30, TimeUnit.SECONDS))
        assertNull(failure.get()?.stackTraceToString(), failure.get())
        assertTrue(output.isFile)

        val extractor = MediaExtractor()
        extractor.setDataSource(output.absolutePath)
        val audioDurations = (0 until extractor.trackCount).mapNotNull { index ->
            extractor.getTrackFormat(index).takeIf { format ->
                format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }?.getLong(MediaFormat.KEY_DURATION)
        }
        extractor.release()

        assertEquals(1, audioDurations.size)
        assertTrue(
            "unexpected duration: ${audioDurations.single()}",
            abs(audioDurations.single() - 1_000_000L) < 350_000L,
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
