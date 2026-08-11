package com.frank.jiesheng

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.view.View
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
import org.hamcrest.Matchers.not
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitActivityTest {
    @Test
    fun splitScreenStartsWithTwoCutPointRowsAndDisabledExport() {
        ActivityScenario.launch(SplitActivity::class.java).use { scenario ->
            onView(withText("把一段声音，拆成几段")).check(matches(isDisplayed()))
            onView(withId(R.id.chooseSplitSourceButton))
                .check(matches(withText("选择音频")))
                .check(matches(isEnabled()))
            onView(withId(R.id.splitExportButton)).check(matches(not(isEnabled())))
            scenario.onActivity { activity ->
                assertEquals(2, activity.findViewById<LinearLayout>(R.id.cutPointList).childCount)
            }
        }
    }

    @Test
    fun cutPointRowsStayBetweenTwoAndTwenty() {
        ActivityScenario.launch(SplitActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val list = activity.findViewById<LinearLayout>(R.id.cutPointList)
                val add = activity.findViewById<android.view.View>(R.id.addCutPointButton)
                repeat(18) { add.performClick() }
                assertEquals(20, list.childCount)
                assertFalse(add.isEnabled)

                repeat(18) {
                    list.getChildAt(list.childCount - 1)
                        .findViewById<android.view.View>(R.id.removeCutPointButton)
                        .performClick()
                }
                assertEquals(2, list.childCount)
                assertTrue(
                    (0 until list.childCount).all { index ->
                        !list.getChildAt(index)
                            .findViewById<android.view.View>(R.id.removeCutPointButton)
                            .isEnabled
                    },
                )
                assertTrue(
                    (0 until list.childCount).all { index ->
                        list.getChildAt(index)
                            .findViewById<View>(R.id.removeCutPointButton)
                            .visibility == View.INVISIBLE
                    },
                )
            }
        }
    }

    @Test
    fun sourceButtonLaunchesSingleAudioDocumentPicker() {
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(
                ActivityResult(Activity.RESULT_CANCELED, null),
            )
            ActivityScenario.launch(SplitActivity::class.java).use {
                onView(withId(R.id.chooseSplitSourceButton)).perform(click())

                val intent = Intents.getIntents().single { it.action == Intent.ACTION_OPEN_DOCUMENT }
                assertEquals("*/*", intent.type)
                assertArrayEquals(
                    arrayOf("audio/*"),
                    intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES),
                )
                assertFalse(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            }
        } finally {
            Intents.release()
        }
    }
}
