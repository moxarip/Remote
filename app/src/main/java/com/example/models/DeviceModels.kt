package com.example.models

enum class DeviceRole {
    HOST,
    ADMIN,
    UNSET
}

enum class CommandType {
    SCAN,
    REFRESH,
    BACKUP,
    PULL,
    CANCEL
}

enum class CommandStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class HostDevice(
    val deviceId: String = "",
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = DeviceRole.HOST.name,
    val status: String = "ONLINE",
    val fcmToken: String = "",
    val lastSeen: Long = System.currentTimeMillis(),
    val vaultId: String = "",
    val vaultPath: String = "",
    val batteryPercent: Int = 100,
    val storageFreeBytes: Long = 0L,
    val storageTotalBytes: Long = 0L,
    val fileCount: Int = 0,
    val folderCount: Int = 0,
    val vaultSizeBytes: Long = 0L,
    val lastScan: Long = 0L
)

data class VaultFile(
    val fileId: String = "",
    val name: String = "",
    val relativePath: String = "",
    val size: Long = 0L,
    val mimeType: String = "*/*",
    val lastModified: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val hostDeviceId: String = "",
    val vaultId: String = "",
    val category: String = "Other",
    val isBackedUp: Boolean = false
)

data class PairingCodeData(
    val code: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (10 * 60 * 1000), // 10 minutes
    val used: Boolean = false,
    val usedByAdminId: String? = null
)

data class BackupCommand(
    val commandId: String = "",
    val type: String = CommandType.SCAN.name,
    val status: String = CommandStatus.PENDING.name,
    val hostId: String = "",
    val adminId: String = "",
    val fileIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val progress: Int = 0,
    val currentFile: String? = null,
    val speedBytesPerSec: Long = 0L,
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
)

data class VaultSummary(
    val filesFound: Int = 0,
    val foldersFound: Int = 0,
    val totalSizeBytes: Long = 0L,
    val lastScanTime: Long = 0L,
    val vaultPath: String = ""
)

data class UserSession(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: DeviceRole = DeviceRole.UNSET,
    val selectedRole: DeviceRole = DeviceRole.UNSET
)
