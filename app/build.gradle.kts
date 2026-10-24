import java.util.Properties

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
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "0.3.3"
        // The Web OAuth client id (Google Cloud): sign-in asks for tokens for it, and the server checks them against it.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"1073093330874-h92fun5sei8rcitlf67jv2s5s9ip7if8.apps.googleusercontent.com\"")
        // The public site: share links (/s/…, /set/…), also used for App Links.
        val shareHost = "findbeatradar.com"
        buildConfigField("String", "SHARE_HOST", "\"$shareHost\"")
        manifestPlaceholders["shareHost"] = shareHost
        // Shown in Settings → About and in the privacy policy (beatradar-backend/templates/privacy.html).
        buildConfigField("String", "SUPPORT_EMAIL", "\"support@findbeatradar.com\"")
        // TODO: the Google Cloud project number linked in Play Console (Play Integrity). 0 = skip the check.
        buildConfigField("long", "CLOUD_PROJECT_NUMBER", "0L")
    }

    // The Play upload key lives outside the repo (the workspace's secrets/ folder); override the properties file with
    // -PsigningProps=path or BEATRADAR_SIGNING_PROPS. Without it, release builds come out unsigned.
    val signingProps = ((project.findProperty("signingProps") as String?) ?: System.getenv("BEATRADAR_SIGNING_PROPS"))
        ?.let(::file) ?: rootProject.file("../secrets/beatradar-upload.properties")
    val uploadKey = signingProps.takeIf { it.isFile }?.let { f ->
        Properties().apply { f.inputStream().use(::load) }.let { p ->
            signingConfigs.create("upload") {
                storeFile = f.resolveSibling(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // The emulator reaches the PC's localhost at 10.0.2.2. Override with -PapiBaseUrl=... (e.g. the PC's LAN IP
            // on a real phone, or http://localhost:8080/ together with `adb reverse tcp:8080 tcp:8080`).
            val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:8080/"
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
        release {
            // The API on its own name, so the website can move elsewhere later without an app update.
            buildConfigField("String", "API_BASE_URL", "\"https://api.findbeatradar.com/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = uploadKey
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        // Library licence texts, version markers and the coroutines debug agent: nothing reads them at run time.
        resources.excludes += listOf("META-INF/**/LICENSE*", "META-INF/*.version", "DebugProbesKt.bin")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        disable += listOf("PropertyEscape", "CredManMissingDal", "UseAppTint")
    }
}

tasks.configureEach {
    if (name == "assembleRelease") {
        doLast {
            val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            val version = android.defaultConfig.versionName
            val defaultApk = File(apkDir, "app-release.apk")
            val targetApk = File(apkDir, "BeatRadar-$version.apk")
            if (defaultApk.exists()) {
                defaultApk.copyTo(targetApk, overwrite = true)
                println("Release APK generated: ${targetApk.name}")
            }
        }
    } else if (name == "bundleRelease") {
        doLast {
            val bundleDir = layout.buildDirectory.dir("outputs/bundle/release").get().asFile
            val version = android.defaultConfig.versionName
            val defaultBundle = File(bundleDir, "app-release.aab")
            val targetBundle = File(bundleDir, "BeatRadar-$version.aab")
            if (defaultBundle.exists()) {
                defaultBundle.copyTo(targetBundle, overwrite = true)
                println("Release AAB generated: ${targetBundle.name}")
            }
        }
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
