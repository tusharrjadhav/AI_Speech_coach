// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  id("org.jetbrains.kotlin.multiplatform") version "2.3.20" apply false
  id("com.android.kotlin.multiplatform.library") version "9.0.1" apply false
  id("com.android.library") version "9.0.1" apply false
  id("org.jetbrains.compose") version "1.11.1" apply false
}
