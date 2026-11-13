plugins {
    id("com.android.library")
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
    implementation(project(":shared"))
    implementation("com.google.mediapipe:tasks-audio:1.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
