plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.itg.outter"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    api (project(":itg-file"))
    api (project(":itg-bitmap"))
    api (project(":itg-encrypt"))
    api (project(":itg-thread-pools"))
    api(project(":itg-verification"))
    api(project(":itg-base"))
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
                groupId = "com.itg"
                artifactId = "itg-outter"
                version = "0.1.0"

                pom {
                    name = "ITG Net"
                    description = "A lightweight Android networking and download library built on OkHttp."
                    packaging = "aar"
                }
            }
        }
    }
}