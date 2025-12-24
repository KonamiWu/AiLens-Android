plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "1.9.0"
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1"
}

android {
    namespace = "com.konami.ailens"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.konami.ailens"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "product"

    productFlavors {
        create("AiLens") {
            dimension = "product"
            applicationIdSuffix = ".AiLens"
            versionNameSuffix = "-AiLens"
            // 可選：添加 BuildConfig 欄位
            // buildConfigField("String", "PRODUCT_NAME", "\"Product A\"")
        }

        create("Despacito1") {
            dimension = "product"
            applicationIdSuffix = ".Despacito1"
            versionNameSuffix = "-Despacito1"
            // buildConfigField("String", "PRODUCT_NAME", "\"Product B\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.gson)

    implementation(libs.socketio)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.azure.speech.sdk)

    // WorkManager for background tasks
    implementation(libs.androidx.work.runtime.ktx)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Google Maps and Navigation - only for AiLens flavor
    add("AiLensImplementation", libs.bundles.google.maps.navigation)

    implementation(libs.bundles.coroutines)
    implementation(libs.androidx.security.crypto)
    implementation(libs.markwon.core)
    configurations.all {
        exclude(group = "com.google.android.gms", module = "play-services-maps")
    }
}
