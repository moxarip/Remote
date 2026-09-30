package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.BackupCommand
import com.example.models.CommandStatus
import com.example.ui.theme.AmberPending
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.StorageUtils
import kotlinx.coroutines.delay

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val isOnline = status.equals("ONLINE", ignoreCase = true)
    val color = if (isOnline) EmeraldOnline else TextMuted
    val label = if (isOnline) "● Online" else "○ Offline"

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier.testTag("status_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isOnline) "Online" else "Offline",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BatteryBadge(
    percent: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SlateCardElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = "Battery",
                tint = if (percent > 20) ElectricBlue else AmberPending,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$percent%",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StorageProgressBar(
    freeBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val progress = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.5f

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Used: ${StorageUtils.formatFileSize(usedBytes)}",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = "Free: ${StorageUtils.formatFileSize(freeBytes)}",
                color = ElectricBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ElectricBlue,
            trackColor = SlateBorder
        )
    }
}

@Composable
fun FileCategoryIcon(
    category: String,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (category.lowercase()) {
        "photos" -> Icons.Default.Image to Color(0xFF38BDF8)
        "videos" -> Icons.Default.Movie to Color(0xFFA855F7)
        "documents" -> Icons.Default.Description to Color(0xFF10B981)
        else -> Icons.Default.Folder to Color(0xFFF59E0B)
    }

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun PairingCodeCard(
    code: String,
    expiresAt: Long,
    modifier: Modifier = Modifier
) {
    var remainingSeconds by remember(expiresAt) {
        mutableStateOf(((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0L))
    }

    LaunchedEffect(expiresAt) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds = ((expiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0L)
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCardElevated),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pairing_code_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "REMOTE HOST",
                color = ElectricBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pairing Code:",
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = code,
                color = TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Expires in: ",
                    color = TextMuted,
                    fontSize = 13.sp
                )
                Text(
                    text = timeFormatted,
                    color = if (remainingSeconds < 60) RoseError else AmberPending,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun CommandProgressCard(
    command: BackupCommand,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = command.status == CommandStatus.RUNNING.name
    val isCompleted = command.status == CommandStatus.COMPLETED.name
    val isFailed = command.status == CommandStatus.FAILED.name

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCardElevated),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCompleted) EmeraldOnline.copy(alpha = 0.4f)
            else if (isFailed) RoseError.copy(alpha = 0.4f)
            else ElectricBlue.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("command_progress_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Sync,
                        contentDescription = "Status",
                        tint = if (isCompleted) EmeraldOnline else ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${command.type} ${if (isCompleted) "COMPLETED ✓" else if (isFailed) "FAILED" else ""}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (isRunning) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp).testTag("cancel_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = RoseError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${command.filesProcessed} / ${command.totalFiles} files",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${command.progress}%",
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (command.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = ElectricBlue,
                    trackColor = SlateBorder
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (!command.currentFile.isNullOrBlank()) {
                    Text(
                        text = "Current:",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = command.currentFile ?: "",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (command.speedBytesPerSec > 0) {
                        Text(
                            text = "Speed: ${StorageUtils.formatSpeed(command.speedBytesPerSec)}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "Status: ${command.status}",
                        color = AmberPending,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (isCompleted) {
                Text(
                    text = "${command.totalFiles} files processed (${StorageUtils.formatFileSize(command.totalBytes)})",
                    color = EmeraldOnline,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            } else if (isFailed) {
                Text(
                    text = command.error ?: "Operation failed",
                    color = RoseError,
                    fontSize = 13.sp
                )
            }
        }
    }
}
