plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.example.speechcoach.history"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
}

dependencies {
    implementation(project(":practice-core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
