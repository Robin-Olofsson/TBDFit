import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// SUPABASE_URL / SUPABASE_ANON_KEY are read from local.properties (already gitignored) rather
// than committed. The anon/publishable key is safe to ship in a client app by design — Supabase's
// security boundary is RLS, not secrecy of this key — but it's still kept out of git so it's not
// hardcoded per-environment and never confused with a true secret (service-role key, DB password),
// which must never appear anywhere in this app.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.tbdfit.phone"
    compileSdk = 37

    defaultConfig {
        // Shared with :wear intentionally — see :wear's build.gradle.kts comment. This is a
        // product-level identity (both form factors of one app), not phone-specific, even though
        // this module's own Kotlin namespace (above) remains phone-specific implementation
        // organization.
        applicationId = "com.tbdfit.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "")}\"")
        // The OAuth 2.0 Web application Client ID from Google Cloud Console (NOT the Android
        // Client ID) — required by Credential Manager's GetGoogleIdOption as the audience Supabase
        // will validate the resulting ID token against. Client-safe configuration, not a secret —
        // same tier as the Supabase anon key above — but still kept out of git via local.properties
        // for environment hygiene. See docs/development/supabase-setup-and-verification.md.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"")
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
        buildConfig = true
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

    // Room's exported schema JSON history (android/phone/schemas/) is the durable-data policy this
    // module now depends on — see docs/architecture/strength-workout-first-slice-design.md's
    // PERSISTENCE/MIGRATION POLICY. It must be committed to version control, not gitignored: it is
    // both the record migrations are checked against and what MigrationTestHelper reads to
    // construct a real "old version" database.
    //
    // Deliberately added to the `main` source set, not `test`: Robolectric's unit-test asset
    // resolution does not go through AGP's per-test-variant asset merge at all — its generated
    // test_config.properties (android_merged_assets) points at the `debug` (main) variant's merged
    // assets output regardless of which source set a test lives in. Confirmed by inspecting
    // phone/build/intermediates/unit_test_config_directory/debugUnitTest/.../test_config.properties
    // after `test`-only wiring silently produced a FileNotFoundException from MigrationTestHelper.
    // The trade-off (a few KB of schema JSON shipping inside the real app's assets) is accepted as
    // the standard, documented way to make Room migration tests runnable under Robolectric.
    sourceSets {
        getByName("main") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}

// Room schema export — see the durable-data policy comment above. Generated JSON lands in
// android/phone/schemas/ and must be committed; MigrationTestHelper reads it via the test source
// set's assets.srcDirs wiring above.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
