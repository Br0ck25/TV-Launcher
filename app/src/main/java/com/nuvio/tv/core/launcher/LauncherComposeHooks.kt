package com.nuvio.tv.core.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nuvio.tv.ui.components.ProfileAvatarCircle
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.screens.setup.LauncherSetupWizard
import com.nuvio.tv.ui.screens.setup.LauncherSetupWizardPrefs
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Composable hooks used by `MainActivity` (and its sidebars). Each one is a single call at the hook site, so that
 * Nuvio TV's file stays almost untouched. See LAUNCHER_TOUCHPOINTS.md.
 */

/** The Apps icon for the side menu entry. */
fun launcherAppsIcon(): ImageVector = Icons.Default.Apps

/**
 * Home key, Apps key and mapped-button requests: each one navigates. The Home key always lands on Home, scrolled
 * to the top, whatever screen (tab, detail page, search, settings, player) is showing.
 */
@Composable
fun LauncherNavigationEffects(
    delegate: LauncherActivityDelegate,
    navController: NavHostController,
    layoutChosen: Boolean?,
    actualRoute: String?
) {
    val homeRequestNonce by delegate.homeRequestNonce.collectAsStateWithLifecycle()
    var pendingHomeScrollReset by remember { mutableStateOf(false) }
    LaunchedEffect(homeRequestNonce, layoutChosen) {
        if (homeRequestNonce == 0 || layoutChosen != true) return@LaunchedEffect
        if (navController.currentDestination?.route == Screen.Home.route) {
            requestHomeScrollToTop(navController)
            return@LaunchedEffect
        }
        // Reset only once Home is the current destination; Home restores its own saved
        // scroll position, so the reset has to land after that restore.
        pendingHomeScrollReset = true
        val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
        if (!popped) {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val openAppsNonce by delegate.openAppsNonce.collectAsStateWithLifecycle()
    LaunchedEffect(openAppsNonce, layoutChosen) {
        if (openAppsNonce == 0 || layoutChosen != true) return@LaunchedEffect
        if (navController.currentDestination?.route == Screen.Apps.route) return@LaunchedEffect
        navController.navigate(Screen.Apps.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // A remote button mapped to open a page (Search, Library, Settings), or the Home gear panel opening Settings.
    val mappedRoute by LauncherRoutes.request.collectAsStateWithLifecycle()
    val mappedRouteNonce by LauncherRoutes.nonce.collectAsStateWithLifecycle()
    LaunchedEffect(mappedRoute, mappedRouteNonce, layoutChosen) {
        val targetRoute = mappedRoute ?: return@LaunchedEffect
        if (layoutChosen != true) return@LaunchedEffect
        LauncherRoutes.request.value = null
        if (navController.currentDestination?.route == targetRoute) return@LaunchedEffect
        if (!navController.popBackStack(targetRoute, inclusive = false)) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    LaunchedEffect(pendingHomeScrollReset, actualRoute) {
        if (!pendingHomeScrollReset || actualRoute != Screen.Home.route) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        requestHomeScrollToTop(navController)
        pendingHomeScrollReset = false
    }
}

/**
 * Double-tapping Home asks for the profile switcher. Consumed on read so a later recomposition (or returning from
 * the switcher) cannot repeat it.
 */
@Composable
fun LauncherProfileSwitchEffect(
    delegate: LauncherActivityDelegate,
    canSwitchProfile: Boolean,
    onSwitchProfile: () -> Unit
) {
    val requested by delegate.profileSwitchRequest.collectAsStateWithLifecycle()
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        delegate.profileSwitchRequest.value = false
        if (canSwitchProfile) onSwitchProfile()
    }
}

/**
 * The first-run setup (Home app, Home button, notifications) comes before sign-in. An update can quietly switch the
 * Home button override off: if it was on and is off now, the setup reopens at the checklist with a notice instead of
 * leaving Home on the stock launcher.
 *
 * Shows the wizard while it is needed and returns true then; [onShown] runs each time it is showing.
 */
@Composable
fun LauncherSetupGate(onShown: () -> Unit): Boolean {
    val context = LocalContext.current
    var show by remember {
        LauncherSetup.checkOverrideLost(context)
        mutableStateOf(!LauncherSetupWizardPrefs.isDone(context))
    }
    if (show) {
        onShown()
        LauncherSetupWizard(
            onFinished = {
                LauncherSetupWizardPrefs.markDone(context)
                show = false
            }
        )
    }
    return show
}

/** False while the first-run setup is still to be done: the update banner must not sit on top of its screens. */
@Composable
fun launcherSetupDone(): Boolean = LauncherSetupWizardPrefs.isDone(LocalContext.current)

/** The profile a floating pill should show, or null when the pill shows the page (setting off, or one profile). */
class LauncherPillProfile(val name: String, val colorHex: String, val avatarImageUrl: String?)

@Composable
fun rememberLauncherPillProfile(
    name: String,
    colorHex: String,
    avatarImageUrl: String?,
    hasSeveralProfiles: Boolean
): LauncherPillProfile? {
    val context = LocalContext.current
    val showsProfile by rememberLauncherLayoutPreferences().pillShowsProfile.collectAsStateWithLifecycle(initialValue = false)
    // Profile mode needs more than one profile; otherwise the page pill stays.
    return if (showsProfile && hasSeveralProfiles && name.isNotEmpty()) {
        LauncherPillProfile(name, colorHex, avatarImageUrl)
    } else null
}

/** The profile picture only floats over Home: on other pages it sat on top of the page's title. */
fun launcherPillVisible(profile: LauncherPillProfile?, selectedRoute: String?): Boolean =
    profile == null || selectedRoute == Screen.Home.route

/**
 * Collapsed rail (legacy sidebar): the profile's picture above Home, so you can see whose Home it is without
 * opening the sidebar. Display only (the expanded row is the one that switches profile), so it never takes the
 * focus when the rail opens.
 */
@Composable
fun BoxScope.LauncherRailProfileAvatar(
    name: String,
    colorHex: String,
    avatarImageUrl: String?,
    itemWidth: Dp,
    itemHeight: Dp,
    avatarSize: Dp
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = 30.dp)
            .offset(x = NuvioTheme.spacing.md)
            .width(itemWidth)
            .height(itemHeight)
            .focusProperties { canFocus = false }
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        ProfileAvatarCircle(
            name = name,
            colorHex = colorHex,
            size = avatarSize,
            avatarImageUrl = avatarImageUrl,
            imageCrossfade = false
        )
    }
}

/**
 * Asks Home to drop its saved scroll/focus state and scroll back to the first row. Safe to call when Home
 * is not the current destination: the reset is stored on the Home ViewModel and applied the next time its
 * content is composed.
 */
internal fun requestHomeScrollToTop(navController: NavHostController) {
    val homeEntry = try {
        navController.getBackStackEntry(Screen.Home.route)
    } catch (_: IllegalArgumentException) {
        // "home" not yet on the back stack (e.g. nav graph not fully initialized).
        return
    }
    // HomeViewModel is @HiltViewModel, so it must come from Hilt's factory: a plain
    // ViewModelProvider(entry) only works if the Home screen already created the instance.
    var context = navController.context
    while (context is android.content.ContextWrapper && context !is androidx.activity.ComponentActivity) {
        context = context.baseContext
    }
    val activity = context as? androidx.activity.ComponentActivity ?: return
    val factory = dagger.hilt.android.internal.lifecycle.HiltViewModelFactory.createInternal(
        activity,
        homeEntry,
        homeEntry.arguments,
        homeEntry.defaultViewModelProviderFactory,
    )
    androidx.lifecycle.ViewModelProvider(
        homeEntry.viewModelStore,
        factory,
        homeEntry.defaultViewModelCreationExtras,
    )[com.nuvio.tv.ui.screens.home.HomeViewModel::class.java].requestScrollToTop()
}
