plugins {
    alias(libs.plugins.android.library)
}

// Nuvio TV Launcher: holds only the launcher's manifest entries and the two resources they use. The app depends on
// it (see app/launcher.gradle.kts) and Android merges the manifest in.
android {
    namespace = "com.tv.launcher.manifest"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }
}
