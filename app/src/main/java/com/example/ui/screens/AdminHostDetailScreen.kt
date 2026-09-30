package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.BackupCommand
import com.example.models.CommandStatus
import com.example.models.CommandType
import com.example.models.HostDevice
import com.example.models.VaultFile
import com.example.ui.components.BatteryBadge
import com.example.ui.components.CommandProgressCard
import com.example.ui.components.FileCategoryIcon
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyanAccent
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHostDetailScreen(
    host: HostDevice,
    files: List<VaultFile>,
    selectedFileIds: Set<String>,
    activeCommand: BackupCommand?,
    selectedCategory: String,
    searchQuery: String,
    onBack: () -> Unit,
    onSendCommand: (CommandType) -> Unit,
    onCancelCommand: (String) -> Unit,
    onToggleFileSelect: (String) -> Unit,
    onSelectAllFiles: (List<VaultFile>) -> Unit,
    onClearSelection: () -> Unit,
    onCategoryChanged: (String) -> Unit,
    onSearchChanged: (String) -> Unit
) {
    BackHandler { onBack() }

    val categories = listOf("All", "Photos", "Videos", "Documents", "Other")

    val filteredFiles = files.filter { file ->
        val matchesCategory = if (selectedCategory == "All") true else file.category.equals(selectedCategory, ignoreCase = true)
        val matchesQuery = if (searchQuery.isBlank()) true else file.name.contains(searchQuery, ignoreCase = true) || file.relativePath.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    val selectedFilesCount = selectedFileIds.size
    val selectedFilesBytes = files.filter { it.fileId in selectedFileIds }.sumOf { it.size }

    Scaffold(
        containerColor = SlateDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("host_detail_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HOST: ${host.name}",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(status = host.status)
                        Spacer(modifier = Modifier.width(6.dp))
                        BatteryBadge(percent = host.batteryPercent)
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = selectedFilesCount > 0) {
                Surface(
                    color = SlateCardElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$selectedFilesCount files selected",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = StorageUtils.formatFileSize(selectedFilesBytes),
                                color = ElectricBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onClearSelection,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                            ) {
                                Text("CLEAR", color = TextSecondary, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { onSendCommand(CommandType.BACKUP) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("backup_selected_button")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = SlateDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("BACKUP", color = SlateDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // HOST STATS HEADER CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Files", color = TextMuted, fontSize = 12.sp)
                                Text(
                                    text = "${host.fileCount}",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text("Storage", color = TextMuted, fontSize = 12.sp)
                                Text(
                                    text = StorageUtils.formatFileSize(host.vaultSizeBytes),
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text("Last Scan", color = TextMuted, fontSize = 12.sp)
                                Text(
                                    text = StorageUtils.formatDate(host.lastScan),
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4 QUICK COMMAND BUTTONS: [ REFRESH ] [ SCAN VAULT ] [ BACKUP ] [ PULL ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSendCommand(CommandType.REFRESH) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("admin_refresh_button")
                            ) {
                                Text("REFRESH", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onSendCommand(CommandType.SCAN) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(40.dp)
                                    .testTag("admin_scan_vault_button")
                            ) {
                                Text("SCAN VAULT", color = CyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onSendCommand(CommandType.BACKUP) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(40.dp)
                                    .testTag("admin_backup_all_button")
                            ) {
                                Text("BACKUP", color = SlateDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onSendCommand(CommandType.PULL) },
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("admin_pull_button")
                            ) {
                                Text("PULL", color = EmeraldOnline, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ACTIVE COMMAND LIVE PROGRESS BANNER
            if (activeCommand != null) {
                item {
                    CommandProgressCard(
                        command = activeCommand,
                        onCancel = { onCancelCommand(activeCommand.commandId) },
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
            }

            // SEARCH BAR
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = { Text("Search files or paths...", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = SlateBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("file_search_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // CATEGORY FILTER PILLS
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { onCategoryChanged(cat) },
                            label = { Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricBlue,
                                containerColor = SlateCard,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedCategory == cat,
                                borderColor = if (selectedCategory == cat) ElectricBlue else SlateBorder
                            ),
                            modifier = Modifier.testTag("filter_chip_$cat")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // FILE TREE HEADER & MULTI-SELECT CONTROLS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Vault",
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RemoteVault/",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }

                    Row {
                        TextButton(
                            onClick = { onSelectAllFiles(filteredFiles) },
                            modifier = Modifier.testTag("select_all_files_button")
                        ) {
                            Text("SELECT ALL", color = ElectricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // FILE LIST
            if (filteredFiles.isEmpty()) {
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
                            Text(
                                text = "No files found in this category",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredFiles, key = { it.fileId }) { file ->
                    val isSelected = selectedFileIds.contains(file.fileId)
                    VaultFileRowItem(
                        file = file,
                        isSelected = isSelected,
                        onToggle = { onToggleFileSelect(file.fileId) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun VaultFileRowItem(
    file: VaultFile,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) SlateCardElevated else SlateCard,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) ElectricBlue.copy(alpha = 0.6f) else SlateBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .testTag("file_row_${file.fileId}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ElectricBlue,
                    checkmarkColor = SlateDark,
                    uncheckedColor = TextMuted
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            FileCategoryIcon(category = file.category)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = file.relativePath,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = StorageUtils.formatFileSize(file.size),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                if (file.isBackedUp) {
                    Text(
                        text = "Backed up ✓",
                        color = EmeraldOnline,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
