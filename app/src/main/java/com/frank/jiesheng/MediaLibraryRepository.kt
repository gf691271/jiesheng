package com.frank.jiesheng

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore

data class AudioFolder(
    val path: String,
    val name: String,
    val items: List<SelectedAudio>,
)

class MediaLibraryRepository(private val contentResolver: ContentResolver) {
    fun load(): List<AudioFolder> {
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED,
            pathColumn,
        )
        val folders = linkedMapOf<String, MutableList<SelectedAudio>>()

        contentResolver.query(collectionUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)

            while (cursor.moveToNext()) {
                val durationMs = cursor.getLong(durationIndex)
                if (durationMs <= 0) continue

                val path = cursor.getString(pathIndex).orEmpty().parentPath()
                val displayName = cursor.getString(nameIndex).orEmpty()
                val mimeType = cursor.getString(mimeTypeIndex)
                val item = SelectedAudio(
                    uri = ContentUris.withAppendedId(collectionUri, cursor.getLong(idIndex)).toString(),
                    name = displayName,
                    durationMs = durationMs,
                    formatLabel = displayName.formatLabel(mimeType),
                    sourceType = SourceType.AUDIO,
                    lastModifiedEpochMs = cursor.getLongOrNull(modifiedIndex)?.times(1_000),
                )
                folders.getOrPut(path) { mutableListOf() }.add(item)
            }
        }

        return folders.map { (path, items) ->
            AudioFolder(path = path, name = path.folderName(), items = items)
        }.sortedBy { it.name }
    }
}

private fun String.parentPath(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) this else substringBeforeLast('/', "")

private fun String.folderName(): String = trimEnd('/').substringAfterLast('/')

private fun String.formatLabel(mimeType: String?): String {
    val extension = substringAfterLast('.', missingDelimiterValue = "")
    if (extension.isNotEmpty()) return extension.uppercase()
    return mimeType?.substringAfter('/', missingDelimiterValue = "")
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()
        ?: "未知格式"
}

private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
    if (isNull(index)) null else getLong(index)
