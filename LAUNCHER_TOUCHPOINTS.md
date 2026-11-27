# Launcher touchpoints

This branch is [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV) `dev` plus the launcher layer.

**The rule: Nuvio TV files get a hook, never logic.** A launcher feature lives in files that only we have; the Nuvio TV
file it appears in gets one call (or a few lines) into them. Hooks use fully qualified names, so we add no import
lines (the import block is where merges conflict most). The Nuvio TV files we may edit are listed in
`.github/launcher-touchpoints.txt`, and the **Launcher checks** workflow fails when a change touches any other file.

To check whether a bug is ours or Nuvio TV's, build the upstream commit. If the bug is there, it is theirs.

## Our own files (never conflict with Nuvio TV)

| Feature | Files |
|---|---|
| Build: app id, version, update repository, libraries, ProGuard | `app/launcher.gradle.kts`, `app/proguard-launcher-rules.pro` |
| Manifest entries: services, boot receiver, Home and Apps filters, icon-pack queries | `launcher-manifest/` (a module whose manifest Android merges into the app's) |
| `MainActivity` logic: Home key, Apps key, Button Mapper, Home role | `core/launcher/LauncherActivityDelegate.kt`, `LauncherComposeHooks.kt` |
| Home button, accessibility takeover, phone setup, ADB setup, Button Mapper | `core/launcher/*`, `app/src/full` and `app/src/playstore` `LocalAdbSetupFactory`, `LoopbackAdbSetup` |
| Installed apps, hiding, renaming, icon packs | `core/apps/*`, `InstalledApp*`, `AppIconChoiceDialog`, `AppMoveMode`, `HomeTile` in `ui/components` |
| The Apps row on Home (Modern layout) | `ui/screens/home/LauncherAppsRow.kt` |
| Top-right clock, bell and gear, and their panels | `ui/screens/home/LauncherHomeOverlay.kt`, `HomeTopBarHeader`, `HomeSettingsDrawer`, `NotificationsDrawer` |
| Apps page | `ui/screens/apps/AppsScreen.kt` |
| Notifications | `core/notification/*` |
| Setup wizard | `ui/screens/setup/LauncherSetupWizard.kt`, `ui/components/LauncherSetupState.kt` |
| Settings | `ButtonMapper*`, `ButtonCaptureDialog`, `DefaultLauncherSettingsContent`, `IconPackPickerDialog`, `LocalAdbSetupDialog`, `LauncherSettingsHooks`, `LauncherIconPackSettings`, `LauncherLayoutSettings`, `core/launcher/LauncherLayoutPreferences.kt` |
| Per-profile Apps row | `core/profile/ActiveProfile.kt` |
| Strings, accessibility config, tests | `res/values/strings_launcher.xml`, `launcher-manifest/src/main/res`, `core/apps/*Test`, `core/launcher/*Test` |

## Hooks in Nuvio TV files (re-check on every Nuvio TV update)

| File | What the hook does |
|---|---|
| `MainActivity.kt` | About 60 lines: a `launcher` delegate property and calls to it from `onCreate`, `onResume`, `onNewIntent`, `onWindowFocusChanged`, `dispatchKeyEvent`; one `LauncherNavigationEffects` call; the setup-wizard gate; an Apps menu entry; Back never exits (two Back handlers, exit handler); the profile in the pill and in the collapsed rail |
| `NuvioApplication.kt` | The services needed only after start-up are injected lazily and started off the main thread, so the accessibility service can bind early. The largest remaining hook |
| `AndroidManifest.xml` | The six app icons are `activity-alias` entries (the Home and accessibility task must be shared) and `MainActivity` is `singleTask` |
| `full/AndroidManifest.xml` | `WRITE_SECURE_SETTINGS`, for the on-device ADB setup (not in the Play Store flavor) |
| `app/build.gradle.kts` | One line applying `launcher.gradle.kts` |
| `settings.gradle.kts` | One line including `launcher-manifest` |
| `HomeScreen.kt` | `LauncherHomeOverlay` call, `withLauncherRows()`, and the Apps row counting as content for the start-up gate |
| `ClassicHomeContent`, `GridHomeContent`, `ModernHomeContent` | One call each to publish the tile size, and the Apps row cases |
| `ModernHomeRows`, `ModernHomeRowsList`, `ModernHomeModels`, `ModernHomePresentation` | Calls into `LauncherAppsRow.kt`, and the `InstalledApp` payload |
| `HomeViewModel`, `HomeViewModelCatalogPipeline`, `HomeViewModelCatalogUtils`, `HomeUiState`, `CatalogOrderViewModel` | The Apps row is a catalog entry, so it can be reordered and turned off in Reorder Home Catalogs |
| `NuvioNavHost.kt`, `Screen.kt` | Apps route |
| `SettingsScreen`, `SettingsCatalog` | Default Launcher and Remote Buttons settings |
| `ThemeSettingsScreen` | One call: the icon pack card |
| `LayoutCardSettings`, `LayoutHomeSettings` | Tile size row, and the "pill shows profile" toggle |
| `ProfileManager.kt` | Two calls publishing the current profile to `ActiveProfile` |
| `res/values/strings.xml` | `app_name` is "Nuvio TV Launcher" |

Branding (app icons in all colours, logo, wordmark, TV banner) replaces Nuvio TV's image files, and `README.md` is ours.
`.gitattributes` makes the sync keep our version of those if Nuvio TV changes them too.

## Syncing with Nuvio TV

**Releases and betas** follow Nuvio TV's tags: the **Follow Nuvio TV releases** workflow builds our beta when they tag a
beta and our release when they tag a release (`scripts/launcher_next_release.py` decides what to build and our version).
Merging `dev` between releases is off unless the repository variable `SYNC_DEV` is `true`. By hand:

```bash
git config merge.ours.driver true      # once: keeps our README and branding on a conflict
git remote add nuviotv https://github.com/NuvioMedia/NuvioTV.git
git fetch nuviotv dev
git merge nuviotv/dev
scripts/check-launcher-touchpoints.sh nuviotv/dev
```

A conflict is almost always in a hook file above: keep Nuvio TV's side, then re-add our one-line call. Nuvio TV bumps
`versionName` in its own `app/build.gradle.kts`; ours is in `app/launcher.gradle.kts`, so that never conflicts.
The commits on this branch are split into new files, hooks, branding and CI, so `git rebase nuviotv/dev` also works.

## Not ported

Live TV, Music, YouTube, VPN and the other Nuvio One additions from `Br0ck25/NuvioTVLauncher` are not in this
branch. The Button Mapper's "Open Live TV" and "Open TV Guide" actions were removed with them, and the Apps and
Settings side-menu toggles (Nuvio One's `SideMenuPages`) have no equivalent here: Apps is always in the menu. The
Tile size setting sizes the Apps row only; Continue Watching, Upcoming and collection cards keep Nuvio TV's sizes.
