package com.nuvio.tv.core.launcher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.nuvio.tv.ui.navigation.Screen
import com.nuvio.tv.ui.screens.ambient.AmbientIdleController
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * True while this app is the device's launcher: the system's Home app, or the accessibility override is on.
 * Then Back must never leave the app, and Home is handled by the launcher.
 */
object LauncherMode {
    @Volatile var active: Boolean = false
}

/** Pages the launcher asks the app to open (a mapped remote button, the Home gear panel). Handled by [LauncherNavigationEffects]. */
object LauncherRoutes {
    internal val request = MutableStateFlow<String?>(null)
    internal val nonce = MutableStateFlow(0)

    fun open(route: String) {
        request.value = route
        nonce.value += 1
    }
}

/**
 * Everything the launcher needs from `MainActivity`, kept here so that `MainActivity` only has to forward its
 * lifecycle and key events (see LAUNCHER_TOUCHPOINTS.md). That keeps merges with Nuvio TV small.
 *
 * Create it as a property of the activity: it registers an activity-result launcher, which must happen before the
 * activity is started.
 */
class LauncherActivityDelegate(
    private val activity: ComponentActivity,
    private val ambient: () -> AmbientIdleController
) {
    /**
     * Incremented on every HOME request (the Home key via the Home app role or the launcher's accessibility
     * service, or a plain HOME-category launch). Navigation observes it and returns to the top of Home.
     */
    internal val homeRequestNonce = MutableStateFlow(0)

    /** Set when Home is double-tapped; consumed once the profile switcher is shown. */
    internal val profileSwitchRequest = MutableStateFlow(false)

    /** Bumped when the remote's Apps button (or an ALL_APPS intent) asks for the Apps page. */
    internal val openAppsNonce = MutableStateFlow(0)

    /** Single vs. double tap detection for Home pressed while the launcher is in front. */
    private val homeKeyGesture = HomeKeyGesture()

    /** True while this app is the device's launcher. */
    val handlesHome: Boolean get() = LauncherMode.active

    /** One-time offer of the native Home role after an update; the result is re-checked, never trusted. */
    private val homeRoleOfferLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        LauncherSetup.recordRoleRequestResult(activity)
        refreshHomeRole()
    }

    /** What a mapped button asks the launcher to do while it is in front. */
    private val foregroundButtonHandler = ForegroundButtonActionHandler { action -> executeForegroundButtonAction(action) }

    // ---------------------------------------------------------------- lifecycle

    /** After the deep link is captured, before the content is set. */
    fun onCreate(savedInstanceState: Bundle?, intent: Intent?) {
        // A first launch from the Home button counts as the first press (a restore after rotation or
        // process death does not), and the Home role is re-read.
        if (savedInstanceState == null) {
            captureHomeRequestIntent(intent, fromNewIntent = false)
            captureAppsRequest(intent)
        }
        refreshHomeRole()
    }

    fun onResume() {
        refreshHomeRole()
        LauncherSetup.takePendingRoleOffer(activity)?.let { offer ->
            runCatching { homeRoleOfferLauncher.launch(offer) }
                .onFailure { LauncherSetup.recordRoleRequestResult(activity, launchFailed = true) }
        }
        HomeForegroundDelivery.attach(activity, ::handleHomeKeyPress)
        // After the first frame, so the Button Mapper is never built on the way to the launcher showing.
        activity.window?.decorView?.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            runCatching { mapper().actionExecutor.attachForegroundHandler(activity, foregroundButtonHandler) }
        }
    }

    fun onNewIntent(intent: Intent?) {
        captureHomeRequestIntent(intent)
        captureAppsRequest(intent)
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) builtMapper()?.cancelPendingGestures()
    }

    // ---------------------------------------------------------------- keys

    /**
     * First look at a key, before Nuvio TV's own handling. Returns true when the launcher consumed it: a button being
     * taught to the Button Mapper, the remote's Apps button, or the Home key.
     */
    fun onKeyEarly(event: KeyEvent): Boolean {
        // Button Mapper: while the user is teaching it a button (Settings), that press belongs to it.
        val mapper = builtMapper()
        if (mapper != null && mapper.captureController.isListening()) {
            if (mapper.onKeyEvent(
                    event = event,
                    source = ButtonEventSource.FOREGROUND,
                    accessibilityEnabled = LauncherAccessibilityService.isServiceEnabled(activity),
                    nativeHomeActive = LauncherSetup.isDefaultHome(activity)
                )
            ) return true
        }
        // The remote's Apps button opens the launcher's own Apps page instead of the TV's app list.
        if (AppsKeyStore.matches(activity, event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                ambient().dismiss()
                openAppsNonce.value += 1
            }
            return true
        }
        // HOME is checked before the ambient handler: while the slideshow is up a Home press should end it
        // and go home. Some firmware delivers raw HOME keys; most send a HOME intent or let the
        // accessibility service filter the key, so this is a fallback, not the only path.
        if (event.keyCode == KeyEvent.KEYCODE_HOME) refreshHomeRole()
        if (event.keyCode == KeyEvent.KEYCODE_HOME && handlesHome) {
            ambient().onInteraction()
            if (event.action == KeyEvent.ACTION_DOWN && ambient().isShowing.value) {
                ambient().dismiss()
            }
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                handleHomeKeyPress(event.eventTime)
            }
            return true
        }
        return false
    }

    /** Last look at a key, after Nuvio TV's own handling: a remote button the user gave actions to. */
    fun onKeyLate(event: KeyEvent): Boolean {
        val mapper = builtMapper() ?: return false
        return event.keyCode != KeyEvent.KEYCODE_HOME &&
            mapper.repository.activeMappingFor(event.keyCode, isGlobal = false) != null &&
            mapper.onKeyEvent(
                event = event,
                source = ButtonEventSource.FOREGROUND,
                accessibilityEnabled = LauncherAccessibilityService.isServiceEnabled(activity),
                nativeHomeActive = LauncherSetup.isDefaultHome(activity)
            )
    }

    // ---------------------------------------------------------------- Home

    /** Home pressed while the launcher is already in front: a single tap goes to the top of Home, two open the profile switcher. */
    private fun handleHomeKeyPress(pressedAtMs: Long) {
        // A Home button the user changed in Button Mapper runs their actions; otherwise it is the usual
        // tap (top of Home) / double tap (profiles).
        val mapper = customHomeMapper()
        if (mapper != null) {
            mapper.onHomePress(pressedAtMs = pressedAtMs, source = ButtonEventSource.FOREGROUND)
            return
        }
        applyHomeKeyPress(homeKeyGesture.onPress(pressedAtMs))
    }

    private fun applyHomeKeyPress(press: HomeKeyPress, openProfileSelection: Boolean = false) {
        if (press == HomeKeyPress.IGNORED) return
        ambient().dismiss()
        homeRequestNonce.value += 1
        if (press == HomeKeyPress.DOUBLE || openProfileSelection) {
            profileSwitchRequest.value = true
        }
    }

    private fun captureAppsRequest(intent: Intent?) {
        if (LauncherHomeRequest.isAppsRequest(intent)) {
            ambient().dismiss()
            openAppsNonce.value += 1
        }
    }

    private fun refreshHomeRole() {
        LauncherMode.active = runCatching { LauncherSetup.handlesHome(activity) }.getOrDefault(false)
    }

    /**
     * Raw keys, native HOME launches and accessibility requests all use the same tap detector. A cold
     * native HOME launch records the first press but leaves startup/onboarding alone.
     */
    private fun captureHomeRequestIntent(intent: Intent?, fromNewIntent: Boolean = true) {
        // A mapped button pressed in another app asked the launcher to do something (open Search ...).
        if (!intent?.getStringExtra(AndroidButtonActionPlatformBridge.EXTRA_BUTTON_MAPPER_ACTION).isNullOrBlank()) {
            val handled = runCatching {
                mapper().onActivityIntent(intent, SystemClock.uptimeMillis(), fromNewIntent)
            }.getOrDefault(false)
            if (handled) return
        }
        val press = LauncherHomeRequest.onIntent(
            intent = intent,
            gesture = homeKeyGesture,
            nowMs = SystemClock.uptimeMillis(),
            fromNewIntent = fromNewIntent
        )
        applyHomeKeyPress(press, LauncherHomeRequest.isProfileSelectionRequested(intent))
    }

    // ---------------------------------------------------------------- Button Mapper

    /** The Button Mapper, built on first use. */
    private fun mapper(): ButtonKeyEventHandler =
        EntryPointAccessors.fromApplication(activity.applicationContext, ButtonMapperEntryPoint::class.java)
            .buttonKeyEventHandler()

    /** The Button Mapper, once it has been built; it is never built by asking. */
    private fun builtMapper(): ButtonKeyEventHandler? = ButtonKeyEventHandler.currentInstance()

    /** The Button Mapper, but only when the user has changed what the Home button does. */
    private fun customHomeMapper(): ButtonKeyEventHandler? {
        val mapper = builtMapper() ?: return null
        val mapping = mapper.repository.activeMappingFor(KeyEvent.KEYCODE_HOME, isGlobal = false) ?: return null
        return if (mapping.matchesFactoryHomeDefaults()) null else mapper
    }

    private fun openMappedRoute(route: String) = LauncherRoutes.open(route)

    private fun executeForegroundButtonAction(action: ButtonAction): Boolean {
        ambient().dismiss()
        return when (action) {
            ButtonAction.GoToTopOfHome -> {
                homeRequestNonce.value += 1
                true
            }
            ButtonAction.OpenProfileSwitcher -> {
                homeRequestNonce.value += 1
                profileSwitchRequest.value = true
                true
            }
            ButtonAction.OpenHome -> { openMappedRoute(Screen.Home.route); true }
            ButtonAction.OpenSearch -> { openMappedRoute(Screen.Search.route); true }
            ButtonAction.OpenLibrary -> { openMappedRoute(Screen.Library.route); true }
            ButtonAction.OpenSettings -> { openMappedRoute(Screen.Settings.route); true }
            ButtonAction.OpenApps,
            ButtonAction.OpenAppDrawer -> {
                openAppsNonce.value += 1
                true
            }
            else -> false
        }
    }
}
