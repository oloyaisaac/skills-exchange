package com.example.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * NOTIF-002: Firebase Cloud Messaging Service
 * Handles push notifications for the Skills Exchange app.
 */
class SkillsExchangeMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "skills_exchange_notifications"
        private const val CHANNEL_NAME = "Skills Exchange"
        private const val CHANNEL_DESCRIPTION = "Notifications for jobs, payments, and updates"

        /**
         * Get the current FCM token
         */
        fun getToken(onResult: (String?) -> Unit) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(task.result)
                } else {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    onResult(null)
                }
            }
        }

        /**
         * Delete the current FCM token (for logout)
         */
        fun deleteToken(onComplete: () -> Unit = {}) {
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener {
                onComplete()
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        // NOTIF-002: Send token to backend
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // NOTIF-002: Persist notification locally
        val notificationData = remoteMessage.data
        val type = notificationData["type"] ?: "GENERAL"
        val title = remoteMessage.notification?.title ?: notificationData["title"] ?: "Skills Exchange"
        val body = remoteMessage.notification?.body ?: notificationData["body"] ?: ""
        val jobId = notificationData["jobId"] ?: ""

        // Show notification
        showNotification(title, body, jobId, type)
    }

    private fun sendTokenToServer(token: String) {
        // NOTIF-002: In production, send token to your backend
        // For now, just log it
        Log.d(TAG, "FCM token to send to server: $token")
    }

    private fun showNotification(title: String, body: String, jobId: String, type: String) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("jobId", jobId)
            putExtra("notificationType", type)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}