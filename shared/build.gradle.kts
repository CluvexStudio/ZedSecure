plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }

    android {
        namespace = "dev.cluvex.zedsecure.shared"
        compileSdk = 37
        minSdk = 24
    }
    jvm("desktop")

    sourceSets {
        val jvmMain = create("jvmMain") {
            dependsOn(getByName("commonMain"))
            dependencies {
                implementation(libs.bouncycastle)

                api(libs.jsch)

                implementation(libs.zxing.core)
            }
        }
        getByName("commonMain") {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.animation)
                implementation(libs.compose.components.resources)

                implementation(libs.compose.material3.mp)
                implementation(libs.lifecycle.viewmodel.compose.mp)
                implementation(libs.lifecycle.runtime.compose.mp)
                implementation(libs.coil.compose)
                implementation(libs.coil.svg)
                implementation(libs.androidx.graphics.shapes)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.reorderable)
            }
        }
        getByName("androidMain") {
            dependsOn(jvmMain)
            dependencies {
                implementation(libs.kotlinx.coroutines.android)

                implementation(libs.androidx.activity.compose)
            }
        }
        getByName("desktopMain") {
            dependsOn(jvmMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
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

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.cluvex.zedsecure.shared.resources"
    generateResClass = always
}

tasks.register<Copy>("copyComposeResForAndroid") {
    dependsOn("prepareComposeResourcesTaskForCommonMain")
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"))
    into(layout.buildDirectory.dir("composeResForAndroid/composeResources/dev.cluvex.zedsecure.shared.resources"))
}
