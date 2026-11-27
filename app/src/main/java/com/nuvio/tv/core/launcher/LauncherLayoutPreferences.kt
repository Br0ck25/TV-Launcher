package com.nuvio.tv.core.launcher

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.ui.components.HomeTiles
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The launcher's own layout settings (Tile size, "pill shows profile"), kept per profile in a file of their own so
 * Nuvio TV's layout preferences and view model stay untouched.
 */
@Singleton
class LauncherLayoutPreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val profileManager: ProfileManager
) {
    private val prefs = context.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)
    private val writes = MutableStateFlow(0)

    private fun tileKey(profile: Int) = "home_tile_width_dp_$profile"
    private fun pillKey(profile: Int) = "pill_shows_profile_$profile"

    /** The chosen Tile size preset (a poster width in dp), or null while tiles follow the poster Width. */
    val homeTileWidthDp: Flow<Int?> = combine(profileManager.activeProfileId, writes) { profile, _ ->
        prefs.getInt(tileKey(profile), 0).takeIf { it > 0 }
    }.distinctUntilChanged()

    /** The floating pill shows the profile's picture and name instead of the page's icon and name. */
    val pillShowsProfile: Flow<Boolean> = combine(profileManager.activeProfileId, writes) { profile, _ ->
        prefs.getBoolean(pillKey(profile), false)
    }.distinctUntilChanged()

    fun setHomeTileWidthDp(widthDp: Int?) {
        val key = tileKey(profileManager.activeProfileId.value)
        prefs.edit().apply { if (widthDp == null) remove(key) else putInt(key, widthDp) }.apply()
        writes.value += 1
    }

    fun setPillShowsProfile(enabled: Boolean) {
        prefs.edit().putBoolean(pillKey(profileManager.activeProfileId.value), enabled).apply()
        writes.value += 1
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LauncherEntryPoint {
    fun launcherLayoutPreferences(): LauncherLayoutPreferences
    fun layoutPreferenceDataStore(): LayoutPreferenceDataStore
}

@Composable
internal fun rememberLauncherLayoutPreferences(): LauncherLayoutPreferences {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, LauncherEntryPoint::class.java)
            .launcherLayoutPreferences()
    }
}

/** The Tile size setting as the layout settings page needs it. */
@Stable
class LauncherTileSize(val chosen: Int?, val choose: (Int) -> Unit)

@Composable
fun rememberLauncherTileSize(): LauncherTileSize {
    val prefs = rememberLauncherLayoutPreferences()
    val chosen by prefs.homeTileWidthDp.collectAsStateWithLifecycle(initialValue = null)
    return remember(chosen, prefs) { LauncherTileSize(chosen) { prefs.setHomeTileWidthDp(it) } }
}

/**
 * Home's tiles read their size from [HomeTiles]. The scale is the chosen preset against the poster width the layouts
 * already size their tiles from, so "follow the poster Width" is exactly 1.
 */
@Composable
fun LauncherTileScaleEffect() {
    val context = LocalContext.current
    val entry = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, LauncherEntryPoint::class.java)
    }
    val poster by entry.layoutPreferenceDataStore().posterCardWidthDp.collectAsStateWithLifecycle(initialValue = 126)
    val tile by entry.launcherLayoutPreferences().homeTileWidthDp.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(poster, tile) {
        HomeTiles.setScale(tile?.let { it / poster.toFloat() } ?: 1f)
    }
}
