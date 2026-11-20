import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.aistudio.skillsexchange.nxptvq"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // D1 fix: inject MAPS_API_KEY from .env (via Secrets Gradle Plugin) at build time.
    // An empty/missing key results in the map fallback UI, NOT a crash.
    // To enable Maps: add MAPS_API_KEY=<your_key> to .env (never commit a real key).
    manifestPlaceholders["MAPS_API_KEY"] = "DEFAULT_MAPS_API_KEY"

    // R06-P2-06: the Google web client id used to be a string literal inside
    // GoogleSignInHelper.kt. It is a project identifier, not a secret, but a literal in source
    // drifts the moment the project changes and cannot be set per build. It now comes from the
    // environment, a Gradle property, or the untracked `.env` file — in that order — and an empty
    // value simply disables the Google button instead of crashing.
    buildConfigField(
      "String",
      "GOOGLE_WEB_CLIENT_ID",
      "\"${googleWebClientId()}\""
    )

  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // R07-P2-05: release builds are shrunk and obfuscated. `proguard-rules.pro` keeps Room,
      // Firebase data models and Moshi types, which are the parts reflection/Room rely on.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
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
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // R05: the retry/formatting tests exercise code that calls android.util.Log; the default
      // "not mocked" stubs would throw instead of returning a value.
      isReturnDefaultValues = true
    }
  }

  // R04-P2-01: Room schema export. The JSON Room generates for every version is the contract the
  // migration tests validate against, so it is committed under `app/schemas` and never edited by
  // hand.
  sourceSets {
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
    getByName("test").assets.srcDir("$projectDir/schemas")
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<Test> {
  // R01-P2-01: one toolchain for compile and tests. Java 11 cannot run Robolectric's
  // Android sandbox (SDK 34+ requires 17), so the whole project pins 17.
  javaLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
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
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Firebase Firestore & Auth:
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.storage)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.functions)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation("com.google.android.gms:play-services-auth:20.5.0")
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  // R07-P2-04: App Check providers. Both artifacts ship in every variant: the debug provider is only
  // *installed* when BuildConfig.DEBUG, so release code never attests with it.
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.play.services.maps)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  implementation(libs.gson)
}

/**
 * R06-P2-06: resolves `GOOGLE_WEB_CLIENT_ID` without ever embedding a value in the repository.
 *
 * Order: environment variable, Gradle property (`-PGOOGLE_WEB_CLIENT_ID=...`), then `KEY=value` in
 * the untracked `.env` at the repository root. Returns an empty string when nothing is set, which
 * makes `GoogleSignInHelper.isConfigured` false and the button explains why.
 */
fun googleWebClientId(): String {
  val fromEnv = System.getenv("GOOGLE_WEB_CLIENT_ID")?.trim().orEmpty()
  if (fromEnv.isNotEmpty()) return fromEnv
  val fromProperty = (project.findProperty("GOOGLE_WEB_CLIENT_ID") as String?)?.trim().orEmpty()
  if (fromProperty.isNotEmpty()) return fromProperty
  val dotEnv = rootProject.file(".env")
  if (!dotEnv.exists()) return ""
  val line = dotEnv.readLines().firstOrNull { it.trim().startsWith("GOOGLE_WEB_CLIENT_ID=") }
    ?: return ""
  return line.substringAfter("=").trim().trim('"')
}
