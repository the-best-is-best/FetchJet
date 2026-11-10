plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
        optIn.add("kotlin.experimental.ExperimentalNativeApi")
    }

    mingwX64 {
        binaries {
            sharedLib {
                baseName = "fjcore"
                binaryOption("pagedAllocator", "false")
                linkerOpts("-lole32")
            }
        }
    }

    sourceSets {
        getByName("mingwX64Main").dependencies {
            implementation(project(":sharedLogic"))
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.network)
            implementation(libs.okio)
        }
    }
}
