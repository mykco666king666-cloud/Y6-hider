package com.example.data.storage

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

object VaultNotificationHelper {

    private const val CHANNEL_VAULT_EVENTS = "obsidian_vault_events"
    private const val CHANNEL_SECURITY_ALERTS = "obsidian_security_alerts"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val eventsChannel = NotificationChannel(
                CHANNEL_VAULT_EVENTS,
                "Vault Activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Import and encrypted backup completion updates"
            }
            val securityChannel = NotificationChannel(
                CHANNEL_SECURITY_ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "PIN lockout and integrity alerts"
            }
            manager.createNotificationChannel(eventsChannel)
            manager.createNotificationChannel(securityChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postVaultEvent(context: Context, title: String, message: String, isSecurity: Boolean = false) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val channelId = if (isSecurity) CHANNEL_SECURITY_ALERTS else CHANNEL_VAULT_EVENTS
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(if (isSecurity) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
    }
}
