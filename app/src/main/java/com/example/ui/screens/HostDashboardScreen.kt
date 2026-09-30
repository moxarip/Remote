package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.BackupCommand
import com.example.models.HostDevice
import com.example.models.PairingCodeData
import com.example.models.VaultSummary
import com.example.ui.components.BatteryBadge
import com.example.ui.components.CommandProgressCard
import com.example.ui.components.PairingCodeCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.StorageProgressBar
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.SlateDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.StorageUtils

@Composable
fun HostDashboardScreen(
    hostDevice: HostDevice?,
    vaultSummary: VaultSummary?,
    selectedVaultPath: String,
    activePairingCode: PairingCodeData?,
    commands: List<BackupCommand>,
    isScanning: Boolean,
    onScanVault: () -> Unit,
    onSelectVaultPath: (String) -> Unit,
    onGeneratePairingCode: () -> Unit,
    onCancelCommand: (String) -> Unit,
    onNavigateSettings: () -> Unit
) {
    val context = LocalContext.current
    var showPairingDialog by remember { mutableStateOf(false) }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onSelectVaultPath(uri.toString())
        }
    }

    Scaffold(
        containerColor = SlateDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "REMOTE HOST",
                            color = ElectricBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status = hostDevice?.status ?: "ONLINE")
                    }
                    Text(
                        text = hostDevice?.name ?: "Android Host Device",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BatteryBadge(percent = hostDevice?.batteryPercent ?: 90)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onNavigateSettings,
                        modifier = Modifier.testTag("host_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Device ID pill
            item {
                Surface(
                    color = SlateCard,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "deviceId: ${hostDevice?.deviceId ?: "android_host"}",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "ROLE: HOST",
                            color = ElectricBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // VAULT SELECTION CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = EmeraldOnline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "VAULT SELECTED ✓",
                                    color = EmeraldOnline,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            TextButton(
                                onClick = { dirPickerLauncher.launch(null) },
                                modifier = Modifier.testTag("change_vault_button")
                            ) {
                                Text(
                                    text = "CHANGE",
                                    color = ElectricBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = selectedVaultPath.ifEmpty { "/storage/emulated/0/RemoteVault/" },
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // SCAN VAULT & PAIRING ACTIONS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onScanVault,
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("scan_vault_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = SlateDark,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Scan",
                                tint = SlateDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SCAN VAULT",
                                color = SlateDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onGeneratePairingCode()
                            showPairingDialog = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("generate_pairing_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Pairing",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PAIR CODE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // SCAN SUMMARY METRICS
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCardElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "VAULT TELEMETRY",
                            color = ElectricBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Files found:", color = TextSecondary, fontSize = 12.sp)
                                Text(
                                    text = "${vaultSummary?.filesFound ?: hostDevice?.fileCount ?: 0}",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text("Folders:", color = TextSecondary, fontSize = 12.sp)
                                Text(
                                    text = "${vaultSummary?.foldersFound ?: hostDevice?.folderCount ?: 0}",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text("Total size:", color = TextSecondary, fontSize = 12.sp)
                                Text(
                                    text = StorageUtils.formatFileSize(vaultSummary?.totalSizeBytes ?: hostDevice?.vaultSizeBytes ?: 0L),
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        StorageProgressBar(
                            freeBytes = hostDevice?.storageFreeBytes ?: 18_500_000_000L,
                            totalBytes = hostDevice?.storageTotalBytes ?: 64_000_000_000L
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Last scan: ${StorageUtils.formatDate(vaultSummary?.lastScanTime ?: hostDevice?.lastScan ?: 0L)}",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // COMMANDS SECTION
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REMOTE COMMANDS",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${commands.size} total",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (commands.isEmpty()) {
                item {
                    Surface(
                        color = SlateCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Idle",
                                tint = TextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Listening for remote commands from Admin...",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(commands, key = { it.commandId }) { cmd ->
                    CommandProgressCard(
                        command = cmd,
                        onCancel = { onCancelCommand(cmd.commandId) },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }

    // PAIRING CODE DIALOG
    if (showPairingDialog && activePairingCode != null) {
        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            containerColor = SlateCard,
            title = {
                Text(
                    text = "Pair with Admin",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter this 6-digit code on the Admin device to establish secure remote control.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PairingCodeCard(
                        code = activePairingCode.code,
                        expiresAt = activePairingCode.expiresAt
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPairingDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("DONE", color = SlateDark, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
