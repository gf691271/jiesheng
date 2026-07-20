package com.frank.jiesheng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AudioQueueTest {
    private val first = item(1, 1_000)
    private val second = item(2, 2_000)
    private val third = item(3, 3_000)
    private val fourth = item(4, 4_000)

    private fun item(id: Int, modified: Long?) = SelectedAudio(
        "content://audio/$id", "$id.m4a", 1_000, "M4A", SourceType.AUDIO, modified,
    )

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
    fun `new items sort oldest first with unknown dates last`() {
        val result = AudioQueue().addAll(listOf(item(3, null), item(2, 2_000), item(1, 1_000)))
            as QueueChange.Updated

        assertEquals(listOf("1.m4a", "2.m4a", "3.m4a"), result.queue.items.map { it.name })
    }

    @Test
    fun `twentieth item is accepted and twenty first is rejected atomically`() {
        val full = (1..20).map { item(it, it.toLong()) }

        assertEquals(20, (AudioQueue().addAll(full) as QueueChange.Updated).queue.items.size)
        assertSame(QueueChange.LimitReached, AudioQueue(full).add(item(21, 21)))
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

    @Test
    fun `batch over limit is rejected without a partial update`() {
        val queue = AudioQueue((1..19).map { item(it, it.toLong()) })

        assertSame(QueueChange.LimitReached, queue.addAll(listOf(item(20, 20), item(21, 21))))
        assertEquals(19, queue.items.size)
    }

    @Test
    fun `batch ignores duplicate URIs and appends new items in order`() {
        val queue = AudioQueue(listOf(first))

        val result = queue.addAll(listOf(first.copy(name = "副本"), second, third))
            as QueueChange.Updated

        assertEquals(listOf(first, second, third), result.queue.items)
    }

    @Test
    fun `equal known timestamps preserve stable selection order`() {
        val equalFirst = item(1, 1_000)
        val equalSecond = item(2, 1_000)
        val equalThird = item(3, 1_000)

        val result = AudioQueue(listOf(equalFirst)).addAll(listOf(equalSecond, equalThird))
            as QueueChange.Updated

        assertEquals(listOf(equalFirst, equalSecond, equalThird), result.queue.items)
    }

    @Test
    fun `multiple unknown timestamps remain last in stable selection order`() {
        val unknownFirst = item(1, null)
        val known = item(2, 1_000)
        val unknownSecond = item(3, null)

        val result = AudioQueue(listOf(unknownFirst)).addAll(listOf(known, unknownSecond))
            as QueueChange.Updated

        assertEquals(listOf(known, unknownFirst, unknownSecond), result.queue.items)
    }
}
