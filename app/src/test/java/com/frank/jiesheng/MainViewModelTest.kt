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
    private val first = SelectedAudio("content://audio/1", "一.m4a", 1_000)
    private val second = SelectedAudio("content://audio/2", "二.mp3", 2_000)
    private val third = SelectedAudio("content://audio/3", "三.wav", 3_000)
    private val fourth = SelectedAudio("content://audio/4", "四.ogg", 4_000)

    @Test
    fun `merge is enabled only with two or three items while idle`() {
        val viewModel = MainViewModel()

        assertFalse(viewModel.state.value.isMergeEnabled)
        viewModel.add(first)
        assertFalse(viewModel.state.value.isMergeEnabled)
        viewModel.add(second)
        assertTrue(viewModel.state.value.isMergeEnabled)
        viewModel.add(third)
        assertTrue(viewModel.state.value.isMergeEnabled)
        viewModel.startExport()
        assertFalse(viewModel.state.value.isMergeEnabled)
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
        viewModel.add(second)
        viewModel.add(third)
        viewModel.add(fourth)

        assertEquals(listOf("这个音频已经添加过了", "最多只能选择 3 个音频"), messages)
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
}
