plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.speechcoach.transcription"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":practice-core"))
    implementation(libs.mediapipe.tasks.audio)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
