package com.frank.jiesheng

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frank.jiesheng.databinding.ActivityMainBinding
import com.frank.jiesheng.databinding.ItemAudioBinding
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun emptyScreenOffersAllThreeSourcesAndDisablesMerge() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.musicLibraryButton))
                .check(matches(withText("音乐库")))
                .check(matches(isDisplayed()))
            onView(withId(R.id.galleryButton))
                .check(matches(withText("视频文件")))
                .check(matches(isDisplayed()))
            onView(withId(R.id.folderButton))
                .check(matches(withText("文件夹")))
                .check(matches(isDisplayed()))
            onView(withId(R.id.mergeButton)).check(matches(not(isEnabled())))
        }
    }

    @Test
    fun videoFileButtonLaunchesMultiSelectVideoDocumentPicker() {
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                ActivityResult(Activity.RESULT_CANCELED, null),
            )
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.galleryButton)).perform(click())

                val intent = Intents.getIntents().single {
                    it.action == Intent.ACTION_OPEN_DOCUMENT
                }
                assertEquals("*/*", intent.type)
                assertArrayEquals(
                    arrayOf("video/*"),
                    intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES),
                )
                assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun allSourceButtonsStayEnabledAtCapacityWhileIdle() {
        val items = (1..20).map { index ->
            SelectedAudio(
                uri = "content://audio/$index",
                name = "$index.m4a",
                durationMs = 1_000,
                formatLabel = "M4A",
                sourceType = SourceType.AUDIO,
                lastModifiedEpochMs = index.toLong(),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val binding = ActivityMainBinding.inflate(activity.layoutInflater)

                binding.bindSourceAvailability(MainUiState(queue = AudioQueue(items)))

                assertTrue(binding.musicLibraryButton.isEnabled)
                assertTrue(binding.galleryButton.isEnabled)
                assertTrue(binding.folderButton.isEnabled)
            }
        }
    }

    @Test
    fun audioCardPrioritizesFilenameAndPlacesControlsAtBottom() {
        val filename = "2026-07-20_这是一个很长很长而且需要完整显示不能用省略号截断的采访录音文件名.mp4"
        val item = SelectedAudio(
            uri = "content://video/1",
            name = filename,
            durationMs = 756_000,
            formatLabel = "MP4",
            sourceType = SourceType.VIDEO,
            lastModifiedEpochMs = Instant.parse("2026-07-20T14:36:08Z").toEpochMilli(),
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val binding = ItemAudioBinding.inflate(activity.layoutInflater)

                binding.bindMetadata(item, ZoneId.of("UTC"))

                assertEquals(filename, binding.nameText.text.toString())
                assertEquals(Int.MAX_VALUE, binding.nameText.maxLines)
                assertNull(binding.nameText.ellipsize)
                assertEquals(LinearLayout.VERTICAL, binding.root.orientation)
                val controls = binding.moveUpButton.parent as LinearLayout
                assertEquals(LinearLayout.HORIZONTAL, controls.orientation)
                assertEquals(controls, binding.moveDownButton.parent)
                assertEquals(controls, binding.removeButton.parent)
                assertNotNull(binding.detailText.background)
                assertNotNull(binding.modifiedText.background)
                assertEquals("MP4（取音频）· 12:36", binding.detailText.text.toString())
                assertEquals("修改于 2026-07-20 14:36:08", binding.modifiedText.text.toString())
            }
        }
    }
}
