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

compose.desktop {
    application {
        mainClass = "com.michelle.raouf.fetch_jet.MainKt"

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
            packageVersion = "1.0.0"
            description = "FetchJet High-Velocity Multi-Engine Downloader"
            vendor = "Michelle Raouf"
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
