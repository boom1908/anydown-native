import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.chaquopy)
}

android {
  namespace = "com.boom.anydown"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.boom.anydown"
    minSdk = 24
    targetSdk = 34
    versionCode = 3
    versionName = "3.0.0"

    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val jksFile = file("anydown-release.jks").let { if (it.exists()) it else file("${rootDir}/anydown-release.jks") }
      storeFile = jksFile
      storePassword = "anydown2026"
      keyAlias = "anydown"
      keyPassword = "anydown2026"
    }
    create("debugConfig") {
      val jksFile = file("anydown-release.jks").let { if (it.exists()) it else file("${rootDir}/anydown-release.jks") }
      storeFile = jksFile
      storePassword = "anydown2026"
      keyAlias = "anydown"
      keyPassword = "anydown2026"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

val assembleNativeLibs by tasks.registering {
  doLast {
    val jniTarget = file("src/main/jniLibs/arm64-v8a/libffmpeg.so")
    val jniLibCpp = file("src/main/jniLibs/arm64-v8a/libc++_shared.so")
    val partsDir = file("src/main/native-parts/arm64-v8a")
    if (!jniTarget.exists() || jniTarget.length() == 0L) {
      val parts = partsDir.listFiles { _, name -> name.startsWith("libffmpeg.so.part-") }?.sortedBy { it.name }
      if (!parts.isNullOrEmpty()) {
        jniTarget.parentFile.mkdirs()
        jniTarget.outputStream().use { out ->
          parts.forEach { part -> part.inputStream().use { it.copyTo(out) } }
        }
      }
    }
    val partsLibCpp = File(partsDir, "libc++_shared.so")
    if ((!jniLibCpp.exists() || jniLibCpp.length() == 0L) && partsLibCpp.exists()) {
      jniLibCpp.parentFile.mkdirs()
      partsLibCpp.copyTo(jniLibCpp, overwrite = true)
    }
  }
}

tasks.named("preBuild") {
  dependsOn(assembleNativeLibs)
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation("com.google.code.gson:gson:2.10.1")
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

chaquopy {
  defaultConfig {
    version = "3.11"
    pip {
      install("yt-dlp")
    }
  }
}
