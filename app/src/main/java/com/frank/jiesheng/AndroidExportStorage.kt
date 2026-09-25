package com.frank.jiesheng

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AndroidExportStorage(context: Context, private val tree: Uri? = null) : ExportStorage {
    private val resolver = context.applicationContext.contentResolver

    override fun create(name: String): String {
        val folder = requireNotNull(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        return DocumentsContract.createDocument(resolver, root, "audio/mp4", name)?.toString()
            ?: throw IOException("无法在所选文件夹创建音频")
    }

    override suspend fun write(source: File, destination: String) {
        resolver.openOutputStream(destination.toUri(), "w").use { output ->
            if (output == null) throw IOException("无法写入所选保存位置")
            source.inputStream().use { input -> copyCancellable(input, output) }
        }
    }

    override fun delete(destination: String): Boolean = DocumentsContract.deleteDocument(resolver, destination.toUri())
}

internal suspend fun AudioMergeEngine.awaitMerge(inputs: List<Uri>, output: File, progress: (Int) -> Unit) {
    suspendCancellableCoroutine<Unit> { continuation ->
        continuation.invokeOnCancellation { cancel() }
        merge(inputs, output, object : MergeListener {
            override fun onProgress(percent: Int) { if (continuation.isActive) progress(percent) }
            override fun onCompleted() { if (continuation.isActive) continuation.resume(Unit) }
            override fun onError(error: Throwable) { if (continuation.isActive) continuation.resumeWithException(error) }
        })
    }
}

internal suspend fun AudioSplitEngine.awaitSplit(input: Uri, segment: SplitSegment, output: File, progress: (Int) -> Unit) {
    suspendCancellableCoroutine<Unit> { continuation ->
        continuation.invokeOnCancellation { cancel() }
        split(input, segment, output, object : SplitListener {
            override fun onProgress(percent: Int) { if (continuation.isActive) progress(percent) }
            override fun onCompleted() { if (continuation.isActive) continuation.resume(Unit) }
            override fun onError(error: Throwable) { if (continuation.isActive) continuation.resumeWithException(error) }
        })
    }
}
