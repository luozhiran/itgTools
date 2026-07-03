// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // alias(libs.plugins.therouter.classpath) apply false  // 暂时移除：不兼容当前 Kotlin 版本
    // alias(libs.plugins.ksp.classpath) apply false  // 暂时移除：与 AGP 降级相关的 KSP 版本调整中
}