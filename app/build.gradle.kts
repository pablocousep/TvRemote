plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Optional private release key from the environment (GitHub secrets). Without it every
// build is signed with the shared key in app/signing so updates install over each other.
val keystorePath: String? = System.getenv("TVREMOTE_KEYSTORE")
val buildNumber = (System.getenv("TVREMOTE_BUILD_NUMBER") ?: System.getenv("GITHUB_RUN_NUMBER"))?.toIntOrNull() ?: 1

android {
    namespace = "com.reelog.tvremote"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.reelog.tvremote"
        minSdk = 26
        targetSdk = 35
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
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("TVREMOTE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TVREMOTE_KEY_ALIAS")
                keyPassword = System.getenv("TVREMOTE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (keystorePath != null) "release" else "shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    buildFeatures { compose = true }
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
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
