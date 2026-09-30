package com.example.services

import android.util.Log
import com.example.models.BackupCommand
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RemoteBackupFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("RemoteBackupFCM", "New FCM Token received: $token")
        // Token can be saved to FirebaseManager for current host
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val commandId = data["commandId"] ?: return
            val hostId = data["hostId"] ?: return
            val type = data["type"] ?: "SCAN"
            val fileIdsStr = data["fileIds"] ?: ""
            val fileIds = if (fileIdsStr.isNotBlank()) fileIdsStr.split(",") else emptyList()

            val command = BackupCommand(
                commandId = commandId,
                hostId = hostId,
                type = type,
                fileIds = fileIds
            )

            HostBackupForegroundService.startCommand(
                context = applicationContext,
                command = command,
                vaultPath = ""
            )
        }
    }
}
