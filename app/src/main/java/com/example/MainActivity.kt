package com.example

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.db.MediaType
import com.example.ui.navigation.VaultRoute
import com.example.ui.screens.albums.AlbumDetailScreen
import com.example.ui.screens.albums.AlbumsScreen
import com.example.ui.screens.backup.BackupRestoreScreen
import com.example.ui.screens.lock.LockScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.permissions.PermissionManagementScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.screens.storage.StorageManagementScreen
import com.example.ui.screens.vault.VaultHomeScreen
import com.example.ui.screens.viewer.PhotoViewerScreen
import com.example.ui.screens.viewer.VideoPlayerScreen
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianVaultTheme
import com.example.ui.viewmodel.VaultGalleryViewModel
import com.example.ui.viewmodel.VaultSecurityViewModel
import java.util.Locale

class MainActivity : FragmentActivity() {

    private val securityViewModel: VaultSecurityViewModel by viewModels()
    private val galleryViewModel: VaultGalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs by securityViewModel.preferences.collectAsStateWithLifecycle()
            val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()

            // Enforce FLAG_SECURE only when enabled and past splash/onboarding
            val allowSecureWindow = prefs.screenshotProtectionEnabled &&
                securityState.isSplashDone &&
                prefs.onboardingCompleted &&
                prefs.pinConfigured
            LaunchedEffect(allowSecureWindow) {
                if (allowSecureWindow) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Process lifecycle observer for auto-lock on background -> foreground
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> securityViewModel.onAppBackgrounded()
                        Lifecycle.Event.ON_START -> {
                            securityViewModel.refreshPermissions(this@MainActivity)
                            securityViewModel.onAppForegrounded()
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            securityViewModel.refreshPermissions(this@MainActivity)
                        }
                        else -> Unit
                    }
                }
                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                onDispose {
                    ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                }
            }

            val baseContext = LocalContext.current
            val localizedContext = remember(prefs.languageCode, baseContext) {
                createLocalizedContext(baseContext, prefs.languageCode)
            }

            CompositionLocalProvider(LocalContext provides localizedContext) {
                ObsidianVaultTheme(
                    themeMode = prefs.themeMode,
                    accentTheme = prefs.accentTheme
                ) {
                    val stage = when {
                        !securityState.isSplashDone -> AppStage.SPLASH
                        !prefs.onboardingCompleted || !prefs.pinConfigured -> AppStage.ONBOARDING
                        !securityState.isUnlocked && prefs.appLockEnabled -> AppStage.LOCKED
                        else -> AppStage.UNLOCKED
                    }

                    AnimatedContent(
                        targetState = stage,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "appStageTransition"
                    ) { currentStage ->
                        when (currentStage) {
                            AppStage.SPLASH -> {
                                SplashScreen(
                                    onFinished = { securityViewModel.finishSplash() }
                                )
                            }

                            AppStage.ONBOARDING -> {
                                OnboardingScreen(
                                    biometricAvailability = securityState.biometricAvailability,
                                    onCompleteOnboarding = { pin, enableBio, onRecoveryReady ->
                                        securityViewModel.completeOnboardingWithPin(pin, enableBio, onRecoveryReady)
                                    },
                                    onEnterVault = {
                                        securityViewModel.clearGeneratedRecoveryCode()
                                    }
                                )
                            }

                            AppStage.LOCKED -> {
                                LockScreen(
                                    pinLength = prefs.pinLength,
                                    biometricEnabled = prefs.biometricEnabled,
                                    securityState = securityState,
                                    onSubmitPin = securityViewModel::verifyPinAndUnlock,
                                    onClearPinError = securityViewModel::clearPinError,
                                    onBiometricSuccess = securityViewModel::onBiometricSuccess,
                                    onRecoverWithKey = securityViewModel::recoverPinWithCode,
                                    onFactoryReset = securityViewModel::factoryResetVault
                                )
                            }

                            AppStage.UNLOCKED -> {
                                VaultMainShell(
                                    securityViewModel = securityViewModel,
                                    galleryViewModel = galleryViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createLocalizedContext(base: Context, langCode: String): Context {
        val locale = Locale.forLanguageTag(langCode)
        Locale.setDefault(locale)
        val locales = base.resources.configuration.locales
        if (!locales.isEmpty && locales[0].language == locale.language) {
            return base
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        val configContext = base.createConfigurationContext(config)
        return object : ContextWrapper(base) {
            override fun getResources(): Resources = configContext.resources
        }
    }
}

private enum class AppStage {
    SPLASH,
    ONBOARDING,
    LOCKED,
    UNLOCKED
}

private data class TopLevelTab(
    val route: VaultRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun VaultMainShell(
    securityViewModel: VaultSecurityViewModel,
    galleryViewModel: VaultGalleryViewModel
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs = remember {
        listOf(
            TopLevelTab(VaultRoute.VaultHome, R.string.nav_vault, Icons.Filled.Lock, Icons.Outlined.Lock),
            TopLevelTab(VaultRoute.Albums, R.string.nav_albums, Icons.Filled.Folder, Icons.Outlined.Folder),
            TopLevelTab(VaultRoute.Storage, R.string.nav_storage, Icons.Filled.SdStorage, Icons.Outlined.SdStorage),
            TopLevelTab(VaultRoute.Profile, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person),
            TopLevelTab(VaultRoute.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    val isTopLevelDestination = currentDest?.let { dest ->
        tabs.any { dest.hasRoute(it.route::class) }
    } ?: true

    val navigateToTab: (VaultRoute) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route) {
                popUpTo(VaultRoute.VaultHome) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val safePopBackStack: () -> Unit = remember(navController) {
        {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (isTopLevelDestination && !useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelDestination && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = VaultRoute.VaultHome
                    ) {
                        composable<VaultRoute.VaultHome> {
                            val homeContext = LocalContext.current
                            val allMedia by galleryViewModel.allMedia.collectAsStateWithLifecycle()
                            val filteredMedia by galleryViewModel.filteredMedia.collectAsStateWithLifecycle()
                            val albums by galleryViewModel.albumsWithStats.collectAsStateWithLifecycle()
                            val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
                            val prefs by securityViewModel.preferences.collectAsStateWithLifecycle()
                            val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()

                            VaultHomeScreen(
                                allMedia = allMedia,
                                filteredMedia = filteredMedia,
                                albums = albums,
                                uiState = galleryState,
                                currentSortOrder = prefs.sortOrder,
                                permissionsSnapshot = securityState.permissionsSnapshot,
                                mediaFileFor = galleryViewModel::mediaFileFor,
                                thumbFileFor = galleryViewModel::thumbFileFor,
                                onLoadDeviceMedia = galleryViewModel::loadDeviceMedia,
                                onRefreshPermissions = {
                                    securityViewModel.refreshPermissions(
                                        com.example.security.VaultPermissionManager.findActivity(homeContext)
                                    )
                                },
                                onSearchChange = galleryViewModel::setSearchQuery,
                                onFilterChange = galleryViewModel::setFilter,
                                onSortChange = galleryViewModel::setSortOrder,
                                onToggleSelect = galleryViewModel::toggleSelection,
                                onSelectAll = galleryViewModel::selectAll,
                                onClearSelection = galleryViewModel::clearSelection,
                                onOpenMedia = { item ->
                                    if (item.mediaType == MediaType.VIDEO) {
                                        navController.navigate(VaultRoute.VideoPlayer(item.id))
                                    } else {
                                        navController.navigate(VaultRoute.PhotoViewer(item.id, null))
                                    }
                                },
                                onImportUris = { uris -> galleryViewModel.importMediaUris(uris) },
                                onSuppressAutoLock = securityViewModel::suppressNextAutoLockForPicker,
                                onBuildDeleteOriginalsSender = galleryViewModel::buildDeleteOriginalsSender,
                                onAttemptPreRDelete = galleryViewModel::attemptPreROriginalsDelete,
                                onDismissOriginalDeletePrompt = galleryViewModel::dismissOriginalDeletePrompt,
                                onMoveSelectedToAlbum = galleryViewModel::moveSelectedToAlbum,
                                onCreateAlbum = { name -> galleryViewModel.createAlbum(name) },
                                onRestoreSelected = galleryViewModel::restoreToGallery,
                                onDeleteSelected = galleryViewModel::deleteItems,
                                onLockNow = securityViewModel::lockNow
                            )
                        }

                        composable<VaultRoute.Albums> {
                            val albums by galleryViewModel.albumsWithStats.collectAsStateWithLifecycle()
                            AlbumsScreen(
                                albums = albums,
                                thumbDir = galleryViewModel.mediaRepo.vaultThumbsDir,
                                onCreateAlbum = galleryViewModel::createAlbum,
                                onRenameAlbum = galleryViewModel::renameAlbum,
                                onDeleteAlbum = galleryViewModel::deleteAlbum,
                                onOpenAlbum = { albumId ->
                                    navController.navigate(VaultRoute.AlbumDetail(albumId))
                                }
                            )
                        }

                        composable<VaultRoute.AlbumDetail> { entry ->
                            val route = entry.toRoute<VaultRoute.AlbumDetail>()
                            val albums by galleryViewModel.albumsWithStats.collectAsStateWithLifecycle()
                            val allMedia by galleryViewModel.allMedia.collectAsStateWithLifecycle()
                            val albumStats = remember(albums, route.albumId) {
                                albums.find { it.album.id == route.albumId }
                            }
                            val albumMedia = remember(allMedia, route.albumId) {
                                allMedia.filter { it.albumId == route.albumId }
                            }
                            AlbumDetailScreen(
                                album = albumStats,
                                mediaItems = albumMedia,
                                allAlbums = albums,
                                mediaFileFor = galleryViewModel::mediaFileFor,
                                thumbFileFor = galleryViewModel::thumbFileFor,
                                onBack = safePopBackStack,
                                onSuppressAutoLock = securityViewModel::suppressNextAutoLockForPicker,
                                onImportIntoAlbum = { uris ->
                                    galleryViewModel.importMediaUris(uris, targetAlbumId = route.albumId)
                                },
                                onOpenMedia = { item ->
                                    if (item.mediaType == MediaType.VIDEO) {
                                        navController.navigate(VaultRoute.VideoPlayer(item.id))
                                    } else {
                                        navController.navigate(VaultRoute.PhotoViewer(item.id, route.albumId))
                                    }
                                },
                                onMoveMediaToAlbum = galleryViewModel::moveMediaToAlbum,
                                onCreateAlbum = { name -> galleryViewModel.createAlbum(name) },
                                onRestoreMedia = galleryViewModel::restoreToGallery,
                                onDeleteMedia = galleryViewModel::deleteItems
                            )
                        }

                        composable<VaultRoute.Storage> {
                            val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
                            val failedMedia by galleryViewModel.failedMedia.collectAsStateWithLifecycle()
                            LaunchedEffect(Unit) {
                                galleryViewModel.refreshStorageReport()
                            }
                            StorageManagementScreen(
                                storageReport = galleryState.storageReport,
                                integrityReport = galleryState.integrityReport,
                                failedMedia = failedMedia,
                                isCheckingStorage = galleryState.isCheckingStorage,
                                isCheckingIntegrity = galleryState.isCheckingIntegrity,
                                onRefreshStorage = galleryViewModel::refreshStorageReport,
                                onClearSafeCache = galleryViewModel::clearSafeCache,
                                onRunIntegrityCheck = galleryViewModel::runIntegrityCheck,
                                onRecoverFailedImports = galleryViewModel::recoverFailedImports
                            )
                        }

                        composable<VaultRoute.Profile> {
                            val prefs by securityViewModel.preferences.collectAsStateWithLifecycle()
                            val allMedia by galleryViewModel.allMedia.collectAsStateWithLifecycle()
                            val albums by galleryViewModel.albumsWithStats.collectAsStateWithLifecycle()
                            ProfileScreen(
                                preferences = prefs,
                                allMedia = allMedia,
                                albumCount = albums.size,
                                onSuppressAutoLock = securityViewModel::suppressNextAutoLockForPicker,
                                onUpdateProfile = securityViewModel::updateProfile,
                                onSelectCustomAvatar = securityViewModel::updateCustomAvatar,
                                onSelectPresetAvatar = securityViewModel::selectPresetAvatar
                            )
                        }

                        composable<VaultRoute.Settings> {
                            val settingsContext = LocalContext.current
                            val prefs by securityViewModel.preferences.collectAsStateWithLifecycle()
                            val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
                            SettingsScreen(
                                preferences = prefs,
                                biometricAvailability = securityState.biometricAvailability,
                                permissionsSnapshot = securityState.permissionsSnapshot,
                                onRefreshPermissions = {
                                    securityViewModel.refreshPermissions(
                                        com.example.security.VaultPermissionManager.findActivity(settingsContext)
                                    )
                                },
                                onSuppressAutoLock = securityViewModel::suppressNextAutoLockForPicker,
                                onChangePin = securityViewModel::changePin,
                                onToggleBiometric = securityViewModel::setBiometricEnabled,
                                onToggleAppLock = securityViewModel::setAppLockEnabled,
                                onToggleLockOnBackground = securityViewModel::setLockOnBackground,
                                onSelectAutoLockTimeout = securityViewModel::setAutoLockTimeout,
                                onToggleScreenshotProtection = securityViewModel::setScreenshotProtection,
                                onToggleAskDeleteOriginal = securityViewModel::setAskDeleteOriginal,
                                onRegenerateRecoveryCode = securityViewModel::regenerateRecoveryCode,
                                onSelectThemeMode = securityViewModel::setThemeMode,
                                onSelectAccentTheme = securityViewModel::setAccentTheme,
                                onSelectLanguage = securityViewModel::setLanguageCode,
                                onUpdateNotificationPrefs = securityViewModel::setNotificationPrefs,
                                onOpenPermissionsScreen = { navController.navigate(VaultRoute.Permissions) },
                                onOpenStorageManagement = { navigateToTab(VaultRoute.Storage) },
                                onOpenBackupRestore = { navController.navigate(VaultRoute.BackupRestore) }
                            )
                        }

                        composable<VaultRoute.Permissions> {
                            val permContext = LocalContext.current
                            val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
                            PermissionManagementScreen(
                                snapshot = securityState.permissionsSnapshot,
                                onBack = safePopBackStack,
                                onRefreshPermissions = {
                                    securityViewModel.refreshPermissions(
                                        com.example.security.VaultPermissionManager.findActivity(permContext)
                                    )
                                },
                                onSuppressAutoLock = securityViewModel::suppressNextAutoLockForPicker
                            )
                        }

                        composable<VaultRoute.BackupRestore> {
                            val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
                            val backupRecords by galleryViewModel.backupRecords.collectAsStateWithLifecycle()
                            BackupRestoreScreen(
                                isBusy = galleryState.isBackupBusy,
                                pendingPreview = galleryState.pendingBackupPreview,
                                backupRecords = backupRecords,
                                onBack = safePopBackStack,
                                onSuppressAutoLock = securityViewModel::suppressNextAutoLockForPicker,
                                onExportBackup = galleryViewModel::exportBackup,
                                onInspectBackup = galleryViewModel::inspectBackupForRestore,
                                onConfirmRestore = galleryViewModel::confirmApplyBackup,
                                onCancelRestorePreview = galleryViewModel::cancelPendingBackupPreview
                            )
                        }

                        composable<VaultRoute.PhotoViewer> { entry ->
                            val route = entry.toRoute<VaultRoute.PhotoViewer>()
                            val allMedia by galleryViewModel.allMedia.collectAsStateWithLifecycle()
                            val albums by galleryViewModel.albumsWithStats.collectAsStateWithLifecycle()
                            val photos = remember(allMedia, route.albumId) {
                                allMedia.filter {
                                    it.mediaType == MediaType.PHOTO &&
                                        (route.albumId == null || it.albumId == route.albumId)
                                }
                            }
                            PhotoViewerScreen(
                                photos = photos,
                                initialPhotoId = route.mediaId,
                                albums = albums,
                                mediaFileFor = galleryViewModel::mediaFileFor,
                                onBack = safePopBackStack,
                                onToggleFavorite = galleryViewModel::toggleFavorite,
                                onMoveToAlbum = { id, targetAlbum ->
                                    galleryViewModel.moveMediaToAlbum(listOf(id), targetAlbum)
                                },
                                onCreateAlbum = { name -> galleryViewModel.createAlbum(name) },
                                onRestoreToGallery = { item, remove ->
                                    galleryViewModel.restoreToGallery(listOf(item), remove)
                                },
                                onDeletePhoto = { item ->
                                    galleryViewModel.deleteItems(listOf(item))
                                }
                            )
                        }

                        composable<VaultRoute.VideoPlayer> { entry ->
                            val route = entry.toRoute<VaultRoute.VideoPlayer>()
                            val allMedia by galleryViewModel.allMedia.collectAsStateWithLifecycle()
                            val albums by galleryViewModel.albumsWithStats.collectAsStateWithLifecycle()
                            val video = remember(allMedia, route.mediaId) {
                                allMedia.find { it.id == route.mediaId }
                            }
                            VideoPlayerScreen(
                                video = video,
                                albums = albums,
                                mediaFileFor = galleryViewModel::mediaFileFor,
                                onBack = safePopBackStack,
                                onToggleFavorite = galleryViewModel::toggleFavorite,
                                onRestoreToGallery = { item, remove ->
                                    galleryViewModel.restoreToGallery(listOf(item), remove)
                                },
                                onDeleteVideo = { item ->
                                    galleryViewModel.deleteItems(listOf(item))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    VaultSnackbarAndRecoveryEffects(
        securityViewModel = securityViewModel,
        galleryViewModel = galleryViewModel,
        snackbarHostState = snackbarHostState
    )
    }
}

@Composable
private fun VaultSnackbarAndRecoveryEffects(
    securityViewModel: VaultSecurityViewModel,
    galleryViewModel: VaultGalleryViewModel,
    snackbarHostState: SnackbarHostState
) {
    val galleryState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val securityState by securityViewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(galleryState.toastMessage) {
        galleryState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            galleryViewModel.clearToast()
        }
    }
    LaunchedEffect(securityState.statusBanner) {
        securityState.statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            securityViewModel.clearStatusBanner()
        }
    }

    securityState.generatedRecoveryCode?.let { code ->
        com.example.ui.components.VaultModalDialog(
            onDismissRequest = { securityViewModel.clearGeneratedRecoveryCode() },
            title = { Text("New Recovery Key Generated") },
            text = {
                Text(
                    "Save this recovery key securely offline. Your previous key is no longer valid:\n\n$code",
                    fontFamily = MonoFontFamily
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        securityViewModel.clearGeneratedRecoveryCode()
                    }
                ) {
                    Text("Copy & Close")
                }
            }
        )
    }
}
