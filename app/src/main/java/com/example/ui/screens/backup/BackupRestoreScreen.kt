package com.example.ui.screens.backup

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.BackupRecordEntity
import com.example.data.storage.BackupPreview
import com.example.ui.components.VaultModalDialog
import com.example.ui.components.VaultPremiumCard
import com.example.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupRestoreScreen(
    isBusy: Boolean,
    pendingPreview: Pair<BackupPreview, String>?,
    backupRecords: List<BackupRecordEntity>,
    onBack: () -> Unit,
    onSuppressAutoLock: () -> Unit,
    onExportBackup: (destUri: Uri, fileName: String, password: String) -> Unit,
    onInspectBackup: (sourceUri: Uri, fileName: String, password: String) -> Unit,
    onConfirmRestore: (replaceExisting: Boolean) -> Unit,
    onCancelRestorePreview: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var showReplaceDoubleConfirm by remember { mutableStateOf(false) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingExportPassword.isNotEmpty()) {
            val fileName = queryDisplayName(context, uri) ?: "obsidian_backup.ovbackup"
            onExportBackup(uri, fileName, pendingExportPassword)
        }
        pendingExportPassword = ""
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = queryDisplayName(context, uri) ?: "selected_backup.ovbackup"
            pendingImportUri = uri to fileName
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    "PBKDF2 + AES-256-GCM authenticated .ovbackup archives",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isBusy) {
            VaultPremiumCard(highlightBorder = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    Column {
                        Text("Processing Encrypted Archive…", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Deriving key & verifying AES-GCM authentication tag",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Export Card
        VaultPremiumCard(highlightBorder = true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Export Encrypted Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Creates a password-locked .ovbackup file containing all hidden photos, videos, albums, and SHA-256 checksums.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showExportPasswordDialog = true },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_backup_button")
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create Encrypted Backup")
            }
        }

        // Restore Card
        VaultPremiumCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Import & Restore Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Select an .ovbackup file, enter its password, verify integrity, and choose Merge or Replace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    onSuppressAutoLock()
                    openBackupLauncher.launch(arrayOf("*/*"))
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_backup_button")
            ) {
                Text("Select .ovbackup File to Restore")
            }
        }

        // History
        VaultPremiumCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Backup & Restore Log", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            if (backupRecords.isEmpty()) {
                Text(
                    "No encrypted backups exported or restored yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                backupRecords.take(10).forEachIndexed { idx, record ->
                    if (idx > 0) HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${record.operationType}: ${record.fileName}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "${record.itemCount} items • ${record.albumCount} albums • ${FormatUtils.formatDateTime(record.createdAtEpochMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = FormatUtils.formatBytes(record.sizeBytes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // Export Password Prompt
    if (showExportPasswordDialog) {
        var pass by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var showPass by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }

        VaultModalDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            title = { Text("Set Backup Password") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Choose a strong password (minimum 6 characters). Without this password, the backup cannot be decrypted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it; err = null },
                        label = { Text("Backup Password") },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; err = null },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            pass.length < 6 -> err = "Password must be at least 6 characters."
                            pass != confirm -> err = "Passwords do not match."
                            else -> {
                                pendingExportPassword = pass
                                showExportPasswordDialog = false
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                onSuppressAutoLock()
                                createBackupLauncher.launch("obsidian_vault_$stamp.ovbackup")
                            }
                        }
                    }
                ) { Text("Choose Location") }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Import Password Prompt
    pendingImportUri?.let { (uri, name) ->
        var pass by remember { mutableStateOf("") }
        var showPass by remember { mutableStateOf(false) }

        VaultModalDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Unlock Backup File") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("File: $name", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Backup Password") },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingImportUri = null
                        onInspectBackup(uri, name, pass)
                    },
                    enabled = pass.isNotEmpty()
                ) { Text("Verify & Preview") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            }
        )
    }

    // Verified Backup Preview (Merge vs Replace confirmation)
    pendingPreview?.let { (preview, fileName) ->
        if (!showReplaceDoubleConfirm) {
            VaultModalDialog(
                onDismissRequest = onCancelRestorePreview,
                title = { Text("Backup Verified") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Archive: $fileName", style = MaterialTheme.typography.labelLarge)
                        Text("Created: ${FormatUtils.formatDateTime(preview.createdAtEpochMs)}")
                        Text("Contents: ${preview.photoCount} photo(s), ${preview.videoCount} video(s), ${preview.albumCount} album(s)")
                        Text("Total Media Size: ${FormatUtils.formatBytes(preview.totalMediaBytes)}")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Choose Merge to keep existing vault files and add missing items, or Replace to overwrite your current vault.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { onConfirmRestore(false) }) {
                        Text("Merge Safely")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onCancelRestorePreview) { Text("Cancel") }
                        OutlinedButton(
                            onClick = { showReplaceDoubleConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Replace All")
                        }
                    }
                }
            )
        } else {
            VaultModalDialog(
                onDismissRequest = { showReplaceDoubleConfirm = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Overwrite Existing Vault?") },
                text = {
                    Text("Replace will permanently remove all current photos, videos, and albums before restoring this backup.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showReplaceDoubleConfirm = false
                            onConfirmRestore(true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Confirm Replace")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReplaceDoubleConfirm = false }) { Text("Back") }
                }
            )
        }
    }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }.getOrNull()
}
