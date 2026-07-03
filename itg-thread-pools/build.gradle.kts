plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.itg.itg_thread_pools"
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                url = uri("${buildDir}/repo")
            }
        }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = project.findProperty("group") as String? ?: "com.itg"
                artifactId = "itg-thread-pools"
                version = project.findProperty("version") as String? ?: "0.1.0"

                pom {
                    name = "ITG Net"
                    description = "A lightweight Android networking and download library built on OkHttp."
                    packaging = "aar"
                }
            }
        }
    }
}