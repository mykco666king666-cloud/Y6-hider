package com.example.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.BiometricAvailability
import com.example.ui.components.PinDotsIndicator
import com.example.ui.components.PinKeypad
import com.example.ui.components.VaultPremiumCard
import com.example.ui.theme.MonoFontFamily

@Composable
fun OnboardingScreen(
    biometricAvailability: BiometricAvailability,
    onCompleteOnboarding: (pin: String, enableBiometrics: Boolean, onRecoveryReady: (String) -> Unit) -> Unit,
    onEnterVault: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var desiredPinLength by remember { mutableIntStateOf(4) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmingPin by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var enableBiometrics by remember {
        mutableStateOf(biometricAvailability == BiometricAvailability.AVAILABLE)
    }
    var generatedRecoveryCode by remember { mutableStateOf<String?>(null) }
    var recoverySavedChecked by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Step indicator bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SETUP • STEP ${step + 1} OF 4",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { (step + 1) / 4f },
                    modifier = Modifier
                        .width(120.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                label = "onboardingStep"
            ) { currentStep ->
                when (currentStep) {
                    0 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vault_shield),
                            contentDescription = "Obsidian Vault Encryption Shield",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "Your Private Media Sanctuary",
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Every imported photo and video is encrypted with AES-256 inside private app storage backed by the Android Keystore.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        FeatureInfoRow(
                            icon = Icons.Default.Lock,
                            title = "100% Local & Zero-Knowledge",
                            subtitle = "No cloud uploads, no tracking, and salted PBKDF2 PIN protection."
                        )
                        Spacer(Modifier.height(12.dp))
                        FeatureInfoRow(
                            icon = Icons.Default.Security,
                            title = "Encrypted Portable Backups",
                            subtitle = "Export password-protected .ovbackup archives anytime."
                        )
                    }

                    1 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Privacy-First Permissions",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Obsidian Vault requests only what it genuinely needs — never broad gallery access.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        FeatureInfoRow(
                            icon = Icons.Default.PhotoLibrary,
                            title = "Android Photo Picker",
                            subtitle = "Select specific photos and videos without granting full storage permissions."
                        )
                        Spacer(Modifier.height(12.dp))
                        FeatureInfoRow(
                            icon = Icons.Default.Shield,
                            title = "User-Approved Original Removal",
                            subtitle = "We never delete your original gallery files silently — Android asks your confirmation each time."
                        )
                        Spacer(Modifier.height(12.dp))
                        FeatureInfoRow(
                            icon = Icons.Default.Fingerprint,
                            title = "Optional Biometrics & Alerts",
                            subtitle = "Biometric unlock and import notifications can be toggled anytime in Settings."
                        )

                        if (biometricAvailability == BiometricAvailability.AVAILABLE) {
                            Spacer(Modifier.height(20.dp))
                            VaultPremiumCard(highlightBorder = enableBiometrics) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Enable Biometric Unlock",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Use fingerprint or face alongside your PIN",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = enableBiometrics,
                                        onCheckedChange = { enableBiometrics = it }
                                    )
                                }
                            }
                        }
                    }

                    2 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (!isConfirmingPin) "Create Security PIN" else "Confirm Security PIN",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (!isConfirmingPin) {
                                "Choose a $desiredPinLength-digit PIN to protect your vault"
                            } else {
                                "Re-enter your $desiredPinLength-digit PIN to verify"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (!isConfirmingPin) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(4, 6).forEach { len ->
                                    FilterChip(
                                        selected = desiredPinLength == len,
                                        onClick = {
                                            desiredPinLength = len
                                            firstPin = ""
                                            confirmPin = ""
                                            pinError = null
                                        },
                                        label = { Text("$len Digits") }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        val currentEntered = if (isConfirmingPin) confirmPin else firstPin
                        PinDotsIndicator(
                            pinLength = desiredPinLength,
                            enteredCount = currentEntered.length,
                            shakeTrigger = shakeTrigger,
                            isError = pinError != null
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = pinError ?: " ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(14.dp))

                        PinKeypad(
                            onDigit = { digit ->
                                pinError = null
                                if (!isConfirmingPin) {
                                    if (firstPin.length < desiredPinLength) {
                                        firstPin += digit.toString()
                                        if (firstPin.length == desiredPinLength) {
                                            isConfirmingPin = true
                                        }
                                    }
                                } else {
                                    if (confirmPin.length < desiredPinLength) {
                                        confirmPin += digit.toString()
                                        if (confirmPin.length == desiredPinLength) {
                                            if (confirmPin == firstPin) {
                                                onCompleteOnboarding(confirmPin, enableBiometrics) { code ->
                                                    generatedRecoveryCode = code
                                                    step = 3
                                                }
                                            } else {
                                                pinError = "PINs did not match. Try again."
                                                confirmPin = ""
                                                shakeTrigger++
                                            }
                                        }
                                    }
                                }
                            },
                            onBackspace = {
                                pinError = null
                                if (isConfirmingPin) {
                                    if (confirmPin.isNotEmpty()) {
                                        confirmPin = confirmPin.dropLast(1)
                                    } else {
                                        isConfirmingPin = false
                                    }
                                } else if (firstPin.isNotEmpty()) {
                                    firstPin = firstPin.dropLast(1)
                                }
                            }
                        )
                    }

                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Save Your Recovery Key",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "If you ever forget your PIN, this unique recovery key lets you reset your PIN without losing your encrypted photos or videos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))

                        VaultPremiumCard(highlightBorder = true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = generatedRecoveryCode ?: "Generating…",
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        generatedRecoveryCode?.let {
                                            clipboard.setText(AnnotatedString(it))
                                            copied = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy recovery key",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = recoverySavedChecked,
                                    onCheckedChange = { recoverySavedChecked = it },
                                    modifier = Modifier.testTag("recovery_saved_checkbox")
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "I have saved my recovery key in a safe place.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when (step) {
                0, 1 -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (step == 1) {
                        OutlinedButton(
                            onClick = { step = 0 },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text("Back")
                        }
                    }
                    Button(
                        onClick = { step++ },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("onboarding_next_button")
                    ) {
                        Text(if (step == 0) "Get Started" else "Set Up Security PIN")
                    }
                }

                2 -> if (isConfirmingPin) {
                    OutlinedButton(
                        onClick = {
                            isConfirmingPin = false
                            firstPin = ""
                            confirmPin = ""
                            pinError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Restart PIN Entry")
                    }
                }

                3 -> Button(
                    onClick = onEnterVault,
                    enabled = recoverySavedChecked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("enter_vault_button")
                ) {
                    Text("Open Obsidian Vault")
                }
            }
        }
    }
}

@Composable
private fun FeatureInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
