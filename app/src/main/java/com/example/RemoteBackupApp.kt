package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class RemoteBackupApp : Application() {

    companion object {
        const val CHANNEL_SYNC_ID = "remote_backup_sync_channel"
        const val CHANNEL_ALERT_ID = "remote_backup_alerts_channel"
        lateinit var instance: RemoteBackupApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val syncChannel = NotificationChannel(
                CHANNEL_SYNC_ID,
                "Remote Backup Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress for backup, scan, and pull operations"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Remote Backup Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for completed operations and remote pairing requests"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(syncChannel)
            notificationManager?.createNotificationChannel(alertChannel)
        }
    }
}
