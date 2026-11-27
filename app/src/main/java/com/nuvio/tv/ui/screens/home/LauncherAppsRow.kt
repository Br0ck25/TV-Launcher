package com.nuvio.tv.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.nuvio.tv.R
import com.nuvio.tv.core.apps.INSTALLED_APPS_CATALOG_KEY
import com.nuvio.tv.core.apps.InstalledApp
import com.nuvio.tv.core.apps.InstalledAppsViewModel
import com.nuvio.tv.ui.components.AppMoveState
import com.nuvio.tv.ui.components.InstalledAppActionsDialog
import com.nuvio.tv.ui.components.InstalledAppCard
import com.nuvio.tv.ui.components.appMoveKeys
import com.nuvio.tv.ui.components.clampedStep
import com.nuvio.tv.ui.components.rememberAppMoveState
import com.nuvio.tv.ui.components.rememberHomeTile
import com.nuvio.tv.ui.util.asStable

/**
 * Launcher: the Apps row of the Modern Home layout. `ModernHomeRows.kt` only calls into this file, so that Nuvio TV's
 * file stays almost untouched (see LAUNCHER_TOUCHPOINTS.md). Long-press on an app opens its menu (open, rename,
 * move, app info); an app picked up with "Move" is carried along the row by the D-pad.
 */
internal class LauncherAppsRow(
    val viewModel: InstalledAppsViewModel,
    val move: AppMoveState,
    val menuApp: MutableState<InstalledApp?>
) {
    /** Where the row was scrolled to before the last step of a move, and the index the carried tile was seen at. */
    internal var seenIndex = -1
    internal var before: Pair<Int, Int>? = null
}

private fun HeroCarouselRow.apps(): List<InstalledApp> =
    items.list.mapNotNull { (it.payload as? ModernPayload.InstalledApp)?.app }

/** The Apps row's state (and its long-press menu), or null for every other row. */
@Composable
internal fun rememberLauncherAppsRow(row: HeroCarouselRow): LauncherAppsRow? {
    if (row.key != INSTALLED_APPS_CATALOG_KEY) return null
    val viewModel: InstalledAppsViewModel = hiltViewModel()
    val menuApp = remember { mutableStateOf<InstalledApp?>(null) }
    val move = rememberAppMoveState()
    val context = LocalContext.current
    menuApp.value?.let { app ->
        val shown = row.apps()
        val index = shown.indexOfFirst { it.packageName == app.packageName }
        if (index < 0) menuApp.value = null else InstalledAppActionsDialog(
            app = app,
            canMove = shown.size > 1,
            onOpen = { viewModel.launch(app) },
            onMove = {
                move.pickUp(app.packageName)
                Toast.makeText(context, R.string.app_move_hint, Toast.LENGTH_SHORT).show()
            },
            onAppInfo = { viewModel.openAppInfo(app) },
            onRename = { viewModel.rename(app, it) },
            iconChooser = viewModel.iconChooser(app),
            onSetHidden = { viewModel.setHidden(app, it) },
            onSetHiddenOnPage = { viewModel.setHiddenOnPage(app, it) },
            onDismiss = { menuApp.value = null }
        )
    }
    return remember(viewModel, move, menuApp) { LauncherAppsRow(viewModel, move, menuApp) }
}

/**
 * While an app is carried along the Apps row, keep it on screen and give it the focus after each step. Without this
 * the focus ring stayed on whichever tile sat in the same place on screen (a neighbour), not on the app being moved,
 * and the moved app could be partly off the edge.
 */
@Composable
internal fun LauncherAppsRow.CarryEffects(
    row: HeroCarouselRow,
    rowListState: LazyListState,
    onRowItemFocused: (String, Int, Boolean) -> Unit
) {
    val carrying = move.carrying
    val carriedAppIndex = row.items.list.indexOfFirst {
        (it.payload as? ModernPayload.InstalledApp)?.app?.packageName == carrying
    }
    // Moving an app: the carried tile keeps the focus, the reordered list lands a few frames after the key press, and
    // the row keeps its scroll position by index when it does. Left alone the row re-anchors on the moved tile, which
    // made every other tile jump a slot (and the focus used to go to the neighbour first, scrolling the row one tile,
    // before the reorder arrived: the smooth-then-snap on each step).
    SideEffect {
        if (carrying == null) {
            seenIndex = -1
            before = null
        } else if (carriedAppIndex != seenIndex) {
            val saved = before
            if (seenIndex >= 0 && saved != null) rowListState.requestScrollToItem(saved.first, saved.second)
            seenIndex = carriedAppIndex
        }
    }
    LaunchedEffect(carrying, carriedAppIndex) {
        if (carrying == null || carriedAppIndex < 0) return@LaunchedEffect
        // The row's focus index follows the app (it is where the carried tile is now).
        onRowItemFocused(row.key, carriedAppIndex, false)
        // Keep it on screen with one smooth scroll by however much of it sticks out.
        val info = rowListState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == carriedAppIndex }
        val end = info.viewportEndOffset - info.afterContentPadding
        when {
            item == null -> rowListState.scrollToItem(carriedAppIndex)
            item.offset + item.size > end -> rowListState.animateScrollBy((item.offset + item.size - end).toFloat())
            item.offset < info.viewportStartOffset -> rowListState.animateScrollBy((item.offset - info.viewportStartOffset).toFloat())
        }
    }
}

/** An app picked up with "Move" is carried along the Apps row by the D-pad. */
internal fun LauncherAppsRow.moveKeys(row: HeroCarouselRow, rowListState: LazyListState): Modifier =
    Modifier.appMoveKeys(move) { delta ->
        val shownApps = row.apps()
        val carried = shownApps.firstOrNull { it.packageName == move.carrying } ?: return@appMoveKeys
        val from = shownApps.indexOf(carried)
        val step = clampedStep(from, delta, shownApps.lastIndex)
        if (step != 0) {
            // Remember where the row is scrolled to, to restore it when the reordered list lands.
            before = rowListState.firstVisibleItemIndex to rowListState.firstVisibleItemScrollOffset
            viewModel.move(carried, step)
        }
    }

/** One app in the Apps row. */
@Composable
internal fun LazyItemScope.LauncherInstalledAppItem(
    state: LauncherAppsRow?,
    app: InstalledApp,
    requester: FocusRequester,
    isTargetItem: Boolean,
    onFocused: () -> Unit,
    cornerRadius: Dp
) {
    if (state == null) return
    // While an app is carried only that app takes the focus: the row's own focus index changes
    // before the reordered list arrives, and the neighbour used to be given the focus then.
    val carryingNow = state.move.carrying
    val wantsFocus = if (carryingNow != null) carryingNow == app.packageName else isTargetItem
    LaunchedEffect(wantsFocus) {
        if (wantsFocus) runCatching { requester.requestFocus() }
    }
    InstalledAppCard(
        app = app,
        loadImage = state.viewModel::image,
        onClick = {
            onFocused()
            state.viewModel.launch(app)
        },
        onLongClick = { state.menuApp.value = app },
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() },
        width = rememberHomeTile()?.width ?: 160.dp,
        showLabel = false,
        // The same corner as the poster cards in this row (the setting), so the tiles match.
        cornerRadius = cornerRadius,
        moving = carryingNow == app.packageName,
        quietFocus = carryingNow != null && carryingNow != app.packageName,
        // Tiles slide to their new place when the order changes (no fade in or out).
        rootModifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
    )
}

/** The Apps row as the Modern layout's carousel row. */
internal fun launcherAppsCarouselRow(apps: List<InstalledApp>, index: Int, title: String): HeroCarouselRow =
    HeroCarouselRow(
        key = INSTALLED_APPS_CATALOG_KEY,
        title = title,
        globalRowIndex = index,
        items = apps.map { app ->
            ModernCarouselItem(
                key = "installed_app_${app.packageName}",
                title = app.label,
                subtitle = null,
                imageUrl = null,
                heroPreview = HeroPreview(
                    title = "", logo = null, description = null,
                    contentTypeText = null, yearText = null, imdbText = null,
                    genres = emptyList<String>().asStable(), poster = null, backdrop = null, imageUrl = null
                ),
                payload = ModernPayload.InstalledApp(app)
            )
        }.asStable()
    )

/** Launcher: keeps the Apps row's content (the installed apps, in the user's order and names) up to date. */
internal fun HomeViewModel.observeInstalledAppsRowPipeline() {
    val repository = installedAppsRepository ?: return
    viewModelScope.launch {
        repository.homeApps
            .distinctUntilChanged()
            .collectLatest { apps ->
                installedAppsCache = apps
                rebuildCatalogOrder(addonsCache)
                scheduleUpdateCatalogRows()
            }
    }
}
