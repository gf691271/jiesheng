package com.frank.jiesheng

data class SelectedAudio(
    val uri: String,
    val name: String,
    val durationMs: Long,
)

data class AudioQueue(
    val items: List<SelectedAudio> = emptyList(),
) {
    fun add(item: SelectedAudio): QueueChange = addAll(listOf(item))

    fun addAll(newItems: List<SelectedAudio>): QueueChange {
        val additions = newItems.distinctBy { it.uri }.filterNot { candidate ->
            items.any { existing -> existing.uri == candidate.uri }
        }
        return when {
            additions.isEmpty() -> QueueChange.Duplicate
            items.size + additions.size > MAX_ITEMS -> QueueChange.LimitReached
            else -> QueueChange.Updated(copy(items = items + additions))
        }
    }

    fun moveUp(index: Int): AudioQueue = move(index, index - 1)

    fun moveDown(index: Int): AudioQueue = move(index, index + 1)

    fun remove(uri: String): AudioQueue = copy(items = items.filterNot { it.uri == uri })

    private fun move(from: Int, to: Int): AudioQueue {
        if (from !in items.indices || to !in items.indices) return this
        val reordered = items.toMutableList()
        val item = reordered.removeAt(from)
        reordered.add(to, item)
        return copy(items = reordered)
    }

    private companion object {
        const val MAX_ITEMS = 3
    }
}

sealed interface QueueChange {
    data class Updated(val queue: AudioQueue) : QueueChange
    data object Duplicate : QueueChange
    data object LimitReached : QueueChange
}
