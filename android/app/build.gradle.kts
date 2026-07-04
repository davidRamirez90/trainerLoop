plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.kapt")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.trainerloop.app"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.trainerloop.app"
    minSdk = 30
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  testOptions {
    unitTests {
      // android.util.Log.d(...) is called by BleLog and would otherwise
      // throw in JVM unit tests. Returning default values makes Log.d
      // a no-op on the test classpath.
      isReturnDefaultValues = true
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.15"
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(platform("androidx.compose:compose-bom:2024.12.01"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.navigation:navigation-compose:2.8.5")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation("androidx.room:room-runtime:2.6.1")
  kapt("androidx.room:room-compiler:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")

  testImplementation("junit:junit:4.13.2")
  testImplementation("io.mockk:mockk:1.13.13")
  testImplementation("app.cash.turbine:turbine:1.2.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
  androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-tooling")
}
