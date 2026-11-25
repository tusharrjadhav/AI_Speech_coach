plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.example.speechcoach.audio"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
}

dependencies {
    implementation(project(":practice-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
