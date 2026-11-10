plugins { kotlin("multiplatform") version "2.4.20" }

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
        mingwX64Main {
            kotlin.srcDir("../../../sharedLogic/src/mingwX64Main/kotlin/com/michelle/raouf/fetch_jet/platform/win")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("io.ktor:ktor-client-core:3.6.1")
                implementation("io.ktor:ktor-client-winhttp:3.6.1")
                implementation("io.ktor:ktor-network:3.6.1")
            }
        }
    }
}
