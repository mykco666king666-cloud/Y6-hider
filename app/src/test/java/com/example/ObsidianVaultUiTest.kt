package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.security.BiometricAvailability
import com.example.ui.screens.lock.LockScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.theme.ObsidianVaultTheme
import com.example.ui.viewmodel.SecurityUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ObsidianVaultUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `onboarding flow creates PIN, shows recovery key, and unlocks vault`() {
        var capturedPin = ""
        var enteredVault = false

        composeRule.setContent {
            ObsidianVaultTheme {
                OnboardingScreen(
                    biometricAvailability = BiometricAvailability.UNAVAILABLE,
                    onCompleteOnboarding = { pin, _, onRecoveryReady ->
                        capturedPin = pin
                        onRecoveryReady("OVLT-TEST-1234-ABCD")
                    },
                    onEnterVault = { enteredVault = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.waitForIdle()

        // Enter PIN 2580
        listOf(2, 5, 8, 0).forEach { digit ->
            composeRule.onNodeWithTag("pin_key_$digit").performScrollTo().performClick()
        }
        composeRule.waitForIdle()

        // Confirm PIN 2580
        listOf(2, 5, 8, 0).forEach { digit ->
            composeRule.onNodeWithTag("pin_key_$digit").performScrollTo().performClick()
        }
        composeRule.waitForIdle()

        assertEquals("2580", capturedPin)
        composeRule.onNodeWithText("OVLT-TEST-1234-ABCD").assertIsDisplayed()

        composeRule.onNodeWithTag("recovery_saved_checkbox").performScrollTo().performClick()
        composeRule.onNodeWithTag("enter_vault_button").assertIsEnabled().performClick()
        assertTrue(enteredVault)
    }

    @Test
    fun `lock screen submits entered PIN and exposes forgot PIN recovery`() {
        var submittedPin = ""
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            ObsidianVaultTheme {
                LockScreen(
                    pinLength = 4,
                    biometricEnabled = false,
                    securityState = SecurityUiState(pinError = null),
                    onSubmitPin = { submittedPin = it },
                    onClearPinError = {},
                    onBiometricSuccess = {},
                    onRecoverWithKey = { _, _ -> Result.success(Unit) },
                    onFactoryReset = {}
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(200)
        listOf(1, 2, 3, 4).forEach { digit ->
            composeRule.onNodeWithTag("pin_key_$digit").performScrollTo().performClick()
            composeRule.mainClock.advanceTimeBy(100)
        }
        assertEquals("1234", submittedPin)

        composeRule.onNodeWithTag("forgot_pin_button").performScrollTo().performClick()
        repeat(10) {
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.onNodeWithText("Recover Vault Access").assertExists()
    }
}
