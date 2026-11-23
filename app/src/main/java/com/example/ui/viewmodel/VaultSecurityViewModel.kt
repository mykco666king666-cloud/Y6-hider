package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ObsidianVaultApp
import com.example.data.preferences.AccentTheme
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.VaultPreferences
import com.example.data.storage.VaultNotificationHelper
import com.example.security.BiometricAvailability
import com.example.security.VaultPermissionManager
import com.example.security.VaultPermissionsSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SecurityUiState(
    val isUnlocked: Boolean = false,
    val isSplashDone: Boolean = false,
    val pinError: String? = null,
    val pinShakeTrigger: Int = 0,
    val lockoutRemainingSeconds: Int = 0,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val permissionsSnapshot: VaultPermissionsSnapshot = VaultPermissionsSnapshot(),
    val generatedRecoveryCode: String? = null,
    val statusBanner: String? = null
)

class VaultSecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ObsidianVaultApp
    private val prefsRepo = app.preferencesRepository
    private val crypto = app.cryptoManager
    private val mediaRepo = app.mediaRepository

    val preferences: StateFlow<VaultPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = VaultPreferences()
    )

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    private var lastBackgroundEpochMs: Long = 0L
    private var suppressNextBackgroundLock: Boolean = false
    private var lastPermRefreshElapsedMs: Long = 0L
    private var permissionRefreshJob: Job? = null
    private var isFinishingSplash: Boolean = false

    @Volatile
    private var preloadedPrefs: VaultPreferences? = null
    @Volatile
    private var hasPreloadedPrefs: Boolean = false
    @Volatile
    private var preloadedSnapshot: VaultPermissionsSnapshot? = null

    init {
        viewModelScope.launch {
            preloadedPrefs = prefsRepo.getSnapshot()
            hasPreloadedPrefs = true
        }
        refreshPermissions(activity = null, force = true)

        viewModelScope.launch {
            preferences
                .map { it.lockoutUntilMs }
                .distinctUntilChanged()
                .collectLatest { lockoutUntil ->
                    while (true) {
                        val remainingMs = lockoutUntil - System.currentTimeMillis()
                        val remainingSec = if (remainingMs > 0L) {
                            ((remainingMs + 999L) / 1000L).toInt()
                        } else {
                            0
                        }
                        if (_uiState.value.lockoutRemainingSeconds != remainingSec) {
                            _uiState.update { it.copy(lockoutRemainingSeconds = remainingSec) }
                        }
                        if (remainingSec <= 0) break
                        delay(1000L)
                    }
                }
        }
    }

    fun finishSplash() {
        if (isFinishingSplash || _uiState.value.isSplashDone) return
        val prefs = preloadedPrefs ?: if (hasPreloadedPrefs) preferences.value else null
        if (prefs != null) {
            val startUnlocked = !prefs.onboardingCompleted || !prefs.pinConfigured || !prefs.appLockEnabled
            val snapshot = preloadedSnapshot ?: _uiState.value.permissionsSnapshot
            _uiState.update {
                it.copy(
                    isSplashDone = true,
                    isUnlocked = startUnlocked,
                    biometricAvailability = snapshot.biometricAvailability,
                    permissionsSnapshot = snapshot
                )
            }
            if (preloadedSnapshot == null) {
                refreshPermissions(activity = null, force = true)
            }
            return
        }

        // DataStore still warming up (e.g. cold startup or fast test clock): transition immediately
        // without stalling the splash screen, then reconcile with persisted preferences in background.
        val currentPrefs = preferences.value
        val fallbackUnlocked = !currentPrefs.onboardingCompleted || !currentPrefs.pinConfigured || !currentPrefs.appLockEnabled
        _uiState.update {
            it.copy(
                isSplashDone = true,
                isUnlocked = fallbackUnlocked
            )
        }
        isFinishingSplash = true
        viewModelScope.launch {
            val loadedPrefs = prefsRepo.getSnapshot()
            preloadedPrefs = loadedPrefs
            hasPreloadedPrefs = true
            val shouldBeUnlocked = !loadedPrefs.onboardingCompleted || !loadedPrefs.pinConfigured || !loadedPrefs.appLockEnabled
            val snapshot = preloadedSnapshot ?: VaultPermissionManager.inspectAllAsync(app, null)
            preloadedSnapshot = snapshot
            lastPermRefreshElapsedMs = SystemClock.elapsedRealtime()
            _uiState.update {
                it.copy(
                    isUnlocked = shouldBeUnlocked,
                    biometricAvailability = snapshot.biometricAvailability,
                    permissionsSnapshot = snapshot
                )
            }
            isFinishingSplash = false
        }
    }

    fun refreshBiometricAvailability() {
        refreshPermissions(activity = null, force = true)
    }

    fun refreshPermissions(activity: Activity? = null, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPermRefreshElapsedMs < 250L && permissionRefreshJob?.isActive != true) {
            return
        }
        lastPermRefreshElapsedMs = now
        permissionRefreshJob?.cancel()
        permissionRefreshJob = viewModelScope.launch {
            val snapshot = VaultPermissionManager.inspectAllAsync(app, activity)
            preloadedSnapshot = snapshot
            _uiState.update {
                it.copy(
                    biometricAvailability = snapshot.biometricAvailability,
                    permissionsSnapshot = snapshot
                )
            }
        }
    }

    fun suppressNextAutoLockForPicker() {
        suppressNextBackgroundLock = true
    }

    fun onAppBackgrounded() {
        if (suppressNextBackgroundLock) return
        lastBackgroundEpochMs = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (suppressNextBackgroundLock) {
            suppressNextBackgroundLock = false
            return
        }
        val prefs = preferences.value
        if (!prefs.onboardingCompleted || !prefs.pinConfigured || !prefs.appLockEnabled || !prefs.lockOnBackground) {
            return
        }
        if (lastBackgroundEpochMs > 0L) {
            val elapsedMs = System.currentTimeMillis() - lastBackgroundEpochMs
            if (elapsedMs >= prefs.autoLockTimeoutSec * 1000L) {
                lockNow()
            }
        }
        lastBackgroundEpochMs = 0L
    }

    fun lockNow() {
        _uiState.update { it.copy(isUnlocked = false, pinError = null) }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            prefsRepo.resetFailedPinAttempts()
            _uiState.update { it.copy(isUnlocked = true, pinError = null) }
        }
    }

    fun verifyPinAndUnlock(pin: String) {
        viewModelScope.launch {
            val prefs = prefsRepo.getSnapshot()
            val now = System.currentTimeMillis()
            if (prefs.lockoutUntilMs > now) {
                val sec = ((prefs.lockoutUntilMs - now + 999L) / 1000L).toInt()
                _uiState.update {
                    it.copy(
                        pinError = "Too many attempts. Wait ${sec}s.",
                        lockoutRemainingSeconds = sec,
                        pinShakeTrigger = it.pinShakeTrigger + 1
                    )
                }
                return@launch
            }

            if (prefsRepo.verifyPin(pin)) {
                prefsRepo.resetFailedPinAttempts()
                _uiState.update { it.copy(isUnlocked = true, pinError = null) }
            } else {
                val (attempts, lockoutUntil) = prefsRepo.recordFailedPinAttempt()
                val msg = if (lockoutUntil > System.currentTimeMillis()) {
                    if (prefs.securityAlertsEnabled) {
                        VaultNotificationHelper.postVaultEvent(
                            app,
                            "Vault Lockout Triggered",
                            "Multiple incorrect PIN attempts detected. Temporary cooldown active.",
                            isSecurity = true
                        )
                    }
                    "Too many failed attempts. Vault locked temporarily."
                } else {
                    "Incorrect PIN ($attempts/5 attempts before cooldown)"
                }
                _uiState.update {
                    it.copy(
                        pinError = msg,
                        pinShakeTrigger = it.pinShakeTrigger + 1
                    )
                }
            }
        }
    }

    fun clearPinError() {
        _uiState.update { it.copy(pinError = null) }
    }

    fun completeOnboardingWithPin(pin: String, enableBiometrics: Boolean, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val recoveryCode = crypto.generateRecoveryCode()
            prefsRepo.configurePinAndRecovery(pin, recoveryCode)
            prefsRepo.setBiometricEnabled(enableBiometrics)
            prefsRepo.setOnboardingCompleted(true)
            _uiState.update {
                it.copy(
                    isUnlocked = true,
                    generatedRecoveryCode = recoveryCode,
                    pinError = null
                )
            }
            onDone(recoveryCode)
        }
    }

    fun clearGeneratedRecoveryCode() {
        _uiState.update { it.copy(generatedRecoveryCode = null) }
    }

    suspend fun changePin(currentPin: String, newPin: String): Result<Unit> {
        if (!prefsRepo.verifyPin(currentPin)) {
            return Result.failure(IllegalArgumentException("Current PIN is incorrect."))
        }
        prefsRepo.updatePinOnly(newPin)
        return Result.success(Unit)
    }

    suspend fun recoverPinWithCode(recoveryCode: String, newPin: String): Result<Unit> {
        if (!prefsRepo.verifyRecoveryCode(recoveryCode)) {
            return Result.failure(IllegalArgumentException("Invalid recovery key. Please check all characters."))
        }
        prefsRepo.updatePinOnly(newPin)
        _uiState.update { it.copy(isUnlocked = true, pinError = null) }
        return Result.success(Unit)
    }

    fun regenerateRecoveryCode() {
        viewModelScope.launch {
            val code = prefsRepo.regenerateRecoveryCode()
            _uiState.update { it.copy(generatedRecoveryCode = code) }
        }
    }

    fun factoryResetVault() {
        viewModelScope.launch {
            mediaRepo.wipeEntireVaultMedia()
            prefsRepo.resetAllSecurityAndOnboarding()
            _uiState.update {
                it.copy(
                    isUnlocked = true,
                    pinError = null,
                    statusBanner = "Vault wiped and reset to first-time setup."
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun setAccentTheme(accent: AccentTheme) = viewModelScope.launch { prefsRepo.setAccentTheme(accent) }
    fun setLanguageCode(code: String) = viewModelScope.launch { prefsRepo.setLanguageCode(code) }
    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepo.setBiometricEnabled(enabled) }
    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAppLockEnabled(enabled) }
    fun setLockOnBackground(enabled: Boolean) = viewModelScope.launch { prefsRepo.setLockOnBackground(enabled) }
    fun setAutoLockTimeout(sec: Int) = viewModelScope.launch { prefsRepo.setAutoLockTimeoutSec(sec) }
    fun setScreenshotProtection(enabled: Boolean) = viewModelScope.launch { prefsRepo.setScreenshotProtectionEnabled(enabled) }
    fun setAskDeleteOriginal(ask: Boolean) = viewModelScope.launch { prefsRepo.setAskDeleteOriginalAfterImport(ask) }

    fun setNotificationPrefs(
        importAlerts: Boolean? = null,
        backupReminders: Boolean? = null,
        securityAlerts: Boolean? = null
    ) = viewModelScope.launch {
        prefsRepo.setNotificationPreferences(importAlerts, backupReminders, securityAlerts)
    }

    fun updateProfile(displayName: String, bio: String, avatarPresetId: Int) = viewModelScope.launch {
        prefsRepo.updateProfile(
            displayName = displayName.trim().ifEmpty { "Vault Guardian" },
            bio = bio.trim().ifEmpty { "Zero-knowledge encrypted local media" },
            avatarPresetId = avatarPresetId
        )
        _uiState.update { it.copy(statusBanner = "Profile updated.") }
    }

    fun updateCustomAvatar(uri: Uri) {
        viewModelScope.launch {
            runCatching { mediaRepo.setCustomAvatarFromUri(uri) }
                .onSuccess { path ->
                    prefsRepo.updateProfile(customAvatarPath = path)
                    _uiState.update { it.copy(statusBanner = "Custom profile image saved.") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusBanner = err.message ?: "Failed to update avatar.") }
                }
        }
    }

    fun selectPresetAvatar(presetId: Int) {
        viewModelScope.launch {
            prefsRepo.updateProfile(avatarPresetId = presetId, clearCustomAvatar = true)
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }
}
