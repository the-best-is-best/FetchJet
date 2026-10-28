import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    // A manual dependsOn() edge (jvmAndroidMain below) switches the default source-set
    // hierarchy OFF unless it is applied explicitly - without this line iosMain is never
    // compiled and every expect declaration reports "no actual for Native".
    applyDefaultHierarchyTemplate()

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedLogic"
            isStatic = true
        }
    }

    jvm()

    android {
        namespace = "com.michelle.raouf.fetch_jet.sharedLogic"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Code shared by JVM desktop and Android only (the libtorrent engine).
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.libtorrent4j.core)
            }
        }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.ktor.client.core)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // libtorrent natives for every desktop OS the app is packaged for
            implementation(libs.libtorrent4j.windows)
            implementation(libs.libtorrent4j.macos)
            implementation(libs.libtorrent4j.linux)
        }
        androidMain.dependencies {
            implementation(libs.libtorrent4j.android.arm64)
            implementation(libs.libtorrent4j.android.arm)
            implementation(libs.libtorrent4j.android.x86)
            implementation(libs.libtorrent4j.android.x64)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.preference.ktx)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ffmpeg)
            implementation(libs.aria2c)
            implementation(libs.ytdlnis.packages.python)
            implementation(libs.ytdlnis.packages.bash)
            implementation(libs.commons.io)
            implementation(libs.commons.compress)
            implementation(libs.gson)
            implementation(libs.okhttp3)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}