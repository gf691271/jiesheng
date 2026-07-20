package com.frank.jiesheng

import android.content.ContentValues
import android.content.ContentProvider
import android.content.ContentUris
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MediaLibraryRepositoryTest {
    @Test
    fun `load groups audio by folder name and maps media values`() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, InMemoryMediaStoreProvider())
        val alphaUri = insertAudio(resolver, "Music/Alpha/", "a.m4a", 11, 61)
        val secondBravoUri = insertAudio(resolver, "Music/Bravo/", "b.m4a", 22, 62)
        val bravoUri = insertAudio(resolver, "Music/Bravo/", "c.mp3", 33, 63)

        val folders = MediaLibraryRepository(resolver).load()

        assertEquals(listOf("Alpha", "Bravo"), folders.map { it.name })
        assertEquals(listOf("Music/Alpha/", "Music/Bravo/"), folders.map { it.path })
        assertEquals(listOf(1, 2), folders.map { it.items.size })
        assertEquals(alphaUri.toString(), folders[0].items.single().uri)
        assertEquals(61_000L, folders[0].items.single().lastModifiedEpochMs)
        assertEquals(
            setOf(secondBravoUri.toString(), bravoUri.toString()),
            folders[1].items.map { it.uri }.toSet(),
        )
        assertEquals(33L, folders[1].items.maxOf { it.durationMs })
        assertTrue(folders.flatMap { it.items }.all { it.sourceType == SourceType.AUDIO })
    }

    @Test
    fun `load excludes audio with nonpositive duration`() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, InMemoryMediaStoreProvider())
        insertAudio(resolver, "Music/Alpha/", "valid.m4a", 1, 1)
        insertAudio(resolver, "Music/Alpha/", "zero.m4a", 0, 2)
        insertAudio(resolver, "Music/Alpha/", "negative.m4a", -1, 3)

        val folders = MediaLibraryRepository(resolver).load()

        assertEquals(listOf("valid.m4a"), folders.single().items.map { it.name })
    }

    @Test
    @Config(sdk = [28])
    fun `api 26 to 28 derives folder from legacy DATA parent path`() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, InMemoryMediaStoreProvider())
        insertLegacyAudio(resolver, "/storage/emulated/0/Recordings/Interview/part-1.m4a")

        val folder = MediaLibraryRepository(resolver).load().single()

        assertEquals("/storage/emulated/0/Recordings/Interview", folder.path)
        assertEquals("Interview", folder.name)
        assertEquals("part-1.m4a", folder.items.single().name)
    }

    private fun insertAudio(
        resolver: android.content.ContentResolver,
        relativePath: String,
        displayName: String,
        durationMs: Long,
        modifiedSeconds: Long,
    ) = requireNotNull(
        resolver.insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            ContentValues().apply {
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/${displayName.substringAfterLast('.')}")
                put(MediaStore.Audio.Media.DURATION, durationMs)
                put(MediaStore.Audio.Media.DATE_MODIFIED, modifiedSeconds)
            },
        ),
    )

    private fun insertLegacyAudio(
        resolver: android.content.ContentResolver,
        dataPath: String,
    ) = requireNotNull(
        resolver.insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Audio.Media.DATA, dataPath)
                put(MediaStore.Audio.Media.DISPLAY_NAME, dataPath.substringAfterLast('/'))
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.DURATION, 1_000L)
                put(MediaStore.Audio.Media.DATE_MODIFIED, 60L)
            },
        ),
    )

    private class InMemoryMediaStoreProvider : ContentProvider() {
        private val rows = mutableListOf<ContentValues>()

        override fun onCreate(): Boolean = true

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val row = ContentValues(values).apply {
                put(MediaStore.Audio.Media._ID, rows.size + 1L)
            }
            rows += row
            return ContentUris.withAppendedId(uri, row.getAsLong(MediaStore.Audio.Media._ID))
        }

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?,
        ): Cursor {
            val columns = requireNotNull(projection)
            return MatrixCursor(columns).apply {
                rows.forEach { row ->
                    addRow(columns.map(row::get).toTypedArray())
                }
            }
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

        override fun getType(uri: Uri): String? = null

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?,
        ): Int = 0
    }
}
