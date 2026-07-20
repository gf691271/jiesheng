package com.frank.jiesheng

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

interface AudioMergeEngine {
    fun merge(inputs: List<Uri>, output: File, listener: MergeListener)
    fun cancel()
}

interface MergeListener {
    fun onProgress(percent: Int)
    fun onCompleted()
    fun onError(error: Throwable)
}

class Media3AudioMergeEngine(context: Context) : AudioMergeEngine {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val progressHolder = ProgressHolder()
    private var transformer: Transformer? = null
    private var outputFile: File? = null
    private var mergeListener: MergeListener? = null

    private val progressPoll = object : Runnable {
        override fun run() {
            val activeTransformer = transformer ?: return
            if (activeTransformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                mergeListener?.onProgress(progressHolder.progress)
            }
            handler.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    override fun merge(inputs: List<Uri>, output: File, listener: MergeListener) {
        check(transformer == null) { "A merge is already running" }
        output.delete()
        outputFile = output
        mergeListener = listener
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
            transformer!!.start(CompositionFactory.audioOnly(inputs), output.absolutePath)
            handler.post(progressPoll)
        } catch (error: Throwable) {
            output.delete()
            finish { it.onError(error) }
        }
    }

    override fun cancel() {
        transformer?.cancel()
        outputFile?.delete()
        clearActiveMerge()
    }

    private fun finish(callback: (MergeListener) -> Unit) {
        val listener = mergeListener
        clearActiveMerge()
        if (listener != null) callback(listener)
    }

    private fun clearActiveMerge() {
        handler.removeCallbacks(progressPoll)
        transformer = null
        outputFile = null
        mergeListener = null
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 200L
    }
}
