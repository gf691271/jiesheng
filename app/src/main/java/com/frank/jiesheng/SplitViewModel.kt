package com.frank.jiesheng

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SplitUiState(
    val source: SelectedAudio? = null,
    val phase: SplitPhase = SplitPhase.Idle,
    val segments: List<SplitSegment> = emptyList(),
) {
    val areEditsEnabled: Boolean
        get() = phase == SplitPhase.Idle

    val isExportEnabled: Boolean
        get() = source != null && phase == SplitPhase.Idle
}

sealed interface SplitPhase {
    data object Idle : SplitPhase
    data object ReadingSource : SplitPhase
    data object ChoosingDestination : SplitPhase
    data class Splitting(val progress: Int) : SplitPhase
    data class Completed(val count: Int) : SplitPhase
    data class Failed(val reason: String) : SplitPhase
}

class SplitViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(SplitUiState())
    val state: StateFlow<SplitUiState> = mutableState.asStateFlow()

    fun beginSourceReading(): Boolean {
        val current = mutableState.value
        if (current.phase != SplitPhase.Idle) return false
        mutableState.value = current.copy(phase = SplitPhase.ReadingSource, segments = emptyList())
        return true
    }

    fun finishSourceReading(source: SelectedAudio) {
        val current = mutableState.value
        if (current.phase != SplitPhase.ReadingSource) return
        mutableState.value = current.copy(
            source = source,
            phase = SplitPhase.Idle,
            segments = emptyList(),
        )
    }

    fun failSourceReading(reason: String) {
        val current = mutableState.value
        if (current.phase != SplitPhase.ReadingSource) return
        mutableState.value = current.copy(phase = SplitPhase.Failed(reason), segments = emptyList())
    }

    fun startExport(pointTexts: List<String>): SplitPlanResult {
        val current = mutableState.value
        val source = current.source
            ?: return SplitPlanResult.Invalid(message = "请先选择音频")
        if (current.phase != SplitPhase.Idle) {
            return SplitPlanResult.Invalid(message = "当前操作尚未完成")
        }
        val result = SplitPlan.create(source.durationMs, pointTexts)
        if (result is SplitPlanResult.Valid) {
            mutableState.value = current.copy(
                phase = SplitPhase.ChoosingDestination,
                segments = result.segments,
            )
        }
        return result
    }

    fun cancelDestinationChoice() {
        val current = mutableState.value
        if (current.phase != SplitPhase.ChoosingDestination) return
        mutableState.value = current.copy(phase = SplitPhase.Idle, segments = emptyList())
    }

    fun beginSplit(): Boolean {
        val current = mutableState.value
        if (current.phase != SplitPhase.ChoosingDestination || current.segments.isEmpty()) return false
        mutableState.value = current.copy(phase = SplitPhase.Splitting(0))
        return true
    }

    fun updateProgress(completedSegments: Int, totalSegments: Int, currentPercent: Int) {
        val current = mutableState.value
        if (current.phase !is SplitPhase.Splitting || totalSegments <= 0) return
        val progress = (
            (completedSegments.coerceAtLeast(0) * 100 + currentPercent.coerceIn(0, 100)) /
                totalSegments
            ).coerceIn(0, 100)
        mutableState.value = current.copy(phase = SplitPhase.Splitting(progress))
    }

    fun finishSplit(count: Int) {
        val current = mutableState.value
        if (current.phase !is SplitPhase.Splitting) return
        mutableState.value = current.copy(phase = SplitPhase.Completed(count))
    }

    fun failSplit(reason: String) {
        val current = mutableState.value
        if (current.phase !is SplitPhase.Splitting && current.phase != SplitPhase.ChoosingDestination) return
        mutableState.value = current.copy(phase = SplitPhase.Failed(reason))
    }

    fun reset() {
        val current = mutableState.value
        mutableState.value = current.copy(phase = SplitPhase.Idle, segments = emptyList())
    }
}
