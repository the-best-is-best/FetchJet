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

// The app version lives in ONE place: sharedLogic/.../core/update/AppVersion.kt (APP_VERSION).
val fetchJetVersion: String = rootProject.file(
    "sharedLogic/src/commonMain/kotlin/com/michelle/raouf/fetch_jet/core/update/AppVersion.kt"
).readText().let { Regex("""APP_VERSION\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
    ?: error("APP_VERSION not found in AppVersion.kt")
// versionCode must grow with every release for Android to accept the update: 1.2.3 -> 10203.
val fetchJetVersionCode: Int = fetchJetVersion.substringBefore('-').split('.')
    .map { it.toIntOrNull() ?: 0 }.let { (it + listOf(0, 0, 0)).take(3) }
    .let { (ma, mi, pa) -> ma * 10000 + mi * 100 + pa }

// ABI splits are on for APK runs only (see splits {} below). Computed here, outside every
// DSL block, so the IDE does not mistake the collection call for a DSL method.
val requestedTasks: List<String> = gradle.startParameter.taskNames
val buildingApksNotBundle: Boolean =
    requestedTasks.none { it.contains("bundle", ignoreCase = true) }

android {
    namespace = "com.michelle.raouf.fetch_jet"
    // Verbose diagnostics (see AppLog): env FETCHJET_VERBOSE_LOGS, else gradle.properties.
    val fetchJetVerboseLogs: Boolean =
        (System.getenv("FETCHJET_VERBOSE_LOGS") ?: project.findProperty("fetchjet.verboseLogs")
            ?.toString() ?: "false")
            .trim().equals("true", ignoreCase = true)
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        buildConfigField("boolean", "VERBOSE_LOGS", fetchJetVerboseLogs.toString())
        applicationId = "com.michelle.raouf.fetch_jet"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = fetchJetVersionCode
        versionName = fetchJetVersion
        ndk {
            abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
        }
    }
    splits {
        abi {
            // One APK per CPU for sideloading. Off while building the .aab (Play splits it
            // itself): AGP cannot build a bundle with ABI splits on ("Multiple shrunk-resources
            // files"), so CI builds the APKs and the bundle in two separate Gradle runs.
            isEnable = buildingApksNotBundle
            // (AGP 9 dropped reset(); include() alone defines the list.)
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
                // Only when an Android release is actually being built: the desktop and iOS jobs
                // also configure this project and must not fail on missing Android secrets.
                val buildsAndroidRelease = gradle.startParameter.taskNames.any { task ->
                    val t = task.substringAfterLast(':')
                    (task.startsWith(":androidApp:") || !task.contains(':')) &&
                            (t.startsWith("assemble") || t.startsWith("bundle") || t.startsWith("package")) &&
                            t.contains("Release", ignoreCase = true)
                }
                if (System.getenv("CI") == "true" && buildsAndroidRelease) {
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
        buildConfig = true
    }
}