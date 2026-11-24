<div align="center">

  <img src="assets/brand/app_logo_wordmark.png" alt="Nuvio" width="300" />

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioTV/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## Nuvio TV Launcher

This repository is a fork of [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV) that turns the app into a
TV launcher. On top of Nuvio TV it adds:

- **Home app and Home button**: set the app as the TV's Home screen, with an accessibility override and boot
  receiver for firmware that will not hand Home over. Back never leaves the launcher.
- **Phone setup and on-device ADB setup**: pair from a phone, or let the TV grant itself the permission it needs.
- **Button Mapper**: single, double and long press actions for remote buttons, in the launcher and globally.
- **Apps row and Apps page**: every installed app, with rename, hide (on Home or everywhere), move, and app info.
- **Icon packs**, per-app icon choice from a file manager, and selectable app icons.
- Notifications drawer, top-right clock, bell and settings gear, first-run setup wizard, and the Tile size and
  "pill shows profile" layout settings.

See [LAUNCHER_TOUCHPOINTS.md](LAUNCHER_TOUCHPOINTS.md) for which files are new and which Nuvio TV files carry our edits.
The app id is `com.nuvio.tv.launcher`, so it installs next to the stock Nuvio TV.

## Get Nuvio TV

- [Android TV on Google Play](https://play.google.com/store/apps/details?id=com.nuvio.app)
- [Android TV APK](https://github.com/NuvioMedia/NuvioTV/releases/latest)

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioTV.git
cd NuvioTV
./gradlew :app:assembleFullDebug
```

Nuvio TV is built with Kotlin, Jetpack Compose, TV Material 3, and Media3. Development requires Android Studio, a JDK, and the Android SDK.

## License

[GNU General Public License v3.0](./LICENSE)
