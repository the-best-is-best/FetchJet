import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile as JavaZipFile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.apache.commons:commons-compress:1.28.0")
    }
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

abstract class SlimPythonTask : DefaultTask() {
    @get:InputFile
    abstract val aar: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val removedPatterns = listOf(
        "usr/lib/python3\\.14/(tkinter|turtledemo|idlelib|idle_test|pydoc_data|ensurepip|test|lib2to3)(/.*)?",
        "usr/lib/python3\\.14/turtle\\.py",
        "usr/lib/python3\\.14/site-packages/pip(/.*)?",
        "usr/lib/python3\\.14/site-packages/pip-[^/]*(/.*)?",
        "usr/lib/python3\\.14/lib-dynload/(_tkinter|_testcapi|_testlimitedcapi|_testinternalcapi|_testclinic|_testclinic_limited|_testbuffer|_testmultiphase|_testimportmultiple|_testsinglephase|_xxtestfuzz|_ctypes_test|xxlimited|xxlimited_35|xxsubtype)\\..*",
        "usr/lib/(libtcl|libtk|libX|libxcb|libfontconfig|libfreetype|libpng)[^/]*",
        "usr/lib/(tcl8|tcl8\\.6|tk8\\.6|itcl[^/]*|thread[^/]*|tdbc[^/]*|sqlite3|jvm|pkgconfig|cmake)(/.*)?",
        "usr/lib/(tclConfig|tkConfig|tclooConfig)\\.sh",
        "usr/lib/pkgIndex\\.tcl",
        "usr/lib/libsqlite3\\.53\\.3\\.so",
        "usr/lib/libsqlite3(\\.so.*)?",
        "usr/lib/libandroid-support\\.so",
        "usr/lib/libandroid-posix-semaphore\\.so",
        "usr/lib/[^/]*\\.(a|la)",
        "usr/etc/fonts(/.*)?"
    ).map { Regex(it) }

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val tmp = temporaryDir
        JavaZipFile(aar.get().asFile).use { aarZip ->
            aarZip.entries().asSequence()
                .filter { it.name.startsWith("jni/") && it.name.endsWith("/libpython.zip.so") }
                .forEach { e ->
                    val abi = e.name.removePrefix("jni/").substringBefore('/')
                    val src = File(tmp, "$abi-libpython.zip.so")
                    aarZip.getInputStream(e).use { i -> src.outputStream().use { o -> i.copyTo(o) } }
                    val dest = File(out, "$abi/libpythonslim.zip.so")
                    dest.parentFile.mkdirs()
                    slim(src, dest)
                    src.delete()
                }
        }
    }

    private val liftedLibs = mapOf(
        "usr/lib/libandroid-support.so" to "libandroid-support.so",
        "usr/lib/libandroid-posix-semaphore.so" to "libandroid-posix-semaphore.so",
        "usr/lib/libsqlite3.so.3.53.3" to "libsqlite3.so"
    )

    private fun slim(src: File, dest: File) {
        ZipFile.builder().setFile(src).get().use { zf ->
            val all = zf.entries.toList()
            liftedLibs.forEach { (entryName, fileName) ->
                val entry = all.first { it.name == entryName }
                zf.getInputStream(entry).use { input ->
                    File(dest.parentFile, fileName).outputStream().use { out -> input.copyTo(out) }
                }
            }
            val removed = all.filter { e -> removedPatterns.any { it.matches(e.name.trimEnd('/')) } }
                .map { it.name.trimEnd('/') }.toMutableSet()
            val survivors = all.map { it.name.trimEnd('/') }.toMutableSet().also { it.removeAll(removed) }
            var changed = true
            while (changed) {
                changed = false
                for (e in all) {
                    val n = e.name.trimEnd('/')
                    if (!e.isUnixSymlink || n !in survivors) continue
                    val target = zf.getInputStream(e).use { String(it.readBytes(), StandardCharsets.UTF_8) }
                    val resolved = File(File(n).parentFile, target).toPath().normalize()
                        .toString().replace('\\', '/')
                    val keep = resolved in survivors || survivors.any { it.startsWith("$resolved/") }
                    if (!keep) {
                        survivors.remove(n)
                        removed.add(n)
                        changed = true
                    }
                }
            }
            ZipArchiveOutputStream(dest).use { zo ->
                for (e: ZipArchiveEntry in all) {
                    if (e.name.trimEnd('/') in removed) continue
                    zo.addRawArchiveEntry(e, zf.getRawInputStream(e))
                }
            }
        }
    }
}

val ffmpegLibFiles: List<File> = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").flatMap { abi ->
    listOf("libffmpeg.so", "libffprobe.so").map { File(projectDir, "ffmpeg-libs/$abi/$it") }
}
tasks.matching { it.name == "preBuild" }.configureEach {
    val needed = ffmpegLibFiles
    doFirst {
        val missing = needed.filter { !it.exists() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing ffmpeg binaries: ${missing.joinToString { it.path }}. " +
                        "Build them with scripts/android-ffmpeg/build-all.sh"
            )
        }
    }
}

val slimPython = tasks.register<SlimPythonTask>("slimPython") {
    val pythonAar = configurations.detachedConfiguration(
        dependencies.create("io.github.deniscerri:ytdlnis_packages.python:${libs.versions.ytdlnis.python.get()}@aar")
    )
    aar.set(layout.file(provider { pythonAar.singleFile }))
    outputDir.set(layout.buildDirectory.dir("generated/slimPython"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(slimPython, SlimPythonTask::outputDir)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
dependencies {
    implementation(project(":androidUi"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

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
    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        buildConfigField("boolean", "VERBOSE_LOGS", fetchJetVerboseLogs.toString())
        applicationId = "com.michelle.raouf.fetch_jet"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = fetchJetVersionCode
        versionName = fetchJetVersion
        if (!buildingApksNotBundle) {
            ndk {
                abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
            }
        }
    }
    splits {
        abi {
            // One APK per CPU for sideloading. Off while building the .aab (Play splits it
            // itself): AGP cannot build a bundle with ABI splits on ("Multiple shrunk-resources
            // files"), so CI builds the APKs and the bundle in two separate Gradle runs.
            isEnable = buildingApksNotBundle
            // AGP 9 dropped reset(), and include() only adds to the default list, so the
            // dead ABIs (no phone ships them) have to be excluded by name.
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            exclude("mips", "mips64", "riscv64", "armeabi")
            isUniversalApk = false
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.directories.add("ffmpeg-libs")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            keepDebugSymbols += "**/*.zip.so"
            excludes += "**/libpython.zip.so"
            excludes += "**/libpython.so"
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
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}