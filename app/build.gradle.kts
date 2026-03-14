plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val rimeAssetsSourceDir = rootProject.projectDir.resolve("../threply/ThreplyKeyboard/RimeResources")
val generatedRimeAssetsDir = layout.buildDirectory.dir("generated/rimeAssets")

val syncRimeAssets by tasks.registering(Copy::class) {
    from(rimeAssetsSourceDir)
    into(generatedRimeAssetsDir.map { it.dir("rime/shared") })
    includeEmptyDirs = false
    exclude(".git/**", ".github/**", ".gitignore", "README*", "**/.DS_Store")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(syncRimeAssets)
}

android {
    namespace = "com.arche.threply"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.arche.threply"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"REPLACE_WITH_GOOGLE_WEB_CLIENT_ID\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main").assets.srcDir(generatedRimeAssetsDir)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    // Compose Core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Security (Encrypted SharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google Sign-In (Credential Manager)
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:6.2.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ML Kit Text Recognition (Chinese + Latin, on-device)
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
