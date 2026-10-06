import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    // AGP 9 ships built-in Kotlin support: the `org.jetbrains.kotlin.android`
    // plugin must NOT be applied any more. Configure Kotlin via the `kotlin {}` block.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// ---- Release signing (keystore generated once, reused for every release) ----
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// ---- Version ----------------------------------------------------------------
// The git tag is the source of truth, not this file: CI passes -PversionName /
// -PversionCode, taken from the tag it is releasing. The literals below are what a
// local build produces, and they must stay in step with the newest tag.
//
// versionCode is derived, never typed: major*10000 + minor*100 + patch. So v1.2.3
// is 10203 and v2.0.0 is 20000 — it always increases across releases, which is the
// only thing Android actually requires of it, and it reads back as the version.
val appVersionName: String = (findProperty("versionName") as String?) ?: "1.1.0"
val appVersionCode: Int = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 10_100

android {
    namespace = "com.del.bitsay"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.del.bitsay"
        // Android 12 is a real floor, not a guess: the widget is built on
        // RemoteViews.RemoteCollectionItems / targetCellWidth / previewLayout (all API 31),
        // and those are the non-deprecated collection APIs on Android 17.
        minSdk = 31
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = false
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // Let AGP pick the schemes: with minSdk 31 it signs v2 + v3, which is exactly
                // what every supported device understands (v1/JAR is pointless above API 24).
                enableV1Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.kotlin_module",
            "/kotlin/**",
            "/DebugProbesKt.bin",
        )
    }

    lint {
        // A missing translation is a bug, not a warning: the default resource set is English and
        // anything absent from values-zh silently falls back to it for Chinese users.
        error += setOf("MissingTranslation", "ExtraTranslation")
        // Lint still should not fail the build for unrelated style advisories.
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
