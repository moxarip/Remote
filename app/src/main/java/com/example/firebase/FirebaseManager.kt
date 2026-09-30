package com.example.firebase

import android.content.Context
import android.util.Log
import com.example.models.BackupCommand
import com.example.models.CommandStatus
import com.example.models.CommandType
import com.example.models.DeviceRole
import com.example.models.HostDevice
import com.example.models.PairingCodeData
import com.example.models.UserSession
import com.example.models.VaultFile
import com.example.models.VaultSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object FirebaseManager {

    private const val TAG = "FirebaseManager"
    const val DEFAULT_PROJECT_ID = "remote-backup-d1ee0"
    const val DEFAULT_DATABASE_URL = "https://remote-backup-d1ee0-default-rtdb.firebaseio.com"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncJob: Job? = null

    // In-memory synced state
    private val _syncedDevices = MutableStateFlow<Map<String, HostDevice>>(emptyMap())
    val syncedDevices: StateFlow<Map<String, HostDevice>> = _syncedDevices.asStateFlow()

    private val _syncedFiles = MutableStateFlow<Map<String, List<VaultFile>>>(emptyMap())
    val syncedFiles: StateFlow<Map<String, List<VaultFile>>> = _syncedFiles.asStateFlow()

    private val _syncedPairings = MutableStateFlow<Map<String, PairingCodeData>>(emptyMap())
    val syncedPairings: StateFlow<Map<String, PairingCodeData>> = _syncedPairings.asStateFlow()

    private val _syncedCommands = MutableStateFlow<Map<String, List<BackupCommand>>>(emptyMap())
    val syncedCommands: StateFlow<Map<String, List<BackupCommand>>> = _syncedCommands.asStateFlow()

    // Map of adminId -> Set of paired hostIds
    private val _adminPairedHosts = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val adminPairedHosts: StateFlow<Map<String, Set<String>>> = _adminPairedHosts.asStateFlow()

    fun sanitizeEmail(email: String): String {
        return email.lowercase().trim().replace(".", "_").replace("@", "_at_")
    }

    fun init(context: Context) {
        Log.d(TAG, "Initializing FirebaseManager directly with RTDB: $DEFAULT_DATABASE_URL")
        startBackgroundCloudSync()
    }

    private fun startBackgroundCloudSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                try {
                    refreshDevicesFromCloudInternal()
                    refreshPairingsFromCloudInternal()
                    refreshCommandsFromCloudInternal()
                } catch (e: Exception) {
                    Log.e(TAG, "Background cloud sync error: ${e.message}")
                }
                delay(4000) // Poll every 4 seconds for instantaneous multi-device updates
            }
        }
    }

    // --- Authentication via Firebase Realtime Database Accounts ---
    suspend fun login(email: String, pass: String): Result<UserSession> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val emailKey = sanitizeEmail(cleanEmail)

        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(Exception("يرجى إدخال البريد الإلكتروني"))
        }
        if (pass.isBlank()) {
            return@withContext Result.failure(Exception("يرجى إدخال كلمة المرور"))
        }

        try {
            val url = "$DEFAULT_DATABASE_URL/accounts/$emailKey.json"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("تعذر الاتصال بـ Firebase (${response.code})"))
            }

            val body = response.body?.string()?.trim() ?: "null"
            if (body == "null" || body.isEmpty()) {
                // Strict check: DO NOT accept random accounts!
                return@withContext Result.failure(
                    Exception("هذا البريد الإلكتروني غير مسجل في Firebase.\nيرجى الضغط على 'إنشاء حساب جديد' والتسجيل أولاً.")
                )
            }

            val json = JSONObject(body)
            val storedPassword = json.optString("password", "")

            if (storedPassword != pass) {
                return@withContext Result.failure(Exception("كلمة المرور غير صحيحة. يرجى التأكد من كلمة المرور."))
            }

            val userId = json.optString("userId", "usr_${UUID.nameUUIDFromBytes(cleanEmail.toByteArray())}")
            val displayName = json.optString("displayName", cleanEmail.substringBefore('@'))
            val roleStr = json.optString("role", "")
            val selectedRole = try {
                if (roleStr.isNotBlank()) DeviceRole.valueOf(roleStr) else DeviceRole.UNSET
            } catch (e: Exception) {
                DeviceRole.UNSET
            }

            val session = UserSession(
                userId = userId,
                email = cleanEmail,
                displayName = displayName,
                role = selectedRole,
                selectedRole = selectedRole
            )

            // Update user lastSeen in Firebase RTDB
            val patchJson = JSONObject().apply {
                put("lastSeen", System.currentTimeMillis())
            }
            val patchReq = Request.Builder()
                .url("$DEFAULT_DATABASE_URL/accounts/$emailKey.json")
                .patch(patchJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(patchReq).execute()

            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}")
            Result.failure(Exception("خطأ في الاتصال بـ Firebase: ${e.message}"))
        }
    }

    suspend fun register(email: String, pass: String): Result<UserSession> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val emailKey = sanitizeEmail(cleanEmail)

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(Exception("يرجى إدخال بريد إلكتروني صالح"))
        }
        if (pass.length < 6) {
            return@withContext Result.failure(Exception("يجب أن تكون كلمة المرور 6 أحرف أو أرقام على الأقل"))
        }

        try {
            // Check if already registered
            val checkUrl = "$DEFAULT_DATABASE_URL/accounts/$emailKey.json"
            val checkReq = Request.Builder().url(checkUrl).get().build()
            val checkResp = httpClient.newCall(checkReq).execute()
            val existingBody = checkResp.body?.string()?.trim() ?: "null"

            if (existingBody != "null" && existingBody.isNotEmpty() && existingBody != "{}") {
                return@withContext Result.failure(Exception("هذا البريد مسجل مسبقاً في Firebase. يرجى تسجيل الدخول."))
            }

            val userId = "usr_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"
            val displayName = cleanEmail.substringBefore('@')

            val accountJson = JSONObject().apply {
                put("userId", userId)
                put("email", cleanEmail)
                put("password", pass)
                put("displayName", displayName)
                put("createdAt", System.currentTimeMillis())
                put("lastSeen", System.currentTimeMillis())
            }

            val putReq = Request.Builder()
                .url("$DEFAULT_DATABASE_URL/accounts/$emailKey.json")
                .put(accountJson.toString().toRequestBody(jsonMediaType))
                .build()
            val putResp = httpClient.newCall(putReq).execute()

            if (!putResp.isSuccessful) {
                return@withContext Result.failure(Exception("فشل إنشاء الحساب في Firebase (${putResp.code})"))
            }

            // Also create user record
            val userRecord = JSONObject().apply {
                put("displayId", displayName)
                put("email", cleanEmail)
                put("lastSeen", System.currentTimeMillis())
            }
            val userReq = Request.Builder()
                .url("$DEFAULT_DATABASE_URL/users/$userId.json")
                .put(userRecord.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(userReq).execute()

            val session = UserSession(
                userId = userId,
                email = cleanEmail,
                displayName = displayName,
                role = DeviceRole.UNSET,
                selectedRole = DeviceRole.UNSET
            )
            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Register exception: ${e.message}")
            Result.failure(Exception("خطأ في تسجيل الحساب: ${e.message}"))
        }
    }

    fun logout() {
        // Clear local state
        Log.d(TAG, "User logged out")
    }

    suspend fun saveRolePreference(userId: String, deviceId: String, role: DeviceRole): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val roleJson = JSONObject().apply {
                put("role", role.name)
                put("updatedAt", System.currentTimeMillis())
            }
            if (userId.isNotBlank()) {
                val req = Request.Builder()
                    .url("$DEFAULT_DATABASE_URL/users/$userId.json")
                    .patch(roleJson.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute()
            }
            if (deviceId.isNotBlank()) {
                val req = Request.Builder()
                    .url("$DEFAULT_DATABASE_URL/devices/$deviceId.json")
                    .patch(roleJson.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute()
            }
            refreshDevicesFromCloudInternal()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving role to RTDB: ${e.message}")
            Result.success(Unit)
        }
    }

    // --- Device Management in Realtime Database ---
    fun registerOrUpdateDevice(device: HostDevice) {
        val current = _syncedDevices.value.toMutableMap()
        current[device.deviceId] = device
        _syncedDevices.value = current

        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("deviceId", device.deviceId)
                    put("userId", device.userId)
                    put("email", device.email)
                    put("ownerUid", device.userId)
                    put("name", device.name)
                    put("role", device.role)
                    put("status", device.status)
                    put("online", device.status.equals("ONLINE", ignoreCase = true))
                    put("app", "Remote Backup Vault")
                    put("batteryPercent", device.batteryPercent)
                    put("storageFreeBytes", device.storageFreeBytes)
                    put("storageTotalBytes", device.storageTotalBytes)
                    put("fileCount", device.fileCount)
                    put("folderCount", device.folderCount)
                    put("vaultSizeBytes", device.vaultSizeBytes)
                    put("vaultPath", device.vaultPath)
                    put("lastScan", device.lastScan)
                    put("lastSeen", System.currentTimeMillis())
                }

                val req = Request.Builder()
                    .url("$DEFAULT_DATABASE_URL/devices/${device.deviceId}.json")
                    .put(json.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute()
                Log.d(TAG, "Updated device ${device.deviceId} in Firebase RTDB")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating device in RTDB: ${e.message}")
            }
        }
    }

    fun updateDeviceStatus(deviceId: String, status: String, battery: Int, freeStorage: Long) {
        val current = _syncedDevices.value.toMutableMap()
        val dev = current[deviceId]
        if (dev != null) {
            current[deviceId] = dev.copy(
                status = status,
                batteryPercent = battery,
                storageFreeBytes = freeStorage,
                lastSeen = System.currentTimeMillis()
            )
            _syncedDevices.value = current
        }

        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("status", status)
                    put("online", status.equals("ONLINE", ignoreCase = true))
                    put("batteryPercent", battery)
                    put("storageFreeBytes", freeStorage)
                    put("lastSeen", System.currentTimeMillis())
                }
                val req = Request.Builder()
                    .url("$DEFAULT_DATABASE_URL/devices/$deviceId.json")
                    .patch(json.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating status in RTDB: ${e.message}")
            }
        }
    }

    suspend fun refreshDevicesFromCloud() = withContext(Dispatchers.IO) {
        refreshDevicesFromCloudInternal()
    }

    private fun refreshDevicesFromCloudInternal() {
        try {
            val req = Request.Builder().url("$DEFAULT_DATABASE_URL/devices.json").get().build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return

            val body = resp.body?.string()?.trim() ?: "null"
            if (body == "null" || body.isEmpty() || body == "{}") return

            val json = JSONObject(body)
            val map = _syncedDevices.value.toMutableMap()

            val keys = json.keys()
            while (keys.hasNext()) {
                val devId = keys.next()
                val devObj = json.optJSONObject(devId) ?: continue

                val role = devObj.optString("role", DeviceRole.HOST.name)
                val status = if (devObj.optBoolean("online", false)) "ONLINE" else devObj.optString("status", "ONLINE")

                val hostDevice = HostDevice(
                    deviceId = devId,
                    userId = devObj.optString("userId", devObj.optString("ownerUid", "")),
                    email = devObj.optString("email", ""),
                    name = devObj.optString("name", devObj.optString("app", "Android Host")),
                    role = role,
                    status = status,
                    lastSeen = devObj.optLong("lastSeen", System.currentTimeMillis()),
                    vaultPath = devObj.optString("vaultPath", ""),
                    batteryPercent = devObj.optInt("batteryPercent", 90),
                    storageFreeBytes = devObj.optLong("storageFreeBytes", 0L),
                    storageTotalBytes = devObj.optLong("storageTotalBytes", 0L),
                    fileCount = devObj.optInt("fileCount", 0),
                    folderCount = devObj.optInt("folderCount", 0),
                    vaultSizeBytes = devObj.optLong("vaultSizeBytes", 0L),
                    lastScan = devObj.optLong("lastScan", 0L)
                )
                map[devId] = hostDevice
            }
            _syncedDevices.value = map
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing devices: ${e.message}")
        }
    }

    // --- Vault & Metadata ---
    fun uploadVaultMetadata(deviceId: String, files: List<VaultFile>, summary: VaultSummary) {
        val filesMap = _syncedFiles.value.toMutableMap()
        filesMap[deviceId] = files
        _syncedFiles.value = filesMap

        scope.launch {
            try {
                val filesArray = JSONArray()
                for (f in files) {
                    val fileObj = JSONObject().apply {
                        put("fileId", f.fileId)
                        put("name", f.name)
                        put("relativePath", f.relativePath)
                        put("size", f.size)
                        put("mimeType", f.mimeType)
                        put("category", f.category)
                        put("lastModified", f.lastModified)
                        put("createdAt", f.createdAt)
                        put("hostDeviceId", f.hostDeviceId)
                        put("vaultId", f.vaultId)
                    }
                    filesArray.put(fileObj)
                }

                val putFilesReq = Request.Builder()
                    .url("$DEFAULT_DATABASE_URL/devices/$deviceId/files.json")
                    .put(filesArray.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(putFilesReq).execute()

                val patchDev = JSONObject().apply {
                    put("fileCount", summary.filesFound)
                    put("folderCount", summary.foldersFound)
                    put("vaultSizeBytes", summary.totalSizeBytes)
                    put("lastScan", summary.lastScanTime)
                    put("vaultPath", summary.vaultPath)
                }
                val patchReq = Request.Builder()
                    .url("$DEFAULT_DATABASE_URL/devices/$deviceId.json")
                    .patch(patchDev.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(patchReq).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading vault metadata: ${e.message}")
            }
        }
    }

    suspend fun fetchHostFiles(deviceId: String): List<VaultFile> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$DEFAULT_DATABASE_URL/devices/$deviceId/files.json").get().build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val body = resp.body?.string()?.trim() ?: "null"
            if (body == "null" || body.isEmpty() || body == "[]") return@withContext emptyList()

            val array = JSONArray(body)
            val list = mutableListOf<VaultFile>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    VaultFile(
                        fileId = obj.optString("fileId", "f_$i"),
                        name = obj.optString("name", "file"),
                        relativePath = obj.optString("relativePath", ""),
                        size = obj.optLong("size", 0L),
                        mimeType = obj.optString("mimeType", "*/*"),
                        category = obj.optString("category", "Other"),
                        lastModified = obj.optLong("lastModified", 0L),
                        createdAt = obj.optLong("createdAt", 0L),
                        hostDeviceId = deviceId,
                        vaultId = obj.optString("vaultId", "")
                    )
                )
            }
            val filesMap = _syncedFiles.value.toMutableMap()
            filesMap[deviceId] = list
            _syncedFiles.value = filesMap
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching host files: ${e.message}")
            emptyList()
        }
    }

    // --- Pairing System ---
    fun createPairingCode(pairing: PairingCodeData): String {
        val map = _syncedPairings.value.toMutableMap()
        map[pairing.code] = pairing
        _syncedPairings.value = map

        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("code", pairing.code)
                    put("hostId", pairing.hostId)
                    put("hostName", pairing.hostName)
                    put("createdBy", pairing.createdBy)
                    put("createdAt", pairing.createdAt)
                    put("expiresAt", pairing.expiresAt)
                    put("used", pairing.used)
                }
                val req = Request.Builder()
                    .url("$DEFAULT_DATABASE_URL/pairings/${pairing.code}.json")
                    .put(json.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating pairing code in RTDB: ${e.message}")
            }
        }
        return pairing.code
    }

    suspend fun claimPairingCode(code: String, adminId: String): Result<HostDevice> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim()

        try {
            val req = Request.Builder().url("$DEFAULT_DATABASE_URL/pairings/$cleanCode.json").get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string()?.trim() ?: "null"

            if (body == "null" || body.isEmpty() || body == "{}") {
                return@withContext Result.failure(Exception("كود الاقتران '$cleanCode' غير صحيح أو غير موجود."))
            }

            val json = JSONObject(body)
            val used = json.optBoolean("used", false)
            val expiresAt = json.optLong("expiresAt", 0L)
            val hostId = json.optString("hostId", "")
            val hostName = json.optString("hostName", "Remote Host")

            if (used) {
                return@withContext Result.failure(Exception("كود الاقتران تم استخدامه مسبقاً."))
            }
            if (System.currentTimeMillis() > expiresAt) {
                return@withContext Result.failure(Exception("انتهت صلاحية كود الاقتران (أكثر من 10 دقائق)."))
            }

            // Mark as used
            val patch = JSONObject().apply {
                put("used", true)
                put("usedByAdminId", adminId)
            }
            val patchReq = Request.Builder()
                .url("$DEFAULT_DATABASE_URL/pairings/$cleanCode.json")
                .patch(patch.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(patchReq).execute()

            // Associate admin
            val adminMap = _adminPairedHosts.value.toMutableMap()
            val hosts = adminMap[adminId]?.toMutableSet() ?: mutableSetOf()
            hosts.add(hostId)
            adminMap[adminId] = hosts
            _adminPairedHosts.value = adminMap

            // Return device
            refreshDevicesFromCloudInternal()
            val host = _syncedDevices.value[hostId] ?: HostDevice(
                deviceId = hostId,
                name = hostName,
                status = "ONLINE"
            )
            Result.success(host)
        } catch (e: Exception) {
            Log.e(TAG, "Error claiming pairing code: ${e.message}")
            Result.failure(Exception("فشل إتمام الاقتران: ${e.message}"))
        }
    }

    private fun refreshPairingsFromCloudInternal() {
        try {
            val req = Request.Builder().url("$DEFAULT_DATABASE_URL/pairings.json").get().build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return

            val body = resp.body?.string()?.trim() ?: "null"
            if (body == "null" || body.isEmpty()) return

            val json = JSONObject(body)
            val map = _syncedPairings.value.toMutableMap()
            val keys = json.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val pObj = json.optJSONObject(code) ?: continue
                map[code] = PairingCodeData(
                    code = code,
                    hostId = pObj.optString("hostId", ""),
                    hostName = pObj.optString("hostName", ""),
                    createdBy = pObj.optString("createdBy", ""),
                    createdAt = pObj.optLong("createdAt", 0L),
                    expiresAt = pObj.optLong("expiresAt", 0L),
                    used = pObj.optBoolean("used", false),
                    usedByAdminId = pObj.optString("usedByAdminId", null)
                )
            }
            _syncedPairings.value = map
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing pairings: ${e.message}")
        }
    }

    // --- Commands System in Realtime Database ---
    fun createCommand(command: BackupCommand) {
        val currentMap = _syncedCommands.value.toMutableMap()
        val list = currentMap[command.hostId]?.toMutableList() ?: mutableListOf()
        list.removeAll { it.commandId == command.commandId }
        list.add(0, command)
        currentMap[command.hostId] = list
        _syncedCommands.value = currentMap

        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("commandId", command.commandId)
                    put("type", command.type)
                    put("status", command.status)
                    put("hostId", command.hostId)
                    put("adminId", command.adminId)
                    put("createdAt", command.createdAt)
                    put("progress", command.progress)
                    put("currentFile", command.currentFile)
                    put("speedBytesPerSec", command.speedBytesPerSec)
                    put("filesProcessed", command.filesProcessed)
                    put("totalFiles", command.totalFiles)
                    val arr = JSONArray()
                    command.fileIds.forEach { arr.put(it) }
                    put("fileIds", arr)
                }

                val req = Request.Builder()
                    .url("$DEFAULT_DATABASE_URL/commands/${command.hostId}/${command.commandId}.json")
                    .put(json.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating command in RTDB: ${e.message}")
            }
        }
    }

    fun updateCommand(command: BackupCommand) {
        val currentMap = _syncedCommands.value.toMutableMap()
        val list = currentMap[command.hostId]?.toMutableList() ?: mutableListOf()
        val idx = list.indexOfFirst { it.commandId == command.commandId }
        if (idx >= 0) {
            list[idx] = command
        } else {
            list.add(0, command)
        }
        currentMap[command.hostId] = list
        _syncedCommands.value = currentMap

        scope.launch {
            try {
                val json = JSONObject().apply {
                    put("status", command.status)
                    put("progress", command.progress)
                    put("currentFile", command.currentFile)
                    put("speedBytesPerSec", command.speedBytesPerSec)
                    put("filesProcessed", command.filesProcessed)
                    put("totalFiles", command.totalFiles)
                    if (command.startedAt != null) put("startedAt", command.startedAt)
                    if (command.completedAt != null) put("completedAt", command.completedAt)
                    if (command.error != null) put("error", command.error)
                }

                val req = Request.Builder()
                    .url("$DEFAULT_DATABASE_URL/commands/${command.hostId}/${command.commandId}.json")
                    .patch(json.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(req).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating command in RTDB: ${e.message}")
            }
        }
    }

    private fun refreshCommandsFromCloudInternal() {
        try {
            val req = Request.Builder().url("$DEFAULT_DATABASE_URL/commands.json").get().build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return

            val body = resp.body?.string()?.trim() ?: "null"
            if (body == "null" || body.isEmpty() || body == "{}") return

            val json = JSONObject(body)
            val currentMap = _syncedCommands.value.toMutableMap()

            val hostKeys = json.keys()
            while (hostKeys.hasNext()) {
                val hostId = hostKeys.next()
                val hostCmds = json.optJSONObject(hostId) ?: continue
                val cmdList = mutableListOf<BackupCommand>()

                val cmdKeys = hostCmds.keys()
                while (cmdKeys.hasNext()) {
                    val cmdId = cmdKeys.next()
                    val cObj = hostCmds.optJSONObject(cmdId) ?: continue

                    val fIds = mutableListOf<String>()
                    val fArr = cObj.optJSONArray("fileIds")
                    if (fArr != null) {
                        for (i in 0 until fArr.length()) {
                            fIds.add(fArr.optString(i))
                        }
                    }

                    cmdList.add(
                        BackupCommand(
                            commandId = cmdId,
                            type = cObj.optString("type", CommandType.SCAN.name),
                            status = cObj.optString("status", CommandStatus.PENDING.name),
                            hostId = hostId,
                            adminId = cObj.optString("adminId", ""),
                            fileIds = fIds,
                            createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
                            startedAt = if (cObj.has("startedAt")) cObj.optLong("startedAt") else null,
                            completedAt = if (cObj.has("completedAt")) cObj.optLong("completedAt") else null,
                            progress = cObj.optInt("progress", 0),
                            currentFile = if (cObj.has("currentFile")) cObj.optString("currentFile") else null,
                            speedBytesPerSec = cObj.optLong("speedBytesPerSec", 0L),
                            filesProcessed = cObj.optInt("filesProcessed", 0),
                            totalFiles = cObj.optInt("totalFiles", 0),
                            error = if (cObj.has("error")) cObj.optString("error") else null
                        )
                    )
                }
                cmdList.sortByDescending { it.createdAt }
                currentMap[hostId] = cmdList
            }
            _syncedCommands.value = currentMap
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing commands: ${e.message}")
        }
    }
}
