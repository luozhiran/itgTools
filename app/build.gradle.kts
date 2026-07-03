plugins {
    alias(libs.plugins.android.application)
    // id("therouter")  // 暂时移除
    // id("com.google.devtools.ksp")  // 暂时移除：与 AGP 降级相关的 KSP 版本调整中
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
    buildFeatures {
        dataBinding = true
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
    // implementation(libs.therouter.router)  // 暂时移除
    // ksp(libs.therouter.apt)  // 暂时移除
}

// JitPack 兼容：outter 是纯聚合模块（无 src/main 源码），其 AAR 的 classes.jar 为空，
// AGP 8.x 的 Baseline Profile 任务 compileReleaseArtProfile 读取空 AAR 会报错。
// 该项目不使用 Baseline Profile，禁用此任务不影响功能。
afterEvaluate {
    tasks.matching { it.name.contains("compileArtProfile", ignoreCase = true) }.configureEach {
        enabled = false
    }
}