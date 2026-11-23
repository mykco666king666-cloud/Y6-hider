package com.example.ui.screens.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.security.BiometricAvailability
import com.example.security.PermissionAccessStatus
import com.example.security.VaultPermissionManager
import com.example.security.VaultPermissionsSnapshot
import com.example.ui.components.PermissionStatusBadge
import com.example.ui.components.VaultPremiumCard
import com.example.util.FormatUtils

@Composable
fun PermissionManagementScreen(
    snapshot: VaultPermissionsSnapshot,
    onBack: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onSuppressAutoLock: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Automatically refresh live Android permission states on every ON_RESUME (e.g. returning from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val photosPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        VaultPermissionManager.markPhotosPrompted(context)
        onRefreshPermissions()
    }

    val videosPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        VaultPermissionManager.markVideosPrompted(context)
        onRefreshPermissions()
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        VaultPermissionManager.markNotificationsPrompted(context)
        onRefreshPermissions()
    }

    val legacyStoragePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        VaultPermissionManager.markStoragePrompted(context)
        onRefreshPermissions()
    }

    val statuses = listOf(
        snapshot.photosAccess,
        snapshot.videosAccess,
        snapshot.usageAccess,
        snapshot.notificationsAccess,
        snapshot.storageAccess,
        snapshot.biometricAccess
    )
    val allowedCount = statuses.count { it == PermissionAccessStatus.ALLOWED }
    val limitedCount = statuses.count { it == PermissionAccessStatus.LIMITED_ACCESS }
    val blockedCount = statuses.count { it == PermissionAccessStatus.NOT_ALLOWED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("permissions_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Permission Management",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onRefreshPermissions,
                modifier = Modifier.testTag("refresh_permissions_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh permission statuses")
            }
        }

        // Live Overview Banner
        VaultPremiumCard(highlightBorder = true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Android Permission Audit", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$allowedCount Allowed • $limitedCount Limited • $blockedCount Not Allowed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Every badge below reflects live Android system state (Android API ${Build.VERSION.SDK_INT}) and updates automatically when you return from Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Photos Access
        PermissionEntryCard(
            icon = Icons.Default.PhotoLibrary,
            title = "Photos Access",
            status = snapshot.photosAccess,
            description = when (snapshot.photosAccess) {
                PermissionAccessStatus.ALLOWED ->
                    "Full access granted to browse and batch-encrypt device photos into the vault."
                PermissionAccessStatus.LIMITED_ACCESS ->
                    "Android 14+ Limited Access active. Only photos you selected are visible; tap Manage Permission to add more."
                PermissionAccessStatus.NOT_ALLOWED ->
                    if (snapshot.photosPermanentlyDenied) {
                        "Denied permanently. Enable in App Settings to browse device photos directly, or use the zero-permission Android Photo Picker."
                    } else {
                        "Not granted. Required only if you want to browse device gallery photos directly; Android Photo Picker also works without permission."
                    }
                PermissionAccessStatus.NOT_REQUIRED -> "No permission needed on this Android version."
            },
            primaryButtonLabel = when {
                snapshot.photosAccess == PermissionAccessStatus.ALLOWED -> "Manage in App Settings"
                snapshot.photosAccess == PermissionAccessStatus.LIMITED_ACCESS -> "Add More Photos"
                snapshot.photosPermanentlyDenied -> "Open App Settings"
                else -> "Manage Permission"
            },
            primaryTestTag = "manage_photos_permission",
            onPrimaryClick = {
                onSuppressAutoLock()
                when {
                    snapshot.photosAccess == PermissionAccessStatus.ALLOWED || snapshot.photosPermanentlyDenied -> {
                        VaultPermissionManager.openAppSettings(context)
                    }
                    else -> {
                        photosPermLauncher.launch(VaultPermissionManager.photosRequestPermissions())
                    }
                }
            },
            secondaryButtonLabel = if (snapshot.photosAccess == PermissionAccessStatus.LIMITED_ACCESS) "App Settings" else null,
            onSecondaryClick = {
                onSuppressAutoLock()
                VaultPermissionManager.openAppSettings(context)
            }
        )

        // 2. Videos Access
        PermissionEntryCard(
            icon = Icons.Default.VideoLibrary,
            title = "Videos Access",
            status = snapshot.videosAccess,
            description = when (snapshot.videosAccess) {
                PermissionAccessStatus.ALLOWED ->
                    "Full access granted to browse and encrypt device videos inside Obsidian Vault."
                PermissionAccessStatus.LIMITED_ACCESS ->
                    "Android 14+ Limited Access active. Only selected videos are accessible; tap Manage Permission to expand your selection."
                PermissionAccessStatus.NOT_ALLOWED ->
                    if (snapshot.videosPermanentlyDenied) {
                        "Denied permanently. Enable Videos access in App Settings, or use the zero-permission Android Photo Picker."
                    } else {
                        "Not granted. Allows direct device video browsing for vault import; Android Photo Picker remains available without permission."
                    }
                PermissionAccessStatus.NOT_REQUIRED -> "No permission needed on this Android version."
            },
            primaryButtonLabel = when {
                snapshot.videosAccess == PermissionAccessStatus.ALLOWED -> "Manage in App Settings"
                snapshot.videosAccess == PermissionAccessStatus.LIMITED_ACCESS -> "Add More Videos"
                snapshot.videosPermanentlyDenied -> "Open App Settings"
                else -> "Manage Permission"
            },
            primaryTestTag = "manage_videos_permission",
            onPrimaryClick = {
                onSuppressAutoLock()
                when {
                    snapshot.videosAccess == PermissionAccessStatus.ALLOWED || snapshot.videosPermanentlyDenied -> {
                        VaultPermissionManager.openAppSettings(context)
                    }
                    else -> {
                        videosPermLauncher.launch(VaultPermissionManager.videosRequestPermissions())
                    }
                }
            },
            secondaryButtonLabel = if (snapshot.videosAccess == PermissionAccessStatus.LIMITED_ACCESS) "App Settings" else null,
            onSecondaryClick = {
                onSuppressAutoLock()
                VaultPermissionManager.openAppSettings(context)
            }
        )

        // 3. Usage Access
        PermissionEntryCard(
            icon = Icons.Default.Analytics,
            title = "Usage Access",
            status = snapshot.usageAccess,
            description = buildString {
                append("Optional special system access for foreground app-switch security diagnostics via Android UsageStatsManager. ")
                append("Never required to import, encrypt, or view photos and videos.")
                snapshot.usageLastForegroundMs?.let { ts ->
                    append("\nVerified active telemetry: last session recorded ${FormatUtils.formatDateTime(ts)}.")
                }
            },
            primaryButtonLabel = "Manage Permission",
            primaryTestTag = "manage_usage_permission",
            onPrimaryClick = {
                onSuppressAutoLock()
                VaultPermissionManager.openUsageAccessSettings(context)
            }
        )

        // 4. Notifications
        PermissionEntryCard(
            icon = Icons.Default.Notifications,
            title = "Notifications",
            status = snapshot.notificationsAccess,
            description = when (snapshot.notificationsAccess) {
                PermissionAccessStatus.ALLOWED ->
                    "Notifications enabled for large media import completions, encrypted .ovbackup alerts, and PIN lockout warnings."
                PermissionAccessStatus.NOT_REQUIRED ->
                    "Runtime notification permission is not required on Android 12 and below; local vault notifications are active."
                else ->
                    if (snapshot.notificationsPermanentlyDenied) {
                        "Blocked in Android Settings. Open Notification Settings to receive import completion, backup, and security alerts."
                    } else {
                        "Used only to alert you when large encrypted imports, backups, or PIN cooldown events complete. No media previews are ever shown."
                    }
            },
            primaryButtonLabel = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    snapshot.notificationsAccess == PermissionAccessStatus.NOT_ALLOWED &&
                    !snapshot.notificationsPermanentlyDenied -> "Manage Permission"
                else -> "Notification Settings"
            },
            primaryTestTag = "manage_notifications_permission",
            onPrimaryClick = {
                onSuppressAutoLock()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    snapshot.notificationsAccess == PermissionAccessStatus.NOT_ALLOWED &&
                    !snapshot.notificationsPermanentlyDenied
                ) {
                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    VaultPermissionManager.openNotificationSettings(context)
                }
            },
            secondaryButtonLabel = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                snapshot.notificationsAccess == PermissionAccessStatus.NOT_ALLOWED &&
                !snapshot.notificationsPermanentlyDenied
            ) "System Settings" else null,
            onSecondaryClick = {
                onSuppressAutoLock()
                VaultPermissionManager.openNotificationSettings(context)
            }
        )

        // 5. Storage Access
        PermissionEntryCard(
            icon = Icons.Default.SdStorage,
            title = "Storage Access",
            status = snapshot.storageAccess,
            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Android 10+ uses Scoped Storage, MediaStore, Storage Access Framework, and app-private internal storage for encrypted vault files. Broad All-Files Access (MANAGE_EXTERNAL_STORAGE) is intentionally never requested."
            } else {
                "On Android 9 and below, Write External Storage is used only when restoring decrypted files back to the public device gallery."
            },
            primaryButtonLabel = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                snapshot.storageAccess == PermissionAccessStatus.NOT_ALLOWED &&
                !snapshot.storagePermanentlyDenied
            ) {
                "Manage Permission"
            } else {
                "App Storage Settings"
            },
            primaryTestTag = "manage_storage_permission",
            onPrimaryClick = {
                onSuppressAutoLock()
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    snapshot.storageAccess == PermissionAccessStatus.NOT_ALLOWED &&
                    !snapshot.storagePermanentlyDenied
                ) {
                    legacyStoragePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    VaultPermissionManager.openAppSettings(context)
                }
            }
        )

        // 6. Biometric Availability
        PermissionEntryCard(
            icon = Icons.Default.Fingerprint,
            title = "Biometric Availability",
            status = snapshot.biometricAccess,
            description = when (snapshot.biometricAvailability) {
                BiometricAvailability.AVAILABLE ->
                    "Biometric hardware detected and enrolled. Fingerprint or face unlock is available in Settings."
                BiometricAvailability.NONE_ENROLLED ->
                    "Biometric sensor present, but no fingerprint or face is enrolled on this device yet."
                BiometricAvailability.UNAVAILABLE ->
                    "No strong biometric authentication hardware is available on this device or emulator."
            },
            primaryButtonLabel = if (snapshot.biometricAvailability == BiometricAvailability.UNAVAILABLE) {
                "Security Settings"
            } else {
                "Manage Biometrics"
            },
            primaryTestTag = "manage_biometric_permission",
            onPrimaryClick = {
                onSuppressAutoLock()
                VaultPermissionManager.openBiometricSettings(context)
            }
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionEntryCard(
    icon: ImageVector,
    title: String,
    status: PermissionAccessStatus,
    description: String,
    primaryButtonLabel: String,
    primaryTestTag: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    VaultPremiumCard(
        highlightBorder = status == PermissionAccessStatus.ALLOWED || status == PermissionAccessStatus.LIMITED_ACCESS
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(8.dp))
            PermissionStatusBadge(
                status = status,
                modifier = Modifier.testTag("${primaryTestTag}_badge")
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPrimaryClick,
                modifier = Modifier
                    .weight(1f)
                    .testTag(primaryTestTag)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(primaryButtonLabel)
            }
            if (secondaryButtonLabel != null && onSecondaryClick != null) {
                OutlinedButton(onClick = onSecondaryClick) {
                    Text(secondaryButtonLabel)
                }
            }
        }
    }
}
