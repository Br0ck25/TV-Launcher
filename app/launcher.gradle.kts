import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.util.Properties

// Nuvio TV Launcher: everything in the build that differs from Nuvio TV. Applied by one line at the end of
// app/build.gradle.kts, so merges with Nuvio TV never touch our values (the app id, the version, the update repository,
// the launcher's libraries). To release, bump the two version values here, commit, and run the Release workflow.

val launcherVersionName = "1.2.0-beta.4"
val launcherVersionCode = 1
val launcherApplicationId = "com.tv.launcher"

// Where the in-app updater looks for releases.
val launcherUpdateOwner = "Br0ck25"
val launcherUpdateRepo = "TV-Launcher"

// The official Nuvio account server and its publishable (public by design) client key, used when a build doesn't set
// its own NUVIO_SUPABASE_URL / NUVIO_SUPABASE_ANON_KEY, so signing in with a real Nuvio account works without any
// extra setup.
val launcherDefaultSupabaseUrl = "https://api.nuvio.tv"
val launcherDefaultSupabaseAnonKey = "sb_publishable_1Clq8rlTVACkdcZuqr6_AD__xUUC_EN"

fun launcherProperties(file: String) = Properties().apply {
    val f = rootProject.file(file)
    if (f.exists()) f.inputStream().use { load(it) }
}
val launcherLocal = launcherProperties("local.properties")
val launcherDev = launcherProperties("local.dev.properties")

fun Properties.nonBlank(key: String): String? = getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
fun launcherQuote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

extensions.configure<ApplicationExtension> {
    defaultConfig {
        applicationId = launcherApplicationId
        versionCode = launcherVersionCode
        versionName = launcherVersionName
        buildConfigField("String", "GITHUB_OWNER", launcherQuote(launcherUpdateOwner))
        buildConfigField("String", "GITHUB_REPO", launcherQuote(launcherUpdateRepo))
    }

    buildTypes {
        // Debug builds read the dev properties first, then the local ones; release builds the local ones only.
        getByName("debug") {
            buildConfigField(
                "String", "SUPABASE_URL",
                launcherQuote(launcherDev.nonBlank("NUVIO_SUPABASE_URL") ?: launcherLocal.nonBlank("NUVIO_SUPABASE_URL") ?: launcherDefaultSupabaseUrl)
            )
            buildConfigField(
                "String", "SUPABASE_ANON_KEY",
                launcherQuote(launcherDev.nonBlank("NUVIO_SUPABASE_ANON_KEY") ?: launcherLocal.nonBlank("NUVIO_SUPABASE_ANON_KEY") ?: launcherDefaultSupabaseAnonKey)
            )
        }
        getByName("release") {
            buildConfigField(
                "String", "SUPABASE_URL",
                launcherQuote(launcherLocal.nonBlank("NUVIO_SUPABASE_URL") ?: launcherDefaultSupabaseUrl)
            )
            buildConfigField(
                "String", "SUPABASE_ANON_KEY",
                launcherQuote(launcherLocal.nonBlank("NUVIO_SUPABASE_ANON_KEY") ?: launcherDefaultSupabaseAnonKey)
            )
        }
        // Keeps the BouncyCastle providers used by the ADB identity certificate (see proguard-launcher-rules.pro).
        configureEach { proguardFiles("proguard-launcher-rules.pro") }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    // Debug builds install next to the release build.
    onVariants(selector().withBuildType("debug")) { variant ->
        val isPlaystore = variant.productFlavors.any { it.second == "playstore" }
        if (!isPlaystore) variant.applicationId.set("$launcherApplicationId.debug")
    }
}

dependencies {
    // The launcher's manifest entries (services, Home filters, queries): merged into the app's manifest.
    add("implementation", project(":launcher-manifest"))
    // On-device ADB client used by the phone-driven Home button setup (runs the commands on this TV).
    add("fullImplementation", "com.github.MuntashirAkon:libadb-android:3.0.0")
    // libadb-android only exposes BouncyCastle at runtime; used to create the ADB identity cert.
    add("fullImplementation", "org.bouncycastle:bcprov-jdk15to18:1.78")
    // Unit tests of the launcher.
    add("testImplementation", "org.robolectric:robolectric:4.17")
    // Icon pack tests parse appfilter.xml; Android's XmlPullParser is not on the JVM test classpath.
    add("testImplementation", "net.sf.kxml:kxml2:2.3.0")
}

// Two copies of BouncyCastle (bcprov-jdk15to18 above and bcprov-jdk18on from other libraries) fail the duplicate-class
// check, so the full flavor keeps the newer one. (The playstore flavor has no launcher libraries.)
configurations.matching { it.name.startsWith("full") }.configureEach {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
}
