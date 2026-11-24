# Launcher touchpoints

This branch is [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV) `dev` (`77cc0bb5f`, 1.2.0-beta.4)
plus the launcher layer. Nuvio TV code outside the files below is unchanged.

To check whether a bug is ours or Nuvio TV's, build the upstream commit. If the bug is there, it is theirs.

## New files (ours, no conflicts with Nuvio TV)

| Feature | Files |
|---|---|
| Home button, accessibility takeover, phone setup, ADB setup, Button Mapper | `core/launcher/*`, `app/src/full` and `app/src/playstore` `LocalAdbSetupFactory`, `LoopbackAdbSetup` |
| Installed apps, hiding, renaming, icon packs | `core/apps/*`, `InstalledApp*`, `AppIconChoiceDialog`, `AppMoveMode`, `HomeTile` in `ui/components` |
| Apps page | `ui/screens/apps/AppsScreen.kt` |
| Notifications | `core/notification/*`, `ui/screens/home/NotificationsDrawer.kt` |
| Top-right clock, bell and settings gear | `HomeTopBarHeader.kt`, `HomeSettingsDrawer.kt` |
| Setup wizard | `ui/screens/setup/LauncherSetupWizard.kt`, `ui/components/LauncherSetupState.kt` |
| Settings pages | `ButtonMapper*`, `ButtonCaptureDialog`, `DefaultLauncherSettingsContent`, `IconPackPickerDialog`, `LocalAdbSetupDialog` |
| Per-profile Apps row | `core/profile/ActiveProfile.kt` |
| Launcher strings and accessibility config | `res/values/strings_launcher.xml`, `res/xml/launcher_accessibility_service_config.xml` |
| Tests | `core/apps/*Test`, `core/launcher/*Test` |

## Nuvio TV files we edit (keep each edit small; re-check on every Nuvio TV update)

| File | What the hook does |
|---|---|
| `MainActivity.kt` | Home/Apps key handling, Button Mapper dispatch, setup wizard gate, Back never exits, sidebar `launcherMode`, Apps menu entry, profile pill |
| `NuvioApplication.kt` | Heavy services made lazy and started off the main thread so the accessibility service can bind early |
| `AndroidManifest.xml`, `full/AndroidManifest.xml` | HOME and ALL_APPS filters, services, boot receiver, app-icon aliases, `WRITE_SECURE_SETTINGS` |
| `app/build.gradle.kts` | App id `com.tv.launcher`, update repo, ADB libraries, test deps |
| `proguard-rules.pro` | Keeps BouncyCastle providers used by the ADB identity certificate |
| `HomeScreen.kt` | Top bar, settings drawer, notifications drawer |
| `ClassicHomeContent`, `GridHomeContent`, `ModernHomeContent`, `ModernHomeRows`, `ModernHomeRowsList`, `ModernHomeModels`, `ModernHomePresentation` | The Apps row (long-press menu, move mode) and `HomeTile` size publishing |
| `HomeViewModel`, `HomeViewModelCatalogPipeline`, `HomeViewModelCatalogUtils`, `HomeUiState`, `CatalogOrderViewModel` | The Apps row is a catalog entry, so it can be reordered and turned off in Reorder Home Catalogs |
| `NuvioNavHost.kt`, `Screen.kt` | Apps route, settings navigation |
| `SettingsScreen`, `SettingsCatalog` | Default Launcher and Remote Buttons settings |
| `ThemeSettingsScreen`, `ThemeSettingsViewModel` | Icon pack picker |
| `LayoutPreferenceDataStore`, `LayoutSettingsViewModel`, `LayoutCardSettings`, `LayoutHomeSettings` | Tile size and "pill shows profile" settings |
| `ProfileManager.kt` | Publishes the current profile to `ActiveProfile` |
| `res/values/strings.xml` | `app_name` is "Nuvio TV Launcher" |

Branding: the app icons (all colour variants), logo, wordmark and TV banner are ours.

## Not ported

Live TV, Music, YouTube, VPN and the other Nuvio One additions from `Br0ck25/NuvioTVLauncher` are not in this
branch. The Button Mapper's "Open Live TV" and "Open TV Guide" actions were removed with them, and the
Apps and Settings side-menu toggles (Nuvio One's `SideMenuPages`) have no equivalent here: Apps is always in the menu.
