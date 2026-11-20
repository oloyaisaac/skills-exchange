import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  // R11-P2-05: coverage is measured, not assumed. `jacoco` is a core Gradle plugin (no version).
  jacoco
}

android {
  namespace = "com.example"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.aistudio.skillsexchange.nxptvq"
    minSdk = 24
    targetSdk = 36
    // R14-P2-05: version from `version.properties`, overridable per build. A placeholder "1.0"/1 is
    // still a placeholder; what matters is that one file owns it, the format is validated, and CI can
    // stamp a build without editing source.
    versionCode = appVersionCode()
    versionName = appVersionName()

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
      // R14-P2-05: a missing keystore used to surface as `validateSigningRelease: Keystore file not
      // found` deep in a release build, which reads like a corruption rather than a missing key. The
      // config is now only applied when the key exists, and the task-graph check below refuses a
      // release build without one, with a sentence that says what to do.
      val keystoreFile = releaseKeystoreFile()
      if (keystoreFile.exists()) {
        storeFile = keystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
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
  // R14-P2-02: Android Lint is wired into CI (`./gradlew :app:lintDebug`) and its XML report is gated by
  // `scripts/check_lint_report.mjs`. `abortOnError = false` is deliberate: the gate is the script, which
  // can distinguish an allowlisted rule with a written reason from a new error, and which reports
  // warnings without failing on them.
  lint {
    abortOnError = false
    checkReleaseBuilds = false
    xmlReport = true
    htmlReport = true
    xmlOutput = file("build/reports/lint-results-debug.xml")
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

// R11-P2-05: unit-test coverage for the Android client.
//
// The reviewer's finding was that nothing measured how much of the client the JVM tests actually
// exercise, so "the suite is big" stood in for "the suite covers the risk". `:app:jacocoTestReport`
// writes the numbers and `:app:jacocoTestCoverageVerification` fails the build below the floor.
jacoco {
  // 0.8.12 understands Java 21 class files; the Gradle default predates the toolchain.
  toolVersion = "0.8.12"
}

tasks.withType<Test>().configureEach {
  extensions.configure<org.gradle.testing.jacoco.plugins.JacocoTaskExtension> {
    // Without this the agent is never attached and every report would claim 0%.
    isEnabled = true
  }
}

fun jacocoClasses(): FileCollection {
  val kotlinClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile)
  val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes").get().asFile)
  return files(kotlinClasses, javaClasses).filter { file ->
    val path = file.path
    // Generated, not written by hand: no test can meaningfully cover it, and including it would make
    // the number depend on how much code the build generates.
    !path.endsWith("R.class") &&
      !path.contains("/R$") &&
      !path.contains("BuildConfig") &&
      !path.contains("_Factory") &&
      !path.contains("_Impl") &&
      !path.contains("ComposableSingletons") &&
      !path.contains("hilt_aggregated_deps")
  }
}

tasks.register<org.gradle.testing.jacoco.tasks.JacocoReport>("jacocoTestReport") {
  group = "verification"
  description = "Writes unit-test coverage (XML + HTML) for the debug variant."
  dependsOn("testDebugUnitTest")
  classDirectories.setFrom(jacocoClasses())
  sourceDirectories.setFrom(files("src/main/java"))
  executionData.setFrom(files(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")))
  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(false)
  }
}

tasks.register<org.gradle.testing.jacoco.tasks.JacocoCoverageVerification>("jacocoTestCoverageVerification") {
  group = "verification"
  description = "Fails when unit-test coverage falls below the floor."
  dependsOn("testDebugUnitTest")
  classDirectories.setFrom(jacocoClasses())
  sourceDirectories.setFrom(files("src/main/java"))
  executionData.setFrom(files(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")))
  violationRules {
    rule {
      // A floor, not a target: it catches a collapse (a suite that stops running, a module that stops
      // being exercised), and sits below today's measured value so ordinary refactoring stays green.
      // The measured number is published as a CI artifact.
      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = "0.20".toBigDecimal()
      }
    }
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
/**
 * R14-P2-05: `KEYSTORE_PATH` wins when set (CI points it at a throwaway key), otherwise the
 * conventional root-level upload key.
 */
fun releaseKeystoreFile(): java.io.File {
  val configured = System.getenv("KEYSTORE_PATH")?.trim().orEmpty()
  val path = if (configured.isNotEmpty()) configured else "${rootDir}/my-upload-key.jks"
  return file(path)
}

/** Reads one key from `version.properties`; a missing file is a build error, not a default. */
fun versionProperty(key: String): String {
  val file = rootProject.file("version.properties")
  require(file.exists()) {
    "version.properties is missing: it is the single source of truth for versionCode and versionName."
  }
  val line = file.readLines()
    .map { it.trim() }
    .firstOrNull { it.startsWith("$key=") }
    ?: error("version.properties has no $key= line.")
  return line.substringAfter("=").trim()
}

fun appVersionCode(): Int {
  val raw = System.getenv("VERSION_CODE")?.trim().takeUnless { it.isNullOrEmpty() } ?: versionProperty("VERSION_CODE")
  val code = raw.toIntOrNull()
  require(code != null && code > 0) {
    "versionCode must be a positive integer, got '$raw'. A re-used or zero code is rejected by Play."
  }
  return code
}

fun appVersionName(): String {
  val raw = System.getenv("VERSION_NAME")?.trim().takeUnless { it.isNullOrEmpty() } ?: versionProperty("VERSION_NAME")
  require(Regex("^\\d+\\.\\d+\\.\\d+$").matches(raw)) {
    "versionName must be MAJOR.MINOR.PATCH, got '$raw'."
  }
  return raw
}

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

// R14-P2-05: a release build without a signing key fails here, at configuration time, with a readable
// message — instead of at `validateSigningRelease` inside a task that has already done work.
gradle.taskGraph.whenReady {
  val wantsRelease = allTasks.any { it.name.contains("Release") }
  if (wantsRelease && !releaseKeystoreFile().exists()) {
    throw GradleException(
      "Release signing key not found at ${releaseKeystoreFile().absolutePath}. " +
        "Set KEYSTORE_PATH (and STORE_PASSWORD / KEY_PASSWORD) or place my-upload-key.jks in the " +
        "repository root. Debug and unit-test builds do not need it."
    )
  }
}
