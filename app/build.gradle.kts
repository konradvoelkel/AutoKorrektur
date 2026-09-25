import com.android.build.api.artifact.SingleArtifact
import java.util.Properties
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    id("jacoco")
}

android {
    namespace = "de.konradvoelkel.android.autokorrektur"
    compileSdk = 37

    val gitCommitCountProvider = providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { text ->
        text.trim().toIntOrNull() ?: 170
    }

    val gitVersionNameProvider = providers.exec {
        commandLine("git", "describe", "--tags", "--always")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { text ->
        // Tags are written v2.0.0; the version name users see should not carry the "v".
        val trimmed = text.trim().removePrefix("v")
        if (trimmed.isNotEmpty()) trimmed else "2.0.0"
    }

    defaultConfig {
        applicationId = "de.konradvoelkel.android.autokorrektur"
        minSdk = 29
        targetSdk = 36
        versionCode = gitCommitCountProvider.getOrElse(170)
        versionName = gitVersionNameProvider.getOrElse("2.0.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // MVP tier feature flags (see docs/PRODUCT_TIERS.md). `core` is the only flavor
    // that ever goes to the public Play Store listing; plus/beta/full are internal/opt-in-tester
    // builds distributed as direct APKs. `full` reproduces today's pre-flavor app exactly (all
    // flags true, all 4 ABIs) so it stays the CI/dev baseline with no coverage regression.
    flavorDimensions += "scope"
    productFlavors {
        create("core") {
            dimension = "scope"
            buildConfigField("boolean", "FEATURE_LIVE_AR", "false")
            buildConfigField("boolean", "FEATURE_VIDEO_SNIPPETS", "false")
            buildConfigField("boolean", "FEATURE_CLOUD_SDXL", "false")
            buildConfigField("boolean", "FEATURE_HIGH_RES_PROGRESSIVE", "false")
            buildConfigField("boolean", "FEATURE_MANUAL_MASK_BRUSH", "false")
            buildConfigField("boolean", "FEATURE_BATCH_PROCESSING", "false")
            buildConfigField("boolean", "FEATURE_EXTRA_EXPORT_LAYOUTS", "false")
            // arm64 only for the Play build. -PscreenshotAbi=x86_64 swaps it for emulator work
            // (store screenshots, UI checks) — never pass it when building a release bundle.
            ndk { abiFilters += (project.findProperty("screenshotAbi") as String? ?: "arm64-v8a") }
            // no applicationIdSuffix: this is "the app" as far as Play/users are concerned
        }
        create("plus") {
            dimension = "scope"
            applicationIdSuffix = ".plus"
            buildConfigField("boolean", "FEATURE_LIVE_AR", "false")
            buildConfigField("boolean", "FEATURE_VIDEO_SNIPPETS", "false")
            buildConfigField("boolean", "FEATURE_CLOUD_SDXL", "false")
            buildConfigField("boolean", "FEATURE_HIGH_RES_PROGRESSIVE", "false")
            buildConfigField("boolean", "FEATURE_MANUAL_MASK_BRUSH", "false")
            buildConfigField("boolean", "FEATURE_BATCH_PROCESSING", "false")
            buildConfigField("boolean", "FEATURE_EXTRA_EXPORT_LAYOUTS", "true")
            ndk { abiFilters += "arm64-v8a" }
        }
        create("beta") {
            dimension = "scope"
            applicationIdSuffix = ".beta"
            buildConfigField("boolean", "FEATURE_LIVE_AR", "false")
            buildConfigField("boolean", "FEATURE_VIDEO_SNIPPETS", "false")
            buildConfigField("boolean", "FEATURE_CLOUD_SDXL", "true")
            buildConfigField("boolean", "FEATURE_HIGH_RES_PROGRESSIVE", "true")
            buildConfigField("boolean", "FEATURE_MANUAL_MASK_BRUSH", "true")
            buildConfigField("boolean", "FEATURE_BATCH_PROCESSING", "true")
            buildConfigField("boolean", "FEATURE_EXTRA_EXPORT_LAYOUTS", "true")
            ndk { abiFilters += "arm64-v8a" }
        }
        create("full") {
            dimension = "scope"
            applicationIdSuffix = ".full"
            buildConfigField("boolean", "FEATURE_LIVE_AR", "true")
            buildConfigField("boolean", "FEATURE_VIDEO_SNIPPETS", "true")
            buildConfigField("boolean", "FEATURE_CLOUD_SDXL", "true")
            buildConfigField("boolean", "FEATURE_HIGH_RES_PROGRESSIVE", "true")
            buildConfigField("boolean", "FEATURE_MANUAL_MASK_BRUSH", "true")
            buildConfigField("boolean", "FEATURE_BATCH_PROCESSING", "true")
            buildConfigField("boolean", "FEATURE_EXTRA_EXPORT_LAYOUTS", "true")
            // no abiFilters override -> all 4 ABIs, for emulators/dev
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val hasReleaseKeystore = keystorePropertiesFile.exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val properties = Properties()
                keystorePropertiesFile.inputStream().use { properties.load(it) }
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
            buildConfigField("String", "BACKEND_URL", "\"http://127.0.0.1:8000/v1/inpaint\"")
            // Evaluation-mode dev sliders (mask upscale/downshift, score threshold, model
            // chooser) — never a real end-user feature, so this stays debug-only regardless
            // of tier flavor.
            buildConfigField("boolean", "FEATURE_EVALUATION_MODE", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            buildConfigField(
                "String",
                "BACKEND_URL",
                "\"https://api.autokorrektur.example.com/v1/inpaint\""
            )
            buildConfigField("boolean", "FEATURE_EVALUATION_MODE", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        // Pre-existing debt baselined so CI isn't blocked by it; new lint issues introduced
        // later still fail CI. (The 119 MissingTranslation entries the baseline used to carry
        // went away on 2026-09-21 when values/strings.xml became purely English with a complete
        // values-de override — StringResourceLocalizationTest now enforces that invariant.)
        baseline = file("lint-baseline.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.exifinterface)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.onnxruntime.android)
    implementation(libs.tensorflow.lite)
    implementation(libs.opencv)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)

    // JVM unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.mockkandroid)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestUtil(libs.androidx.test.orchestrator)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
}

// The large build inputs (ML models, instrumented-test fixtures) are not in git; they are
// downloaded by scripts/fetch_assets.sh from the hashes in scripts/assets.manifest. Without this
// check a fresh clone fails deep inside the build (a missing asset surfaces as an aapt error or,
// worse, at runtime), so fail early with the one command that fixes it. Existence only — the
// script owns hash verification, and hashing ~200 MB on every build would be pointless overhead.
val verifyAssets = tasks.register("verifyAssets") {
    group = "verification"
    description = "Fails if the assets listed in scripts/assets.manifest are missing (run scripts/fetch_assets.sh)."
    val manifestFile = rootProject.file("scripts/assets.manifest")
    val repoRoot = rootProject.projectDir
    inputs.file(manifestFile)
    outputs.upToDateWhen { false }
    doLast {
        val missing = manifestFile.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val fields = line.split(Regex("\\s+"))
                if (fields.size < 4) return@mapNotNull null
                val (kind, dest) = fields
                val target = File(repoRoot, dest)
                val present = when (kind) {
                    "file" -> target.isFile && target.length() > 0
                    // An archive is installed iff fetch_assets.sh left its stamp behind.
                    "archive" -> File(target, ".assets-stamp").isFile
                    else -> true
                }
                if (present) null else dest
            }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing build assets:\n" + missing.joinToString("\n") { "  $it" } +
                    "\n\nThese are kept out of git (see scripts/assets.manifest). Fetch them with:" +
                    "\n  scripts/fetch_assets.sh\n"
            )
        }
    }
}

tasks.named("preBuild") { dependsOn(verifyAssets) }

// What each flavor is allowed to ask the user's device for, checked against the MERGED manifest --
// dependencies contribute permissions of their own, and Play prints the merged set on the store
// page (androidx.work once put ACCESS_NETWORK_STATE next to a policy promising no network).
// Exact equality, not a blocklist: an unexplained new permission and a silently vanished one are
// both bugs, and the vanished one is how a privacy promise quietly becomes a lie in the other
// direction. `{applicationId}` stands for the variant's own id, which carries the flavor suffix.
val basePermissions = setOf(
    "android.permission.CAMERA",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "{applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

// beta/full turn on cloud SDXL (INTERNET), video (READ_MEDIA_VIDEO/AUDIO) and batch processing,
// whose WorkManager brings the remaining four. core/plus remove all of them in their manifests.
val networkedPermissions = basePermissions + setOf(
    "android.permission.INTERNET",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.WAKE_LOCK",
)

val permissionAllowlist = mapOf(
    "core" to basePermissions,
    "plus" to basePermissions,
    "beta" to networkedPermissions,
    "full" to networkedPermissions,
)

abstract class VerifyPermissionsTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowed: SetProperty<String>

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun verify() {
        val xml = mergedManifest.get().asFile.readText()
        val found = Regex("""<uses-permission[^>]*android:name="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml).map { it.groupValues[1] }.toSortedSet()
        val expected = allowed.get().map { it.replace("{applicationId}", applicationId.get()) }.toSortedSet()

        val added = found - expected
        val missing = expected - found
        if (added.isNotEmpty() || missing.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Permission set of ${variantName.get()} does not match its allowlist.")
                    if (added.isNotEmpty()) {
                        appendLine("  unexpected (a dependency added these; remove them in the flavor manifest")
                        appendLine("  with tools:node=\"remove\", or widen the allowlist deliberately):")
                        added.forEach { appendLine("    + $it") }
                    }
                    if (missing.isNotEmpty()) {
                        appendLine("  gone (a feature that needs one of these will fail at runtime):")
                        missing.forEach { appendLine("    - $it") }
                    }
                    appendLine("  allowlist: app/build.gradle.kts, permissionAllowlist")
                    append("  merged manifest: ${mergedManifest.get().asFile}")
                }
            )
        }
        stamp.get().asFile.writeText(found.joinToString("\n", postfix = "\n"))
    }
}

androidComponents {
    onVariants { variant ->
        val allowedForFlavor = permissionAllowlist[variant.flavorName] ?: return@onVariants
        val verify = tasks.register<VerifyPermissionsTask>("verify${variant.name.replaceFirstChar(Char::titlecase)}Permissions") {
            group = "verification"
            description = "Fails if ${variant.name} requests a different permission set than its allowlist."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowed.set(allowedForFlavor)
            applicationId.set(variant.applicationId)
            variantName.set(variant.name)
            stamp.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
        }
        // Wired into the two ways an artifact leaves this project, plus `check`, so it is not a
        // task someone has to remember to run.
        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar(Char::titlecase)}" }
            .configureEach { dependsOn(verify) }
        tasks.matching { it.name == "bundle${variant.name.replaceFirstChar(Char::titlecase)}" }
            .configureEach { dependsOn(verify) }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(verify) }
    }
}

// Coverage is measured against the "full" flavor specifically: it's the only flavor that
// exercises every code path (all FEATURE_* flags true), and product flavors don't have a
// meaningful combined/aggregate coverage report the way a single-variant project would.
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testFullDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "androidx/**/*.*"
    )
    val debugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/fullDebug") {
        exclude(fileFilter)
    }
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(layout.buildDirectory.get()) {
        include("outputs/unit_test_code_coverage/fullDebugUnitTest/testFullDebugUnitTest.exec")
    })
}
