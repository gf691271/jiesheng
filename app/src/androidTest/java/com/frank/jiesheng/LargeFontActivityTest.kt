package com.frank.jiesheng

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeFontActivityTest {
    @Test fun sourceLabelsFitAtStandardAndLargeFontScales() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val original = Settings.System.getString(context.contentResolver, "font_scale") ?: "1.0"
        fun setScale(value: String) {
            automation.executeShellCommand("settings put system font_scale $value").use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (kotlin.math.abs(context.resources.configuration.fontScale - value.toFloat()) > 0.01 &&
                SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        }
        try {
            for (scale in listOf("1.0", "1.3", "2.0")) {
                setScale(scale)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        assertEquals(scale.toFloat(), activity.resources.configuration.fontScale, 0.01f)
                        for (id in listOf(R.id.musicLibraryButton, R.id.galleryButton, R.id.folderButton)) {
                            val button = activity.findViewById<TextView>(id)
                            assertTrue(button.text.toString(), button.layout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                            assertEquals(button.text.length, button.layout.getLineEnd(button.layout.lineCount - 1))
                            assertTrue((0 until button.layout.lineCount).all { button.layout.getEllipsisCount(it) == 0 })
                        }
                        val sources = activity.findViewById<android.view.View>(R.id.sourceButtons)
                        sources.requestRectangleOnScreen(Rect(0, 0, sources.width, sources.height), true)
                    }
                    instrumentation.waitForIdleSync()
                    val screenshot = automation.takeScreenshot()
                    File(context.getExternalFilesDir(null), "main-font-$scale.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    screenshot.recycle()
                }
            }
        } finally { setScale(original) }
    }
}
