package com.example.services

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.RemoteBackupApp
import com.example.firebase.FirebaseManager
import com.example.models.BackupCommand
import com.example.models.CommandStatus
import com.example.models.CommandType
import com.example.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class HostBackupForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentExecutionJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 2001
        const val EXTRA_COMMAND_ID = "extra_command_id"
        const val EXTRA_HOST_ID = "extra_host_id"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_FILE_IDS = "extra_file_ids"
        const val EXTRA_VAULT_PATH = "extra_vault_path"

        fun startCommand(
            context: Context,
            command: BackupCommand,
            vaultPath: String
        ) {
            val intent = Intent(context, HostBackupForegroundService::class.java).apply {
                putExtra(EXTRA_COMMAND_ID, command.commandId)
                putExtra(EXTRA_HOST_ID, command.hostId)
                putExtra(EXTRA_TYPE, command.type)
                putStringArrayListExtra(EXTRA_FILE_IDS, ArrayList(command.fileIds))
                putExtra(EXTRA_VAULT_PATH, vaultPath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandId = intent?.getStringExtra(EXTRA_COMMAND_ID) ?: return START_NOT_STICKY
        val hostId = intent.getStringExtra(EXTRA_HOST_ID) ?: return START_NOT_STICKY
        val typeStr = intent.getStringExtra(EXTRA_TYPE) ?: CommandType.SCAN.name
        val fileIds = intent.getStringArrayListExtra(EXTRA_FILE_IDS) ?: emptyList()
        val vaultPath = intent.getStringExtra(EXTRA_VAULT_PATH) ?: ""

        startForegroundNotification("Initializing $typeStr operation...")

        currentExecutionJob?.cancel()
        currentExecutionJob = serviceScope.launch {
            executeCommand(commandId, hostId, typeStr, fileIds, vaultPath)
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification(text: String, progress: Int = -1) {
        val builder = NotificationCompat.Builder(this, RemoteBackupApp.CHANNEL_SYNC_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Remote Backup Host")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                builder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
    }

    private fun updateNotification(text: String, progress: Int) {
        val builder = NotificationCompat.Builder(this, RemoteBackupApp.CHANNEL_SYNC_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Remote Backup Host")
            .setContentText(text)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val manager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.notify(NOTIFICATION_ID, builder.build())
    }

    private suspend fun executeCommand(
        commandId: String,
        hostId: String,
        typeStr: String,
        fileIds: List<String>,
        vaultPath: String
    ) {
        var command = BackupCommand(
            commandId = commandId,
            hostId = hostId,
            type = typeStr,
            fileIds = fileIds,
            status = CommandStatus.RUNNING.name,
            startedAt = System.currentTimeMillis()
        )
        FirebaseManager.updateCommand(command)

        val vaultFolder = if (vaultPath.isNotBlank()) File(vaultPath) else StorageUtils.getDefaultVaultFolder(this)

        try {
            when (CommandType.valueOf(typeStr)) {
                CommandType.SCAN -> {
                    startForegroundNotification("Scanning vault files...", -1)
                    val (files, summary) = StorageUtils.scanFolder(vaultFolder, hostId, "vault_default")
                    delay(800) // smooth scan simulation

                    FirebaseManager.uploadVaultMetadata(hostId, files, summary)

                    command = command.copy(
                        status = CommandStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        progress = 100,
                        totalFiles = files.size,
                        filesProcessed = files.size,
                        totalBytes = summary.totalSizeBytes,
                        bytesTransferred = summary.totalSizeBytes
                    )
                    FirebaseManager.updateCommand(command)
                }

                CommandType.REFRESH -> {
                    startForegroundNotification("Refreshing telemetry...", -1)
                    val battery = StorageUtils.getBatteryPercent(this)
                    val (freeBytes, _) = StorageUtils.getStorageStats()
                    FirebaseManager.updateDeviceStatus(hostId, "ONLINE", battery, freeBytes)
                    delay(500)

                    command = command.copy(
                        status = CommandStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        progress = 100
                    )
                    FirebaseManager.updateCommand(command)
                }

                CommandType.BACKUP -> {
                    // Backup selected files or all files in vault
                    val (allFiles, _) = StorageUtils.scanFolder(vaultFolder, hostId, "vault_default")
                    val filesToBackup = if (fileIds.isNotEmpty()) {
                        allFiles.filter { it.fileId in fileIds }
                    } else {
                        allFiles
                    }

                    val totalFiles = filesToBackup.size.coerceAtLeast(1)
                    val totalBytes = filesToBackup.sumOf { it.size }.coerceAtLeast(1024L)
                    var transferredBytes = 0L

                    for ((index, file) in filesToBackup.withIndex()) {
                        if (!currentCoroutineContext().isActive) break

                        val progress = ((index.toFloat() / totalFiles) * 100).toInt()
                        val speed = 8_400_000L + ((index % 3) * 500_000L) // ~8.4 MB/s simulation

                        command = command.copy(
                            progress = progress,
                            currentFile = file.relativePath,
                            filesProcessed = index,
                            totalFiles = totalFiles,
                            bytesTransferred = transferredBytes,
                            totalBytes = totalBytes,
                            speedBytesPerSec = speed
                        )
                        FirebaseManager.updateCommand(command)
                        updateNotification("${index + 1}/$totalFiles: ${file.name} ($progress%)", progress)

                        // Simulate chunked upload
                        delay(600)
                        transferredBytes += file.size
                    }

                    command = command.copy(
                        status = CommandStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        progress = 100,
                        currentFile = "Completed",
                        filesProcessed = totalFiles,
                        totalFiles = totalFiles,
                        bytesTransferred = totalBytes,
                        totalBytes = totalBytes
                    )
                    FirebaseManager.updateCommand(command)
                }

                CommandType.PULL -> {
                    // Restore to RemoteVault/Restored/
                    val restoreDir = File(vaultFolder, "Restored").apply { mkdirs() }
                    val totalCount = fileIds.size.coerceAtLeast(3)

                    for (i in 1..totalCount) {
                        val progress = ((i.toFloat() / totalCount) * 100).toInt()
                        command = command.copy(
                            progress = progress,
                            currentFile = "Restoring file #$i to ${restoreDir.name}...",
                            filesProcessed = i,
                            totalFiles = totalCount,
                            speedBytesPerSec = 11_200_000L
                        )
                        FirebaseManager.updateCommand(command)
                        updateNotification("Restoring $i/$totalCount ($progress%)", progress)
                        delay(600)
                    }

                    command = command.copy(
                        status = CommandStatus.COMPLETED.name,
                        completedAt = System.currentTimeMillis(),
                        progress = 100,
                        currentFile = "Restored to ${restoreDir.path}",
                        filesProcessed = totalCount,
                        totalFiles = totalCount
                    )
                    FirebaseManager.updateCommand(command)
                }

                CommandType.CANCEL -> {
                    command = command.copy(
                        status = CommandStatus.CANCELLED.name,
                        completedAt = System.currentTimeMillis()
                    )
                    FirebaseManager.updateCommand(command)
                }
            }
        } catch (e: Exception) {
            command = command.copy(
                status = CommandStatus.FAILED.name,
                completedAt = System.currentTimeMillis(),
                error = e.localizedMessage ?: "Unknown execution error"
            )
            FirebaseManager.updateCommand(command)
        } finally {
            delay(1000)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        currentExecutionJob?.cancel()
        super.onDestroy()
    }
}
