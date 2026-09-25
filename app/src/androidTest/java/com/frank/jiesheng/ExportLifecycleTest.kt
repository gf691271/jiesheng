package com.frank.jiesheng

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportLifecycleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var source: SelectedAudio

    @Before fun prepare() {
        val input = File(context.cacheDir, "export-lifecycle.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("tone-440.wav").use { stream ->
            input.outputStream().use { stream.copyTo(it) }
        }
        source = SelectedAudio(Uri.fromFile(input).toString(), "lifecycle.wav", 400L, "WAV", SourceType.AUDIO, null)
        ExportFixtureProvider.entered = CountDownLatch(1)
        ExportFixtureProvider.release = CountDownLatch(1)
        ExportFixtureProvider.refuseDelete = false
    }

    @After fun cleanup() {
        ExportFixtureProvider.release?.countDown()
        ExportFixtureProvider.entered = null
        ExportFixtureProvider.release = null
        ExportFixtureProvider.refuseDelete = false
        File(context.cacheDir, "export-fixture").listFiles()?.forEach { it.delete() }
    }

    @Test fun mergeContinuesAcrossRecreationAndNewPageReceivesCompletion() = mergeRecreation(cancel = false)
    @Test fun mergeCancelAfterRecreationWaitsForWriterAndBlocksNewExport() = mergeRecreation(cancel = true)

    private fun mergeRecreation(cancel: Boolean) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: MainViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity)[MainViewModel::class.java]
                model.addAll(listOf(source, source.copy(uri = source.uri + "?second", name = "second.wav")))
                // The queue needs distinct URIs, both resolve to the same fixture file.
                assertTrue(model.startExport())
                val destination = DocumentsContract.buildDocumentUri(ExportFixtureProvider.AUTHORITY, "merge.m4a")
                File(context.cacheDir, "export-fixture").mkdirs()
                File(context.cacheDir, "export-fixture/merge.m4a").createNewFile()
                model.exportTo(context, destination)
            }
            assertTrue("writer never started", ExportFixtureProvider.entered!!.await(15, TimeUnit.SECONDS))
            scenario.recreate()
            scenario.onActivity { activity ->
                assertSame(model, ViewModelProvider(activity)[MainViewModel::class.java])
                assertTrue(model.state.value.phase is MergePhase.Merging)
                if (cancel) {
                    model.stopExport()
                    assertEquals(MergePhase.Stopping, model.state.value.phase)
                    assertFalse(model.startExport())
                }
            }
            ExportFixtureProvider.release!!.countDown()
            await { model.state.value.phase == MergePhase.Idle }
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TextView>(R.id.resultText).text.startsWith(if (cancel) "已取消" else "已保存"))
                val output = File(context.cacheDir, "export-fixture/merge.m4a")
                if (cancel) assertFalse(output.exists()) else assertTrue(output.length() > 0)
            }
        }
    }

    @Test fun splitContinuesAcrossRecreation() = splitRecreation(cancel = false)
    @Test fun splitCancelAfterRecreationReportsUndeletableFile() = splitRecreation(cancel = true)

    private fun splitRecreation(cancel: Boolean) {
        ActivityScenario.launch(SplitActivity::class.java).use { scenario ->
            lateinit var model: SplitViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity)[SplitViewModel::class.java]
                model.beginSourceReading()
                // Test plan needs whole-second points; the short codec fixture is repeated via a longer WAV asset.
                val input = File(context.cacheDir, "export-lifecycle-long.wav")
                InstrumentationRegistry.getInstrumentation().context.assets.open("audit-12s.wav").use { stream ->
                    input.outputStream().use { stream.copyTo(it) }
                }
                model.finishSourceReading(source.copy(uri = Uri.fromFile(input).toString(), durationMs = 12_000))
                model.updatePoints(listOf("00:04", "00:08"))
                assertTrue(model.startExport(model.pointTexts) is SplitPlanResult.Valid)
                model.exportTo(context, DocumentsContract.buildTreeDocumentUri(ExportFixtureProvider.AUTHORITY, "root"))
            }
            assertTrue("writer never started", ExportFixtureProvider.entered!!.await(15, TimeUnit.SECONDS))
            scenario.recreate()
            scenario.onActivity { activity ->
                assertSame(model, ViewModelProvider(activity)[SplitViewModel::class.java])
                assertTrue(model.state.value.phase is SplitPhase.Splitting)
                if (cancel) {
                    ExportFixtureProvider.refuseDelete = true
                    model.stopExport()
                    assertEquals(SplitPhase.Stopping, model.state.value.phase)
                    assertFalse(model.state.value.isExportEnabled)
                }
            }
            ExportFixtureProvider.release!!.countDown()
            await { model.state.value.phase == SplitPhase.Idle }
            scenario.onActivity { activity ->
                val message = activity.findViewById<TextView>(R.id.splitResultText).text.toString()
                assertTrue(message, message.contains(if (cancel) "1 个文件未能删除" else "已保存 3 段音频"))
                if (cancel) assertTrue(message.contains("lifecycle_01.m4a"))
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue("export did not finish", condition())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
