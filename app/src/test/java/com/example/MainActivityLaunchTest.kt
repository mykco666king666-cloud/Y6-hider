package com.example

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `MainActivity launches without FLAG_SECURE black screen and renders Splash then Onboarding`() {
        composeRule.mainClock.autoAdvance = false

        // Advance past splash initial frames
        repeat(10) {
            composeRule.mainClock.advanceTimeBy(100)
        }

        // Verify FLAG_SECURE is NOT set by default so Streaming Emulator / Preview is never blacked out
        val flags = composeRule.activity.window.attributes.flags
        assertEquals(0, flags and WindowManager.LayoutParams.FLAG_SECURE)

        composeRule.onNodeWithText("OBSIDIAN VAULT").assertIsDisplayed()

        // Advance past splash animation + delay (~1800ms total) into OnboardingScreen
        repeat(25) {
            composeRule.mainClock.advanceTimeBy(100)
        }

        composeRule.onNodeWithText("Your Private Media Sanctuary").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_next_button").assertIsDisplayed()
    }
}
