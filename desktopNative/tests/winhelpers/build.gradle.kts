plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

kotlin {
    mingwX64 {
        binaries {
            executable {
                baseName = "winhelpers"
                entryPoint = "main"
                binaryOption("pagedAllocator", "false")
                linkerOpts("-lole32")
            }
        }
    }
    sourceSets {
        commonMain {
            kotlin.srcDir("../../../sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/feature/device_sync")
            kotlin.srcDir("../../../sharedLogic/src/nativeDesktopMain/kotlin/com/michelle/raouf/fetch_jet/platform/desktop")
            kotlin.srcDir("../../../sharedLogic/src/nativeDesktopMain/kotlin/com/michelle/raouf/fetch_jet/platform/proxy")
            kotlin.exclude("**/PairedDeviceStore.kt")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
                implementation("io.ktor:ktor-client-core:3.6.1")
                implementation("com.squareup.okio:okio:3.18.2")
            }
        }
        mingwX64Main {
            kotlin.srcDir("../../../sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/platform/win")
            kotlin.srcDir("../../../sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/feature/device_sync")
            kotlin.srcDir("../../../sharedLogic/src/nativeDesktopMain/kotlin/com/michelle/raouf/fetch_jet/feature/device_sync")
            kotlin.srcDir("../../../sharedLogic/src/nativeDesktopMain/kotlin/com/michelle/raouf/fetch_jet/platform/torrent")
            kotlin.srcDir("../../../sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/platform/desktop")
            kotlin.srcDir("../../../sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/platform/proxy")
            kotlin.exclude("**/DeviceSyncFactory.mingw.kt")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("io.ktor:ktor-client-core:3.6.1")
                implementation("io.ktor:ktor-client-winhttp:3.6.1")
                implementation("io.ktor:ktor-network:3.6.1")
            }
        }
    }
}
