package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R
import com.nuvio.tv.core.launcher.rememberLauncherLayoutPreferences

/**
 * Launcher: "One pill, one job": the floating pill shows the page's name, or the profile. Needs more than one profile
 * to show anything. Stored by the launcher itself (see LauncherLayoutPreferences).
 */
@Composable
internal fun LauncherPillProfileToggle() {
    val prefs = rememberLauncherLayoutPreferences()
    val checked by prefs.pillShowsProfile.collectAsStateWithLifecycle(initialValue = false)
    SettingsToggleRow(
        title = stringResource(R.string.layout_pill_shows_profile),
        subtitle = stringResource(R.string.layout_pill_shows_profile_sub),
        checked = checked,
        onToggle = { prefs.setPillShowsProfile(!checked) }
    )
}
