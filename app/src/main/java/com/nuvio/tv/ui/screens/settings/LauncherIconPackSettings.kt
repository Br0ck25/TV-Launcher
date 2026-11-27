package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.apps.IconPackInfo
import com.nuvio.tv.core.apps.IconPackManager
import com.nuvio.tv.core.apps.InstalledAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Launcher: the icon pack setting on the Appearance page. It has a view model of its own, so Nuvio TV's theme
 * settings view model stays untouched (see LAUNCHER_TOUCHPOINTS.md).
 */
@HiltViewModel
class LauncherIconPackViewModel @Inject constructor(
    private val iconPackManager: IconPackManager,
    private val installedAppsRepository: InstalledAppsRepository
) : ViewModel() {
    val selectedPackage: StateFlow<String?> = installedAppsRepository.selectedIconPack

    private val _installedPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val installedPacks: StateFlow<List<IconPackInfo>> = _installedPacks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun select(packageName: String?) = installedAppsRepository.setIconPack(packageName)

    /** Looks for installed icon packs. Only done when the picker is opened, not at start-up. */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.update { true }
            val packs = runCatching { iconPackManager.getInstalledIconPacks() }.getOrDefault(emptyList())
            _installedPacks.value = packs
            _isLoading.update { false }
        }
    }
}

@Composable
internal fun LauncherIconPackCard(viewModel: LauncherIconPackViewModel = hiltViewModel()) {
    val selected by viewModel.selectedPackage.collectAsStateWithLifecycle()
    val packs by viewModel.installedPacks.collectAsStateWithLifecycle()
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    SettingsGroupCard(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.appearance_icon_pack_title),
        subtitle = stringResource(R.string.appearance_icon_pack_subtitle)
    ) {
        val currentPackName = remember(selected, packs) {
            selected?.let { pkg -> packs.firstOrNull { it.packageName == pkg }?.name ?: pkg }
        }
        SettingsActionRow(
            title = stringResource(R.string.appearance_icon_pack_row_title),
            subtitle = stringResource(R.string.appearance_icon_pack_row_subtitle),
            value = currentPackName ?: stringResource(R.string.appearance_icon_pack_system_default),
            onClick = {
                viewModel.refresh()
                showDialog = true
            }
        )
    }

    if (showDialog) {
        IconPackPickerDialog(
            installedPacks = packs,
            selectedPackage = selected,
            isLoading = loading,
            onSelect = { pkg ->
                viewModel.select(pkg)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}
