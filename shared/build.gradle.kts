plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidLibrary {
        namespace = "com.example.speechcoach.shared"
        compileSdk = 36
        minSdk = 28
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
