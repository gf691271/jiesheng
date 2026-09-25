package com.frank.jiesheng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitViewModelTest {
    @Test
    fun `saved cut points and source restore without duplicate input values`() {
        val saved = androidx.lifecycle.SavedStateHandle()
        val original = SplitViewModel(saved)
        val points = (1..20).map { "00:%02d".format(it) }
        original.updatePoints(points)
        original.beginSourceReading()
        original.finishSourceReading(source)
        val restored = SplitViewModel(saved)
        assertEquals(points, restored.pointTexts)
        assertEquals(source, restored.state.value.source)
        assertTrue(restored.state.value.isExportEnabled)
    }

    private val source = SelectedAudio(
        uri = "content://audio/source",
        name = "访谈.mp3",
        durationMs = 300_000L,
        formatLabel = "MP3",
        sourceType = SourceType.AUDIO,
        lastModifiedEpochMs = null,
    )

    @Test
    fun `source reading freezes edits and makes a valid source exportable`() {
        val viewModel = SplitViewModel()

        assertTrue(viewModel.beginSourceReading())
        assertEquals(SplitPhase.ReadingSource, viewModel.state.value.phase)
        assertFalse(viewModel.state.value.areEditsEnabled)
        assertFalse(viewModel.state.value.isExportEnabled)

        viewModel.finishSourceReading(source)

        assertEquals(SplitPhase.Idle, viewModel.state.value.phase)
        assertEquals(source, viewModel.state.value.source)
        assertTrue(viewModel.state.value.areEditsEnabled)
        assertTrue(viewModel.state.value.isExportEnabled)
    }

    @Test
    fun `valid points freeze editing while destination is chosen`() {
        val viewModel = preparedViewModel()

        val result = viewModel.startExport(listOf("03:00", "01:00"))

        assertTrue(result is SplitPlanResult.Valid)
        assertEquals(SplitPhase.ChoosingDestination, viewModel.state.value.phase)
        assertEquals(
            listOf(
                SplitSegment(0L, 60_000L),
                SplitSegment(60_000L, 180_000L),
                SplitSegment(180_000L, 300_000L),
            ),
            viewModel.state.value.segments,
        )
        assertFalse(viewModel.state.value.areEditsEnabled)
        assertFalse(viewModel.state.value.isExportEnabled)
    }

    @Test
    fun `invalid points leave the idle workflow unchanged`() {
        val viewModel = preparedViewModel()

        val result = viewModel.startExport(listOf("00:00", "05:00"))

        assertTrue(result is SplitPlanResult.Invalid)
        assertEquals(SplitPhase.Idle, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.segments.isEmpty())
        assertEquals(source, viewModel.state.value.source)
    }

    @Test
    fun `export without a source returns an actionable validation error`() {
        val result = SplitViewModel().startExport(listOf("01:00", "02:00"))

        assertEquals(
            SplitPlanResult.Invalid(message = "请先选择音频"),
            result,
        )
    }

    @Test
    fun `overall progress includes completed and current segment`() {
        val viewModel = preparedExportViewModel()
        assertTrue(viewModel.beginSplit())

        viewModel.updateProgress(completedSegments = 1, totalSegments = 3, currentPercent = 50)

        assertEquals(SplitPhase.Splitting(50), viewModel.state.value.phase)
        viewModel.updateProgress(completedSegments = 2, totalSegments = 3, currentPercent = 150)
        assertEquals(SplitPhase.Splitting(100), viewModel.state.value.phase)
    }

    @Test
    fun `canceling destination choice restores editable state and clears plan`() {
        val viewModel = preparedExportViewModel()

        viewModel.cancelDestinationChoice()

        assertEquals(SplitPhase.Idle, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.segments.isEmpty())
        assertTrue(viewModel.state.value.areEditsEnabled)
        assertEquals(source, viewModel.state.value.source)
    }

    @Test
    fun `stale source callback cannot replace source after export starts`() {
        val viewModel = preparedExportViewModel()
        val stale = source.copy(uri = "content://audio/stale", name = "过期.mp3")

        assertFalse(viewModel.beginSourceReading())
        viewModel.finishSourceReading(stale)

        assertEquals(source, viewModel.state.value.source)
        assertEquals(SplitPhase.ChoosingDestination, viewModel.state.value.phase)
    }

    @Test
    fun `completion and failure reset to the selected source`() {
        val viewModel = preparedExportViewModel()
        viewModel.beginSplit()
        viewModel.finishSplit(3)
        assertEquals(SplitPhase.Completed(3), viewModel.state.value.phase)

        viewModel.reset()
        assertEquals(SplitPhase.Idle, viewModel.state.value.phase)
        assertEquals(source, viewModel.state.value.source)
        assertTrue(viewModel.state.value.segments.isEmpty())

        viewModel.startExport(listOf("01:00", "03:00"))
        viewModel.beginSplit()
        viewModel.failSplit("空间不足")
        assertEquals(SplitPhase.Failed("空间不足"), viewModel.state.value.phase)

        viewModel.reset()
        assertEquals(SplitPhase.Idle, viewModel.state.value.phase)
        assertEquals(source, viewModel.state.value.source)
        assertTrue(viewModel.state.value.segments.isEmpty())
    }

    private fun preparedViewModel(): SplitViewModel = SplitViewModel().apply {
        beginSourceReading()
        finishSourceReading(source)
    }

    private fun preparedExportViewModel(): SplitViewModel = preparedViewModel().apply {
        startExport(listOf("01:00", "03:00"))
    }
}
