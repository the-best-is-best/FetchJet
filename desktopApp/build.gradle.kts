import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":sharedUI"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.components.resources)

    implementation(libs.compose.uiToolingPreview)
}

// The app version lives in ONE place: sharedLogic/.../core/update/AppVersion.kt (APP_VERSION).
// jpackage needs a plain MAJOR.MINOR.PATCH (no "-beta"): the pre-release part is dropped here.
val fetchJetVersion: String = (rootProject.file(
    "sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/core/update/AppVersion.kt"
).readText().let { Regex("""APP_VERSION\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
    ?: error("APP_VERSION not found in AppVersion.kt")).substringBefore('-')

// Verbose diagnostics: env FETCHJET_VERBOSE_LOGS wins, then fetchjet.verboseLogs in
// gradle.properties. Baked into the launcher as a JVM system property.
val fetchJetVerboseLogs: Boolean =
    (System.getenv("FETCHJET_VERBOSE_LOGS") ?: project.findProperty("fetchjet.verboseLogs")
        ?.toString() ?: "false")
        .trim().equals("true", ignoreCase = true)

compose.desktop {
    application {
        mainClass = "com.michelle.raouf.fetch_jet.MainKt"
        jvmArgs += listOf("-Dfetchjet.verboseLogs=$fetchJetVerboseLogs")

        // packageRelease* would run ProGuard with no keep rules for ktor/okhttp/okio and
        // the packaged app would crash at startup. Keep it off until rules exist.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.AppImage
            )
            packageName = "FetchJet"
            packageVersion = fetchJetVersion
            description = "FetchJet High-Velocity Multi-Engine Downloader"
            vendor = "Michelle Raouf"
            // Double-clicking a .torrent opens it in FetchJet (the path arrives as the first arg).
            fileAssociation(
                mimeType = "application/x-bittorrent",
                extension = "torrent",
                description = "BitTorrent file"
            )
            // includeAllModules bundles the whole JDK (~270 MB incl. JavaFX, jshell, javadoc...).
            // This explicit list keeps the runtime small. Re-check with
            //   ./gradlew :desktopApp:suggestRuntimeModules
            // after adding a dependency, and add what it prints.
            modules(
                "java.base", "java.desktop", "java.logging", "java.management",
                "java.naming", "java.net.http", "java.prefs", "java.sql", "java.xml",
                "jdk.httpserver",      // com.sun.net.httpserver bridge on port 28450
                "jdk.crypto.ec",       // TLS to most hosts on JDK 17/21
                "jdk.unsupported",     // sun.misc.Unsafe (okio, coroutines, skiko)
                "jdk.charsets", "jdk.zipfs", "jdk.localedata", "jdk.accessibility"
            )

            val iconDir = project.file("src/main/resources")
            val winIcon = File(iconDir, "launcher.ico")
            val macIcon = File(iconDir, "launcher.icns")
            val linuxIcon = File(iconDir, "launcher.png")

            windows {
                shortcut = true
                menu = true
                menuGroup = "FetchJet"
                dirChooser = true
                perUserInstall = true
                // Stable product id so a newer MSI upgrades the old install instead of
                // installing side by side. Never change this value.
                upgradeUuid = "7E1C3F2A-5B7D-4C8E-9A61-4F2A9C1B7D30"
                if (winIcon.exists()) {
                    iconFile.set(winIcon)
                }
            }

            macOS {
                // Stable identifier (jpackage would otherwise derive one you cannot keep).
                bundleID = "com.michelle.raouf.fetch_jet"
                dockName = "FetchJet"
                appCategory = "public.app-category.utilities"
                if (macIcon.exists()) {
                    iconFile.set(macIcon)
                }
            }

            linux {
                shortcut = true
                packageName = "fetchjet"
                debMaintainer = "eng.michelle.raouf@gmail.com"
                menuGroup = "Network"
                appCategory = "Network"
                if (linuxIcon.exists()) {
                    iconFile.set(linuxIcon)
                }
            }
        }
    }
}
