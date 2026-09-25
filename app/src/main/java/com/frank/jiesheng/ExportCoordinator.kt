package com.frank.jiesheng

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ExportPart(
    val name: String,
    val destination: String? = null,
    val transform: suspend (File, (Int) -> Unit) -> Unit,
)

internal interface ExportStorage {
    fun create(name: String): String
    suspend fun write(source: File, destination: String)
    fun delete(destination: String): Boolean
}

internal data class ExportOutcome(
    val completed: Boolean,
    val reason: String? = null,
    val retainedNames: List<String> = emptyList(),
) {
    fun message(success: String): String {
        val result = if (completed) success else reason?.let { "导出失败：$it" } ?: "已取消导出"
        return if (retainedNames.isEmpty()) result else
            "$result\n所选保存位置仍有 ${retainedNames.size} 个文件未能删除，请手动清理：\n${retainedNames.joinToString("\n")}"
    }
}

/** One retained owner, one session. Idle is emitted only AFTER copy and rollback have finished. */
internal class ExportCoordinator(
    private val scope: CoroutineScope,
    private val cacheDir: File,
    private val storage: ExportStorage,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private var active: Job? = null

    fun start(
        parts: List<ExportPart>,
        progress: (Int) -> Unit,
        stopping: () -> Unit,
        finished: (ExportOutcome) -> Unit,
    ): Boolean {
        if (active != null || parts.isEmpty()) return false
        val session = scope.launch(start = CoroutineStart.LAZY) {
            val documents = parts.mapNotNull { part -> part.destination?.let { it to part.name } }.toMutableList()
            val temporaryFiles = mutableListOf<File>()
            var completed = false
            var reason: String? = null
            try {
                parts.forEachIndexed { index, part ->
                    currentCoroutineContext().ensureActive()
                    val output = File.createTempFile("jiesheng-export-", ".m4a", cacheDir)
                    temporaryFiles += output
                    val owner = currentCoroutineContext()[Job]!!
                    part.transform(output) { percent ->
                        if (owner.isActive) progress((index * 100 + percent.coerceIn(0, 100)) / parts.size)
                    }
                    withContext(io) {
                        // Register synchronously with creation: cancellation cannot orphan a new document.
                        val destination = part.destination ?: storage.create(part.name).also {
                            documents += it to part.name
                        }
                        currentCoroutineContext().ensureActive()
                        storage.write(output, destination)
                    }
                    currentCoroutineContext().ensureActive()
                    output.delete()
                }
                completed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reason = error.message ?: "无法写入音频"
            } finally {
                // Even ViewModel clearing must finish rollback. No Activity or mutable next-session paths.
                withContext(NonCancellable) {
                    if (!completed) stopping()
                    val retained = withContext(io) {
                        temporaryFiles.forEach { it.delete() }
                        if (completed) emptyList() else documents.filter { (uri, _) ->
                            try { !storage.delete(uri) } catch (_: Exception) { true }
                        }.map { it.second }
                    }
                    active = null
                    finished(ExportOutcome(completed, reason, retained))
                }
            }
        }
        active = session
        session.start()
        return true
    }

    fun cancel(stopping: () -> Unit) {
        val session = active ?: return
        stopping()
        session.cancel()
    }
}

internal suspend fun copyCancellable(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        currentCoroutineContext().ensureActive()
        output.write(buffer, 0, count)
    }
    currentCoroutineContext().ensureActive()
    output.flush()
}
