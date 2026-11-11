import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)
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

// Memory measuring run: env FETCHJET_NMT=true (or fetchjet.nmt=true) turns on the JVM's native
// memory tracking, read with `jcmd <pid> VM.native_memory summary`. Never on in a release.
val fetchJetNativeMemoryTracking: Boolean =
    (System.getenv("FETCHJET_NMT") ?: project.findProperty("fetchjet.nmt")?.toString() ?: "false")
        .trim().equals("true", ignoreCase = true)

// The Chromium extension ships inside the app (resources fetchjet-extension/chromium): the app
// unpacks it for "Load unpacked", so it works without the Chrome / Edge stores.
val bundledExtensionDir = layout.buildDirectory.dir("generated/fetchjetExtension")
val extensionManifestVersion: String = Regex(""""version"\s*:\s*"([^"]+)"""")
    .find(rootProject.file("extension/manifest.json").readText())?.groupValues?.get(1)
    ?: error("version not found in extension/manifest.json")
val appExtensionVersion: String = Regex("""EXTENSION_VERSION\s*=\s*"([^"]+)"""")
    .find(
        rootProject.file(
            "sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/core/update/AppVersion.kt"
        ).readText()
    )?.groupValues?.get(1) ?: error("EXTENSION_VERSION not found in AppVersion.kt")
check(extensionManifestVersion == appExtensionVersion) {
    "extension/manifest.json is $extensionManifestVersion but EXTENSION_VERSION in AppVersion.kt is $appExtensionVersion"
}
val bundleExtension by tasks.registering(Sync::class) {
    from(rootProject.file("extension")) {
        include(
            "background.js", "yt-core.js", "content.js", "popup.html", "popup.js",
            "options.html", "options.js", "icon16.png", "icon48.png", "icon128.png",
            "manifest.json", "platform.js", "i18n/**", "_locales/**"
        )
    }
    into(bundledExtensionDir.map { it.dir("fetchjet-extension/chromium") })
}
sourceSets.main {
    resources.srcDir(bundledExtensionDir)
}
tasks.named("processResources") { dependsOn(bundleExtension) }

compose.desktop {
    application {
        mainClass = "com.michelle.raouf.fetch_jet.MainKt"
        jvmArgs += listOf(
            "-Dfetchjet.verboseLogs=$fetchJetVerboseLogs",
            "-XX:+UseSerialGC",
            "-Xms32m",
            "-Xmx1g",
            "-XX:MinHeapFreeRatio=10",
            "-XX:MaxHeapFreeRatio=30"
        )
        if (fetchJetNativeMemoryTracking) jvmArgs += listOf("-XX:NativeMemoryTracking=summary")
        System.getenv("FETCHJET_JVM_ARGS")?.split(' ')?.filter { it.isNotBlank() }
            ?.let { jvmArgs += it }

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
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleURLTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleURLName</key>
                                <string>Magnet link</string>
                                <key>CFBundleURLSchemes</key>
                                <array>
                                    <string>magnet</string>
                                </array>
                            </dict>
                            <dict>
                                <key>CFBundleURLName</key>
                                <string>FetchJet link</string>
                                <key>CFBundleURLSchemes</key>
                                <array>
                                    <string>fetchjet</string>
                                </array>
                            </dict>
                        </array>
                    """.trimIndent()
                }
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
