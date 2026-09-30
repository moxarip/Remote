package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.models.DeviceRole
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminHostDetailScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HostDashboardScreen
import com.example.ui.screens.RoleSelectScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateDark
import com.example.ui.viewmodels.MainViewModel

enum class AppScreen {
    SPLASH,
    AUTH,
    ROLE_SELECT,
    HOST_DASHBOARD,
    ADMIN_DASHBOARD,
    ADMIN_HOST_DETAIL,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) {
                RemoteBackupAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun RemoteBackupAppContent(viewModel: MainViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }
    val currentUser by viewModel.currentUser.collectAsState()

    // Host states
    val hostDevice by viewModel.currentHostDevice.collectAsState()
    val vaultSummary by viewModel.vaultSummary.collectAsState()
    val selectedVaultPath by viewModel.selectedVaultPath.collectAsState()
    val activePairingCode by viewModel.activePairingCode.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val hostCommands by viewModel.hostCommands.collectAsState()

    // Admin states
    val pairedHosts by viewModel.pairedHosts.collectAsState()
    val selectedHost by viewModel.selectedHost.collectAsState()
    val selectedHostFiles by viewModel.selectedHostFiles.collectAsState()
    val selectedFileIds by viewModel.adminSelectedFileIds.collectAsState()
    val activeCommand by viewModel.selectedHostActiveCommand.collectAsState()
    val filterCategory by viewModel.fileFilterCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val pairingDialogVisible by viewModel.pairingDialogVisible.collectAsState()
    val pairingInputCode by viewModel.pairingInputCode.collectAsState()
    val pairingError by viewModel.pairingError.collectAsState()

    val authState by viewModel.authUiState.collectAsState()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        containerColor = SlateDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        when (currentScreen) {
            AppScreen.SPLASH -> {
                SplashScreen(
                    onSplashFinished = {
                        if (viewModel.repository.hasSavedHostSession()) {
                            // Host phone left at home: auto-boot directly into Host Dashboard without login or tampering!
                            viewModel.restoreHostSession()
                            currentScreen = AppScreen.HOST_DASHBOARD
                        } else {
                            // Admin phone: requires login on every launch for security
                            currentScreen = AppScreen.AUTH
                        }
                    }
                )
            }

            AppScreen.AUTH -> {
                AuthScreen(
                    state = authState,
                    onEmailChanged = viewModel::onEmailChanged,
                    onPasswordChanged = viewModel::onPasswordChanged,
                    onToggleMode = viewModel::toggleAuthMode,
                    onSubmit = {
                        viewModel.authenticate()
                    }
                )
                // When authenticated, transition
                LaunchedEffect(currentUser) {
                    val user = currentUser
                    if (user != null) {
                        val savedRole = viewModel.repository.getSavedRole()
                        currentScreen = when {
                            savedRole == DeviceRole.ADMIN -> AppScreen.ADMIN_DASHBOARD
                            user.selectedRole == DeviceRole.HOST || savedRole == DeviceRole.HOST -> AppScreen.HOST_DASHBOARD
                            user.selectedRole == DeviceRole.ADMIN -> AppScreen.ADMIN_DASHBOARD
                            else -> AppScreen.ROLE_SELECT
                        }
                    }
                }
            }

            AppScreen.ROLE_SELECT -> {
                val isSavingRole by viewModel.isSavingRole.collectAsState()
                RoleSelectScreen(
                    deviceId = viewModel.repository.localDeviceId,
                    isSaving = isSavingRole,
                    onRoleSelected = { role ->
                        viewModel.setRole(role) {
                            currentScreen = if (role == DeviceRole.HOST) {
                                AppScreen.HOST_DASHBOARD
                            } else {
                                AppScreen.ADMIN_DASHBOARD
                            }
                        }
                    }
                )
            }

            AppScreen.HOST_DASHBOARD -> {
                HostDashboardScreen(
                    hostDevice = hostDevice,
                    vaultSummary = vaultSummary,
                    selectedVaultPath = selectedVaultPath,
                    activePairingCode = activePairingCode,
                    commands = hostCommands,
                    isScanning = isScanning,
                    onScanVault = viewModel::scanHostVault,
                    onSelectVaultPath = viewModel::setVaultPath,
                    onGeneratePairingCode = viewModel::generatePairingCode,
                    onCancelCommand = { cmdId -> viewModel.repository.cancelCommand(viewModel.repository.localDeviceId, cmdId) },
                    onNavigateSettings = { currentScreen = AppScreen.SETTINGS }
                )
            }

            AppScreen.ADMIN_DASHBOARD -> {
                LaunchedEffect(Unit) {
                    viewModel.refreshDevices()
                }
                AdminDashboardScreen(
                    hosts = pairedHosts,
                    showAddDialog = pairingDialogVisible,
                    pairingCodeInput = pairingInputCode,
                    pairingError = pairingError,
                    currentUserEmail = currentUser?.email ?: "",
                    onOpenAddDialog = viewModel::openAddHostDialog,
                    onCloseAddDialog = viewModel::closeAddHostDialog,
                    onPairingCodeChanged = viewModel::onPairingInputCodeChanged,
                    onSubmitPairingCode = {
                        viewModel.submitPairingCode()
                        if (viewModel.selectedHost.value != null) {
                            currentScreen = AppScreen.ADMIN_HOST_DETAIL
                        }
                    },
                    onSelectHost = { host ->
                        viewModel.selectHost(host)
                        currentScreen = AppScreen.ADMIN_HOST_DETAIL
                    },
                    onNavigateSettings = { currentScreen = AppScreen.SETTINGS }
                )
            }

            AppScreen.ADMIN_HOST_DETAIL -> {
                selectedHost?.let { host ->
                    LaunchedEffect(host.deviceId) {
                        viewModel.refreshSelectedHostFiles()
                    }
                    AdminHostDetailScreen(
                        host = host,
                        files = selectedHostFiles,
                        selectedFileIds = selectedFileIds,
                        activeCommand = activeCommand,
                        selectedCategory = filterCategory,
                        searchQuery = searchQuery,
                        onBack = {
                            viewModel.clearSelectedHost()
                            currentScreen = AppScreen.ADMIN_DASHBOARD
                        },
                        onSendCommand = viewModel::sendAdminCommand,
                        onCancelCommand = viewModel::cancelAdminCommand,
                        onToggleFileSelect = viewModel::toggleFileSelection,
                        onSelectAllFiles = viewModel::selectAllFiles,
                        onClearSelection = viewModel::clearFileSelection,
                        onCategoryChanged = viewModel::setFilterCategory,
                        onSearchChanged = viewModel::setSearchQuery,
                        onRefreshFiles = viewModel::refreshSelectedHostFiles
                    )
                } ?: run {
                    currentScreen = AppScreen.ADMIN_DASHBOARD
                }
            }

            AppScreen.SETTINGS -> {
                SettingsScreen(
                    user = currentUser,
                    currentRole = currentUser?.selectedRole ?: DeviceRole.ADMIN,
                    deviceId = viewModel.repository.localDeviceId,
                    onBack = {
                        currentScreen = if (currentUser?.selectedRole == DeviceRole.HOST) {
                            AppScreen.HOST_DASHBOARD
                        } else {
                            AppScreen.ADMIN_DASHBOARD
                        }
                    },
                    onSwitchRole = { newRole ->
                        viewModel.setRole(newRole)
                        currentScreen = if (newRole == DeviceRole.HOST) {
                            AppScreen.HOST_DASHBOARD
                        } else {
                            AppScreen.ADMIN_DASHBOARD
                        }
                    },
                    onLogout = {
                        viewModel.logout()
                        currentScreen = AppScreen.AUTH
                    }
                )
            }
        }
    }
}
