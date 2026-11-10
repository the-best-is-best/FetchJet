plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

val repo = rootDir.resolve("../../../..").canonicalFile
val pkg = "kotlin/com/michelle/raouf/fetch_jet"

kotlin {
    jvm()
    sourceSets {
        commonMain {
            kotlin.srcDir(repo.resolve("sharedLogic/src/commonMain/$pkg/feature/device_sync"))
            kotlin.srcDir(repo.resolve("sharedLogic/src/commonMain/$pkg/core/security"))
            kotlin.exclude("**/PairedDeviceStore.kt", "**/Secrets.kt")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            }
        }
        jvmMain {
            kotlin.srcDir(repo.resolve("sharedLogic/src/jvmAndroidMain/$pkg/feature/device_sync"))
            kotlin.srcDir(repo.resolve("sharedLogic/src/jvmAndroidMain/$pkg/core/security"))
            kotlin.exclude("**/DeviceSyncFactory.jvmAndroid.kt")
        }
    }
}

val runtimeFiles = configurations.getByName("jvmRuntimeClasspath")
val peerJar = tasks.named<Jar>("jvmJar").flatMap { it.archiveFile }
val classpathFile = layout.buildDirectory.file("peer-classpath.txt")

tasks.register("peerClasspath") {
    dependsOn("jvmJar")
    inputs.files(runtimeFiles)
    outputs.file(classpathFile)
    doLast {
        val entries = listOf(peerJar.get().asFile) + runtimeFiles.files
        classpathFile.get().asFile.writeText(entries.joinToString(File.pathSeparator) { it.absolutePath })
    }
}
