rootProject.name = "FetchJet"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val nativeDesktopBuild = providers.gradleProperty("fetchjet.nativeDesktop").orNull == "true"
val hasAndroidSdk = !System.getenv("ANDROID_HOME").isNullOrBlank() ||
    !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() ||
    file("local.properties").let { it.isFile && it.readLines().any { line -> line.trim().startsWith("sdk.dir") } }

if (hasAndroidSdk || !nativeDesktopBuild) {
    include(":androidApp")
    include(":androidUi")
}
include(":sharedLogic")


include(":kmpLocalization")
project(":kmpLocalization").projectDir = file("packages/kmp-localization/kmpLocalization")

if (providers.gradleProperty("fetchjet.nativeDesktop").orNull == "true") {
    include(":desktopNativeCore")
    project(":desktopNativeCore").projectDir = file("desktopNative/core")
}
