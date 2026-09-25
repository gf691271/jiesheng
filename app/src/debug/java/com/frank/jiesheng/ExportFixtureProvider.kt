package com.frank.jiesheng

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Test-only SAF destination; can hold a writer open across Activity recreation/cancellation. */
class ExportFixtureProvider : DocumentsProvider() {
    companion object {
        const val AUTHORITY = "com.frank.jiesheng.test.exports"
        @Volatile var entered: CountDownLatch? = null
        @Volatile var release: CountDownLatch? = null
        @Volatile var refuseDelete = false
    }

    override fun onCreate() = true
    override fun isChildDocument(parentDocumentId: String, documentId: String) = parentDocumentId == "root"
    private fun directory() = File(context!!.cacheDir, "export-fixture").apply { mkdirs() }
    private fun file(id: String) = File(directory(), id.substringAfterLast('/'))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        file(displayName).createNewFile()
        return displayName
    }

    override fun deleteDocument(documentId: String) {
        check(!refuseDelete) { "Test provider refuses deletion" }
        check(file(documentId).delete())
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode.contains('w')) {
            entered?.countDown()
            check(release?.await(30, TimeUnit.SECONDS) != false) { "Timed out waiting for test writer" }
        }
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return MatrixCursor(columns).apply {
            addRow(columns.map {
                when (it) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> documentId
                    DocumentsContract.Document.COLUMN_MIME_TYPE -> "audio/mp4"
                    DocumentsContract.Document.COLUMN_SIZE -> file(documentId).length()
                    else -> null
                }
            })
        }
    }

    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(projection ?: emptyArray())
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        MatrixCursor(projection ?: emptyArray())
}
