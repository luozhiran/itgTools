pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "cn.therouter") {
                useModule("cn.therouter:plugin:${requested.version}")
            }
        }
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven { url = uri("https://maven.therouter.cn:8443/repository/maven-public/") }
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
        maven { url = uri("https://maven.therouter.cn:8443/repository/maven-public/") }
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
include(":itg-string")
include(":itg-ui")
include(":itg-ksp-annotations")
include(":itg-ksp-runtime")
include(":itg-ksp-compiler")
include(":itg-coroutine-pools")
include(":itg-concurrent-core")
include(":itg-dialog")
