package com.example.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.R
import com.example.data.preferences.AccentTheme
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.VaultPreferences
import com.example.data.storage.VaultNotificationHelper
import com.example.security.BiometricAvailability
import com.example.security.PermissionAccessStatus
import com.example.security.VaultPermissionManager
import com.example.security.VaultPermissionsSnapshot
import com.example.ui.components.PermissionStatusBadge
import com.example.ui.components.VaultModalDialog
import com.example.ui.components.VaultPremiumCard
import com.example.util.FormatUtils
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    preferences: VaultPreferences,
    biometricAvailability: BiometricAvailability,
    permissionsSnapshot: VaultPermissionsSnapshot,
    onRefreshPermissions: () -> Unit,
    onSuppressAutoLock: () -> Unit,
    onChangePin: suspend (currentPin: String, newPin: String) -> Result<Unit>,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
    onToggleLockOnBackground: (Boolean) -> Unit,
    onSelectAutoLockTimeout: (Int) -> Unit,
    onToggleScreenshotProtection: (Boolean) -> Unit,
    onToggleAskDeleteOriginal: (Boolean) -> Unit,
    onRegenerateRecoveryCode: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccentTheme: (AccentTheme) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onUpdateNotificationPrefs: (importAlerts: Boolean?, backupReminders: Boolean?, securityAlerts: Boolean?) -> Unit,
    onOpenPermissionsScreen: () -> Unit,
    onOpenStorageManagement: () -> Unit,
    onOpenBackupRestore: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var showNotificationSettingsRedirect by remember { mutableStateOf(false) }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        VaultPermissionManager.markNotificationsPrompted(context)
        onRefreshPermissions()
        if (granted) {
            pendingNotificationAction?.invoke() ?: onUpdateNotificationPrefs(true, null, null)
        } else {
            showNotificationSettingsRedirect = true
        }
        pendingNotificationAction = null
    }

    val requestNotificationsOrEnable: (() -> Unit) -> Unit = { enableAction ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !VaultNotificationHelper.hasNotificationPermission(context)
        ) {
            if (permissionsSnapshot.notificationsPermanentlyDenied) {
                showNotificationSettingsRedirect = true
            } else {
                pendingNotificationAction = enableAction
                showNotificationRationale = true
            }
        } else {
            enableAction()
        }
    }

    val summaryAllowedCount = listOf(
        permissionsSnapshot.photosAccess,
        permissionsSnapshot.videosAccess,
        permissionsSnapshot.usageAccess,
        permissionsSnapshot.notificationsAccess,
        permissionsSnapshot.storageAccess,
        permissionsSnapshot.biometricAccess
    ).count { it == PermissionAccessStatus.ALLOWED }
    val summaryLimitedCount = listOf(
        permissionsSnapshot.photosAccess,
        permissionsSnapshot.videosAccess
    ).count { it == PermissionAccessStatus.LIMITED_ACCESS }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        // 1. Appearance & Theme Section
        SectionHeader("Appearance & Personalization")
        VaultPremiumCard {
            Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = preferences.themeMode == mode,
                        onClick = { onSelectThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                    ) {
                        Text(
                            text = when (mode) {
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.SYSTEM -> "System"
                            },
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Accent Palette", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AccentTheme.entries.forEach { accent ->
                    val selected = preferences.accentTheme == accent
                    val color = Color(accent.primaryHex)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.22f))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) color else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { onSelectAccentTheme(accent) }
                            .testTag("accent_${accent.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = accent.label,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SettingsNavigationRow(
                icon = Icons.Default.Language,
                title = "Language",
                subtitle = AppLanguage.entries.find { it.code == preferences.languageCode }?.displayName ?: "English",
                onClick = { showLanguageDialog = true }
            )
        }

        // 2. Security & Privacy
        SectionHeader("Security & Privacy")
        VaultPremiumCard {
            SettingsNavigationRow(
                icon = Icons.Default.Key,
                title = "Change Security PIN",
                subtitle = "${preferences.pinLength}-digit salted PBKDF2 PIN",
                onClick = { showChangePinDialog = true },
                testTag = "change_pin_row"
            )
            Spacer(Modifier.height(12.dp))
            SettingsNavigationRow(
                icon = Icons.Default.Key,
                title = "Regenerate Recovery Key",
                subtitle = "Create a new offline PIN recovery token",
                onClick = onRegenerateRecoveryCode
            )
            Spacer(Modifier.height(12.dp))

            SettingsSwitchRow(
                icon = Icons.Default.Fingerprint,
                title = "Biometric Unlock",
                subtitle = when (biometricAvailability) {
                    BiometricAvailability.AVAILABLE -> "Fingerprint or face authentication enabled"
                    BiometricAvailability.NONE_ENROLLED -> "Enroll biometrics in Android Settings first"
                    BiometricAvailability.UNAVAILABLE -> "Biometric hardware unavailable on this device"
                },
                checked = preferences.biometricEnabled,
                enabled = biometricAvailability == BiometricAvailability.AVAILABLE,
                onCheckedChange = onToggleBiometric
            )
            Spacer(Modifier.height(10.dp))

            SettingsSwitchRow(
                icon = Icons.Default.Lock,
                title = "Require PIN on Launch",
                subtitle = "Lock vault whenever application opens",
                checked = preferences.appLockEnabled,
                onCheckedChange = onToggleAppLock
            )
            Spacer(Modifier.height(10.dp))

            SettingsSwitchRow(
                icon = Icons.Default.Lock,
                title = "Lock on Background",
                subtitle = "Re-lock when leaving Obsidian Vault",
                checked = preferences.lockOnBackground,
                onCheckedChange = onToggleLockOnBackground
            )
            Spacer(Modifier.height(10.dp))

            SettingsNavigationRow(
                icon = Icons.Default.Timer,
                title = "Auto-Lock Timeout",
                subtitle = FormatUtils.formatTimeoutLabel(preferences.autoLockTimeoutSec),
                onClick = { showTimeoutDialog = true }
            )
            Spacer(Modifier.height(10.dp))

            SettingsSwitchRow(
                icon = Icons.Default.ScreenshotMonitor,
                title = "Screenshot & Recents Shield",
                subtitle = "Block screenshots & hide preview in App Switcher",
                checked = preferences.screenshotProtectionEnabled,
                onCheckedChange = onToggleScreenshotProtection
            )
            Spacer(Modifier.height(10.dp))

            SettingsSwitchRow(
                icon = Icons.Default.Policy,
                title = "Prompt Original Deletion",
                subtitle = "Offer system-confirmed gallery removal after import",
                checked = preferences.askDeleteOriginalAfterImport,
                onCheckedChange = onToggleAskDeleteOriginal
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            SettingsNavigationRow(
                icon = Icons.Default.Security,
                title = "Permission Management",
                subtitle = "$summaryAllowedCount Allowed" +
                    (if (summaryLimitedCount > 0) " • $summaryLimitedCount Limited" else "") +
                    " • Photos, Videos, Usage, Notifications & Biometrics",
                onClick = onOpenPermissionsScreen,
                testTag = "open_permissions_row"
            )
            Spacer(Modifier.height(14.dp))

            // Usage Access (Optional Android Special Permission)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Usage Access (App Monitor)", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(8.dp))
                    PermissionStatusBadge(
                        status = permissionsSnapshot.usageAccess,
                        modifier = Modifier.testTag("settings_usage_access_badge")
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("Optional foreground app-switch security telemetry via Android UsageStatsManager. Never required to import or view photos and videos.")
                        permissionsSnapshot.usageLastForegroundMs?.let { ts ->
                            append(" Verified active telemetry: last usage recorded ${FormatUtils.formatDateTime(ts)}.")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onSuppressAutoLock()
                        VaultPermissionManager.openUsageAccessSettings(context)
                    },
                    modifier = Modifier
                        .padding(start = 36.dp)
                        .testTag("settings_manage_usage_access_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (permissionsSnapshot.usageAccess == PermissionAccessStatus.ALLOWED) {
                            "Manage Usage Access"
                        } else {
                            "Open Usage Access Settings"
                        }
                    )
                }
            }
        }

        // 3. Notifications
        SectionHeader("Notifications")
        VaultPremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("System Notification Permission", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when (permissionsSnapshot.notificationsAccess) {
                            PermissionAccessStatus.ALLOWED -> "Allowed for import completion, backup & security events"
                            PermissionAccessStatus.NOT_REQUIRED -> "Not required below Android 13; notifications active"
                            else -> if (permissionsSnapshot.notificationsPermanentlyDenied) {
                                "Denied in Android Settings — open Notification Settings to enable"
                            } else {
                                "Used for import completion, backup status & security alerts"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                PermissionStatusBadge(
                    status = permissionsSnapshot.notificationsAccess,
                    modifier = Modifier.testTag("settings_notifications_badge")
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onSuppressAutoLock()
                    VaultPermissionManager.openNotificationSettings(context)
                },
                modifier = Modifier.testTag("settings_notification_settings_button")
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Notification Settings")
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))

            SettingsSwitchRow(
                icon = Icons.Default.Notifications,
                title = "Import Completion Alerts",
                subtitle = "Notify when large photo/video batches finish encrypting",
                checked = preferences.importAlertsEnabled,
                onCheckedChange = { want ->
                    if (want) {
                        requestNotificationsOrEnable { onUpdateNotificationPrefs(true, null, null) }
                    } else {
                        onUpdateNotificationPrefs(false, null, null)
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            SettingsSwitchRow(
                icon = Icons.Default.Notifications,
                title = "Backup Alerts",
                subtitle = "Confirm encrypted .ovbackup archive completion",
                checked = preferences.backupRemindersEnabled,
                onCheckedChange = { want ->
                    if (want) {
                        requestNotificationsOrEnable { onUpdateNotificationPrefs(null, true, null) }
                    } else {
                        onUpdateNotificationPrefs(null, false, null)
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            SettingsSwitchRow(
                icon = Icons.Default.Notifications,
                title = "Security Lockout Alerts",
                subtitle = "Alert when repeated failed PIN entries trigger cooldown",
                checked = preferences.securityAlertsEnabled,
                onCheckedChange = { want ->
                    if (want) {
                        requestNotificationsOrEnable { onUpdateNotificationPrefs(null, null, true) }
                    } else {
                        onUpdateNotificationPrefs(null, null, false)
                    }
                }
            )
        }

        // 4. Data, Storage & Backups
        SectionHeader("Data, Storage & Backups")
        VaultPremiumCard {
            SettingsNavigationRow(
                icon = Icons.Default.SdStorage,
                title = "Storage & Integrity Diagnostics",
                subtitle = "Inspect vault footprint, clear cache & verify checksums",
                onClick = onOpenStorageManagement
            )
            Spacer(Modifier.height(12.dp))
            SettingsNavigationRow(
                icon = Icons.Default.Backup,
                title = "Encrypted Backup & Restore",
                subtitle = "Export or restore password-locked .ovbackup archives",
                onClick = onOpenBackupRestore,
                testTag = "open_backup_row"
            )
        }

        // 5. About, Privacy & Help
        SectionHeader("About & Support")
        VaultPremiumCard {
            SettingsNavigationRow(
                icon = Icons.Default.Info,
                title = "About Obsidian Vault",
                subtitle = "Version ${BuildConfig.VERSION_NAME} • Local AES-256 Engine",
                onClick = { showAboutDialog = true }
            )
            Spacer(Modifier.height(12.dp))
            SettingsNavigationRow(
                icon = Icons.Default.Policy,
                title = "Privacy Policy",
                subtitle = "Zero-telemetry offline architecture",
                onClick = { showPrivacyDialog = true }
            )
            Spacer(Modifier.height(12.dp))
            SettingsNavigationRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "Help & Support",
                subtitle = "Photo Picker, backups & PIN recovery guide",
                onClick = { showHelpDialog = true }
            )
        }

        Spacer(Modifier.height(28.dp))
    }

    // Dialogs
    if (showChangePinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        VaultModalDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("Change Security PIN") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 6) { currentPin = v; error = null } },
                        label = { Text("Current PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 6) { newPin = v; error = null } },
                        label = { Text("New PIN (4–6 digits)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 6) { confirmPin = v; error = null } },
                        label = { Text("Confirm New PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            newPin.length !in listOf(4, 6) -> error = "New PIN must be 4 or 6 digits."
                            newPin != confirmPin -> error = "New PINs do not match."
                            else -> scope.launch {
                                onChangePin(currentPin, newPin)
                                    .onSuccess { showChangePinDialog = false }
                                    .onFailure { error = it.message }
                            }
                        }
                    }
                ) { Text("Update PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTimeoutDialog) {
        val options = listOf(0, 15, 30, 60, 300)
        VaultModalDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("Auto-Lock Timeout") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { sec ->
                        val selected = preferences.autoLockTimeoutSec == sec
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectAutoLockTimeout(sec)
                                    showTimeoutDialog = false
                                }
                        ) {
                            Text(
                                text = FormatUtils.formatTimeoutLabel(sec),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) { Text("Close") }
            }
        )
    }

    if (showLanguageDialog) {
        VaultModalDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val selected = preferences.languageCode == lang.code
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectLanguage(lang.code)
                                    showLanguageDialog = false
                                }
                        ) {
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Close") }
            }
        )
    }

    if (showNotificationRationale) {
        VaultModalDialog(
            onDismissRequest = {
                showNotificationRationale = false
                pendingNotificationAction = null
            },
            title = { Text("Enable Vault Notifications?") },
            text = {
                Text("Obsidian Vault uses local notifications only to alert you when large media imports, backups, or PIN cooldowns occur. No media previews are ever shown in notifications.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            onSuppressAutoLock()
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) { Text("Allow") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotificationRationale = false
                        pendingNotificationAction = null
                    }
                ) { Text("Not Now") }
            }
        )
    }

    if (showNotificationSettingsRedirect) {
        VaultModalDialog(
            onDismissRequest = { showNotificationSettingsRedirect = false },
            title = { Text("Notification Permission Denied") },
            text = {
                Text("To receive import completion, backup status, or vault security alerts, enable notifications for Obsidian Vault in Android Notification Settings.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationSettingsRedirect = false
                        onSuppressAutoLock()
                        VaultPermissionManager.openNotificationSettings(context)
                    }
                ) { Text("Notification Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationSettingsRedirect = false }) { Text("Dismiss") }
            }
        )
    }

    if (showAboutDialog) {
        VaultModalDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Obsidian Vault") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Obsidian Vault v${BuildConfig.VERSION_NAME}\n" +
                            "Package: ${BuildConfig.APPLICATION_ID}\n\n" +
                            "• File Encryption: AES-256-CTR + Android Keystore\n" +
                            "• PIN Storage: Salted PBKDF2-HMAC-SHA256\n" +
                            "• Backups: PBKDF2 + AES-256-GCM (.ovbackup)\n" +
                            "• Media Player: Jetpack Media3 ExoPlayer"
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("OK") } }
        )
    }

    if (showPrivacyDialog) {
        VaultModalDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "1. 100% Offline: Obsidian Vault never transmits photos, videos, PINs, or analytics to any server.\n\n" +
                            "2. Minimal Permissions: Uses the Android Photo Picker so we never ask for broad gallery access.\n\n" +
                            "3. User-Controlled Originals: Public gallery files are never silently deleted; removal always uses Android's official system confirmation."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") } }
        )
    }

    if (showHelpDialog) {
        VaultModalDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help & FAQ") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "• How do I hide media?\nTap Import on the Vault tab, select photos or videos, and confirm if you wish to remove the original gallery copies.\n\n" +
                            "• How do I move files back?\nSelect items (long-press) or open any photo/video and tap Restore.\n\n" +
                            "• Forgot PIN?\nUse your OVLT recovery key on the Lock Screen to reset your PIN without losing media."
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("Got It") } }
        )
    }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
