package com.example.ui.screens.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricAvailability
import com.example.ui.components.PinDotsIndicator
import com.example.ui.components.PinKeypad
import com.example.ui.viewmodel.SecurityUiState
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    pinLength: Int,
    biometricEnabled: Boolean,
    securityState: SecurityUiState,
    onSubmitPin: (String) -> Unit,
    onClearPinError: () -> Unit,
    onBiometricSuccess: () -> Unit,
    onRecoverWithKey: suspend (recoveryCode: String, newPin: String) -> Result<Unit>,
    onFactoryReset: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx: android.content.Context? = context
        while (ctx is android.content.ContextWrapper && ctx !is FragmentActivity) {
            ctx = ctx.baseContext
        }
        ctx as? FragmentActivity
    }
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showFactoryResetConfirm by remember { mutableStateOf(false) }
    var biometricMessage by remember { mutableStateOf<String?>(null) }

    val canUseBiometrics = biometricEnabled &&
        securityState.biometricAvailability == BiometricAvailability.AVAILABLE &&
        activity != null

    val triggerBiometric: () -> Unit = {
        if (canUseBiometrics) {
            BiometricAuthHelper.authenticate(
                activity = activity,
                title = "Unlock Obsidian Vault",
                subtitle = "Authenticate with biometrics to decrypt your private media",
                negativeText = "Use PIN",
                onSuccess = onBiometricSuccess,
                onError = { msg -> biometricMessage = msg }
            )
        }
    }

    LaunchedEffect(canUseBiometrics) {
        if (canUseBiometrics && securityState.lockoutRemainingSeconds == 0) {
            triggerBiometric()
        }
    }

    // Clear dots automatically when an error triggers
    LaunchedEffect(securityState.pinShakeTrigger) {
        if (securityState.pinShakeTrigger > 0) {
            enteredPin = ""
        }
    }

    val pulse = rememberInfiniteTransition(label = "lockPulse")
    val iconScale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockScale"
    )

    val isLockedOut = securityState.lockoutRemainingSeconds > 0

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Vault Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.unlock_vault_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.unlock_vault_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(26.dp))

                PinDotsIndicator(
                    pinLength = pinLength,
                    enteredCount = enteredPin.length,
                    shakeTrigger = securityState.pinShakeTrigger,
                    isError = securityState.pinError != null
                )

                Spacer(Modifier.height(12.dp))
                val statusText = when {
                    isLockedOut -> "Cooldown active: try again in ${securityState.lockoutRemainingSeconds}s"
                    securityState.pinError != null -> securityState.pinError
                    biometricMessage != null -> biometricMessage
                    else -> " "
                }
                Text(
                    text = statusText ?: " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLockedOut || securityState.pinError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("lock_error_text")
                )
            }

            PinKeypad(
                onDigit = { d ->
                    if (!isLockedOut && enteredPin.length < pinLength) {
                        onClearPinError()
                        biometricMessage = null
                        enteredPin += d.toString()
                        if (enteredPin.length == pinLength) {
                            onSubmitPin(enteredPin)
                        }
                    }
                },
                onBackspace = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                    }
                },
                showBiometricButton = canUseBiometrics,
                onBiometricClick = triggerBiometric,
                enabled = !isLockedOut
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { showRecoveryDialog = true },
                    modifier = Modifier.testTag("forgot_pin_button")
                ) {
                    Text(stringResource(R.string.forgot_pin))
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showRecoveryDialog,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        BackHandler(enabled = showRecoveryDialog) {
            showRecoveryDialog = false
        }
        var recoveryInput by remember { mutableStateOf("") }
        var newPinInput by remember { mutableStateOf("") }
        var recoveryErr by remember { mutableStateOf<String?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showRecoveryDialog = false }
                )
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Recover Vault Access",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Text(
                        "Enter the recovery key generated during setup (e.g. OVLT-XXXX-XXXX-XXXX) and choose a new $pinLength-digit PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = {
                            recoveryInput = it.uppercase()
                            recoveryErr = null
                        },
                        label = { Text("Recovery Key") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_key_input")
                    )
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { v ->
                            if (v.length <= pinLength && v.all { it.isDigit() }) {
                                newPinInput = v
                                recoveryErr = null
                            }
                        },
                        label = { Text("New $pinLength-Digit PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recovery_new_pin_input")
                    )
                    recoveryErr?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = {
                            showRecoveryDialog = false
                            showFactoryResetConfirm = true
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Lost Recovery Key? Reset Vault")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRecoveryDialog = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newPinInput.length != pinLength) {
                                    recoveryErr = "Enter a $pinLength-digit PIN."
                                    return@Button
                                }
                                scope.launch {
                                    onRecoverWithKey(recoveryInput, newPinInput)
                                        .onSuccess { showRecoveryDialog = false }
                                        .onFailure { recoveryErr = it.message }
                                }
                            },
                            modifier = Modifier.testTag("recovery_reset_pin_button")
                        ) {
                            Text("Reset PIN")
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showFactoryResetConfirm,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        BackHandler(enabled = showFactoryResetConfirm) {
            showFactoryResetConfirm = false
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showFactoryResetConfirm = false }
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Erase Entire Vault?",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Text(
                        "Without your PIN or Recovery Key, encrypted media cannot be decrypted. " +
                            "Resetting will permanently delete all hidden photos, videos, and albums.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { showFactoryResetConfirm = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showFactoryResetConfirm = false
                                onFactoryReset()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Erase & Reset")
                        }
                    }
                }
            }
        }
    }
}
