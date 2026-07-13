plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("therouter")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.itg.itgtools"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.itg.itgtools"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
    sourceSets {
        getByName("debug").kotlin.srcDir("build/generated/ksp/debug/kotlin")
        getByName("release").kotlin.srcDir("build/generated/ksp/release/kotlin")
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation(project(":outter"))
    implementation(project(":itg-ui"))
    implementation(project(":itg-coroutine-pools"))
    implementation(project(":itg-concurrent-core"))
    implementation(project(":itg-ksp-annotations"))
    implementation(project(":itg-ksp-runtime"))
    implementation(libs.therouter.router)
    ksp(libs.therouter.apt)
    ksp(project(":itg-ksp-compiler"))
}

// JitPack 兼容：outter 是纯聚合模块（无 src/main 源码），其 AAR 的 classes.jar 为空，
// AGP 8.x 的 Baseline Profile 任务 compileReleaseArtProfile 读取空 AAR 会报错。
// 该项目不使用 Baseline Profile，禁用此任务不影响功能。
afterEvaluate {
    tasks.matching { it.name.contains("compileArtProfile", ignoreCase = true) }.configureEach {
        enabled = false
    }
}