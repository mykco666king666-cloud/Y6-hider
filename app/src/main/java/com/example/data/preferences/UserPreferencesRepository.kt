package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "obsidian_vault_prefs"
)

enum class ThemeMode(val label: String) {
    DARK("Obsidian Dark"),
    LIGHT("Alabaster Light"),
    SYSTEM("System Default")
}

enum class AccentTheme(val label: String, val primaryHex: Long, val secondaryHex: Long) {
    CHAMPAGNE_GOLD("Champagne Gold", 0xFFE5B869, 0xFFF3D59B),
    CYBER_EMERALD("Cyber Emerald", 0xFF2DD4BF, 0xFF5EEAD4),
    ELECTRIC_COBALT("Electric Cobalt", 0xFF60A5FA, 0xFF93C5FD),
    CRIMSON_VELVET("Crimson Velvet", 0xFFF43F5E, 0xFFFB7185),
    AMETHYST("Royal Amethyst", 0xFFA78BFA, 0xFFC4B5FD)
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch")
}

enum class SortOrder(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    LARGEST("Largest Size"),
    NAME_ASC("Name A–Z")
}

data class VaultPreferences(
    val onboardingCompleted: Boolean = false,
    val pinConfigured: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val pinLength: Int = 4,
    val recoveryHash: String? = null,
    val recoverySalt: String? = null,
    val failedAttempts: Int = 0,
    val lockoutUntilMs: Long = 0L,
    val biometricEnabled: Boolean = false,
    val appLockEnabled: Boolean = true,
    val lockOnBackground: Boolean = true,
    val autoLockTimeoutSec: Int = 0,
    val screenshotProtectionEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentTheme: AccentTheme = AccentTheme.CHAMPAGNE_GOLD,
    val languageCode: String = AppLanguage.ENGLISH.code,
    val importAlertsEnabled: Boolean = true,
    val backupRemindersEnabled: Boolean = true,
    val securityAlertsEnabled: Boolean = true,
    val askDeleteOriginalAfterImport: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val displayName: String = "Vault Guardian",
    val profileBio: String = "Zero-knowledge encrypted local media",
    val avatarPresetId: Int = 0,
    val customAvatarPath: String? = null,
    val memberSinceMs: Long = 0L
)

class UserPreferencesRepository(
    private val context: Context,
    private val cryptoManager: VaultCryptoManager
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PIN_CONFIGURED = booleanPreferencesKey("pin_configured")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_LENGTH = intPreferencesKey("pin_length")
        val RECOVERY_HASH = stringPreferencesKey("recovery_hash")
        val RECOVERY_SALT = stringPreferencesKey("recovery_salt")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKOUT_UNTIL_MS = longPreferencesKey("lockout_until_ms")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val LOCK_ON_BACKGROUND = booleanPreferencesKey("lock_on_background")
        val AUTO_LOCK_TIMEOUT_SEC = intPreferencesKey("auto_lock_timeout_sec")
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val IMPORT_ALERTS = booleanPreferencesKey("import_alerts")
        val BACKUP_REMINDERS = booleanPreferencesKey("backup_reminders")
        val SECURITY_ALERTS = booleanPreferencesKey("security_alerts")
        val ASK_DELETE_ORIGINAL = booleanPreferencesKey("ask_delete_original")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PROFILE_BIO = stringPreferencesKey("profile_bio")
        val AVATAR_PRESET_ID = intPreferencesKey("avatar_preset_id")
        val CUSTOM_AVATAR_PATH = stringPreferencesKey("custom_avatar_path")
        val MEMBER_SINCE_MS = longPreferencesKey("member_since_ms")
    }

    val preferencesFlow: Flow<VaultPreferences> = context.vaultDataStore.data.map { prefs ->
        VaultPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            pinConfigured = prefs[Keys.PIN_CONFIGURED] ?: false,
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT],
            pinLength = prefs[Keys.PIN_LENGTH] ?: 4,
            recoveryHash = prefs[Keys.RECOVERY_HASH],
            recoverySalt = prefs[Keys.RECOVERY_SALT],
            failedAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
            lockoutUntilMs = prefs[Keys.LOCKOUT_UNTIL_MS] ?: 0L,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: true,
            lockOnBackground = prefs[Keys.LOCK_ON_BACKGROUND] ?: true,
            autoLockTimeoutSec = prefs[Keys.AUTO_LOCK_TIMEOUT_SEC] ?: 0,
            screenshotProtectionEnabled = prefs[Keys.SCREENSHOT_PROTECTION] ?: false,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK,
            accentTheme = prefs[Keys.ACCENT_THEME]?.let {
                runCatching { AccentTheme.valueOf(it) }.getOrNull()
            } ?: AccentTheme.CHAMPAGNE_GOLD,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: AppLanguage.ENGLISH.code,
            importAlertsEnabled = prefs[Keys.IMPORT_ALERTS] ?: true,
            backupRemindersEnabled = prefs[Keys.BACKUP_REMINDERS] ?: true,
            securityAlertsEnabled = prefs[Keys.SECURITY_ALERTS] ?: true,
            askDeleteOriginalAfterImport = prefs[Keys.ASK_DELETE_ORIGINAL] ?: true,
            sortOrder = prefs[Keys.SORT_ORDER]?.let {
                runCatching { SortOrder.valueOf(it) }.getOrNull()
            } ?: SortOrder.NEWEST,
            displayName = prefs[Keys.DISPLAY_NAME] ?: "Vault Guardian",
            profileBio = prefs[Keys.PROFILE_BIO] ?: "Zero-knowledge encrypted local media",
            avatarPresetId = prefs[Keys.AVATAR_PRESET_ID] ?: 0,
            customAvatarPath = prefs[Keys.CUSTOM_AVATAR_PATH],
            memberSinceMs = prefs[Keys.MEMBER_SINCE_MS] ?: 0L
        )
    }

    suspend fun getSnapshot(): VaultPreferences = preferencesFlow.first()

    /**
     * Configures initial PIN + Recovery Key securely (salted PBKDF2 hash only, never plain text).
     */
    suspend fun configurePinAndRecovery(pin: String, recoveryCode: String) {
        val (pinSalt, pinHash, recSalt, recHash) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val pSalt = cryptoManager.generateSalt()
            val pHash = cryptoManager.hashSecret(pin, pSalt)
            val rSalt = cryptoManager.generateSalt()
            val normalizedRecovery = recoveryCode.trim().uppercase()
            val rHash = cryptoManager.hashSecret(normalizedRecovery, rSalt)
            Array(4) { idx ->
                when (idx) {
                    0 -> cryptoManager.encodeBase64(pSalt)
                    1 -> pHash
                    2 -> cryptoManager.encodeBase64(rSalt)
                    else -> rHash
                }
            }
        }

        context.vaultDataStore.edit { prefs ->
            prefs[Keys.PIN_CONFIGURED] = true
            prefs[Keys.PIN_HASH] = pinHash
            prefs[Keys.PIN_SALT] = pinSalt
            prefs[Keys.PIN_LENGTH] = pin.length
            prefs[Keys.RECOVERY_HASH] = recHash
            prefs[Keys.RECOVERY_SALT] = recSalt
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
            if ((prefs[Keys.MEMBER_SINCE_MS] ?: 0L) == 0L) {
                prefs[Keys.MEMBER_SINCE_MS] = System.currentTimeMillis()
            }
        }
    }

    suspend fun updatePinOnly(newPin: String) {
        val (pinSaltB64, pinHash) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val pinSalt = cryptoManager.generateSalt()
            cryptoManager.encodeBase64(pinSalt) to cryptoManager.hashSecret(newPin, pinSalt)
        }
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.PIN_CONFIGURED] = true
            prefs[Keys.PIN_HASH] = pinHash
            prefs[Keys.PIN_SALT] = pinSaltB64
            prefs[Keys.PIN_LENGTH] = newPin.length
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun regenerateRecoveryCode(): String {
        val (newCode, recSaltB64, recHash) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val code = cryptoManager.generateRecoveryCode()
            val recSalt = cryptoManager.generateSalt()
            val hash = cryptoManager.hashSecret(code, recSalt)
            Triple(code, cryptoManager.encodeBase64(recSalt), hash)
        }
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.RECOVERY_HASH] = recHash
            prefs[Keys.RECOVERY_SALT] = recSaltB64
        }
        return newCode
    }

    suspend fun verifyPin(pin: String): Boolean {
        val snap = getSnapshot()
        val hash = snap.pinHash ?: return false
        val salt = snap.pinSalt ?: return false
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            cryptoManager.verifySecret(pin, salt, hash)
        }
    }

    suspend fun verifyRecoveryCode(code: String): Boolean {
        val snap = getSnapshot()
        val hash = snap.recoveryHash ?: return false
        val salt = snap.recoverySalt ?: return false
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            cryptoManager.verifySecret(code.trim().uppercase(), salt, hash)
        }
    }

    suspend fun recordFailedPinAttempt(): Pair<Int, Long> {
        var newAttempts = 0
        var lockoutUntil = 0L
        context.vaultDataStore.edit { prefs ->
            newAttempts = (prefs[Keys.FAILED_ATTEMPTS] ?: 0) + 1
            prefs[Keys.FAILED_ATTEMPTS] = newAttempts
            if (newAttempts >= 5) {
                val penaltySec = when {
                    newAttempts >= 8 -> 120L
                    newAttempts >= 6 -> 60L
                    else -> 30L
                }
                lockoutUntil = System.currentTimeMillis() + penaltySec * 1000L
                prefs[Keys.LOCKOUT_UNTIL_MS] = lockoutUntil
            }
        }
        return newAttempts to lockoutUntil
    }

    suspend fun resetFailedPinAttempts() {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
            if ((prefs[Keys.MEMBER_SINCE_MS] ?: 0L) == 0L) {
                prefs[Keys.MEMBER_SINCE_MS] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setLockOnBackground(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.LOCK_ON_BACKGROUND] = enabled }
    }

    suspend fun setAutoLockTimeoutSec(seconds: Int) {
        context.vaultDataStore.edit { it[Keys.AUTO_LOCK_TIMEOUT_SEC] = seconds }
    }

    suspend fun setScreenshotProtectionEnabled(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.SCREENSHOT_PROTECTION] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.vaultDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentTheme(accent: AccentTheme) {
        context.vaultDataStore.edit { it[Keys.ACCENT_THEME] = accent.name }
    }

    suspend fun setLanguageCode(code: String) {
        context.vaultDataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setNotificationPreferences(
        importAlerts: Boolean? = null,
        backupReminders: Boolean? = null,
        securityAlerts: Boolean? = null
    ) {
        context.vaultDataStore.edit { prefs ->
            importAlerts?.let { prefs[Keys.IMPORT_ALERTS] = it }
            backupReminders?.let { prefs[Keys.BACKUP_REMINDERS] = it }
            securityAlerts?.let { prefs[Keys.SECURITY_ALERTS] = it }
        }
    }

    suspend fun setAskDeleteOriginalAfterImport(ask: Boolean) {
        context.vaultDataStore.edit { it[Keys.ASK_DELETE_ORIGINAL] = ask }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.vaultDataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }

    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        avatarPresetId: Int? = null,
        customAvatarPath: String? = null,
        clearCustomAvatar: Boolean = false
    ) {
        context.vaultDataStore.edit { prefs ->
            displayName?.let { prefs[Keys.DISPLAY_NAME] = it }
            bio?.let { prefs[Keys.PROFILE_BIO] = it }
            avatarPresetId?.let { prefs[Keys.AVATAR_PRESET_ID] = it }
            if (clearCustomAvatar) {
                prefs.remove(Keys.CUSTOM_AVATAR_PATH)
            } else {
                customAvatarPath?.let { prefs[Keys.CUSTOM_AVATAR_PATH] = it }
            }
        }
    }

    suspend fun resetAllSecurityAndOnboarding() {
        context.vaultDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = false
            prefs[Keys.PIN_CONFIGURED] = false
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.PIN_SALT)
            prefs.remove(Keys.RECOVERY_HASH)
            prefs.remove(Keys.RECOVERY_SALT)
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL_MS] = 0L
            prefs[Keys.BIOMETRIC_ENABLED] = false
        }
    }
}
