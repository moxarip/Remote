package com.example.repository

import android.content.Context
import android.os.Build
import com.example.firebase.FirebaseManager
import com.example.models.BackupCommand
import com.example.models.CommandStatus
import com.example.models.CommandType
import com.example.models.DeviceRole
import com.example.models.HostDevice
import com.example.models.PairingCodeData
import com.example.models.UserSession
import com.example.models.VaultFile
import com.example.models.VaultSummary
import com.example.services.HostBackupForegroundService
import com.example.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.random.Random

class BackupRepository(private val context: Context) {

    private val repoScope = CoroutineScope(Dispatchers.IO)

    // Current device identification
    val localDeviceId: String = run {
        val prefs = context.getSharedPreferences("remote_backup_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("local_device_id", null)
        if (id == null) {
            val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(8)
            id = "android_$randomSuffix"
            prefs.edit().putString("local_device_id", id).apply()
        }
        id
    }

    val localDeviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    // Current Session
    private val _currentUser = MutableStateFlow<UserSession?>(null)
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    // Host specific state
    private val _currentHostDevice = MutableStateFlow<HostDevice?>(null)
    val currentHostDevice: StateFlow<HostDevice?> = _currentHostDevice.asStateFlow()

    private val _activePairingCode = MutableStateFlow<PairingCodeData?>(null)
    val activePairingCode: StateFlow<PairingCodeData?> = _activePairingCode.asStateFlow()

    private val _hostFiles = MutableStateFlow<List<VaultFile>>(emptyList())
    val hostFiles: StateFlow<List<VaultFile>> = _hostFiles.asStateFlow()

    private val _vaultSummary = MutableStateFlow<VaultSummary?>(null)
    val vaultSummary: StateFlow<VaultSummary?> = _vaultSummary.asStateFlow()

    private val _selectedVaultPath = MutableStateFlow<String>("")
    val selectedVaultPath: StateFlow<String> = _selectedVaultPath.asStateFlow()

    // Admin specific state: combined list of paired hosts
    val pairedHosts: StateFlow<List<HostDevice>> = combine(
        FirebaseManager.syncedDevices,
        FirebaseManager.adminPairedHosts,
        _currentUser
    ) { devices, adminMap, user ->
        val adminId = user?.userId ?: ""
        val hostIds = adminMap[adminId] ?: emptySet()
        // If empty, also include any locally registered host if role is ADMIN for seamless same-device testing
        if (hostIds.isEmpty()) {
            devices.values.filter { it.role == DeviceRole.HOST.name }
        } else {
            devices.values.filter { it.deviceId in hostIds }
        }
    }.stateIn(repoScope, SharingStarted.Lazily, emptyList())

    init {
        FirebaseManager.init(context)
        // Default vault directory
        val defaultDir = StorageUtils.getDefaultVaultFolder(context)
        _selectedVaultPath.value = defaultDir.absolutePath
    }

    suspend fun login(email: String, pass: String): Result<UserSession> {
        val result = FirebaseManager.login(email, pass)
        result.onSuccess { session ->
            _currentUser.value = session
        }
        return result
    }

    suspend fun register(email: String, pass: String): Result<UserSession> {
        val result = FirebaseManager.register(email, pass)
        result.onSuccess { session ->
            _currentUser.value = session
        }
        return result
    }

    fun logout() {
        FirebaseManager.logout()
        _currentUser.value = null
        _currentHostDevice.value = null
        _activePairingCode.value = null
    }

    suspend fun setDeviceRole(role: DeviceRole) {
        val user = _currentUser.value ?: return
        _currentUser.value = user.copy(selectedRole = role, role = role)

        // Store role preference in Firebase Realtime Database
        FirebaseManager.saveRolePreference(
            userId = user.userId,
            deviceId = localDeviceId,
            role = role
        )

        if (role == DeviceRole.HOST) {
            val (freeBytes, totalBytes) = StorageUtils.getStorageStats()
            val host = HostDevice(
                deviceId = localDeviceId,
                userId = user.userId,
                name = localDeviceName,
                role = DeviceRole.HOST.name,
                status = "ONLINE",
                lastSeen = System.currentTimeMillis(),
                vaultId = "vault_default",
                vaultPath = _selectedVaultPath.value,
                batteryPercent = StorageUtils.getBatteryPercent(context),
                storageFreeBytes = freeBytes,
                storageTotalBytes = totalBytes
            )
            _currentHostDevice.value = host
            FirebaseManager.registerOrUpdateDevice(host)

            // Auto initial scan for demonstration
            scanLocalVault()
        } else {
            // Register or update Admin device entry in Firebase RTDB
            val adminDevice = HostDevice(
                deviceId = localDeviceId,
                userId = user.userId,
                name = localDeviceName,
                role = DeviceRole.ADMIN.name,
                status = "ONLINE",
                lastSeen = System.currentTimeMillis()
            )
            FirebaseManager.registerOrUpdateDevice(adminDevice)
        }
    }

    fun setVaultPath(path: String) {
        _selectedVaultPath.value = path
        val host = _currentHostDevice.value
        if (host != null) {
            val updated = host.copy(vaultPath = path)
            _currentHostDevice.value = updated
            FirebaseManager.registerOrUpdateDevice(updated)
        }
    }

    fun scanLocalVault() {
        val host = _currentHostDevice.value ?: return
        val folder = File(_selectedVaultPath.value.ifEmpty { StorageUtils.getDefaultVaultFolder(context).absolutePath })
        val (files, summary) = StorageUtils.scanFolder(folder, host.deviceId, host.vaultId)

        _hostFiles.value = files
        _vaultSummary.value = summary

        val (freeBytes, totalBytes) = StorageUtils.getStorageStats()
        val updatedHost = host.copy(
            fileCount = summary.filesFound,
            folderCount = summary.foldersFound,
            vaultSizeBytes = summary.totalSizeBytes,
            lastScan = summary.lastScanTime,
            storageFreeBytes = freeBytes,
            storageTotalBytes = totalBytes,
            batteryPercent = StorageUtils.getBatteryPercent(context),
            lastSeen = System.currentTimeMillis(),
            vaultPath = summary.vaultPath
        )
        _currentHostDevice.value = updatedHost
        FirebaseManager.uploadVaultMetadata(host.deviceId, files, summary)
    }

    fun generatePairingCode(): PairingCodeData {
        val host = _currentHostDevice.value
            ?: HostDevice(
                deviceId = localDeviceId,
                userId = _currentUser.value?.userId ?: "user_default",
                name = localDeviceName
            )
        val codeNum = Random.nextInt(100_000, 999_999).toString()
        val pairing = PairingCodeData(
            code = codeNum,
            hostId = host.deviceId,
            hostName = host.name,
            createdBy = host.userId,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (10 * 60 * 1000)
        )
        FirebaseManager.createPairingCode(pairing)
        _activePairingCode.value = pairing
        return pairing
    }

    fun claimPairingCode(code: String): Result<HostDevice> {
        val adminId = _currentUser.value?.userId ?: "admin_user"
        return FirebaseManager.claimPairingCode(code.trim(), adminId)
    }

    fun sendCommand(
        hostId: String,
        type: CommandType,
        fileIds: List<String> = emptyList()
    ): String {
        val adminId = _currentUser.value?.userId ?: "admin_user"
        val cmdId = "cmd_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
        val command = BackupCommand(
            commandId = cmdId,
            type = type.name,
            status = CommandStatus.PENDING.name,
            hostId = hostId,
            adminId = adminId,
            fileIds = fileIds,
            createdAt = System.currentTimeMillis()
        )

        FirebaseManager.createCommand(command)

        // If this device is also the target host (e.g. testing both or running host),
        // invoke foreground service immediately
        if (hostId == localDeviceId || _currentHostDevice.value?.deviceId == hostId) {
            HostBackupForegroundService.startCommand(
                context = context,
                command = command,
                vaultPath = _selectedVaultPath.value
            )
        }

        return cmdId
    }

    fun cancelCommand(hostId: String, commandId: String) {
        val cmd = BackupCommand(
            commandId = commandId,
            hostId = hostId,
            type = CommandType.CANCEL.name,
            status = CommandStatus.CANCELLED.name,
            completedAt = System.currentTimeMillis()
        )
        FirebaseManager.updateCommand(cmd)
    }

    fun getCommandsForHost(hostId: String): StateFlow<List<BackupCommand>> {
        return FirebaseManager.syncedCommands.combine(
            MutableStateFlow(hostId)
        ) { map, id ->
            map[id] ?: emptyList()
        }.stateIn(repoScope, SharingStarted.Lazily, emptyList())
    }

    fun getFilesForHost(hostId: String): StateFlow<List<VaultFile>> {
        return FirebaseManager.syncedFiles.combine(
            MutableStateFlow(hostId)
        ) { map, id ->
            map[id] ?: emptyList()
        }.stateIn(repoScope, SharingStarted.Lazily, emptyList())
    }
}
