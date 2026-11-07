// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.therouter.classpath) apply false
    alias(libs.plugins.ksp.classpath) apply false
}

// Keep Android artifacts aligned with the RN installation used to bundle business JS.
// An external node_modules directory can be supplied during local migration/verification.
val rnModules = providers.gradleProperty("rnNodeModulesDir")
    .map { file(it) }
    .orElse(file("node_modules"))
    .get()
val rnAndroidProperties = rnModules.resolve("react-native/ReactAndroid/gradle.properties")
val rnHermesProperties = rnModules.resolve("react-native/sdks/hermes-engine/version.properties")
val rnPackageJson = file("package.json").readText()
fun declaredVersion(name: String): String =
    Regex("\"${Regex.escape(name)}\"\\s*:\\s*\"([^\"]+)\"")
        .find(rnPackageJson)?.groupValues?.get(1)
        ?: error("Missing $name in root package.json")
val reactProps = java.util.Properties().apply {
    if (rnAndroidProperties.isFile) rnAndroidProperties.inputStream().use(::load)
}
val hermesProps = java.util.Properties().apply {
    if (rnHermesProperties.isFile) rnHermesProperties.inputStream().use(::load)
}
val reactVersion = reactProps.getProperty("VERSION_NAME") ?: declaredVersion("react-native")
val hermesVersion = hermesProps.getProperty("HERMES_V1_VERSION_NAME") ?: declaredVersion("hermes-compiler")
require(reactVersion == declaredVersion("react-native")) { "Installed react-native version differs from root package.json" }
require(hermesVersion == declaredVersion("hermes-compiler")) { "Installed Hermes compiler version differs from root package.json" }
extra["rnReactCoordinate"] = "${reactProps.getProperty("react.internal.publishingGroup", "com.facebook.react")}:react-android:$reactVersion"
extra["rnHermesCoordinate"] = "${reactProps.getProperty("react.internal.hermesPublishingGroup", "com.facebook.hermes")}:hermes-android:$hermesVersion"
