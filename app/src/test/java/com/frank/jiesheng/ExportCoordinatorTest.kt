package com.frank.jiesheng

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExportCoordinatorTest {
    @get:Rule val folder = TemporaryFolder()

    private class Storage : ExportStorage {
        val events = mutableListOf<String>()
        var deleteSucceeds = true
        var write: suspend () -> Unit = {}
        override fun create(name: String) = name.also { events += "create:$it" }
        override suspend fun write(source: File, destination: String) {
            events += "write:$destination"
            write()
            events += "closed:$destination"
        }
        override fun delete(destination: String): Boolean {
            events += "delete:$destination"
            return deleteSucceeds
        }
    }

    private fun part(name: String, destination: String? = null) = ExportPart(name, destination) { file, progress ->
        file.writeBytes(byteArrayOf(1, 2, 3))
        progress(100)
    }

    @Test fun `cancel waits for slow writer before deleting or admitting another session`() = runTest {
        val storage = Storage()
        val gate = CompletableDeferred<Unit>()
        storage.write = { withContext(NonCancellable) { gate.await() } }
        val owner = ExportCoordinator(this, folder.root, storage, StandardTestDispatcher(testScheduler))
        var outcome: ExportOutcome? = null
        var stopping = false
        assertTrue(owner.start(listOf(part("first", "old-uri")), {}, { stopping = true }, { outcome = it }))
        runCurrent()
        owner.cancel { stopping = true }
        assertTrue(stopping)
        assertFalse(owner.start(listOf(part("new")), {}, {}, {}))
        runCurrent()
        assertNull(outcome)
        assertFalse(storage.events.any { it.startsWith("delete:") })
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(outcome!!.completed)
        assertEquals(listOf("write:old-uri", "closed:old-uri", "delete:old-uri"), storage.events)
        assertTrue(folder.root.listFiles()!!.isEmpty())
        storage.write = {}
        assertTrue(owner.start(listOf(part("new")), {}, {}, { outcome = it }))
        advanceUntilIdle()
        assertTrue(outcome!!.completed)
        assertFalse(storage.events.contains("delete:new"))
    }

    @Test fun `cancelled split reports each retained file and does not delete source`() = runTest {
        val storage = Storage().apply { deleteSucceeds = false }
        var writes = 0
        storage.write = { if (++writes == 2) awaitCancellation() }
        val owner = ExportCoordinator(this, folder.root, storage, StandardTestDispatcher(testScheduler))
        var result: ExportOutcome? = null
        owner.start(listOf(part("part_01"), part("part_02"), part("part_03")), {}, {}, { result = it })
        runCurrent()
        owner.cancel {}
        advanceUntilIdle()
        assertEquals(listOf("part_01", "part_02"), result!!.retainedNames)
        assertTrue(result!!.message("done").contains("已取消导出"))
        assertTrue(result!!.message("done").contains("2 个文件"))
        assertTrue(result!!.message("done").contains("part_02"))
        assertFalse(storage.events.any { it.contains("part_03") })
    }

    @Test fun `write failure rolls back previous parts and reports failed deletions`() = runTest {
        val storage = Storage().apply { deleteSucceeds = false; write = { error("disk full") } }
        val owner = ExportCoordinator(this, folder.root, storage, StandardTestDispatcher(testScheduler))
        var result: ExportOutcome? = null
        owner.start(listOf(part("part_01")), {}, {}, { result = it })
        advanceUntilIdle()
        assertEquals("disk full", result!!.reason)
        assertEquals(listOf("part_01"), result!!.retainedNames)
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test fun `late transform progress cannot affect the next session`() = runTest {
        val storage = Storage()
        val owner = ExportCoordinator(this, folder.root, storage, StandardTestDispatcher(testScheduler))
        lateinit var lateProgress: (Int) -> Unit
        val first = ExportPart("first", "destination") { _, progress -> lateProgress = progress; awaitCancellation() }
        val seen = mutableListOf<Int>()
        owner.start(listOf(first), { seen += it }, {}, {})
        runCurrent()
        owner.cancel {}
        advanceUntilIdle()
        owner.start(listOf(part("second")), { seen += it }, {}, {})
        advanceUntilIdle()
        val before = seen.toList()
        lateProgress(72)
        assertEquals(before, seen)
        assertEquals(listOf(100), seen)
    }

    @Test fun `successful multi part export never rolls back destinations`() = runTest {
        val storage = Storage()
        var result: ExportOutcome? = null
        ExportCoordinator(this, folder.root, storage, StandardTestDispatcher(testScheduler))
            .start(listOf(part("one"), part("two")), {}, {}, { result = it })
        advanceUntilIdle()
        assertTrue(result!!.completed)
        assertEquals(2, storage.events.count { it.startsWith("create:") })
        assertFalse(storage.events.any { it.startsWith("delete:") })
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test fun `copy checks cancellation between chunks`() = runTest {
        val input = ByteArrayInputStream(ByteArray(100_000))
        var copied = 0
        lateinit var job: kotlinx.coroutines.Job
        val output = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                copied += length
                job.cancel()
            }
        }
        job = launch { copyCancellable(input, output) }
        advanceUntilIdle()
        assertEquals(DEFAULT_BUFFER_SIZE, copied)
        assertTrue(job.isCancelled)
    }
}
