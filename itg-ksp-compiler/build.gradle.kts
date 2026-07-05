plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(project(":itg-ksp-annotations"))
    implementation(libs.ksp.symbol.processing.api)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}
