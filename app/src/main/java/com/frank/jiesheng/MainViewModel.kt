package com.frank.jiesheng

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainUiState(
    val queue: AudioQueue = AudioQueue(),
    val phase: MergePhase = MergePhase.Idle,
) {
    val isMergeEnabled: Boolean
        get() = queue.items.size in 2..3 && phase == MergePhase.Idle
}

sealed interface MergePhase {
    data object Idle : MergePhase
    data object ChoosingDestination : MergePhase
    data class Merging(val progress: Int) : MergePhase
    data class Completed(val fileName: String) : MergePhase
    data class Failed(val reason: String) : MergePhase
}

class MainViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = mutableMessages.asSharedFlow()

    fun add(item: SelectedAudio) {
        when (val change = mutableState.value.queue.add(item)) {
            is QueueChange.Updated -> mutableState.update { it.copy(queue = change.queue) }
            QueueChange.Duplicate -> mutableMessages.tryEmit("这个音频已经添加过了")
            QueueChange.LimitReached -> mutableMessages.tryEmit("最多只能选择 3 个音频")
        }
    }

    fun moveUp(index: Int) {
        mutableState.update { it.copy(queue = it.queue.moveUp(index)) }
    }

    fun moveDown(index: Int) {
        mutableState.update { it.copy(queue = it.queue.moveDown(index)) }
    }

    fun remove(uri: String) {
        mutableState.update { it.copy(queue = it.queue.remove(uri)) }
    }

    fun startExport() {
        if (mutableState.value.isMergeEnabled) {
            mutableState.update { it.copy(phase = MergePhase.ChoosingDestination) }
        }
    }

    fun beginMerge() {
        mutableState.update { it.copy(phase = MergePhase.Merging(0)) }
    }

    fun updateProgress(progress: Int) {
        mutableState.update { it.copy(phase = MergePhase.Merging(progress.coerceIn(0, 100))) }
    }

    fun finishExport(fileName: String) {
        mutableState.update { it.copy(phase = MergePhase.Completed(fileName)) }
    }

    fun failExport(reason: String) {
        mutableState.update { it.copy(phase = MergePhase.Failed(reason)) }
    }

    fun cancelExport() {
        mutableState.update { it.copy(phase = MergePhase.Idle) }
    }
}
