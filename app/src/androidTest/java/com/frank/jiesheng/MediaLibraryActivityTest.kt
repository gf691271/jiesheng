package com.frank.jiesheng

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLibraryActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val insertedUris = mutableListOf<Uri>()
    private lateinit var folderPath: String
    private lateinit var folderLabel: String
    private lateinit var firstName: String
    private lateinit var secondName: String
    private lateinit var firstResultUri: String

    @Before
    fun insertIndexedAudio() {
        val suffix = UUID.randomUUID().toString().take(8)
        folderPath = "Music/JieshengTest$suffix/"
        folderLabel = "JieshengTest$suffix\n$folderPath · 2 个音频"
        firstName = "a-complete-recording-name-$suffix.wav"
        secondName = "b-complete-recording-name-$suffix.wav"
        firstResultUri = insertAudio(firstName)
        insertAudio(secondName)
    }

    @After
    fun removeIndexedAudio() {
        insertedUris.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun folderSummaryOpensFullAudioNames() {
        ActivityScenario.launch<Activity>(activityIntent()).use {
            assertEventually(withText(folderLabel), matches(withText(folderLabel)))
            onView(withText(firstName)).checkDoesNotExist()

            onView(withText(folderLabel)).perform(click())

            onView(withText(firstName)).check(matches(isNotChecked()))
            onView(withText(secondName)).check(matches(isNotChecked()))
        }
    }

    @Test
    fun remainingCapacityBlocksExtraSelectionAndConfirmationReturnsUris() {
        val intent = activityIntent().putExtra("remaining_capacity", 1)
        ActivityScenario.launchActivityForResult<Activity>(intent).use { scenario ->
            assertEventually(withText(folderLabel), matches(withText(folderLabel)))
            onView(withText(folderLabel)).perform(click())

            onView(withText(firstName)).perform(click()).check(matches(isChecked()))
            onView(withText(secondName)).perform(click()).check(matches(isNotChecked()))
            onView(withId(resourceId("confirmButton"))).perform(click())

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(
                arrayListOf(firstResultUri),
                scenario.result.resultData?.getStringArrayListExtra("selected_media_uris"),
            )
        }
    }

    private fun activityIntent() = Intent().apply {
        setClassName(context.packageName, "${context.packageName}.MediaLibraryActivity")
    }

    private fun insertAudio(displayName: String): String {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val inserted = requireNotNull(
            context.contentResolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, folderPath)
                    put(MediaStore.Audio.Media.DURATION, 400L)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                },
            ),
        )
        insertedUris += inserted
        InstrumentationRegistry.getInstrumentation().context.assets.open("tone-440.wav").use { input ->
            context.contentResolver.openOutputStream(inserted).use { output ->
                requireNotNull(output)
                input.copyTo(output)
            }
        }
        context.contentResolver.update(
            inserted,
            ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
            null,
            null,
        )
        val id = ContentUris.parseId(inserted)
        return ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            id,
        ).toString()
    }

    private fun assertEventually(matcher: Matcher<android.view.View>, assertion: ViewAssertion) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (true) {
            try {
                onView(matcher).check(assertion)
                return
            } catch (error: NoMatchingViewException) {
                if (SystemClock.uptimeMillis() >= deadline) throw error
                SystemClock.sleep(50)
            }
        }
    }

    private fun resourceId(name: String): Int =
        context.resources.getIdentifier(name, "id", context.packageName)
}

private fun androidx.test.espresso.ViewInteraction.checkDoesNotExist() = check(
    androidx.test.espresso.assertion.ViewAssertions.doesNotExist(),
)
