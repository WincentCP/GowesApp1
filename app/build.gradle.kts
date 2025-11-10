plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "edu.uph.m23si1.gowesapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "edu.uph.m23si1.gowesapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    // --- THIS BLOCK IS NOW FIXED ---

    // Default dependencies (using corrected aliases from your TOML)
    implementation(libs.androidx.core.ktx) // Added this to TOML
    implementation(libs.appcompat)         // Corrected from libs.androidx.appcompat
    implementation(libs.material)
    implementation(libs.constraintlayout)  // Corrected from libs.androidx.constraintlayout
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)     // Corrected from libs.androidx.junit
    androidTestImplementation(libs.espresso.core) // Corrected from libs.androidx.espresso.core

    // --- ADDED DEPENDENCIES (from new TOML entries) ---

    // For the circular profile picture
    implementation(libs.circleimageview)

    // For the QR Code Scanner Camera
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
}