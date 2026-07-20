package com.frank.jiesheng

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val first = item(1)
    private val second = item(2)
    private val third = item(3)
    private val fourth = item(4)

    private fun item(id: Int) = SelectedAudio(
        "content://audio/$id", "$id.m4a", 1_000, "M4A", SourceType.AUDIO, id.toLong(),
    )

    @Test
    fun `merge is enabled with at least two items while idle`() {
        val viewModel = MainViewModel()

        assertFalse(viewModel.state.value.isMergeEnabled)
        viewModel.add(first)
        assertFalse(viewModel.state.value.isMergeEnabled)
        viewModel.add(second)
        assertTrue(viewModel.state.value.isMergeEnabled)
        viewModel.add(third)
        assertTrue(viewModel.state.value.isMergeEnabled)
        assertTrue(viewModel.startExport())
        assertFalse(viewModel.state.value.isMergeEnabled)
    }

    @Test
    fun `source entrances remain enabled at capacity while idle`() {
        val fullQueue = AudioQueue((1..20).map(::item))

        assertTrue(MainUiState(queue = fullQueue).areSourcesEnabled)
        assertFalse(
            MainUiState(
                queue = fullQueue,
                phase = MergePhase.ChoosingDestination,
            ).areSourcesEnabled,
        )
    }

    @Test
    fun `duplicate and limit selections emit Chinese messages`() = runTest {
        val viewModel = MainViewModel()
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.take(2).toList(messages)
        }

        viewModel.add(first)
        viewModel.add(first.copy(name = "副本.m4a"))
        viewModel.addAll((2..20).map(::item))
        viewModel.add(item(21))

        assertEquals(listOf("这个音频已经添加过了", "最多只能选择 20 个音频"), messages)
    }

    @Test
    fun `export transitions preserve selected order`() {
        val viewModel = MainViewModel()
        viewModel.add(first)
        viewModel.add(second)
        val expected = viewModel.state.value.queue

        viewModel.startExport()
        assertEquals(MergePhase.ChoosingDestination, viewModel.state.value.phase)
        viewModel.beginMerge()
        assertEquals(MergePhase.Merging(0), viewModel.state.value.phase)
        viewModel.updateProgress(42)
        assertEquals(MergePhase.Merging(42), viewModel.state.value.phase)
        viewModel.finishExport("完成.m4a")
        assertEquals(MergePhase.Completed("完成.m4a"), viewModel.state.value.phase)
        viewModel.failExport("空间不足")
        assertEquals(MergePhase.Failed("空间不足"), viewModel.state.value.phase)
        viewModel.cancelExport()
        assertEquals(MergePhase.Idle, viewModel.state.value.phase)
        assertEquals(expected, viewModel.state.value.queue)
    }

    @Test
    fun `reordering and removal update queue without changing audio data`() {
        val viewModel = MainViewModel()
        viewModel.add(first)
        viewModel.add(second)
        viewModel.add(third)

        viewModel.moveUp(2)
        assertEquals(listOf(first, third, second), viewModel.state.value.queue.items)
        viewModel.moveDown(0)
        assertEquals(listOf(third, first, second), viewModel.state.value.queue.items)
        viewModel.remove(first.uri)
        assertEquals(listOf(third, second), viewModel.state.value.queue.items)
    }

    @Test
    fun `batch over limit leaves existing selection untouched`() = runTest {
        val viewModel = MainViewModel()
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.messages.take(1).toList(messages)
        }
        viewModel.addAll((1..19).map(::item))

        viewModel.addAll(listOf(item(20), item(21)))

        assertEquals(19, viewModel.state.value.queue.items.size)
        assertEquals(listOf("最多只能选择 20 个音频"), messages)
    }

    @Test
    fun `two item queue cannot export while reading sources and is mergeable after completion`() {
        val viewModel = MainViewModel()
        viewModel.addAll(listOf(first, second))

        assertTrue(viewModel.beginSourceReading(1))
        assertFalse(viewModel.state.value.areSourcesEnabled)
        assertFalse(viewModel.state.value.areQueueEditsEnabled)
        assertFalse(viewModel.state.value.isMergeEnabled)

        assertFalse(viewModel.startExport())
        viewModel.moveUp(1)

        assertEquals(MergePhase.ReadingSources, viewModel.state.value.phase)
        assertEquals(listOf(first, second), viewModel.state.value.queue.items)

        viewModel.finishSourceReading(listOf(third))

        assertEquals(MergePhase.Idle, viewModel.state.value.phase)
        assertTrue(viewModel.state.value.isMergeEnabled)
        assertEquals(listOf(first, second, third), viewModel.state.value.queue.items)
    }

    @Test
    fun `stale source callback cannot change queue during export`() {
        val viewModel = MainViewModel()
        viewModel.addAll(listOf(first, second))
        viewModel.startExport()

        viewModel.add(third)
        viewModel.addAll(listOf(third, fourth))
        viewModel.finishSourceReading(listOf(third))
        viewModel.remove(first.uri)

        assertEquals(MergePhase.ChoosingDestination, viewModel.state.value.phase)
        assertEquals(listOf(first, second), viewModel.state.value.queue.items)
    }
}
