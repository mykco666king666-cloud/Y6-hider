package com.example.security

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class PermissionAccessStatus(val label: String) {
    ALLOWED("Allowed"),
    NOT_ALLOWED("Not Allowed"),
    LIMITED_ACCESS("Limited Access"),
    NOT_REQUIRED("Not Required")
}

data class VaultPermissionsSnapshot(
    val photosAccess: PermissionAccessStatus = PermissionAccessStatus.NOT_ALLOWED,
    val photosPermanentlyDenied: Boolean = false,
    val videosAccess: PermissionAccessStatus = PermissionAccessStatus.NOT_ALLOWED,
    val videosPermanentlyDenied: Boolean = false,
    val usageAccess: PermissionAccessStatus = PermissionAccessStatus.NOT_ALLOWED,
    val usageLastForegroundMs: Long? = null,
    val notificationsAccess: PermissionAccessStatus = PermissionAccessStatus.NOT_ALLOWED,
    val notificationsPermanentlyDenied: Boolean = false,
    val storageAccess: PermissionAccessStatus = PermissionAccessStatus.NOT_REQUIRED,
    val storagePermanentlyDenied: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.UNAVAILABLE,
    val biometricAccess: PermissionAccessStatus = PermissionAccessStatus.NOT_REQUIRED
)

/**
 * Queries live Android OS permission states across API 24-36 and launches real Android Settings intents.
 * Never caches granted/denied states permanently; only tracks whether a runtime prompt was previously shown
 * so permanent denial (!shouldShowRequestPermissionRationale) can be distinguished from initial state.
 */
object VaultPermissionManager {

    private const val PROMPT_HISTORY_PREFS = "vault_permission_prompt_history"
    private const val KEY_PHOTOS_PROMPTED = "prompted_photos"
    private const val KEY_VIDEOS_PROMPTED = "prompted_videos"
    private const val KEY_NOTIFICATIONS_PROMPTED = "prompted_notifications"
    private const val KEY_STORAGE_PROMPTED = "prompted_storage"

    suspend fun inspectAllAsync(
        context: Context,
        activity: Activity? = findActivity(context)
    ): VaultPermissionsSnapshot {
        val appContext = context.applicationContext
        val baseSnapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            inspectAll(appContext, null)
        }
        if (activity == null) return baseSnapshot

        return baseSnapshot.copy(
            photosPermanentlyDenied = isPermanentlyDenied(
                context = appContext,
                activity = activity,
                status = baseSnapshot.photosAccess,
                historyKey = KEY_PHOTOS_PROMPTED,
                permissionsToCheck = photosRequestPermissions()
            ),
            videosPermanentlyDenied = isPermanentlyDenied(
                context = appContext,
                activity = activity,
                status = baseSnapshot.videosAccess,
                historyKey = KEY_VIDEOS_PROMPTED,
                permissionsToCheck = videosRequestPermissions()
            ),
            notificationsPermanentlyDenied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isPermanentlyDenied(
                    context = appContext,
                    activity = activity,
                    status = baseSnapshot.notificationsAccess,
                    historyKey = KEY_NOTIFICATIONS_PROMPTED,
                    permissionsToCheck = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            } else {
                false
            },
            storagePermanentlyDenied = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                isPermanentlyDenied(
                    context = appContext,
                    activity = activity,
                    status = baseSnapshot.storageAccess,
                    historyKey = KEY_STORAGE_PROMPTED,
                    permissionsToCheck = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                )
            } else {
                false
            }
        )
    }

    fun inspectAll(context: Context, activity: Activity? = findActivity(context)): VaultPermissionsSnapshot {
        val photosStatus = checkPhotosStatus(context)
        val videosStatus = checkVideosStatus(context)
        val usageGranted = hasUsageStatsPermission(context)
        val notifStatus = checkNotificationsStatus(context)
        val storageStatus = checkStorageStatus(context)
        val bioAvail = BiometricAuthHelper.checkAvailability(context)
        val bioStatus = when (bioAvail) {
            BiometricAvailability.AVAILABLE -> PermissionAccessStatus.ALLOWED
            BiometricAvailability.NONE_ENROLLED -> PermissionAccessStatus.NOT_ALLOWED
            BiometricAvailability.UNAVAILABLE -> PermissionAccessStatus.NOT_REQUIRED
        }

        val lastUsageMs = if (usageGranted) queryOwnRecentUsageTimestamp(context) else null

        return VaultPermissionsSnapshot(
            photosAccess = photosStatus,
            photosPermanentlyDenied = isPermanentlyDenied(
                context = context,
                activity = activity,
                status = photosStatus,
                historyKey = KEY_PHOTOS_PROMPTED,
                permissionsToCheck = photosRequestPermissions()
            ),
            videosAccess = videosStatus,
            videosPermanentlyDenied = isPermanentlyDenied(
                context = context,
                activity = activity,
                status = videosStatus,
                historyKey = KEY_VIDEOS_PROMPTED,
                permissionsToCheck = videosRequestPermissions()
            ),
            usageAccess = if (usageGranted) PermissionAccessStatus.ALLOWED else PermissionAccessStatus.NOT_ALLOWED,
            usageLastForegroundMs = lastUsageMs,
            notificationsAccess = notifStatus,
            notificationsPermanentlyDenied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isPermanentlyDenied(
                    context = context,
                    activity = activity,
                    status = notifStatus,
                    historyKey = KEY_NOTIFICATIONS_PROMPTED,
                    permissionsToCheck = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            } else {
                false
            },
            storageAccess = storageStatus,
            storagePermanentlyDenied = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                isPermanentlyDenied(
                    context = context,
                    activity = activity,
                    status = storageStatus,
                    historyKey = KEY_STORAGE_PROMPTED,
                    permissionsToCheck = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                )
            } else {
                false
            },
            biometricAvailability = bioAvail,
            biometricAccess = bioStatus
        )
    }

    fun checkPhotosStatus(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): PermissionAccessStatus {
        return when {
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val full = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
                val limited = isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                when {
                    full -> PermissionAccessStatus.ALLOWED
                    limited -> PermissionAccessStatus.LIMITED_ACCESS
                    else -> PermissionAccessStatus.NOT_ALLOWED
                }
            }
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> {
                if (isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)) {
                    PermissionAccessStatus.ALLOWED
                } else {
                    PermissionAccessStatus.NOT_ALLOWED
                }
            }
            else -> {
                if (isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    PermissionAccessStatus.ALLOWED
                } else {
                    PermissionAccessStatus.NOT_ALLOWED
                }
            }
        }
    }

    fun checkVideosStatus(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): PermissionAccessStatus {
        return when {
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val full = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
                val limited = isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                when {
                    full -> PermissionAccessStatus.ALLOWED
                    limited -> PermissionAccessStatus.LIMITED_ACCESS
                    else -> PermissionAccessStatus.NOT_ALLOWED
                }
            }
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> {
                if (isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)) {
                    PermissionAccessStatus.ALLOWED
                } else {
                    PermissionAccessStatus.NOT_ALLOWED
                }
            }
            else -> {
                if (isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    PermissionAccessStatus.ALLOWED
                } else {
                    PermissionAccessStatus.NOT_ALLOWED
                }
            }
        }
    }

    fun checkNotificationsStatus(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): PermissionAccessStatus {
        val osEnabled = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)

        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            val runtimeGranted = isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
            when {
                runtimeGranted && osEnabled -> PermissionAccessStatus.ALLOWED
                runtimeGranted && !osEnabled -> PermissionAccessStatus.NOT_ALLOWED
                else -> PermissionAccessStatus.NOT_ALLOWED
            }
        } else {
            if (osEnabled) PermissionAccessStatus.NOT_REQUIRED else PermissionAccessStatus.NOT_ALLOWED
        }
    }

    fun checkStorageStatus(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): PermissionAccessStatus {
        return if (sdkInt >= Build.VERSION_CODES.Q) {
            // Android 10+ uses Scoped Storage, app-private vault filesDir, Photo Picker & SAF.
            // MANAGE_EXTERNAL_STORAGE is intentionally never requested.
            PermissionAccessStatus.NOT_REQUIRED
        } else {
            if (isGranted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PermissionAccessStatus.ALLOWED
            } else {
                PermissionAccessStatus.NOT_ALLOWED
            }
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    fun queryOwnRecentUsageTimestamp(context: Context): Long? {
        if (!hasUsageStatsPermission(context)) return null
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 24L * 60L * 60L * 1000L,
                now
            )
            stats?.firstOrNull { it.packageName == context.packageName }?.lastTimeUsed?.takeIf { it > 0L }
        }.getOrNull()
    }

    fun photosRequestPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        return when {
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun videosRequestPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
        return when {
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun markPhotosPrompted(context: Context) = markPrompted(context, KEY_PHOTOS_PROMPTED)
    fun markVideosPrompted(context: Context) = markPrompted(context, KEY_VIDEOS_PROMPTED)
    fun markNotificationsPrompted(context: Context) = markPrompted(context, KEY_NOTIFICATIONS_PROMPTED)
    fun markStoragePrompted(context: Context) = markPrompted(context, KEY_STORAGE_PROMPTED)

    private fun markPrompted(context: Context, key: String) {
        context.getSharedPreferences(PROMPT_HISTORY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, true)
            .apply()
    }

    private fun wasPrompted(context: Context, key: String): Boolean {
        return context.getSharedPreferences(PROMPT_HISTORY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, false)
    }

    private fun isPermanentlyDenied(
        context: Context,
        activity: Activity?,
        status: PermissionAccessStatus,
        historyKey: String,
        permissionsToCheck: Array<String>
    ): Boolean {
        if (status != PermissionAccessStatus.NOT_ALLOWED) return false
        if (!wasPrompted(context, historyKey)) return false
        if (activity == null) return false
        return permissionsToCheck.none { perm ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper && ctx !is Activity) {
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openUsageAccessSettings(context: Context) {
        val packageIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(packageIntent)
            true
        }.getOrDefault(false)
        if (!launched) {
            val genericIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(genericIntent) }
                .onFailure { openAppSettings(context) }
        }
    }

    fun openNotificationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return
        }
        openAppSettings(context)
    }

    fun openBiometricSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching {
                context.startActivity(enrollIntent)
                true
            }.getOrDefault(false)
            if (ok) return
        }
        val secIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(secIntent) }
            .onFailure { openAppSettings(context) }
    }
}
