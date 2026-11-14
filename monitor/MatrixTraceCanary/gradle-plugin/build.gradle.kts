plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.itg"
version = "0.1.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly("com.android.tools.build:gradle-api:9.2.1")
    implementation("org.ow2.asm:asm-commons:9.9")
}

gradlePlugin {
    plugins {
        create("matrixSlowMethod") {
            id = "com.itg.matrix-slow-method"
            implementationClass = "com.itg.matrixtracecanary.gradle.SlowMethodPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
