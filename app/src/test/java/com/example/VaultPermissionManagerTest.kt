package com.example

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.MediaType
import com.example.data.preferences.VaultPreferences
import com.example.data.storage.DeviceMediaCandidate
import com.example.security.BiometricAvailability
import com.example.security.PermissionAccessStatus
import com.example.security.VaultPermissionManager
import com.example.security.VaultPermissionsSnapshot
import com.example.ui.components.DeviceGalleryPickerSheet
import com.example.ui.components.MediaPermissionDeniedDialog
import com.example.ui.screens.permissions.PermissionManagementScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.ObsidianVaultTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class VaultPermissionManagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("vault_permission_prompt_history", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        Shadows.shadowOf(app).denyPermissions(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val appOps = app.getSystemService(AppOpsManager::class.java)
        Shadows.shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            app.packageName,
            AppOpsManager.MODE_IGNORED
        )
        val nm = app.getSystemService(NotificationManager::class.java)
        Shadows.shadowOf(nm).setNotificationsEnabled(true)
    }

    @Test
    fun `android 14+ detects allowed, limited selected photos & videos, and denied states`() {
        // Initially denied
        assertEquals(
            PermissionAccessStatus.NOT_ALLOWED,
            VaultPermissionManager.checkPhotosStatus(app, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
        assertEquals(
            PermissionAccessStatus.NOT_ALLOWED,
            VaultPermissionManager.checkVideosStatus(app, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )

        // Limited Selection Access granted on Android 14+
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        assertEquals(
            PermissionAccessStatus.LIMITED_ACCESS,
            VaultPermissionManager.checkPhotosStatus(app, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
        assertEquals(
            PermissionAccessStatus.LIMITED_ACCESS,
            VaultPermissionManager.checkVideosStatus(app, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )

        // Full photo access granted while video remains limited
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        assertEquals(
            PermissionAccessStatus.ALLOWED,
            VaultPermissionManager.checkPhotosStatus(app, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
        assertEquals(
            PermissionAccessStatus.LIMITED_ACCESS,
            VaultPermissionManager.checkVideosStatus(app, Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )

        // Request permission arrays include selected media permission on Android 14+
        assertArrayEquals(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ),
            VaultPermissionManager.photosRequestPermissions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
        assertArrayEquals(
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ),
            VaultPermissionManager.videosRequestPermissions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        )
    }

    @Test
    fun `android 13, 12, and 9 permission rules resolve accurately per API level`() {
        // Android 13 (API 33): granular media + notifications
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.POST_NOTIFICATIONS
        )
        assertEquals(
            PermissionAccessStatus.ALLOWED,
            VaultPermissionManager.checkPhotosStatus(app, Build.VERSION_CODES.TIRAMISU)
        )
        assertEquals(
            PermissionAccessStatus.NOT_ALLOWED,
            VaultPermissionManager.checkVideosStatus(app, Build.VERSION_CODES.TIRAMISU)
        )
        assertEquals(
            PermissionAccessStatus.ALLOWED,
            VaultPermissionManager.checkNotificationsStatus(app, Build.VERSION_CODES.TIRAMISU)
        )

        // Android 12 (API 32): READ_EXTERNAL_STORAGE + scoped storage NOT_REQUIRED + notifications NOT_REQUIRED
        assertEquals(
            PermissionAccessStatus.NOT_ALLOWED,
            VaultPermissionManager.checkPhotosStatus(app, Build.VERSION_CODES.S_V2)
        )
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertEquals(
            PermissionAccessStatus.ALLOWED,
            VaultPermissionManager.checkPhotosStatus(app, Build.VERSION_CODES.S_V2)
        )
        assertEquals(
            PermissionAccessStatus.ALLOWED,
            VaultPermissionManager.checkVideosStatus(app, Build.VERSION_CODES.S_V2)
        )
        assertEquals(
            PermissionAccessStatus.NOT_REQUIRED,
            VaultPermissionManager.checkNotificationsStatus(app, Build.VERSION_CODES.S_V2)
        )
        assertEquals(
            PermissionAccessStatus.NOT_REQUIRED,
            VaultPermissionManager.checkStorageStatus(app, Build.VERSION_CODES.S_V2)
        )

        // Android 9 (API 28): WRITE_EXTERNAL_STORAGE checked
        assertEquals(
            PermissionAccessStatus.NOT_ALLOWED,
            VaultPermissionManager.checkStorageStatus(app, Build.VERSION_CODES.P)
        )
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        assertEquals(
            PermissionAccessStatus.ALLOWED,
            VaultPermissionManager.checkStorageStatus(app, Build.VERSION_CODES.P)
        )
    }

    @Test
    fun `usage access AppOps status and permanent denial refresh cleanly on resume`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Initially not granted & not yet prompted -> not permanently denied
        val initial = VaultPermissionManager.inspectAll(app, activity)
        assertEquals(PermissionAccessStatus.NOT_ALLOWED, initial.usageAccess)
        assertFalse(initial.photosPermanentlyDenied)

        // After user was prompted & rationale isn't shown, permanent denial flips true
        VaultPermissionManager.markPhotosPrompted(app)
        val afterDenial = VaultPermissionManager.inspectAll(app, activity)
        assertTrue(afterDenial.photosPermanentlyDenied)

        // Returning from Android Settings with grants updates inspectAll live
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES)
        val appOps = app.getSystemService(AppOpsManager::class.java)
        Shadows.shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            app.packageName,
            AppOpsManager.MODE_ALLOWED
        )
        val afterSettingsReturn = VaultPermissionManager.inspectAll(app, activity)
        assertEquals(PermissionAccessStatus.ALLOWED, afterSettingsReturn.photosAccess)
        assertFalse(afterSettingsReturn.photosPermanentlyDenied)
        assertEquals(PermissionAccessStatus.ALLOWED, afterSettingsReturn.usageAccess)
    }

    @Test
    fun `permission management screen displays all 6 permissions, accurate badges, and real settings intents`() {
        val snapshot = VaultPermissionsSnapshot(
            photosAccess = PermissionAccessStatus.LIMITED_ACCESS,
            videosAccess = PermissionAccessStatus.NOT_ALLOWED,
            videosPermanentlyDenied = true,
            usageAccess = PermissionAccessStatus.NOT_ALLOWED,
            notificationsAccess = PermissionAccessStatus.NOT_ALLOWED,
            notificationsPermanentlyDenied = true,
            storageAccess = PermissionAccessStatus.NOT_REQUIRED,
            biometricAvailability = BiometricAvailability.AVAILABLE,
            biometricAccess = PermissionAccessStatus.ALLOWED
        )
        var refreshCount = 0

        composeRule.setContent {
            ObsidianVaultTheme {
                PermissionManagementScreen(
                    snapshot = snapshot,
                    onBack = {},
                    onRefreshPermissions = { refreshCount++ },
                    onSuppressAutoLock = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Photos Access").assertIsDisplayed()
        composeRule.onNodeWithText("Videos Access").assertIsDisplayed()
        composeRule.onNodeWithText("Usage Access").assertIsDisplayed()
        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Storage Access").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Biometric Availability").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Limited Access").assertIsDisplayed()
        composeRule.onNodeWithText("Not Required").assertExists()
        composeRule.onNodeWithText("Allowed").assertExists()

        // Tapping Manage Usage Access launches real Android Usage Access Settings intent
        composeRule.onNodeWithTag("manage_usage_permission").performScrollTo().performClick()
        val usageIntent: Intent? = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(usageIntent)
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, usageIntent?.action)

        // Tapping Manage Notifications launches real Android Notification Settings intent
        composeRule.onNodeWithTag("manage_notifications_permission").performScrollTo().performClick()
        val notifIntent: Intent? = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(notifIntent)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifIntent?.action)
        assertEquals(app.packageName, notifIntent?.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        // Refresh button triggers live re-inspection
        composeRule.onNodeWithTag("refresh_permissions_button").performClick()
        assertTrue(refreshCount >= 1)
    }

    @Test
    fun `settings screen shows Usage Access, Permission Management row, and real Settings intents`() {
        var openedPermissionsScreen = false
        val snapshot = VaultPermissionsSnapshot(
            photosAccess = PermissionAccessStatus.ALLOWED,
            videosAccess = PermissionAccessStatus.LIMITED_ACCESS,
            usageAccess = PermissionAccessStatus.ALLOWED,
            notificationsAccess = PermissionAccessStatus.NOT_ALLOWED,
            storageAccess = PermissionAccessStatus.NOT_REQUIRED,
            biometricAvailability = BiometricAvailability.NONE_ENROLLED,
            biometricAccess = PermissionAccessStatus.NOT_ALLOWED
        )

        composeRule.setContent {
            ObsidianVaultTheme {
                SettingsScreen(
                    preferences = VaultPreferences(),
                    biometricAvailability = BiometricAvailability.NONE_ENROLLED,
                    permissionsSnapshot = snapshot,
                    onRefreshPermissions = {},
                    onSuppressAutoLock = {},
                    onChangePin = { _, _ -> Result.success(Unit) },
                    onToggleBiometric = {},
                    onToggleAppLock = {},
                    onToggleLockOnBackground = {},
                    onSelectAutoLockTimeout = {},
                    onToggleScreenshotProtection = {},
                    onToggleAskDeleteOriginal = {},
                    onRegenerateRecoveryCode = {},
                    onSelectThemeMode = {},
                    onSelectAccentTheme = {},
                    onSelectLanguage = {},
                    onUpdateNotificationPrefs = { _, _, _ -> },
                    onOpenPermissionsScreen = { openedPermissionsScreen = true },
                    onOpenStorageManagement = {},
                    onOpenBackupRestore = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("open_permissions_row").performScrollTo().performClick()
        assertTrue(openedPermissionsScreen)

        composeRule.onNodeWithTag("settings_usage_access_badge").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_manage_usage_access_button").performScrollTo().performClick()
        val launchedUsage = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(launchedUsage)
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, launchedUsage?.action)

        composeRule.onNodeWithTag("settings_notification_settings_button").performScrollTo().performClick()
        val launchedNotif = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(launchedNotif)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, launchedNotif?.action)
    }

    @Test
    fun `denied dialog handles permanently denied Open App Settings and photo picker fallback`() {
        var settingsOpened = false
        var pickerOpened = false

        composeRule.setContent {
            ObsidianVaultTheme {
                MediaPermissionDeniedDialog(
                    mediaType = MediaType.PHOTO,
                    permanentlyDenied = true,
                    onRetryPermission = {},
                    onOpenAppSettings = { settingsOpened = true },
                    onUsePhotoPicker = { pickerOpened = true },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_app_settings_button").performClick()
        assertTrue(settingsOpened)
        assertFalse(pickerOpened)
    }

    @Test
    fun `limited gallery picker sheet supports select more and real URI import`() {
        val candidateUri = Uri.parse("content://media/external/images/media/101")
        var imported: List<Uri> = emptyList()
        var selectMoreClicked = false
        var pickerOpened = false

        composeRule.setContent {
            ObsidianVaultTheme {
                DeviceGalleryPickerSheet(
                    mediaType = MediaType.PHOTO,
                    accessStatus = PermissionAccessStatus.LIMITED_ACCESS,
                    refreshKey = 1,
                    onLoadDeviceMedia = {
                        listOf(
                            DeviceMediaCandidate(
                                uri = candidateUri,
                                mediaType = MediaType.PHOTO,
                                displayName = "IMG_TEST.jpg",
                                sizeBytes = 2048L,
                                durationMs = 0L,
                                dateAddedSec = 1700000000L
                            )
                        )
                    },
                    onImportSelectedUris = { imported = it },
                    onManageLimitedSelection = { selectMoreClicked = true },
                    onOpenAppSettings = {},
                    onOpenSystemPhotoPicker = { pickerOpened = true },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Limited Selection Active").assertIsDisplayed()
        composeRule.onNodeWithText("Select More").performClick()
        assertTrue(selectMoreClicked)

        composeRule.onNodeWithText("Select Photos to Import").assertIsDisplayed()
        composeRule.onNodeWithTag("switch_to_photo_picker_button").assertIsDisplayed()
        composeRule.onNode(
            androidx.compose.ui.test.hasContentDescription("IMG_TEST.jpg")
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Encrypt & Import (1)").performClick()
        assertEquals(listOf(candidateUri), imported)
        assertFalse(pickerOpened)
    }
}
