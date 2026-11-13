package com.example.prismdiff.feature.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.prismdiff.core.designsystem.PrismDiffColors
import com.example.prismdiff.core.designsystem.PrismDiffTheme
import com.example.prismdiff.core.model.AppLaunchScreen
import com.example.prismdiff.core.model.ImageAsset
import com.example.prismdiff.feature.browser.CollectionBrowserScreen
import com.example.prismdiff.feature.home.StudioHomeScreen
import com.example.prismdiff.feature.settings.SettingsAndShaderLabScreen
import com.example.prismdiff.feature.studio.PrismDiffViewModel
import com.example.prismdiff.feature.studio.StudioScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object BrowserRoute

@Serializable
data object StudioRoute

@Serializable
data object SettingsRoute

@Composable
fun PrismDiffAppShell(
    viewModel: PrismDiffViewModel,
    launchedWithShareIntent: Boolean,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val derivedBitmap by viewModel.derivedPreviewBitmap.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    var initialRouteApplied by remember { mutableStateOf(false) }

    // Keep-screen-on preference management
    DisposableEffect(state.preferences.keepScreenOnInStudio) {
        val window = (context as? Activity)?.window
        if (state.preferences.keepScreenOnInStudio) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Configurable startup destination once DataStore loads
    LaunchedEffect(state.preferencesLoaded, launchedWithShareIntent) {
        if (!initialRouteApplied && state.preferencesLoaded) {
            initialRouteApplied = true
            val targetRoute: Any = when {
                launchedWithShareIntent -> StudioRoute
                state.preferences.launchScreen == AppLaunchScreen.COLLECTION_BROWSER -> BrowserRoute
                state.preferences.launchScreen == AppLaunchScreen.DIRECT_CANVAS -> StudioRoute
                else -> HomeRoute
            }
            if (targetRoute != HomeRoute) {
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Status Snackbar notifications
    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Share exported PNG/GIF/JSON cleanly via Android Sharesheet
    LaunchedEffect(state.exportedShareUri) {
        val shareUri = state.exportedShareUri
        if (shareUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = state.exportedMimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PrismDiff Export"))
            viewModel.consumeExportedShareUri()
        }
    }

    // Zero-permission Photo Picker + SAF Folder Tree + System Trash/Delete launchers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.ingestPickedUris(uris, folderLabel = "Photo Picker", openInStudio = true)
            navController.navigate(StudioRoute) { launchSingleTop = true }
        }
    }

    val folderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.openSafFolderTree(treeUri)
            navController.navigate(BrowserRoute) { launchSingleTop = true }
        }
    }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { res ->
        viewModel.onSystemTrashOrDeleteCompleted(res.resultCode == Activity.RESULT_OK)
    }

    val launchPicker: () -> Unit = {
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val launchSafFolder: () -> Unit = {
        folderTreeLauncher.launch(null)
    }
    val shareAssets: (List<ImageAsset>) -> Unit = { items ->
        val uris = ArrayList<Uri>(items.map { Uri.parse(it.uriString) })
        if (uris.size == 1) {
            val single = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(single, "Share Photo"))
        } else if (uris.size > 1) {
            val multi = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(multi, "Share ${uris.size} Photos"))
        }
    }

    PrismDiffTheme(
        themeMode = state.preferences.themeMode,
        useDynamicColor = state.preferences.useDynamicColor,
    ) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDest = backStackEntry?.destination

        val navItems = listOf(
            Triple(HomeRoute, stringResource(R.string.nav_home), Icons.Default.Home),
            Triple(BrowserRoute, stringResource(R.string.nav_browser), Icons.Default.PhotoLibrary),
            Triple(StudioRoute, stringResource(R.string.nav_compare), Icons.Default.Compare),
            Triple(SettingsRoute, stringResource(R.string.nav_settings), Icons.Default.Settings),
        )

        val navigateTopLevel: (Any) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp && !state.lightsOutImmersive

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useRail && !state.lightsOutImmersive) {
                        NavigationBar {
                            navItems.forEach { (route, label, icon) ->
                                val selected = currentDest?.hasRoute(route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(route) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("nav_tab_${label.lowercase()}"),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (useRail) {
                        NavigationRail {
                            navItems.forEach { (route, label, icon) ->
                                val selected = currentDest?.hasRoute(route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(route) },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("nav_rail_${label.lowercase()}"),
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = HomeRoute,
                        ) {
                            composable<HomeRoute> {
                                StudioHomeScreen(
                                    state = state,
                                    decoder = viewModel.decoder,
                                    onPickPhotos = launchPicker,
                                    onOpenSafFolder = launchSafFolder,
                                    onOpenDirectCanvas = { navigateTopLevel(StudioRoute) },
                                    onResumeSession = { session ->
                                        viewModel.resumeSession(session)
                                        navigateTopLevel(StudioRoute)
                                    },
                                    onOpenCluster = { cluster ->
                                        viewModel.loadClusterIntoStudio(cluster)
                                        navigateTopLevel(StudioRoute)
                                    },
                                    onApplyPresetAndCompare = { preset ->
                                        viewModel.applyPreset(preset)
                                        navigateTopLevel(StudioRoute)
                                    },
                                    onOpenSamplePair = { nameA, nameB ->
                                        val a = state.assets.find { it.displayName == nameA }
                                        val b = state.assets.find { it.displayName == nameB }
                                        if (a != null && b != null) {
                                            viewModel.loadAssetsIntoStudio(listOf(a.id, b.id))
                                            navigateTopLevel(StudioRoute)
                                        }
                                    },
                                )
                            }

                            composable<BrowserRoute> {
                                CollectionBrowserScreen(
                                    state = state,
                                    decoder = viewModel.decoder,
                                    onPickPhotos = launchPicker,
                                    onOpenSafFolder = launchSafFolder,
                                    onSearchChange = viewModel::setSearchQuery,
                                    onSortChange = viewModel::setSortOrder,
                                    onToggleSelect = viewModel::toggleBrowserSelection,
                                    onInvertSelection = viewModel::invertBrowserSelection,
                                    onOpenPairInStudio = { asset ->
                                        viewModel.loadPairAtAsset(asset)
                                        navigateTopLevel(StudioRoute)
                                    },
                                    onCompareSelected = {
                                        viewModel.compareSelectedFromBrowser()
                                        navigateTopLevel(StudioRoute)
                                    },
                                    onStartTournament = {
                                        viewModel.startOrAdvanceTournament()
                                        navigateTopLevel(StudioRoute)
                                    },
                                    onTrashUnselected = { viewModel.requestTrashOrDeleteUnselected(permanentDelete = false) },
                                    onDeleteSelected = { viewModel.requestTrashOrDeleteSelected(permanentDelete = true) },
                                    onShareSelected = shareAssets,
                                )
                            }

                            composable<StudioRoute> {
                                StudioScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    derivedBitmap = derivedBitmap,
                                    onLaunchPhotoPicker = launchPicker,
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsAndShaderLabScreen(
                                    state = state,
                                    viewModel = viewModel,
                                    onOpenShaderInStudio = { navigateTopLevel(StudioRoute) },
                                )
                            }
                        }
                    }
                }
            }

            // Two-stage safety Deletion / Trash confirmation dialog
            state.pendingDeletion?.let { preview ->
                val mbText = String.format("%.2f MB", preview.totalBytes / (1024.0 * 1024.0))
                val sampleNames = preview.targets.take(4).joinToString(", ") { it.displayName }
                AlertDialog(
                    onDismissRequest = viewModel::cancelDeletionPreview,
                    title = { Text(stringResource(R.string.delete_confirm_title)) },
                    text = {
                        val body = if (preview.permanentDelete) {
                            stringResource(R.string.delete_confirm_permanent, preview.targets.size, mbText)
                        } else {
                            stringResource(R.string.delete_confirm_reversible, preview.targets.size, mbText)
                        }
                        Text("$body\n\nKeeping ${preview.keptCount} photo(s).\nTargets: $sampleNames")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.executeConfirmedDeletion { pendingIntent ->
                                    systemDeleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                                }
                            },
                            colors = if (preview.permanentDelete) {
                                ButtonDefaults.buttonColors(containerColor = PrismDiffColors.PrismRed)
                            } else ButtonDefaults.buttonColors(),
                            modifier = Modifier.testTag("confirm_deletion_dialog_button"),
                        ) {
                            Text(
                                if (preview.permanentDelete) stringResource(R.string.action_confirm_delete)
                                else stringResource(R.string.action_confirm_trash),
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::cancelDeletionPreview) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }
    }
}
