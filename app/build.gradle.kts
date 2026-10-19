plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.boimin.beatradar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.boimin.beatradar"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        // TODO: the Web OAuth client id from the Google Cloud project (the server checks tokens against it).
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"REPLACE_ME.apps.googleusercontent.com\"")
        // TODO: the share-link domain is not decided yet (PLAN section 11.2). Also used for App Links.
        val shareHost = "beatradar.example"
        buildConfigField("String", "SHARE_HOST", "\"$shareHost\"")
        manifestPlaceholders["shareHost"] = shareHost
        // TODO: the Google Cloud project number linked in Play Console (Play Integrity). 0 = skip the check.
        buildConfigField("long", "CLOUD_PROJECT_NUMBER", "0L")
    }

    buildTypes {
        debug {
            // The emulator reaches the PC's localhost at 10.0.2.2. Override with -PapiBaseUrl=... (e.g. the PC's LAN IP
            // on a real phone, or http://localhost:8080/ together with `adb reverse tcp:8080 tcp:8080`).
            val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:8080/"
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
        release {
            // TODO: the production domain is not decided yet (PLAN section 11.2).
            buildConfigField("String", "API_BASE_URL", "\"https://api.beatradar.example/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.billing.ktx)
    implementation(libs.play.integrity)
    // Play services pull in an old Fragment; the ActivityResult APIs need 1.3+ (lint blocks release builds otherwise).
    implementation(libs.androidx.fragment)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
