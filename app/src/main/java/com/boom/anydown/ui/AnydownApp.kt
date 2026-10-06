package com.boom.anydown.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.boom.anydown.model.HomeUiState
import com.boom.anydown.LogViewerScreen
import com.boom.anydown.ui.aura.AuraOverlay
import com.boom.anydown.ui.aura.AuraOverlayController
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.downloads.DownloadsScreen
import com.boom.anydown.ui.home.HomeIdleContent
import com.boom.anydown.ui.home.HomePlaylistContent
import com.boom.anydown.ui.home.HomeResultContent
import com.boom.anydown.ui.home.accentForFormat
import com.boom.anydown.ui.playlist.PlaylistSelectionScreen
import com.boom.anydown.ui.spotify.SpotifyCollectionDialog
import com.boom.anydown.ui.spotify.SpotifyMatchContent
import com.boom.anydown.ui.theme.AnydownColors
import com.boom.anydown.ui.tools.ToolsScreen
import com.boom.anydown.util.CrashLogger
import com.boom.anydown.util.DownloadLocation
import com.boom.anydown.viewmodel.AnydownViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_TOOLS = "tools"
private const val ROUTE_PLAYLIST_SECTION = "playlist/{formatId}"
private const val ROUTE_DEBUG_LOGS = "debug-logs"

@Composable
fun AnydownApp(viewModel: AnydownViewModel = viewModel()) {
    // ViewModel is hoisted here, above the NavHost, so it survives tab
    // switches — Home's Idle/Result state and the Downloads list are never
    // tied to the nav back stack.
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auraController = remember { AuraOverlayController() }

    var downloadsTabPosition by remember { mutableStateOf(Offset.Zero) }
    val downloads by viewModel.downloads.collectAsState()
    var downloadLocation by remember { mutableStateOf(DownloadLocation.displayName(context)) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            CrashLogger.log("DOWNLOAD LOCATION PICKER: cancelled; keeping current destination")
            return@rememberLauncherForActivityResult
        }

        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isWritePermission
            }
        }.onFailure { error ->
            CrashLogger.log(
                "DOWNLOAD LOCATION PICKER: persist permission failed uri=$uri " +
                    "error=${error.stackTraceToString()}"
            )
        }.getOrDefault(false)

        CrashLogger.log(
            "DOWNLOAD LOCATION PICKER: selected uri=$uri persistedWritePermission=$persisted"
        )
        if (persisted) {
            DownloadLocation.save(context, uri)
            downloadLocation = DownloadLocation.displayName(context)
            CrashLogger.log(
                "DOWNLOAD LOCATION: saved uri=${DownloadLocation.currentUri(context)} " +
                    "displayName=${DownloadLocation.displayName(context)}"
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AnydownColors.background,
            bottomBar = {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                if (currentRoute == ROUTE_DEBUG_LOGS) return@Scaffold

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .brutalistBox(cornerRadius = 22.dp, shadowOffset = 6.dp)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NavIcon(
                        icon = Icons.Filled.Home,
                        label = "Home",
                        selected = currentRoute == ROUTE_HOME,
                        onClick = {
                            navController.navigate(ROUTE_HOME) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavIcon(
                        icon = Icons.Filled.Build,
                        label = "Tools",
                        selected = currentRoute == ROUTE_TOOLS,
                        onClick = {
                            navController.navigate(ROUTE_TOOLS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavIcon(
                        icon = Icons.Filled.Download,
                        label = "Downloads",
                        selected = currentRoute == ROUTE_DOWNLOADS,
                        modifier = Modifier.onGloballyPositioned {
                            val bounds = it.boundsInWindow()
                            downloadsTabPosition = Offset(bounds.center.x, bounds.center.y)
                        },
                        onClick = {
                            navController.navigate(ROUTE_DOWNLOADS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier.padding(padding)
            ) {
                composable(ROUTE_HOME) {
                    when (val state = viewModel.homeState) {
                        is HomeUiState.Idle -> {
                            HomeIdleContent(
                                state = state,
                                onLinkChanged = viewModel::onLinkChanged,
                                onFetch = viewModel::fetchVideo,
                                onClipboardDetected = viewModel::onClipboardLinkDetected,
                                onAcceptClipboard = viewModel::acceptClipboardSuggestion,
                                onDismissClipboard = viewModel::dismissClipboardSuggestion,
                                onDebugLogsUnlocked = {
                                    navController.navigate(ROUTE_DEBUG_LOGS) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                            state.spotifyCollectionKind?.let { kind ->
                                SpotifyCollectionDialog(
                                    kind = kind,
                                    onConvert = {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://www.tunemymusic.com/")
                                            )
                                        )
                                        viewModel.dismissSpotifyCollectionDialog()
                                    },
                                    onDismiss = viewModel::dismissSpotifyCollectionDialog
                                )
                            }
                        }
                        is HomeUiState.SpotifyTrack -> SpotifyMatchContent(
                            match = state.match,
                            onProceed = { startOffset ->
                                auraController.fire(
                                    start = startOffset,
                                    end = downloadsTabPosition,
                                    color = AnydownColors.green,
                                    scope = scope
                                )
                                viewModel.downloadSpotifyMatch(state.match, context)
                                viewModel.grabAnother()
                            },
                            onGrabAnother = viewModel::grabAnother
                        )
                        is HomeUiState.Result -> HomeResultContent(
                            video = state.video,
                            onFormatSelected = { format, startOffset ->
                                auraController.fire(
                                    start = startOffset,
                                    end = downloadsTabPosition,
                                    color = AnydownColors.green,
                                    scope = scope
                                )
                                viewModel.onFormatSelected(format, state.video, context)
                            },
                            onGrabAnother = viewModel::grabAnother
                        )
                        is HomeUiState.Playlist -> HomePlaylistContent(
                            playlist = state.playlist,
                            selectedCounts = state.playlist.formats.associate {
                                it.id to viewModel.selectedCount(it.id)
                            },
                            onOpenSection = { format ->
                                navController.navigate("playlist/${format.id}")
                            },
                            onDownloadSelected = { startOffset ->
                                auraController.fire(
                                    start = startOffset,
                                    end = downloadsTabPosition,
                                    color = AnydownColors.green,
                                    scope = scope
                                )
                                viewModel.downloadSelectedPlaylistItems(context)
                            },
                            onGrabAnother = viewModel::grabAnother
                        )
                    }
                }
                composable(
                    route = ROUTE_PLAYLIST_SECTION,
                    arguments = listOf(navArgument("formatId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val formatId = backStackEntry.arguments?.getString("formatId").orEmpty()
                    val state = viewModel.homeState as? HomeUiState.Playlist
                    if (state == null) {
                        navController.popBackStack()
                    } else {
                        val format = state.playlist.formats.find { it.id == formatId }
                        PlaylistSelectionScreen(
                            sectionLabel = format?.label ?: "Select videos",
                            accent = accentForFormat(formatId),
                            entries = state.playlist.entries,
                            selectedIds = viewModel.selectedIds(formatId),
                            onToggle = { entryId -> viewModel.toggleSelection(formatId, entryId) },
                            onSelectAll = {
                                viewModel.setSelection(formatId, state.playlist.entries.map { it.id }.toSet())
                            },
                            onDeselectAll = { viewModel.setSelection(formatId, emptySet()) },
                            onConfirm = { navController.popBackStack() }
                        )
                    }
                }
                composable(ROUTE_DOWNLOADS) {
                    DownloadsScreen(
                        downloads = downloads,
                        downloadLocation = downloadLocation,
                        onChangeLocation = { folderPicker.launch(null) },
                        onDelete = { id -> viewModel.deleteDownload(id, context) },
                        onOpen = { item ->
                            val uri = Uri.parse(item.filePath)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Open with"))
                        },
                        onCancel = viewModel::cancelDownload,
                        onGrabAnother = {
                            // Global reset rule: jump to Home AND force IdleState.
                            viewModel.grabAnother()
                            navController.navigate(ROUTE_HOME) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(ROUTE_TOOLS) {
                    ToolsScreen()
                }
                composable(ROUTE_DEBUG_LOGS) {
                    LogViewerScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        // Sits above the Scaffold so particles can fly over content + bottom bar.
        AuraOverlay(controller = auraController, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun NavIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) AnydownColors.green else AnydownColors.textMuted
            )
        }
    }
}
