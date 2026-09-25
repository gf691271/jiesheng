package com.frank.jiesheng

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SplitUiState(
    val source: SelectedAudio? = null,
    val phase: SplitPhase = SplitPhase.Idle,
    val segments: List<SplitSegment> = emptyList(),
    val notice: String? = null,
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
    data object Stopping : SplitPhase
    data class Splitting(val progress: Int) : SplitPhase
    data class Completed(val count: Int) : SplitPhase
    data class Failed(val reason: String) : SplitPhase
}

class SplitViewModel(private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private var export: ExportCoordinator? = null
    var pointTexts: List<String>
        get() = savedState.get<ArrayList<String>>("points")?.toList() ?: listOf("", "")
        private set(value) { savedState["points"] = ArrayList(value) }

    fun updatePoints(points: List<String>) {
        if (state.value.areEditsEnabled && points.size in 2..20) pointTexts = points.toList()
    }

    private fun restoredSource(): SelectedAudio? {
        val values = savedState.get<ArrayList<String>>("source") ?: return null
        return SelectedAudio(values[0], values[1], values[2].toLong(), values[3], SourceType.AUDIO, null)
    }

    private val mutableState = MutableStateFlow(SplitUiState(source = restoredSource()))
    val state: StateFlow<SplitUiState> = mutableState.asStateFlow()

    fun readSource(context: Context, uri: Uri) {
        val app = context.applicationContext
        if (!beginSourceReading()) return
        viewModelScope.launch {
            try {
                finishSourceReading(withContext(Dispatchers.IO) { DocumentMetadataReader(app).read(uri, SourceType.AUDIO) })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = state.value.copy(phase = SplitPhase.Idle, notice = error.message ?: "无法读取音频")
            }
        }
    }

    fun exportTo(context: Context, tree: Uri) {
        val current = state.value
        val source = current.source ?: return
        if (!beginSplit()) return
        val app = context.applicationContext
        val names = SplitExportNames.forSource(source.name, current.segments.size)
        val owner = ExportCoordinator(viewModelScope, app.cacheDir, AndroidExportStorage(app, tree))
        export = owner
        owner.start(
            current.segments.mapIndexed { index, segment ->
                ExportPart(names[index]) { output, progress ->
                    Media3AudioSplitEngine(app).awaitSplit(source.uri.toUri(), segment, output, progress)
                }
            },
            { percent ->
                if (state.value.phase is SplitPhase.Splitting) {
                    mutableState.value = state.value.copy(phase = SplitPhase.Splitting(percent))
                }
            },
            { mutableState.value = state.value.copy(phase = SplitPhase.Stopping) },
            { outcome ->
                export = null
                mutableState.value = state.value.copy(
                    phase = SplitPhase.Idle, segments = emptyList(),
                    notice = outcome.message("已保存 ${names.size} 段音频到所选文件夹"),
                )
            },
        )
    }

    fun stopExport() {
        export?.cancel { mutableState.value = state.value.copy(phase = SplitPhase.Stopping) }
    }

    fun beginSourceReading(): Boolean {
        val current = mutableState.value
        if (current.phase != SplitPhase.Idle) return false
        mutableState.value = current.copy(phase = SplitPhase.ReadingSource, segments = emptyList())
        return true
    }

    fun finishSourceReading(source: SelectedAudio) {
        val current = mutableState.value
        if (current.phase != SplitPhase.ReadingSource) return
        savedState["source"] = arrayListOf(source.uri, source.name, source.durationMs.toString(), source.formatLabel)
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
                notice = null,
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
