package com.boom.anydown.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boom.anydown.model.HomeUiState
import com.boom.anydown.ui.aura.AuraOverlay
import com.boom.anydown.ui.aura.AuraOverlayController
import com.boom.anydown.ui.brutalist.brutalistBox
import com.boom.anydown.ui.downloads.DownloadsScreen
import com.boom.anydown.ui.home.HomeIdleContent
import com.boom.anydown.ui.home.HomeResultContent
import com.boom.anydown.ui.theme.AnydownColors
import com.boom.anydown.viewmodel.AnydownViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_DOWNLOADS = "downloads"

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AnydownColors.background,
            bottomBar = {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

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
                        is HomeUiState.Idle -> HomeIdleContent(
                            state = state,
                            onLinkChanged = viewModel::onLinkChanged,
                            onFetch = viewModel::fetchVideo,
                            onClipboardDetected = viewModel::onClipboardLinkDetected,
                            onAcceptClipboard = viewModel::acceptClipboardSuggestion,
                            onDismissClipboard = viewModel::dismissClipboardSuggestion
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
                    }
                }
                composable(ROUTE_DOWNLOADS) {
                    DownloadsScreen(
                        downloads = viewModel.downloads,
                        onDelete = { id -> viewModel.deleteDownload(id, context) },
                        onOpen = { item ->
                            // TODO: item.filePath is empty until the real backend
                            // writes an actual file — wire this to the real path.
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
