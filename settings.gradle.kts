pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
// foojay 插件在受限网络/JitPack 环境无法下载 JDK，改用本地安装的 JDK。
// 本地 JDK 路径由 gradle.properties 中的 org.gradle.java.installations.paths 指定。
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
// }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ItgTools"
include(":app")
include(":itg-bitmap")
include(":itg-file")
include(":itg-encrypt")

include(":outter")
include(":itg-thread-pools")
include(":itg-verification")
include(":itg-base")
