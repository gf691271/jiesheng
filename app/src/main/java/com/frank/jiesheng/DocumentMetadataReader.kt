package com.frank.jiesheng

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

class DocumentMetadataReader(private val context: Context) {
    fun read(uri: Uri): SelectedAudio {
        val name = readDisplayName(uri)
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
        return SelectedAudio(
            uri = uri.toString(),
            name = name,
            durationMs = duration,
            formatLabel = name.substringAfterLast('.', missingDelimiterValue = "").uppercase(),
            sourceType = SourceType.AUDIO,
            lastModifiedEpochMs = null,
        )
    }

    private fun readDisplayName(uri: Uri): String {
        val displayName = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: RuntimeException) {
            null
        }
        return displayName ?: uri.lastPathSegment ?: "未命名音频"
    }
}

class UnreadableAudioException(name: String, cause: Throwable? = null) :
    Exception("无法读取音频：$name", cause)
