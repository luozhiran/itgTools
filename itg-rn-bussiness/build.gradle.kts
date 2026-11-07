plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.itg.rnbussiness"
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
    api(project(":itg-rn-container"))
}

val rnNodeModules = providers.gradleProperty("rnNodeModulesDir")
    .map { rootProject.file(it) }
    .orElse(rootProject.file("node_modules"))
val rnAssets = file("src/main/assets")
val rnMetro = layout.buildDirectory.file("generated/rn/metro/index.android.bundle")
val rnBundle = tasks.register<Exec>("bundleItgRnRelease") {
    inputs.files(fileTree("js"), rootProject.file("package.json"), rootProject.file("metro.config.js"), rootProject.file("babel.config.js"))
    outputs.file(rnMetro)
    doFirst {
        val modules = rnNodeModules.get()
        val cli = modules.resolve("react-native/cli.js")
        require(cli.isFile) { "RN dependencies missing. Run npm ci at repository root or set -PrnNodeModulesDir=<path>/node_modules" }
        rnMetro.get().asFile.parentFile.mkdirs()
        rnAssets.mkdirs()
    }
    environment("ITG_RN_NODE_MODULES", rnNodeModules.get().absolutePath)
    commandLine("node", rnNodeModules.get().resolve("react-native/cli.js").absolutePath,
        "bundle", "--platform", "android", "--dev", "false", "--entry-file", file("js/index.js").absolutePath,
        "--config", rootProject.file("metro.config.js").absolutePath, "--bundle-output", rnMetro.get().asFile.absolutePath,
        "--assets-dest", rnAssets.absolutePath)
}
val platform = when {
    System.getProperty("os.name").startsWith("Mac") -> "osx-bin"
    System.getProperty("os.name").startsWith("Windows") -> "win64-bin"
    else -> "linux64-bin"
}
val hermesc = rnNodeModules.get().resolve("hermes-compiler/hermesc/$platform/hermesc${if (platform == "win64-bin") ".exe" else ""}")
val rnCompile = tasks.register<Exec>("compileItgRnHermesRelease") {
    dependsOn(rnBundle)
    inputs.file(rnMetro)
    outputs.file(rnAssets.resolve("index.android.bundle"))
    doFirst { require(hermesc.isFile) { "Hermes compiler missing: $hermesc" } }
    commandLine(hermesc.absolutePath, "-emit-binary", "-out",
        rnAssets.resolve("index.android.bundle").absolutePath, rnMetro.get().asFile.absolutePath)
}
tasks.matching { it.name == "mergeReleaseAssets" }.configureEach { dependsOn(rnCompile) }

afterEvaluate {
    publishing {
        repositories { maven { url = uri(layout.buildDirectory.dir("repo")) } }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.itg"
                artifactId = "itg-rn-bussiness"
                version = "0.1.0"
            }
        }
    }
}
