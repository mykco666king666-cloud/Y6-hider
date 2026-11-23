package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.db.MediaType
import com.example.data.storage.DeviceMediaCandidate
import com.example.security.PermissionAccessStatus
import com.example.ui.theme.VaultDanger
import com.example.ui.theme.VaultInfo
import com.example.ui.theme.VaultSuccess
import com.example.ui.theme.VaultWarning
import com.example.util.FormatUtils

@Composable
fun PermissionStatusBadge(
    status: PermissionAccessStatus,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (status) {
        PermissionAccessStatus.ALLOWED -> VaultSuccess.copy(alpha = 0.16f) to VaultSuccess
        PermissionAccessStatus.LIMITED_ACCESS -> VaultWarning.copy(alpha = 0.18f) to VaultWarning
        PermissionAccessStatus.NOT_ALLOWED -> VaultDanger.copy(alpha = 0.16f) to VaultDanger
        PermissionAccessStatus.NOT_REQUIRED -> VaultInfo.copy(alpha = 0.16f) to VaultInfo
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceGalleryPickerSheet(
    mediaType: MediaType,
    accessStatus: PermissionAccessStatus,
    refreshKey: Int,
    onLoadDeviceMedia: suspend (MediaType) -> List<DeviceMediaCandidate>,
    onImportSelectedUris: (List<Uri>) -> Unit,
    onManageLimitedSelection: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenSystemPhotoPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var candidates by remember { mutableStateOf<List<DeviceMediaCandidate>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }

    LaunchedEffect(mediaType, accessStatus, refreshKey) {
        isLoading = true
        candidates = onLoadDeviceMedia(mediaType)
        isLoading = false
    }

    val typeLabel = if (mediaType == MediaType.VIDEO) "Videos" else "Photos"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Device $typeLabel",
                            style = MaterialTheme.typography.titleLarge
                        )
                        PermissionStatusBadge(status = accessStatus)
                    }
                    Text(
                        text = if (accessStatus == PermissionAccessStatus.LIMITED_ACCESS) {
                            "Showing the $typeLabel you granted access to"
                        } else {
                            "Select $typeLabel to encrypt into Obsidian Vault"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (accessStatus == PermissionAccessStatus.LIMITED_ACCESS) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VaultWarning.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, VaultWarning.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Limited Selection Active",
                                style = MaterialTheme.typography.labelLarge,
                                color = VaultWarning
                            )
                            Text(
                                text = "Grant more items or use the zero-permission System Photo Picker.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onManageLimitedSelection) {
                            Text("Select More")
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenSystemPhotoPicker()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("switch_to_photo_picker_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("System Photo Picker", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onOpenAppSettings
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Settings")
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    candidates.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No accessible $typeLabel found",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (accessStatus == PermissionAccessStatus.LIMITED_ACCESS) {
                                    "Tap \"Select More\" to choose items, or open the System Photo Picker."
                                } else {
                                    "Your device MediaStore has no matching $typeLabel, or you can import using the System Photo Picker."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenSystemPhotoPicker()
                                }
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open Android Photo Picker")
                            }
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 104.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = candidates,
                                key = { it.uri.toString() },
                                contentType = { it.mediaType }
                            ) { candidate ->
                                val selected = candidate.uri in selectedUris
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            selectedUris = if (selected) {
                                                selectedUris - candidate.uri
                                            } else {
                                                selectedUris + candidate.uri
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = candidate.uri,
                                        contentDescription = candidate.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                            contentDescription = if (selected) "Selected" else "Select",
                                            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    if (candidate.mediaType == MediaType.VIDEO) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(Modifier.width(2.dp))
                                                Text(
                                                    text = FormatUtils.formatDuration(candidate.durationMs),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val toImport = selectedUris.toList()
                        onDismiss()
                        if (toImport.isNotEmpty()) {
                            onImportSelectedUris(toImport)
                        }
                    },
                    enabled = selectedUris.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selectedUris.isEmpty()) "Select $typeLabel to Import"
                        else "Encrypt & Import (${selectedUris.size})"
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MediaPermissionDeniedDialog(
    mediaType: MediaType,
    permanentlyDenied: Boolean,
    onRetryPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onUsePhotoPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val label = if (mediaType == MediaType.VIDEO) "Videos" else "Photos"

    VaultModalDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                if (permanentlyDenied) "$label Access Disabled in Settings"
                else "$label Access Denied"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (permanentlyDenied) {
                        "Android no longer shows the $label permission prompt because access was permanently denied. " +
                            "You can enable access in App Settings, or import right now without permissions using the Android Photo Picker."
                    } else {
                        "Obsidian Vault needs $label permission to browse your device gallery directly inside the app. " +
                            "Alternatively, you can use the zero-permission Android Photo Picker anytime."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            if (permanentlyDenied) {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenAppSettings()
                    },
                    modifier = Modifier.testTag("open_app_settings_button")
                ) {
                    Text("Open App Settings")
                }
            } else {
                Button(
                    onClick = {
                        onDismiss()
                        onRetryPermission()
                    },
                    modifier = Modifier.testTag("retry_media_permission_button")
                ) {
                    Text("Grant Access")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        onUsePhotoPicker()
                    },
                    modifier = Modifier.testTag("fallback_photo_picker_button")
                ) {
                    Text("Use Photo Picker")
                }
            }
        }
    )
}
