package com.frank.jiesheng

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                .check(matches(withText("相册")))
                .check(matches(isDisplayed()))
            onView(withId(R.id.folderButton))
                .check(matches(withText("文件夹")))
                .check(matches(isDisplayed()))
            onView(withId(R.id.mergeButton)).check(matches(not(isEnabled())))
        }
    }

    @Test
    fun audioCardShowsCompleteFilenameSourceDurationAndExactModifiedTime() {
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
                val row = activity.layoutInflater.inflate(R.layout.item_audio, null)
                val nameText = row.findViewById<TextView>(R.id.nameText)
                val detailText = row.findViewById<TextView>(R.id.detailText)
                val modifiedText = row.findViewById<TextView>(R.id.modifiedText)

                nameText.text = item.name
                detailText.text = AudioText.detail(item)
                modifiedText.text = AudioText.modified(item.lastModifiedEpochMs, ZoneId.of("UTC"))

                assertEquals(filename, nameText.text.toString())
                assertEquals(Int.MAX_VALUE, nameText.maxLines)
                assertNull(nameText.ellipsize)
                assertEquals("MP4（取音频）· 12:36", detailText.text.toString())
                assertEquals("修改于 2026-07-20 14:36:08", modifiedText.text.toString())
            }
        }
    }
}
