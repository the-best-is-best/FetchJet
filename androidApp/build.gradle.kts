import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":sharedUI"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.michelle.raouf.fetch_jet"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.michelle.raouf.fetch_jet"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.zip.so"
        }
    }
    // Release signing: keystore path and passwords come ONLY from the environment
    // (CI secrets or your shell). Nothing is hard-coded here any more.
    //   SIGNING_KEY_STORE_PATH   e.g. C:/keys/tbib.jks   (defaults to ../keys/tbib.jks)
    //   SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD
    val releaseKeystore: File? = run {
        val fromEnv =
            System.getenv("SIGNING_KEY_STORE_PATH")?.takeIf { it.isNotBlank() }?.let { file(it) }
        val ciKey = file("../keys/tbib.jks")
        when {
            fromEnv?.exists() == true -> fromEnv
            ciKey.exists() -> ciKey
            else -> null
        }
    }
    val releaseStorePassword = System.getenv("SIGNING_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")
    val hasReleaseSigning = releaseKeystore != null &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Local convenience only. On CI a release without the signing secrets must fail
                // loudly instead of publishing a debug-signed "release" APK.
                if (System.getenv("CI") == "true") {
                    throw GradleException("Release signing not configured: set SIGNING_KEY_STORE_BASE64, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD secrets")
                }
                logger.warn("WARNING: release signing not configured (SIGNING_* env vars missing) - signing release with the DEBUG key")
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}