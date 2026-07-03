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