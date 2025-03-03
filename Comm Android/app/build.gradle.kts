plugins {
    id("com.android.application")
    kotlin("android")
}

val compileSdkVersion: String by project
val minSdkVersion: String by project
val targetSdkVersion: String by project
val packageName = "com.garmin.android.apps.connectiq.sample.comm"
val versionCode: String by project
val versionName: String by project

android {
    namespace = this@Build_gradle.packageName
    compileSdk = this@Build_gradle.compileSdkVersion.toInt()

    defaultConfig {
        applicationId = this@Build_gradle.packageName
        minSdk = minSdkVersion.toInt()
        targetSdk = targetSdkVersion.toInt()
        versionCode = this@Build_gradle.versionCode.toInt()
        versionName = this@Build_gradle.versionName
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation ("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.garmin.connectiq:ciq-companion-app-sdk:2.0.3@aar")
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.3")
}