plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tbdfit.wear"
    compileSdk = 37

    defaultConfig {
        // Must match :phone's applicationId exactly: Google Play services requires matching
        // package name (and matching signing certificate) between phone and Wear apps for Data
        // Layer (DataClient/MessageClient) communication to work at all. Deliberately
        // product-neutral (com.tbdfit.app), not phone-specific — this identity now represents the
        // whole TBDFit Android product across both form factors, not one of them. The Kotlin
        // source namespace (com.tbdfit.wear, above) is unaffected — only the installed app
        // identity changes. Future release builds of both apps must use a compatible (matching)
        // signing identity for the same reason — release signing itself remains deferred. See
        // docs/development/supabase-setup-and-verification.md for more.
        applicationId = "com.tbdfit.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Sandbox-network workaround: Robolectric's default artifact fetcher hardcodes
                // repo1.maven.org, which this environment's network policy blocks; the identical
                // artifacts are reachable via the Apache-hosted Maven Central mirror. Remove this
                // if building somewhere without that restriction.
                it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
                it.systemProperty("robolectric.dependency.repo.id", "central")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
