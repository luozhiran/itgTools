plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.itg.itg_coroutine_pools"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    publishing {
        repositories {
            maven { url = uri("${buildDir}/repo") }
        }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.itg"
                artifactId = "itg-coroutine-pools"
                version = "0.1.0"
                pom {
                    name = "ITG Coroutine Pools"
                    description = "A coroutine-based concurrency library for Android, API-compatible with itg-thread-pools."
                    packaging = "aar"
                }
            }
        }
    }
}
