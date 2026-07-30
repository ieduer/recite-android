plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseKeystorePath = providers.environmentVariable("RECITE_ANDROID_KEYSTORE_PATH")
    .orElse(providers.environmentVariable("BDFZ_ANDROID_KEYSTORE_PATH"))
val releaseStorePassword = providers.environmentVariable("RECITE_ANDROID_KEYSTORE_PASSWORD")
    .orElse(providers.environmentVariable("BDFZ_ANDROID_KEYSTORE_PASSWORD"))
val releaseKeyAlias = providers.environmentVariable("RECITE_ANDROID_KEY_ALIAS")
    .orElse(providers.environmentVariable("BDFZ_ANDROID_KEY_ALIAS"))
val releaseKeyPassword = providers.environmentVariable("RECITE_ANDROID_KEY_PASSWORD")
    .orElse(providers.environmentVariable("BDFZ_ANDROID_KEY_PASSWORD"))
val releaseSigningReady = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isPresent }

android {
    namespace = "net.bdfz.recite"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.bdfz.recite.direct"
        minSdk = 23
        targetSdk = 37
        versionCode = 4
        versionName = "0.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "USER_CENTER_URL", "\"https://my.bdfz.net\"")
        buildConfigField("String", "RECITE_API_URL", "\"https://recite.bdfz.net\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://img.bdfz.net/apps/recite-android/latest.json\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("direct") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystorePath.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
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
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.4.0"))
    implementation("com.squareup.okhttp3:okhttp")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.10")
    testImplementation("org.json:json:20260719")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
