import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.receivemoney"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.receivemoney"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Paid mode calls Azure directly (demo setup, no backend). The key is read from
        // local.properties, which is git-ignored, and is NOT secret once built into an APK:
        // use a free-tier (F0) key and regenerate it after the demo.
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        fun setting(name: String, default: String = ""): String =
            (localProps.getProperty(name) ?: project.findProperty(name) as String? ?: default).trim()
        buildConfigField("String", "AZURE_SPEECH_KEY", "\"${setting("azureSpeechKey")}\"")
        buildConfigField("String", "AZURE_SPEECH_REGION", "\"${setting("azureSpeechRegion")}\"")
        buildConfigField("String", "AZURE_VOICE_EN_FEMALE", "\"${setting("azureVoiceEnFemale", "en-US-JennyNeural")}\"")
        buildConfigField("String", "AZURE_VOICE_EN_MALE", "\"${setting("azureVoiceEnMale", "en-US-GuyNeural")}\"")
        buildConfigField("String", "AZURE_VOICE_KM_FEMALE", "\"${setting("azureVoiceKmFemale", "km-KH-SreymomNeural")}\"")
        buildConfigField("String", "AZURE_VOICE_KM_MALE", "\"${setting("azureVoiceKmMale", "km-KH-PisethNeural")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Real org.json for JVM unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
}
