package com.example.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.data.db.MediaType
import com.example.data.db.VaultMediaEntity
import com.example.data.preferences.VaultPreferences
import com.example.ui.components.VaultModalDialog
import com.example.ui.components.VaultPremiumCard
import com.example.util.FormatUtils
import java.io.File

@Composable
fun ProfileScreen(
    preferences: VaultPreferences,
    allMedia: List<VaultMediaEntity>,
    albumCount: Int,
    onSuppressAutoLock: () -> Unit,
    onUpdateProfile: (displayName: String, bio: String, presetId: Int) -> Unit,
    onSelectCustomAvatar: (Uri) -> Unit,
    onSelectPresetAvatar: (Int) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onSelectCustomAvatar(uri)
    }

    val photoCount = remember(allMedia) { allMedia.count { it.mediaType == MediaType.PHOTO } }
    val videoCount = remember(allMedia) { allMedia.count { it.mediaType == MediaType.VIDEO } }
    val totalVaultBytes = remember(allMedia) { allMedia.sumOf { it.sizeBytes } }

    // Security score calculation
    val securityScore = remember(preferences) {
        var score = 40 // Base AES-256 + Keystore
        if (preferences.pinConfigured) score += 25
        if (preferences.biometricEnabled) score += 15
        if (preferences.screenshotProtectionEnabled) score += 10
        if (preferences.lockOnBackground) score += 10
        score.coerceAtMost(100)
    }

    val haloTransition = rememberInfiniteTransition(label = "avatarHalo")
    val haloAngle by haloTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloAngle"
    )

    val primary = MaterialTheme.colorScheme.primary
    val haloBrush = remember(primary) {
        Brush.sweepGradient(
            listOf(Color.Transparent, primary, primary.copy(alpha = 0.25f), Color.Transparent)
        )
    }
    val customFile by produceState<File?>(initialValue = null, key1 = preferences.customAvatarPath) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            preferences.customAvatarPath?.let { File(it) }?.takeIf { it.exists() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium)
            IconButton(
                onClick = { showEditDialog = true },
                modifier = Modifier.testTag("edit_profile_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit profile")
            }
        }

        // Animated Avatar Hero Card
        VaultPremiumCard(highlightBorder = true) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(116.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        rotate(haloAngle) {
                            drawArc(
                                brush = haloBrush,
                                startAngle = 0f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                onSuppressAutoLock()
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (customFile != null) {
                            AsyncImage(
                                model = customFile,
                                contentDescription = "Custom profile avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (preferences.avatarPresetId == 0) {
                            Image(
                                painter = painterResource(id = R.drawable.img_profile_avatar),
                                contentDescription = "Obsidian avatar emblem",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val presetIcon = when (preferences.avatarPresetId) {
                                1 -> Icons.Default.Shield
                                2 -> Icons.Default.Lock
                                else -> Icons.Default.VerifiedUser
                            }
                            Icon(
                                presetIcon,
                                contentDescription = "Preset avatar",
                                tint = primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clickable {
                                onSuppressAutoLock()
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Change avatar photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = preferences.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preferences.profileBio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                // Preset avatar chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Emblem", 1 to "Shield", 2 to "Vault", 3 to "Verified").forEach { (id, label) ->
                        val selected = preferences.customAvatarPath == null && preferences.avatarPresetId == id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = if (selected) primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectPresetAvatar(id) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Security Standing Card
        VaultPremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Security Posture", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (securityScore >= 90) "Maximum Local Hardening" else "Standard Vault Protection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "$securityScore%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { securityScore / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(12.dp))
            SecurityBadgeRow(
                icon = Icons.Default.Key,
                label = "PIN Protection",
                value = if (preferences.pinConfigured) "${preferences.pinLength}-Digit PBKDF2" else "Not set"
            )
            Spacer(Modifier.height(6.dp))
            SecurityBadgeRow(
                icon = Icons.Default.Fingerprint,
                label = "Biometric Unlock",
                value = if (preferences.biometricEnabled) "Enabled" else "Disabled"
            )
            Spacer(Modifier.height(6.dp))
            SecurityBadgeRow(
                icon = Icons.Default.Shield,
                label = "Screenshot Shield",
                value = if (preferences.screenshotProtectionEnabled) "Active (FLAG_SECURE)" else "Off"
            )
        }

        // Live Vault Statistics Grid
        Text("Vault Statistics", style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VaultStatTile(
                icon = Icons.Default.Photo,
                label = "Hidden Photos",
                value = photoCount.toString(),
                modifier = Modifier.weight(1f)
            )
            VaultStatTile(
                icon = Icons.Default.Videocam,
                label = "Hidden Videos",
                value = videoCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VaultStatTile(
                icon = Icons.Default.SdStorage,
                label = "Vault Storage",
                value = FormatUtils.formatBytes(totalVaultBytes),
                modifier = Modifier.weight(1f)
            )
            VaultStatTile(
                icon = Icons.Default.Folder,
                label = "Private Albums",
                value = albumCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        // App Metadata Footer
        VaultPremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Vault Initialized", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(FormatUtils.formatDateShort(preferences.memberSinceMs), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("App Version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showEditDialog) {
        var nameInput by remember { mutableStateOf(preferences.displayName) }
        var bioInput by remember { mutableStateOf(preferences.profileBio) }
        var preset by remember { mutableIntStateOf(preferences.avatarPresetId) }

        VaultModalDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Customize Vault Profile") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_name_input")
                    )
                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text("Vault Note / Status") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_bio_input")
                    )
                    OutlinedButton(
                        onClick = {
                            showEditDialog = false
                            onSuppressAutoLock()
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pick Custom Avatar Photo")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateProfile(nameInput, bioInput, preset)
                        showEditDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
    }
}

@Composable
private fun SecurityBadgeRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun VaultStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    VaultPremiumCard(modifier = modifier) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
