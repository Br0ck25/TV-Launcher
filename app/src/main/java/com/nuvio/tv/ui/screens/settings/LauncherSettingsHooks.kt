package com.nuvio.tv.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R

/** Launcher: the rail entries for the Default Launcher and Remote Buttons settings (see LAUNCHER_TOUCHPOINTS.md). */
@Composable
internal fun launcherSectionSpec(category: SettingsCategory): SettingsSectionSpec =
    if (category == SettingsCategory.REMOTE_BUTTONS) {
        SettingsSectionSpec(category, stringResource(R.string.settings_remote_buttons), Icons.Default.SettingsRemote, destination = SettingsSectionDestination.Inline)
    } else {
        SettingsSectionSpec(category, stringResource(R.string.settings_default_launcher), Icons.Default.Home, destination = SettingsSectionDestination.Inline)
    }
