plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.itg.concurrent"
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

    // compileOnly: 编译时需要，运行时由 app 选择提供
    compileOnly(project(":itg-thread-pools"))
    compileOnly(project(":itg-coroutine-pools"))

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
                artifactId = "itg-concurrent-core"
                version = "0.1.0"
                pom {
                    name = "ITG Concurrent Core"
                    description = "Unified concurrency middleware for Android — switch between thread-pool and coroutine backends."
                    packaging = "aar"
                }
            }
        }
    }
}
