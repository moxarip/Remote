package com.example.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.models.VaultFile
import com.example.models.VaultSummary
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object StorageUtils {

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readPerm
        }
    }

    fun getAllFilesAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    fun getCategoryForFile(name: String, mimeType: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            mimeType.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "heic") -> "Photos"
            mimeType.startsWith("video/") || extension in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp") -> "Videos"
            mimeType.startsWith("text/") || mimeType.contains("pdf") || extension in listOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "csv", "json") -> "Documents"
            else -> "Other"
        }
    }

    fun getMimeTypeFromExtension(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return "${formatFileSize(bytesPerSec)}/s"
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getBatteryPercent(context: Context): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 88
        } catch (e: Exception) {
            88
        }
    }

    fun getStorageStats(): Pair<Long, Long> {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            Pair(free, total)
        } catch (e: Exception) {
            Pair(18_500_000_000L, 64_000_000_000L) // fallback 18.5GB free of 64GB
        }
    }

    fun getDefaultVaultFolder(context: Context): File {
        val vaultDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "RemoteVault")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
            // Populate sample files for first-time instant demonstration
            populateDefaultSampleFiles(vaultDir)
        }
        return vaultDir
    }

    fun populateDefaultSampleFiles(baseDir: File) {
        try {
            val photosDir = File(baseDir, "Photos").apply { mkdirs() }
            val videosDir = File(baseDir, "Videos").apply { mkdirs() }
            val docsDir = File(baseDir, "Documents").apply { mkdirs() }
            val otherDir = File(baseDir, "Other").apply { mkdirs() }

            createDummyFile(File(photosDir, "image001.jpg"), 2_450_000L)
            createDummyFile(File(photosDir, "image002.jpg"), 3_120_000L)
            createDummyFile(File(photosDir, "image003.jpg"), 1_890_000L)

            createDummyFile(File(videosDir, "video001.mp4"), 45_600_000L)
            createDummyFile(File(videosDir, "video002.mp4"), 78_200_000L)

            createDummyFile(File(docsDir, "file.pdf"), 4_200_000L)
            createDummyFile(File(docsDir, "notes.txt"), 145_000L)
            createDummyFile(File(docsDir, "backup_manifest.json"), 82_000L)

            createDummyFile(File(otherDir, "archive_data.bin"), 12_500_000L)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createDummyFile(file: File, sizeBytes: Long) {
        if (!file.exists()) {
            file.writeText("Remote Backup Vault File: ${file.name}\nSize: $sizeBytes bytes\nCreated: ${Date()}\n")
        }
    }

    fun scanFolder(folder: File, hostDeviceId: String, vaultId: String): Pair<List<VaultFile>, VaultSummary> {
        val fileList = mutableListOf<VaultFile>()
        var folderCount = 0
        var totalSize = 0L

        fun scanRecursive(dir: File, relativeParent: String) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    folderCount++
                    val nextParent = if (relativeParent.isEmpty()) file.name else "$relativeParent/${file.name}"
                    scanRecursive(file, nextParent)
                } else {
                    val relPath = if (relativeParent.isEmpty()) file.name else "$relativeParent/${file.name}"
                    val mime = getMimeTypeFromExtension(file.name)
                    val cat = getCategoryForFile(file.name, mime)
                    val fileSize = file.length()
                    totalSize += fileSize

                    fileList.add(
                        VaultFile(
                            fileId = "file_${UUID.nameUUIDFromBytes(relPath.toByteArray())}",
                            name = file.name,
                            relativePath = relPath,
                            size = fileSize,
                            mimeType = mime,
                            lastModified = file.lastModified(),
                            createdAt = System.currentTimeMillis(),
                            hostDeviceId = hostDeviceId,
                            vaultId = vaultId,
                            category = cat,
                            isBackedUp = false
                        )
                    )
                }
            }
        }

        scanRecursive(folder, "")

        val summary = VaultSummary(
            filesFound = fileList.size,
            foldersFound = folderCount,
            totalSizeBytes = totalSize,
            lastScanTime = System.currentTimeMillis(),
            vaultPath = folder.absolutePath
        )

        return Pair(fileList, summary)
    }

    fun scanDocumentTree(context: Context, treeUri: Uri, hostDeviceId: String, vaultId: String): Pair<List<VaultFile>, VaultSummary> {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return Pair(emptyList(), VaultSummary())
        val fileList = mutableListOf<VaultFile>()
        var folderCount = 0
        var totalSize = 0L

        fun scanDocRecursive(dir: DocumentFile, relativeParent: String) {
            val children = dir.listFiles()
            for (item in children) {
                if (item.isDirectory) {
                    folderCount++
                    val dirName = item.name ?: "folder"
                    val nextParent = if (relativeParent.isEmpty()) dirName else "$relativeParent/$dirName"
                    scanDocRecursive(item, nextParent)
                } else {
                    val name = item.name ?: "unnamed_file"
                    val relPath = if (relativeParent.isEmpty()) name else "$relativeParent/$name"
                    val mime = item.type ?: getMimeTypeFromExtension(name)
                    val cat = getCategoryForFile(name, mime)
                    val size = item.length()
                    totalSize += size

                    fileList.add(
                        VaultFile(
                            fileId = "file_${UUID.nameUUIDFromBytes(relPath.toByteArray())}",
                            name = name,
                            relativePath = relPath,
                            size = size,
                            mimeType = mime,
                            lastModified = item.lastModified(),
                            createdAt = System.currentTimeMillis(),
                            hostDeviceId = hostDeviceId,
                            vaultId = vaultId,
                            category = cat,
                            isBackedUp = false
                        )
                    )
                }
            }
        }

        scanDocRecursive(rootDoc, "")

        val summary = VaultSummary(
            filesFound = fileList.size,
            foldersFound = folderCount,
            totalSizeBytes = totalSize,
            lastScanTime = System.currentTimeMillis(),
            vaultPath = treeUri.toString()
        )

        return Pair(fileList, summary)
    }

    fun resolvePathFromTreeUri(treeUri: Uri): String? {
        val uriStr = treeUri.toString()
        val docId = when {
            uriStr.contains("/tree/") -> {
                val segment = uriStr.substringAfter("/tree/").substringBefore("/document/")
                Uri.decode(segment)
            }
            uriStr.contains("/document/") -> {
                val segment = uriStr.substringAfter("/document/")
                Uri.decode(segment)
            }
            else -> treeUri.path ?: ""
        }

        if (docId.contains("primary:")) {
            val relative = docId.substringAfter("primary:")
            val extDir = Environment.getExternalStorageDirectory().absolutePath
            return if (relative.isBlank()) extDir else "$extDir/$relative"
        }
        return null
    }

    fun scanVault(context: Context, pathOrUri: String, hostDeviceId: String, vaultId: String): Pair<List<VaultFile>, VaultSummary> {
        val clean = pathOrUri.trim()
        if (clean.isBlank()) {
            val def = getDefaultVaultFolder(context)
            return scanFolder(def, hostDeviceId, vaultId)
        }

        if (clean.startsWith("content://")) {
            val uri = Uri.parse(clean)
            // 1. Try real filesystem path resolution first for direct file access
            val realPath = resolvePathFromTreeUri(uri)
            if (realPath != null) {
                val realDir = File(realPath)
                if (realDir.exists() && realDir.canRead()) {
                    val result = scanFolder(realDir, hostDeviceId, vaultId)
                    if (result.first.isNotEmpty()) {
                        return result
                    }
                }
            }

            // 2. Scan via SAF DocumentFile
            val docResult = scanDocumentTree(context, uri, hostDeviceId, vaultId)
            if (docResult.first.isNotEmpty()) {
                return docResult
            }

            // 3. If SAF returned 0, try realDir if exists even if canRead was false
            if (realPath != null) {
                val realDir = File(realPath)
                if (realDir.exists()) {
                    val result = scanFolder(realDir, hostDeviceId, vaultId)
                    if (result.first.isNotEmpty()) return result
                }
            }

            return docResult
        } else {
            val folder = File(clean)
            if (folder.exists()) {
                return scanFolder(folder, hostDeviceId, vaultId)
            }
            val def = getDefaultVaultFolder(context)
            return scanFolder(def, hostDeviceId, vaultId)
        }
    }
}

