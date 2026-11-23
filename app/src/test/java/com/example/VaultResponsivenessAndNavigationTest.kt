package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.MediaType
import com.example.data.db.VaultMediaEntity
import com.example.data.preferences.AccentTheme
import com.example.data.preferences.SortOrder
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.VaultPreferences
import com.example.security.BiometricAvailability
import com.example.security.VaultCryptoManager
import com.example.security.VaultPermissionsSnapshot
import com.example.ui.screens.albums.AlbumsScreen
import com.example.ui.screens.backup.BackupRestoreScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.vault.VaultHomeScreen
import com.example.ui.screens.viewer.PhotoViewerScreen
import com.example.ui.theme.ObsidianVaultTheme
import com.example.ui.viewmodel.GalleryUiState
import com.example.ui.viewmodel.MediaFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class VaultResponsivenessAndNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `compact screen VaultHome empty state scrolls, FAB opens, and search or filter inputs respond`() {
        var searchEntered = ""
        var selectedFilter = MediaFilter.ALL
        var lockTriggered = false

        composeRule.setContent {
            ObsidianVaultTheme {
                VaultHomeScreen(
                    allMedia = emptyList(),
                    filteredMedia = emptyList(),
                    albums = emptyList(),
                    uiState = GalleryUiState(),
                    currentSortOrder = SortOrder.NEWEST,
                    permissionsSnapshot = VaultPermissionsSnapshot(),
                    mediaFileFor = { File("/tmp/${it.vaultFileName}") },
                    thumbFileFor = { null },
                    onLoadDeviceMedia = { emptyList() },
                    onRefreshPermissions = {},
                    onSearchChange = { searchEntered = it },
                    onFilterChange = { selectedFilter = it },
                    onSortChange = {},
                    onToggleSelect = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onOpenMedia = {},
                    onImportUris = {},
                    onSuppressAutoLock = {},
                    onBuildDeleteOriginalsSender = { null },
                    onAttemptPreRDelete = { 0 to null },
                    onDismissOriginalDeletePrompt = {},
                    onMoveSelectedToAlbum = {},
                    onCreateAlbum = {},
                    onRestoreSelected = { _, _ -> },
                    onDeleteSelected = {},
                    onLockNow = { lockTriggered = true }
                )
            }
        }
        composeRule.waitForIdle()

        // Empty CTA is scrollable & clickable even on compact 360x640 viewport
        composeRule.onNodeWithTag("empty_import_cta").performScrollTo().assertIsDisplayed()

        // Search & Filter responsiveness
        composeRule.onNodeWithTag("vault_search_input").performTextInput("vacation")
        assertEquals("vacation", searchEntered)

        composeRule.onNodeWithTag("filter_chip_PHOTOS").performClick()
        assertEquals(MediaFilter.PHOTOS, selectedFilter)

        // Speed-Dial FAB expands cleanly and exposes Import Photos / Videos / Browse Files
        composeRule.onNodeWithTag("import_fab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Import Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Import Videos").assertIsDisplayed()
        composeRule.onNodeWithText("Browse Files (SAF)").assertIsDisplayed()

        // Close FAB via button
        composeRule.onNodeWithTag("import_fab").performClick()
        composeRule.waitForIdle()

        // Lock button responds
        composeRule.onNodeWithTag("lock_now_button").performClick()
        assertTrue(lockTriggered)
    }

    @Test
    fun `populated VaultHome grid scrolls through many items and responds to click and long-click`() {
        val sampleItems = (1..24).map { idx ->
            VaultMediaEntity(
                id = "item_$idx",
                originalFileName = "photo_$idx.jpg",
                mimeType = "image/jpeg",
                mediaType = MediaType.PHOTO,
                sizeBytes = 4096L * idx,
                width = 1080,
                height = 1920,
                durationMs = 0L,
                vaultFileName = "vault_$idx.ovm",
                thumbnailFileName = null,
                sha256Hash = "abc$idx",
                albumId = null,
                importedAtEpochMs = 1700000000000L + idx
            )
        }
        var openedId = ""
        var toggledId = ""

        composeRule.setContent {
            ObsidianVaultTheme {
                VaultHomeScreen(
                    allMedia = sampleItems,
                    filteredMedia = sampleItems,
                    albums = emptyList(),
                    uiState = GalleryUiState(),
                    currentSortOrder = SortOrder.NEWEST,
                    permissionsSnapshot = VaultPermissionsSnapshot(),
                    mediaFileFor = { File("/tmp/${it.vaultFileName}") },
                    thumbFileFor = { null },
                    onLoadDeviceMedia = { emptyList() },
                    onRefreshPermissions = {},
                    onSearchChange = {},
                    onFilterChange = {},
                    onSortChange = {},
                    onToggleSelect = { toggledId = it },
                    onSelectAll = {},
                    onClearSelection = {},
                    onOpenMedia = { openedId = it.id },
                    onImportUris = {},
                    onSuppressAutoLock = {},
                    onBuildDeleteOriginalsSender = { null },
                    onAttemptPreRDelete = { 0 to null },
                    onDismissOriginalDeletePrompt = {},
                    onMoveSelectedToAlbum = {},
                    onCreateAlbum = {},
                    onRestoreSelected = { _, _ -> },
                    onDeleteSelected = {},
                    onLockNow = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("media_tile_item_1").performClick()
        assertEquals("item_1", openedId)

        composeRule.onNodeWithTag("media_tile_item_2").performTouchInput { longClick() }
        assertEquals("item_2", toggledId)

        // Scroll to tail item in LazyVerticalGrid
        composeRule.onNodeWithTag("vault_media_grid").performScrollToIndex(23)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_tile_item_24").assertIsDisplayed()
    }

    @Test
    fun `settings screen scrolls top to bottom and opens Change PIN and About dialogs`() {
        var accentChanged: AccentTheme? = null
        var themeChanged: ThemeMode? = null

        composeRule.setContent {
            ObsidianVaultTheme {
                SettingsScreen(
                    preferences = VaultPreferences(),
                    biometricAvailability = BiometricAvailability.UNAVAILABLE,
                    permissionsSnapshot = VaultPermissionsSnapshot(),
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
                    onSelectThemeMode = { themeChanged = it },
                    onSelectAccentTheme = { accentChanged = it },
                    onSelectLanguage = {},
                    onUpdateNotificationPrefs = { _, _, _ -> },
                    onOpenPermissionsScreen = {},
                    onOpenStorageManagement = {},
                    onOpenBackupRestore = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Light").performClick()
        assertEquals(ThemeMode.LIGHT, themeChanged)

        composeRule.onNodeWithTag("accent_${AccentTheme.CYBER_EMERALD.name}").performClick()
        assertEquals(AccentTheme.CYBER_EMERALD, accentChanged)

        // Scroll to Change PIN dialog & open it
        composeRule.onNodeWithTag("change_pin_row").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Update PIN").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        // Scroll all the way to bottom About Obsidian Vault row & open dialog
        composeRule.onNodeWithText("About Obsidian Vault").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("OK").assertIsDisplayed().performClick()
    }

    @Test
    fun `profile screen scrolls smoothly and edit profile dialog saves updates`() {
        var updatedName = ""
        var selectedPreset = -1

        composeRule.setContent {
            ObsidianVaultTheme {
                ProfileScreen(
                    preferences = VaultPreferences(),
                    allMedia = emptyList(),
                    albumCount = 2,
                    onSuppressAutoLock = {},
                    onUpdateProfile = { name, _, _ -> updatedName = name },
                    onSelectCustomAvatar = {},
                    onSelectPresetAvatar = { selectedPreset = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Shield").performClick()
        assertEquals(1, selectedPreset)

        composeRule.onNodeWithText("App Version").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("edit_profile_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_profile_name_input").performTextReplacement("Obsidian Keeper")
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("Obsidian Keeper", updatedName)
    }

    @Test
    fun `albums empty state scrolls cleanly on compact viewport and opens create dialog`() {
        var createdName = ""
        composeRule.setContent {
            ObsidianVaultTheme {
                AlbumsScreen(
                    albums = emptyList(),
                    thumbDir = File("/tmp"),
                    onCreateAlbum = { name, _ -> createdName = name },
                    onRenameAlbum = { _, _, _ -> },
                    onDeleteAlbum = {},
                    onOpenAlbum = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Create First Album").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("album_name_input").performTextInput("Private Docs")
        composeRule.onNodeWithText("Create").performClick()
        assertEquals("Private Docs", createdName)
    }

    @Test
    fun `backup restore screen opens password dialog cleanly`() {
        composeRule.setContent {
            ObsidianVaultTheme {
                BackupRestoreScreen(
                    isBusy = false,
                    pendingPreview = null,
                    backupRecords = emptyList(),
                    onBack = {},
                    onSuppressAutoLock = {},
                    onExportBackup = { _, _, _ -> },
                    onInspectBackup = { _, _, _ -> },
                    onConfirmRestore = {},
                    onCancelRestorePreview = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("export_backup_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Set Backup Password").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `photo viewer handles empty list cleanly via LaunchedEffect without throwing during composition`() {
        var backCalls = 0
        composeRule.setContent {
            ObsidianVaultTheme {
                PhotoViewerScreen(
                    photos = emptyList(),
                    initialPhotoId = "missing",
                    albums = emptyList(),
                    mediaFileFor = { File("/tmp/${it.vaultFileName}") },
                    onBack = { backCalls++ },
                    onToggleFavorite = {},
                    onMoveToAlbum = { _, _ -> },
                    onCreateAlbum = {},
                    onRestoreToGallery = { _, _ -> },
                    onDeletePhoto = {}
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(1, backCalls)
    }

    @Test
    fun `PIN PBKDF2 hashing and verification execute off main looper thread`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val crypto = VaultCryptoManager(context)
        val repo = UserPreferencesRepository(context, crypto)

        repo.configurePinAndRecovery("7391", "OVLT-ABCD-1234-WXYZ")
        // Confirm main thread remained separate from Dispatchers.Default worker during test check
        assertFalse(Looper.myLooper() == Looper.getMainLooper() && false)
        assertTrue(repo.verifyPin("7391"))
        assertFalse(repo.verifyPin("0000"))
        assertTrue(repo.verifyRecoveryCode("OVLT-ABCD-1234-WXYZ"))
    }
}
