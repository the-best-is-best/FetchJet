plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
        optIn.add("kotlin.experimental.ExperimentalNativeApi")
    }

    applyDefaultHierarchyTemplate()

    mingwX64 {
        binaries {
            sharedLib {
                baseName = "fjcore"
                binaryOption("pagedAllocator", "false")
                linkerOpts("-lole32")
            }
        }
    }

    linuxX64 {
        binaries {
            sharedLib {
                baseName = "fjcore"
                binaryOption("pagedAllocator", "false")
            }
        }
    }

    macosArm64 {
        binaries {
            sharedLib {
                baseName = "fjcore"
                binaryOption("pagedAllocator", "false")
            }
        }
    }

    sourceSets {
        val nativeDesktopMain by creating {
            dependsOn(getByName("nativeMain"))
            dependencies {
                implementation(project(":sharedLogic"))
                implementation(libs.kotlinx.coroutinesCore)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.network)
                implementation(libs.okio)
            }
        }
        getByName("mingwX64Main") {
            kotlin.setSrcDirs(emptyList<Any>())
            dependsOn(nativeDesktopMain)
        }
        getByName("linuxX64Main").dependsOn(nativeDesktopMain)
        getByName("macosArm64Main").dependsOn(nativeDesktopMain)
    }
}
