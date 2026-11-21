package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

object SystemNotificationHelper {

    const val CHANNEL_ID_JOBS = "channel_job_alerts"
    const val CHANNEL_ID_ESCROW = "channel_escrow_momo"
    const val CHANNEL_ID_CHAT = "channel_resident_chat"

    fun setupNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val jobsChannel = NotificationChannel(
                CHANNEL_ID_JOBS,
                "Job & Quote Broadcasts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts for incoming neighborhood job requests and bids"
                enableVibration(true)
                setShowBadge(true)
            }

            val escrowChannel = NotificationChannel(
                CHANNEL_ID_ESCROW,
                "Escrow & MoMo Payouts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when Mobile Money funds are locked or released"
                enableVibration(true)
                setShowBadge(true)
            }

            val chatChannel = NotificationChannel(
                CHANNEL_ID_CHAT,
                "Resident & Worker Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Direct messages and trade negotiation updates"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(jobsChannel, escrowChannel, chatChannel))
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun sendLocalNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        actionLabel: String? = null
    ) {
        if (!hasNotificationPermission(context)) return

        setupNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (actionLabel != null) {
            builder.addAction(android.R.drawable.ic_menu_view, actionLabel, pendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Handled when permission revoked
        }
    }

    fun openNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
