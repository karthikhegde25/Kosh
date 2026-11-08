import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing: values come from keystore.properties (local, git-ignored) or from environment
// variables (CI). If neither is present the release APK is still built, just left unsigned.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

// Rules the release build always needs. They are written here, next to the build settings, so the build still works if
// proguard-rules.pro is ever out of date: optional PdfBox classes that aren't bundled, the JavaScript bridges the PDF
// viewer and editor call by name, and PdfBox's reflection-created encryption handlers.
val generatedR8Rules: File = layout.buildDirectory.file("kosh-generated-rules.pro").get().asFile.also { file ->
    file.parentFile.mkdirs()
    file.writeText(
        """
        -dontwarn com.gemalto.jp2.**
        -dontwarn org.bouncycastle.**
        -dontwarn javax.xml.stream.**
        -keep class com.tom_roush.pdfbox.pdmodel.encryption.** { *; }
        -keepattributes Signature,*Annotation*,JavascriptInterface
        -keep class com.kosh.android.viewer.WebInterface { *; }
        -keep class com.kosh.android.tools.EditBridge { *; }
        """.trimIndent() + "\n",
    )
}

android {
    namespace = "com.kosh.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kosh.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        // Keep only the languages the app actually ships. Libraries bundle strings for dozens of locales;
        // this drops the ones we don't translate. Add more codes here when Kosh gets translations.
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "KOSH_KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "KOSH_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KOSH_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KOSH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true      // R8: shrink, optimize and obfuscate code
            isShrinkResources = true    // remove resources nothing references
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                generatedR8Rules,
            )
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/versions/**",
                "/kotlin-tooling-metadata.json",
                "DebugProbesKt.bin",
            )
        }
    }

    // No Play dependency-metadata blob inside the APK/AAB.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Skip the runtime null-check calls Kotlin inserts into every function: smaller, faster bytecode.
        freeCompilerArgs += listOf("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    // Room writes its schema JSON here so future migrations can be tested against it.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Lossless PDF editing: merge, split, rotate, reorder, page numbers, encrypt and decrypt (Apache 2.0).
    implementation(libs.pdfbox.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
