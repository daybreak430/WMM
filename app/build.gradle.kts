plugins {
    id("com.android.application") version "9.3.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.10"
}

android {
    namespace = "com.wmm.ledger"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wmm.ledger"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "0.7.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
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

ksp { arg("room.schemaLocation", "${projectDir}/schemas") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("io.github.kyant0:backdrop:1.0.6") {
        // Shapes 的 Android 实现共用本项目的 AndroidX Compose UI。
        exclude(group = "org.jetbrains.compose.ui", module = "ui")
    }
    implementation("com.mohamedrejeb.dnd:compose-dnd:1.0.0-alpha01") {
        // Android 端使用项目已有的 AndroidX Compose BOM。
        exclude(group = "org.jetbrains.compose.foundation", module = "foundation")
        exclude(group = "org.jetbrains.compose.ui", module = "ui")
    }
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("net.zetetic:sqlcipher-android:4.19.1")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
