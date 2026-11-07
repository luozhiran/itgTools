plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.itg.rncontainer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.keep")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing { singleVariant("release") { withSourcesJar() } }

}

dependencies {
    api(rootProject.extra["rnReactCoordinate"] as String)
    api(libs.androidx.fragment)
    implementation(rootProject.extra["rnHermesCoordinate"] as String)
    implementation(libs.androidx.appcompat)
}

afterEvaluate {
    publishing {
        repositories { maven { url = uri(layout.buildDirectory.dir("repo")) } }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.itg"
                artifactId = "itg-rn-container"
                version = "0.1.0"
            }
        }
    }
}
