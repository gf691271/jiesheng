package com.frank.jiesheng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AudioQueueTest {
    private val first = SelectedAudio("content://audio/1", "一.m4a", 1_000)
    private val second = SelectedAudio("content://audio/2", "二.mp3", 2_000)
    private val third = SelectedAudio("content://audio/3", "三.wav", 3_000)
    private val fourth = SelectedAudio("content://audio/4", "四.ogg", 4_000)

    @Test
    fun `add appends a new audio item`() {
        val result = AudioQueue().add(first) as QueueChange.Updated

        assertEquals(listOf(first), result.queue.items)
    }

    @Test
    fun `add reports duplicate URI without changing queue`() {
        val queue = AudioQueue(listOf(first))

        assertSame(QueueChange.Duplicate, queue.add(first.copy(name = "副本.m4a")))
    }

    @Test
    fun `add reports limit when a fourth distinct item is selected`() {
        val queue = AudioQueue(listOf(first, second, third))

        assertSame(QueueChange.LimitReached, queue.add(fourth))
    }

    @Test
    fun `move operations at list boundaries preserve order`() {
        val queue = AudioQueue(listOf(first, second, third))

        assertEquals(queue, queue.moveUp(0))
        assertEquals(queue, queue.moveDown(2))
    }

    @Test
    fun `valid move swaps only neighboring items`() {
        val queue = AudioQueue(listOf(first, second, third))

        assertEquals(listOf(second, first, third), queue.moveUp(1).items)
        assertEquals(listOf(first, third, second), queue.moveDown(1).items)
    }

    @Test
    fun `remove preserves the order of remaining items`() {
        val queue = AudioQueue(listOf(first, second, third))

        assertEquals(listOf(first, third), queue.remove(second.uri).items)
    }
}
