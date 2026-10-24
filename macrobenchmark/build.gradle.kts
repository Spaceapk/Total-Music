plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

/*
 * Separate, self-instrumenting Macrobenchmark module. It targets the app's
 * `prodMeasurement` variant through the dimension fallback below; it never
 * targets the historical `.benchmark` package or changes the app signing.
 */
android {
    namespace = "com.music.bitchord.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("env", "prod")
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("measurement") {
            // com.android.test supplies debug by default, not release. The
            // target app's release-like variant is selected by the fallback;
            // no release build type is needed in this test-only module.
            matchingFallbacks += listOf("release")
            // Only the test APK is debug-signed. The target app remains the
            // non-debuggable, release-signed prodMeasurement variant.
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
