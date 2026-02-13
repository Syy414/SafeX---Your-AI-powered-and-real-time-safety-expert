package com.safex.app.guardian

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.safex.app.MainActivity
import com.safex.app.R
import com.safex.app.data.local.AlertEntity

/**
 * Creates the SafeX alert notification channel and posts warning notifications.
 */
object SafeXNotificationHelper {

    const val CHANNEL_ID = "safex_alerts"
    private const val CHANNEL_NAME = "SafeX Alerts"
    private const val CHANNEL_DESC = "Warnings about potential scam messages detected by SafeX"

    /** Call once at app startup or before first notification. Idempotent. */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Post a warning notification the user can tap to open Alerts tab + alert detail.
     *
     * Deep-link contract:
     *  - Intent extra "alert_id" → UUID of the alert
     *  - Intent extra "open_tab" → "alerts"
     *  - Agent 1 reads these in MainActivity and routes accordingly.
     */
    /**
     * Post a warning notification (overload for when you have the entity).
     */
    fun postWarningNotification(context: Context, alert: AlertEntity) {
        postWarning(
            context,
            alert.id,
            alert.headline ?: "Potential scam detected",
            alert.riskLevel
        )
    }

    /**
     * Post a warning notification (overload for primitive fields, used by ScanTextWorker).
     */
    fun postWarning(context: Context, id: String, headline: String, riskLevel: String, type: String = "generic") {
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alert_id", id)
            putExtra("open_tab", "alerts")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: replace with SafeX icon
            .setContentTitle("⚠\uFE0F SafeX Warning")
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(headline))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(id.hashCode(), notification)
    }
}
