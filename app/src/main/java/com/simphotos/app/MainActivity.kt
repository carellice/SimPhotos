package com.simphotos.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simphotos.app.ui.MainViewModel
import com.simphotos.app.ui.Screen
import com.simphotos.app.ui.screens.DoneScreen
import com.simphotos.app.ui.screens.GroupDetailScreen
import com.simphotos.app.ui.screens.HomeScreen
import com.simphotos.app.ui.screens.PermissionScreen
import com.simphotos.app.ui.screens.ResultsScreen
import com.simphotos.app.ui.screens.ScanScreen
import com.simphotos.app.ui.screens.SettingsScreen
import com.simphotos.app.ui.theme.SimPhotosTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimPhotosTheme {
                // Surface provides the right content colour (light/dark) to every Text below.
                Surface(color = MaterialTheme.colorScheme.background) { App(vm) }
            }
        }
    }

    private fun hasFullAccess(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPartialAccess(): Boolean =
        Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun App(vm: MainViewModel) {
        val state by vm.state.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val update by vm.update.collectAsStateWithLifecycle()
        var granted by remember { mutableStateOf(hasFullAccess() || hasPartialAccess()) }
        var denied by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            granted = hasFullAccess() || hasPartialAccess()
            denied = !granted
            if (granted) vm.refreshGalleryCount()
        }
        val deleteLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result -> vm.onDeleteResult(result.resultCode == Activity.RESULT_OK) }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            granted = hasFullAccess() || hasPartialAccess()
            if (granted) vm.refreshGalleryCount()
        }
        LaunchedEffect(state.error) {
            state.error?.let { snackbar.showSnackbar(it); vm.dismissError() }
        }

        BackHandler(enabled = granted && state.screen != Screen.Home) { vm.back() }

        Box(Modifier.fillMaxSize()) {
            if (!granted) {
                PermissionScreen(
                    denied = denied,
                    onGrant = {
                        permissionLauncher.launch(
                            when {
                                Build.VERSION.SDK_INT >= 34 -> arrayOf(
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                                )
                                Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        )
                    },
                    onOpenSettings = {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        )
                    },
                )
            } else {
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = {
                        val forward = depth(targetState) >= depth(initialState)
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(300)) { it / 6 * dir } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { -it / 6 * dir } + fadeOut(tween(200)))
                    },
                    contentKey = { it::class },
                    label = "screens",
                ) { screen ->
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            state = state,
                            hasResults = state.groups.isNotEmpty(),
                            onScan = vm::startScan,
                            onOpenResults = { vm.navigate(Screen.Results) },
                            onSettings = { vm.navigate(Screen.Settings) },
                        )
                        Screen.Scanning -> ScanScreen(state.progress, onCancel = { vm.back() })
                        Screen.Results -> ResultsScreen(
                            state = state,
                            deleteMode = settings.deleteMode,
                            onBack = { vm.back() },
                            onSettings = { vm.navigate(Screen.Settings) },
                            onToggle = vm::toggleDelete,
                            onOpenGroup = { vm.navigate(Screen.Detail(it)) },
                            onSelectAll = vm::selectAll,
                            onConfirmDelete = {
                                vm.buildDeleteRequest()?.let {
                                    deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
                                }
                            },
                        )
                        is Screen.Detail -> {
                            val group = state.groups.firstOrNull { it.id == screen.groupId }
                            if (group == null) {
                                LaunchedEffect(Unit) { vm.navigate(Screen.Results) }
                            } else {
                                GroupDetailScreen(
                                    group = group,
                                    toDelete = state.toDelete,
                                    onBack = { vm.back() },
                                    onToggle = vm::toggleDelete,
                                    onKeepOnly = { vm.keepOnly(group.id, it) },
                                )
                            }
                        }
                        Screen.Settings -> SettingsScreen(
                            settings = settings,
                            onBack = { vm.back() },
                            onChange = vm::updateSettings,
                            onClearCache = vm::clearCache,
                            currentVersion = vm.currentVersion,
                            update = update,
                            onCheckUpdate = vm::checkForUpdates,
                            onDownloadUpdate = vm::downloadUpdate,
                            onCancelDownload = vm::cancelUpdateDownload,
                            onInstallUpdate = vm::installUpdate,
                        )
                        Screen.Done -> DoneScreen(
                            summary = state.lastDelete,
                            remainingGroups = state.groups.size,
                            onHome = { vm.navigate(Screen.Home) },
                            onContinue = { vm.navigate(Screen.Results) },
                        )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }

    private fun depth(s: Screen) = when (s) {
        Screen.Home -> 0
        Screen.Settings, Screen.Scanning -> 1
        Screen.Results -> 2
        is Screen.Detail -> 3
        Screen.Done -> 4
    }
}
