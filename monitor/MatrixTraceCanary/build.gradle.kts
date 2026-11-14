plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.itg.matrixtracecanary"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    api(libs.matrix.android.lib)
    api(libs.matrix.trace.canary)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                url = uri(layout.buildDirectory.dir("repo"))
            }
        }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.itg"
                artifactId = "matrix-trace-canary"
                version = "0.1.0"

                pom {
                    name = "ITG Matrix Trace Canary"
                    description = "Android library wrapping Tencent Matrix Trace runtime monitoring."
                    packaging = "aar"
                }
            }
        }
    }
}
