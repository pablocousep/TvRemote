plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional private release key from the environment (GitHub secrets). Without it every
// GitHub build is signed with the shared key in app/signing so updates install over each other.
val keystorePath: String? = System.getenv("TVREMOTE_KEYSTORE")
// Private Google Play upload key (GitHub secrets). Never the shared key: it is public.
val uploadKeystorePath: String? = System.getenv("TVREMOTE_UPLOAD_KEYSTORE")
val buildNumber = (System.getenv("TVREMOTE_BUILD_NUMBER") ?: System.getenv("GITHUB_RUN_NUMBER"))?.toIntOrNull() ?: 1

android {
    namespace = "com.reelog.tvremote"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.reelog.tvremote"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.3.$buildNumber"
    }
    signingConfigs {
        // Shared key committed to the repo so every build (local or CI) has the same
        // signature and in-app updates can install over the previous version.
        create("shared") {
            storeFile = file("signing/tvremote.jks")
            storePassword = "tvremote"
            keyAlias = "tvremote"
            keyPassword = "tvremote"
        }
        if (uploadKeystorePath != null) {
            create("upload") {
                storeFile = file(uploadKeystorePath)
                storePassword = System.getenv("TVREMOTE_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TVREMOTE_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("TVREMOTE_UPLOAD_KEY_PASSWORD")
            }
        }
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("TVREMOTE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TVREMOTE_KEY_ALIAS")
                keyPassword = System.getenv("TVREMOTE_KEY_PASSWORD")
            }
        }
    }
    // github: APK published on GitHub releases, updates itself from there.
    // play: Google Play bundle; Play forbids self-updates, so no updater, install permission
    // or update notifications.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            signingConfig = signingConfigs.getByName(if (keystorePath != null) "release" else "shared")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            // Unsigned without the upload key; Play re-signs with its own app signing key.
            if (uploadKeystorePath != null) signingConfig = signingConfigs.getByName("upload")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
                // Optional Maven mirror for Robolectric's Android jars (-ProbolectricRepo=https://…).
                (project.findProperty("robolectricRepo") as String?)?.let { url ->
                    it.systemProperty("robolectric.dependency.repo.url", url)
                }
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    // Only the GitHub build checks for updates in the background.
    "githubImplementation"("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
