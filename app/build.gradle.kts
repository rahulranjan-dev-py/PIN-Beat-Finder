import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Release signing credentials, resolved in this order:
 *  1. Environment variables (CI): KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
 *  2. A git-ignored `keystore.properties` at the repository root (local builds), with keys
 *     storeFile, storePassword, keyAlias, keyPassword.
 * When neither is present `assembleRelease` still works but is signed with the debug key so the
 * APK stays installable for testing; the build prints a warning so it is never mistaken for a
 * store-ready artifact.
 */
data class ReleaseSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

val releaseSigning: ReleaseSigning? = run {
    val env = System.getenv()
    val fromEnv = env["KEYSTORE_FILE"]?.takeIf { it.isNotBlank() }?.let { path ->
        ReleaseSigning(
            storeFile = rootProject.file(path),
            storePassword = env["KEYSTORE_PASSWORD"].orEmpty(),
            keyAlias = env["KEY_ALIAS"].orEmpty(),
            keyPassword = env["KEY_PASSWORD"].orEmpty(),
        )
    }
    val propsFile = rootProject.file("keystore.properties")
    val fromFile = if (fromEnv == null && propsFile.exists()) {
        val props = Properties().apply { propsFile.inputStream().use(::load) }
        ReleaseSigning(
            storeFile = rootProject.file(props.getProperty("storeFile", "")),
            storePassword = props.getProperty("storePassword", ""),
            keyAlias = props.getProperty("keyAlias", ""),
            keyPassword = props.getProperty("keyPassword", ""),
        )
    } else null
    (fromEnv ?: fromFile)?.takeIf { cfg ->
        val ok = cfg.storeFile.isFile && cfg.storePassword.isNotEmpty() && cfg.keyAlias.isNotEmpty()
        if (!ok) logger.warn("Release signing config found but incomplete (missing keystore file, password or alias); ignoring it.")
        ok
    }
}

android {
    namespace = "com.pinbeatfinder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pinbeatfinder"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // data.gov.in key for the official Department of Posts directory. The public sample key
        // works but is shared and throttled; set DATA_GOV_IN_API_KEY (env) or dataGovInApiKey
        // (gradle.properties / -P) to use your own free key from https://data.gov.in.
        val dataGovInApiKey = System.getenv("DATA_GOV_IN_API_KEY")?.takeIf { it.isNotBlank() }
            ?: (project.findProperty("dataGovInApiKey") as String?)?.takeIf { it.isNotBlank() }
            ?: "579b464db66ec23bdd000001cdd3946e44ce4aad7209ff7b23ac571b"
        buildConfigField("String", "DATA_GOV_IN_API_KEY", "\"$dataGovInApiKey\"")
    }

    signingConfigs {
        releaseSigning?.let { cfg ->
            create("release") {
                storeFile = cfg.storeFile
                storePassword = cfg.storePassword
                keyAlias = cfg.keyAlias
                keyPassword = cfg.keyPassword.ifEmpty { cfg.storePassword }
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val cfg = releaseSigning
            signingConfig = if (cfg != null) {
                logger.lifecycle("Release build: signing with keystore ${cfg.storeFile.name} (alias ${cfg.keyAlias}).")
                signingConfigs.getByName("release")
            } else {
                logger.warn("Release build: NO release keystore configured; signing with the DEBUG key. Not suitable for Google Play.")
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // FastExcel / Aalto / commons-compress ship META-INF entries that clash when merged.
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/versions/9/**",
                "META-INF/*.kotlin_module",
                "META-INF/services/javax.xml.stream.*",
                "META-INF/INDEX.LIST",
                "/META-INF/{AL2.0,LGPL2.1}",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

room {
    // Exported schemas make Room migrations verifiable and are committed with the code.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose (versions from the BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Room (KSP)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines & serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Phonetic matching (Double Metaphone)
    implementation(libs.commons.codec)

    // Excel (.xlsx) read/write. FastExcel is a streaming, POI-free implementation that is
    // light enough for Android. Its reader is built on Aalto (a StAX parser); Android does not
    // ship javax.xml.stream, so the StAX API jar is bundled explicitly.
    implementation(libs.fastexcel)
    implementation(libs.fastexcel.reader)
    implementation(libs.stax.api)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
