package com.frank.jiesheng

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

interface AudioSplitEngine {
    fun split(input: Uri, segment: SplitSegment, output: File, listener: SplitListener)
    fun cancel()
}

interface SplitListener {
    fun onProgress(percent: Int)
    fun onCompleted()
    fun onError(error: Throwable)
}

@OptIn(markerClass = [UnstableApi::class])
class Media3AudioSplitEngine(context: Context) : AudioSplitEngine {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val progressHolder = ProgressHolder()
    private var transformer: Transformer? = null
    private var outputFile: File? = null
    private var splitListener: SplitListener? = null

    private val progressPoll = object : Runnable {
        override fun run() {
            val activeTransformer = transformer ?: return
            if (activeTransformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                splitListener?.onProgress(progressHolder.progress)
            }
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    override fun split(input: Uri, segment: SplitSegment, output: File, listener: SplitListener) {
        check(transformer == null) { "A split is already running" }
        output.delete()
        outputFile = output
        splitListener = listener
        listener.onProgress(0)

        val transformerListener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                finish { it.onCompleted() }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                output.delete()
                finish { it.onError(exportException) }
            }
        }

        try {
            transformer = Transformer.Builder(appContext)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(transformerListener)
                .build()
            transformer!!.start(SplitCompositionFactory.audioClip(input, segment), output.absolutePath)
            handler.post(progressPoll)
        } catch (error: Throwable) {
            output.delete()
            finish { it.onError(error) }
        }
    }

    override fun cancel() {
        transformer?.cancel()
        outputFile?.delete()
        clearActiveSplit()
    }

    private fun finish(callback: (SplitListener) -> Unit) {
        val listener = splitListener
        clearActiveSplit()
        if (listener != null) callback(listener)
    }

    private fun clearActiveSplit() {
        handler.removeCallbacks(progressPoll)
        transformer = null
        outputFile = null
        splitListener = null
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 200L
    }
}
