package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nuvio.tv.core.launcher.LauncherRoutes
import com.nuvio.tv.ui.navigation.Screen

/**
 * Launcher: settings, notifications and TV inputs at the top right of Home, with their two panels. `HomeScreen.kt`
 * only calls this once (see LAUNCHER_TOUCHPOINTS.md). [scrollToTopTrigger] is Home's own "back to the top" signal:
 * going home also closes the panels.
 */
@Composable
internal fun BoxScope.LauncherHomeOverlay(scrollToTopTrigger: Int, showTopBar: Boolean) {
    com.nuvio.tv.core.launcher.LauncherTileScaleEffect()
    var showSettingsDrawer by rememberSaveable { mutableStateOf(false) }
    var showNotificationsDrawer by rememberSaveable { mutableStateOf(false) }
    var handledPanelCloseTrigger by rememberSaveable { mutableStateOf(scrollToTopTrigger) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger != handledPanelCloseTrigger) {
            handledPanelCloseTrigger = scrollToTopTrigger
            showSettingsDrawer = false
            showNotificationsDrawer = false
        }
    }
    if (showTopBar) {
        HomeTopBarHeader(
            onNotificationsClick = { showNotificationsDrawer = true },
            onSettingsClick = { showSettingsDrawer = true },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
    if (showSettingsDrawer) {
        HomeSettingsDrawer(
            onDismiss = { showSettingsDrawer = false },
            onOpenNuvioSettings = { LauncherRoutes.open(Screen.Settings.route) }
        )
    }
    if (showNotificationsDrawer) {
        NotificationsDrawer(onDismiss = { showNotificationsDrawer = false })
    }
}

/**
 * The Apps row is content: with it on Home, the "no add-ons" and "no catalogs" error screens are not shown (the row
 * itself is the Home screen). Returns the same state when there is nothing to change.
 */
internal fun HomeUiState.withLauncherRows(): HomeUiState =
    if (error != null && homeRows.any { it is HomeRow.InstalledApps && it.apps.isNotEmpty() }) copy(error = null) else this
