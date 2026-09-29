import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

android {
    namespace = "dev.cluvex.zedsecure"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zedsecure.vpn"
        minSdk = 24
        targetSdk = 36
        versionCode = (project.property("zedsecure.versionCode") as String).toInt()
        versionName = project.property("zedsecure.versionName") as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("main").assets.srcDir(
        rootProject.file("shared/build/composeResForAndroid"),
    )

    val buildingBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    splits {
        abi {
            isEnable = !buildingBundle
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true

            excludes += "**/x86/*.so"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
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

tasks.matching {
    it.name.matches(Regex("merge.*Assets")) || it.name.contains("lint", ignoreCase = true)
}.configureEach {
    dependsOn(":shared:copyComposeResForAndroid")
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle)

    implementation(files("libs/zedcore.aar"))

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
