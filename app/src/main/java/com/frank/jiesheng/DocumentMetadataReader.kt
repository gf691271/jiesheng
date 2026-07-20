package com.frank.jiesheng

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns

class DocumentMetadataReader(private val context: Context) {
    fun read(uri: Uri): SelectedAudio = read(uri, SourceType.AUDIO)

    fun read(uri: Uri, sourceType: SourceType): SelectedAudio {
        val metadata = readMetadata(uri)
        val name = metadata.name
        val retriever = MediaMetadataRetriever()
        val duration = try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (error: RuntimeException) {
            throw UnreadableAudioException(name, error)
        } finally {
            retriever.release()
        }
        if (duration == null || duration <= 0) throw UnreadableAudioException(name)
        if (!hasAudioTrack(uri, name)) throw NoAudioTrackException(name)
        return SelectedAudio(
            uri = uri.toString(),
            name = name,
            durationMs = duration,
            formatLabel = formatLabel(name, metadata.mimeType),
            sourceType = sourceType,
            lastModifiedEpochMs = metadata.lastModifiedEpochMs,
        )
    }

    private fun readMetadata(uri: Uri): Metadata {
        var displayName: String? = null
        var lastModifiedEpochMs: Long? = null
        var mimeType: String? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.stringAt(OpenableColumns.DISPLAY_NAME)
                    lastModifiedEpochMs = cursor.longAt(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    mimeType = cursor.stringAt(DocumentsContract.Document.COLUMN_MIME_TYPE)
                }
            }
        } catch (_: RuntimeException) {
            // Providers may not expose document columns.
        }
        if (lastModifiedEpochMs == null && uri.authority == MediaStore.AUTHORITY) {
            lastModifiedEpochMs = try {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.longAt(MediaStore.MediaColumns.DATE_MODIFIED)?.times(1000)
                    } else {
                        null
                    }
                }
            } catch (_: RuntimeException) {
                null
            }
        }
        if (mimeType == null) {
            mimeType = try {
                context.contentResolver.getType(uri)
            } catch (_: RuntimeException) {
                null
            }
        }
        return Metadata(
            name = displayName ?: uri.lastPathSegment ?: "未命名媒体",
            lastModifiedEpochMs = lastModifiedEpochMs,
            mimeType = mimeType,
        )
    }

    private fun hasAudioTrack(uri: Uri, name: String): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, emptyMap())
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index)
                    .getString(android.media.MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }
        } catch (error: RuntimeException) {
            throw UnreadableAudioException(name, error)
        } finally {
            extractor.release()
        }
    }

    private fun formatLabel(name: String, mimeType: String?): String {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        if (extension.isNotEmpty()) return extension.uppercase()
        return mimeType?.substringAfter('/', missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase()
            ?: "未知格式"
    }

    private data class Metadata(
        val name: String,
        val lastModifiedEpochMs: Long?,
        val mimeType: String?,
    )
}

private fun android.database.Cursor.stringAt(columnName: String): String? =
    getColumnIndex(columnName).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

private fun android.database.Cursor.longAt(columnName: String): Long? =
    getColumnIndex(columnName).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

class UnreadableAudioException(name: String, cause: Throwable? = null) :
    Exception("无法读取音频：$name", cause)

class NoAudioTrackException(name: String) : Exception("媒体不包含音轨：$name")
