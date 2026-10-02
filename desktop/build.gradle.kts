import org.jetbrains.compose.desktop.application.dsl.TargetFormat

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17

        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

val zedVersion = project.property("zedsecure.versionName") as String

val generateVersion by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    val version = zedVersion
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile.resolve("dev/cluvex/zedsecure/desktop")
        dir.mkdirs()
        dir.resolve("BuildVersion.kt").writeText(
            "package dev.cluvex.zedsecure.desktop\n\ninternal const val BUILD_VERSION = \"$version\"\n",
        )
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateVersion)
}

configurations.configureEach {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { showStandardStreams = false }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3.mp)
    implementation(libs.compose.components.resources)
    implementation(libs.lifecycle.viewmodel.compose.mp)
    implementation(libs.lifecycle.runtime.compose.mp)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.compose.native.tray)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

compose.desktop {
    application {
        mainClass = "dev.cluvex.zedsecure.desktop.GuiKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "ZedSecure"
            packageVersion = zedVersion
            description = "ZedSecure VPN client"
            vendor = "Cluvex Studio"
            linux {
                iconFile.set(project.file("icons/zedsecure.png"))
                packageName = "zedsecure"
                menuGroup = "Network"
                shortcut = true
            }
            windows {
                iconFile.set(project.file("icons/zedsecure.ico"))
                menuGroup = "ZedSecure"
                shortcut = true
            }
            macOS {
                iconFile.set(project.file("icons/zedsecure.icns"))
                bundleID = "dev.cluvex.zedsecure"
                minimumSystemVersion = "12.0"
            }
        }
    }
}
