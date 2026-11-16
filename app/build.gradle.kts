plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun signingValue(name: String): String? =
    providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull

val releaseKeystore = signingValue("ANCHOR_KEYSTORE_FILE")

android {
    namespace = "app.anchor"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.anchor"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("ANCHOR_KEYSTORE_PASSWORD")
                keyAlias = signingValue("ANCHOR_KEY_ALIAS")
                keyPassword = signingValue("ANCHOR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "STRICT", "false")
        }
        release {
            buildConfigField("boolean", "STRICT", "true")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
            isMinifyEnabled = false
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
        unitTests.isIncludeAndroidResources = true
    }
    if (project.hasProperty("recordScreenshots")) testBuildType = "release"
}

// Screenshots for the README: ./gradlew testReleaseUnitTest -PrecordScreenshots
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("recordScreenshots")) {
        exclude("**/ScreenshotTest*")
        failOnNoDiscoveredTests = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.79.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.79.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
