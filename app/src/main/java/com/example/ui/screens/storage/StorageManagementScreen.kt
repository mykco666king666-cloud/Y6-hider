package com.example.ui.screens.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.VaultMediaEntity
import com.example.data.storage.IntegrityReport
import com.example.data.storage.StorageReport
import com.example.ui.components.VaultPremiumCard
import com.example.ui.theme.VaultInfo
import com.example.ui.theme.VaultSuccess
import com.example.ui.theme.VaultWarning
import com.example.util.FormatUtils

@Composable
fun StorageManagementScreen(
    storageReport: StorageReport?,
    integrityReport: IntegrityReport?,
    failedMedia: List<VaultMediaEntity>,
    isCheckingStorage: Boolean,
    isCheckingIntegrity: Boolean,
    onRefreshStorage: () -> Unit,
    onClearSafeCache: () -> Unit,
    onRunIntegrityCheck: () -> Unit,
    onRecoverFailedImports: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.storage_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Real-time encrypted disk & integrity diagnostics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRefreshStorage,
                enabled = !isCheckingStorage,
                modifier = Modifier.testTag("refresh_storage_button")
            ) {
                if (isCheckingStorage) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh storage info")
                }
            }
        }

        // Main Storage Card
        VaultPremiumCard(highlightBorder = true) {
            val report = storageReport
            val vaultTotal = report?.totalVaultBytes ?: 0L
            val photoBytes = report?.photoBytes ?: 0L
            val videoBytes = report?.videoBytes ?: 0L
            val thumbBytes = report?.thumbnailBytes ?: 0L
            val cacheBytes = report?.cacheBytes ?: 0L
            val availBytes = report?.availableDeviceBytes ?: 0L
            val totalDevice = report?.totalDeviceBytes?.coerceAtLeast(1L) ?: 1L

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "TOTAL VAULT FOOTPRINT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = FormatUtils.formatBytes(vaultTotal),
                        style = MaterialTheme.typography.displayLarge
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${(report?.photoCount ?: 0) + (report?.videoCount ?: 0)} files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${FormatUtils.formatBytes(availBytes)} free on device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Segmented Storage Bar
            val barTotal = (photoBytes + videoBytes + thumbBytes + cacheBytes).coerceAtLeast(1L)
            val pFrac = (photoBytes.toFloat() / barTotal).coerceIn(0f, 1f)
            val vFrac = (videoBytes.toFloat() / barTotal).coerceIn(0f, 1f)
            val tFrac = (thumbBytes.toFloat() / barTotal).coerceIn(0f, 1f)
            val cFrac = (cacheBytes.toFloat() / barTotal).coerceIn(0f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (pFrac > 0f) Box(Modifier.weight(pFrac).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                if (vFrac > 0f) Box(Modifier.weight(vFrac).fillMaxHeight().background(VaultInfo))
                if (tFrac > 0f) Box(Modifier.weight(tFrac).fillMaxHeight().background(VaultSuccess))
                if (cFrac > 0f) Box(Modifier.weight(cFrac).fillMaxHeight().background(VaultWarning))
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            StorageLegendRow(
                color = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.Photo,
                label = "Encrypted Photos (${report?.photoCount ?: 0})",
                value = FormatUtils.formatBytes(photoBytes)
            )
            Spacer(Modifier.height(8.dp))
            StorageLegendRow(
                color = VaultInfo,
                icon = Icons.Default.Videocam,
                label = "Encrypted Videos (${report?.videoCount ?: 0})",
                value = FormatUtils.formatBytes(videoBytes)
            )
            Spacer(Modifier.height(8.dp))
            StorageLegendRow(
                color = VaultSuccess,
                icon = Icons.Default.SdStorage,
                label = "Encrypted Thumbnails",
                value = FormatUtils.formatBytes(thumbBytes)
            )
            Spacer(Modifier.height(8.dp))
            StorageLegendRow(
                color = VaultWarning,
                icon = Icons.Default.CleaningServices,
                label = "Temporary App Cache",
                value = FormatUtils.formatBytes(cacheBytes)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Device Storage: ${FormatUtils.formatBytes(availBytes)} available of ${FormatUtils.formatBytes(totalDevice)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Safe Cache Clearing
        VaultPremiumCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    Text("Safe Cache Management", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Clears temporary decode & export buffers only. Your encrypted photos, videos, and albums are never touched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onClearSafeCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_cache_button")
            ) {
                Text("Clear Temporary Cache (${FormatUtils.formatBytes(storageReport?.cacheBytes ?: 0L)})")
            }
        }

        // Integrity Verification
        VaultPremiumCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    Text("AES & SHA-256 Integrity Check", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Decrypts and hashes every stored vault file to confirm zero corruption or missing data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRunIntegrityCheck,
                enabled = !isCheckingIntegrity,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("integrity_check_button")
            ) {
                if (isCheckingIntegrity) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Verifying Files…")
                } else {
                    Text("Run Full Integrity Audit")
                }
            }

            AnimatedVisibility(visible = integrityReport != null) {
                integrityReport?.let { rep ->
                    val allHealthy = rep.missingFiles.isEmpty() && rep.corruptedFiles.isEmpty()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (allHealthy) VaultSuccess.copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (allHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (allHealthy) VaultSuccess else MaterialTheme.colorScheme.error
                            )
                            Column {
                                Text(
                                    text = if (allHealthy) {
                                        "All ${rep.healthyCount} files verified intact"
                                    } else {
                                        "${rep.healthyCount}/${rep.checkedCount} healthy • ${rep.missingFiles.size} missing • ${rep.corruptedFiles.size} corrupted"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        // Failed Import Recovery
        VaultPremiumCard {
            val failedCount = (storageReport?.failedImportCount ?: 0) + failedMedia.size
            val orphanCount = storageReport?.orphanedTempFilesCount ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = if (failedCount > 0 || orphanCount > 0) VaultWarning else MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    Text("Failed Import Recovery", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${failedMedia.size} interrupted record(s) • $orphanCount orphaned temp file(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (failedMedia.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                failedMedia.take(3).forEach { failed ->
                    Text(
                        text = "• ${failed.originalFileName}: ${failed.failureReason ?: "Interrupted"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRecoverFailedImports,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recover_imports_button")
            ) {
                Text("Clean Up Interrupted & Orphaned Files")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StorageLegendRow(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
