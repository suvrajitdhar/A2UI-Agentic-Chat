import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Base URL of the A2UI agent server (../a2ui-agent-server).
 * Default: the Android emulator's alias for the host machine. Override for a physical device with
 * `agentBaseUrl=http://192.168.1.20:3000` in gradle.properties (or `-PagentBaseUrl=...`).
 */
val agentBaseUrl: String = providers.gradleProperty("agentBaseUrl").getOrElse("http://10.0.2.2:3000")

android {
    namespace = "com.sdz.smpl.a2ui"
    // The A2UI libraries require compiling against API 37.1+ (minor SDK level).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.sdz.smpl.a2ui"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AGENT_BASE_URL", "\"$agentBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
}

// Fail a hung unit test run instead of blocking CI forever.
tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(5))
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // A2UI renderer: protocol model, Compose runtime/UI, Material 3 basic catalog
    implementation(libs.a2ui.model)
    implementation(libs.a2ui.compose.runtime)
    implementation(libs.a2ui.compose.ui)
    implementation(libs.a2ui.material3)

    // Networking (SSE over Ktor), JSON, image loading for the catalog's Image component
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.a2ui.compose.ui.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
