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

include(":androidApp")
include(":desktopApp")
include(":sharedLogic")
include(":sharedUI")


include(":kmpLocalization")
project(":kmpLocalization").projectDir = file("packages/kmp-localization/kmpLocalization")

if (providers.gradleProperty("fetchjet.nativeDesktop").orNull == "true") {
    include(":desktopNativeCore")
    project(":desktopNativeCore").projectDir = file("desktopNative/core")
}
