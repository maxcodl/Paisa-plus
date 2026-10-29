import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// Paths in keystore.properties are relative to the repository root, not app/.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun signingValue(name: String, environmentName: String): String? =
    providers.environmentVariable(environmentName).orNull
        ?: providers.gradleProperty(name).orNull
        ?: keystoreProperties.getProperty(name)

val releaseSigningValues = mapOf(
    "storeFile" to signingValue("storeFile", "RELEASE_STORE_FILE"),
    "storePassword" to signingValue("storePassword", "RELEASE_STORE_PASSWORD"),
    "keyAlias" to signingValue("keyAlias", "RELEASE_KEY_ALIAS"),
    "keyPassword" to signingValue("keyPassword", "RELEASE_KEY_PASSWORD"),
)
val hasReleaseSigning = releaseSigningValues.values.any { it != null }
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning").orNull.toBoolean()
if (hasReleaseSigning || requireReleaseSigning) {
    val missing = releaseSigningValues.filterValues { it.isNullOrBlank() }.keys
    require(missing.isEmpty()) { "Missing release signing settings: ${missing.joinToString()}. See docs/release-builds.md." }
    require(rootProject.file(releaseSigningValues.getValue("storeFile")!!).isFile) {
        "Release keystore file does not exist. Check storeFile / RELEASE_STORE_FILE."
    }
}

// ABI-split resource shrinking and bundles need separate build outputs.
val bundleBuild = providers.gradleProperty("bundleBuild").orNull.toBoolean()
if (bundleBuild) {
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("app-bundle"))
}

android {
    namespace = "com.paisaplus.compose"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.paisaplus.compose"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.9.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigningValues.getValue("storeFile")!!)
                storePassword = releaseSigningValues.getValue("storePassword")
                keyAlias = releaseSigningValues.getValue("keyAlias")
                keyPassword = releaseSigningValues.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        // With keystore.properties present, debug builds use the release key too, so they install over GitHub releases.
        debug {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Keep the local sideload fallback; CI requires real credentials explicitly.
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    splits {
        abi {
            isEnable = !bundleBuild
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")
    implementation("androidx.hilt:hilt-work:1.4.0")
    ksp("androidx.hilt:hilt-compiler:1.4.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("io.github.kyant0:backdrop:2.0.1")
    // HCT dynamic schemes: every M3 scheme variant + contrast level + 2025 (Expressive) spec.
    implementation("com.materialkolor:material-kolor:5.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.sqlite:sqlite-bundled-jvm:2.6.2")
}
