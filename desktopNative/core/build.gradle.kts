plugins { kotlin("multiplatform") version "2.4.20" }
kotlin {
    mingwX64 {
        binaries {
            sharedLib {
                baseName = "fjcore"
                freeCompilerArgs += listOf("-Xallocator=std")
            }
        }
    }
    sourceSets {
        mingwX64Main.dependencies {
            implementation("io.ktor:ktor-client-core:3.6.1")
            implementation("io.ktor:ktor-client-winhttp:3.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("com.squareup.okio:okio:3.18.2")
        }
    }
}
