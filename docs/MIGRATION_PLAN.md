# new/release → 当前分支 迁移方案

## 概述

将 `new/release` 分支上新增的 **itg-coroutine-pools** 和 **itg-concurrent-core** 两个模块迁移到当前分支，同时兼容 `minSdk = 21`。

---

## 一、环境差异分析

| 配置项 | new/release | 当前分支 | 影响 |
|--------|:----------:|:------:|------|
| AGP | 9.2.1 | **8.2.2** | `compileSdk` 语法不同 |
| Kotlin | 2.1.10 | **1.9.22** | 协程版本上限不同 |
| minSdk | 24 | **21** | 兼容性需确认 |
| compileSdk | 36 (version catalog) | **34** (硬编码) | 改为 `compileSdk = 34` |
| kotlinOptions | 无 | **`jvmTarget = "11"`** | 需要添加 |
| 插件 | `android.library` | `android.library` + **`kotlin.android`** | 需要添加 |
| kotlinx-coroutines | 1.9.0 | **1.8.1** (最高兼容) | 版本降级 |
| maven-publish | 模块级插件 | 根项目 + 模块级插件 | 保持一致 |

---

## 二、兼容性确认

### 2.1 minSdk = 21 兼容性

| 组件 | 最低 API | 结论 |
|------|:---:|:---:|
| kotlinx-coroutines-core | **14** | ✅ 兼容 |
| kotlinx-coroutines-android | **14** | ✅ 兼容 |
| `Dispatchers.Main` (Android) | **14** | ✅ 兼容 |
| `Dispatchers.IO.limitedParallelism()` | **14** | ✅ 兼容 |
| `Channel` / `Flow` | **14** | ✅ 兼容 |
| `Handler(Looper.getMainLooper())` | **1** | ✅ 兼容 |
| `Looper.myLooper()` | **1** | ✅ 兼容 |

**结论：所有协程组件最低支持 API 14，minSdk = 21 完全兼容。**

### 2.2 Kotlin 1.9.22 兼容性

| Kotlin 版本 | 最高兼容协程版本 |
|------------|:---:|
| 1.8.x | coroutines 1.7.x |
| **1.9.x** | **coroutines 1.8.1** |
| 2.0.x | coroutines 1.9.x |

new/release 使用 `kotlinx-coroutines 1.9.0`（需要 Kotlin 2.0+），当前分支需降级到 **1.8.1**。

### 2.3 源码语法兼容性

| 语法特性 | Kotlin 1.9.22 | 说明 |
|----------|:---:|------|
| `suspend` 函数 | ✅ | 自 1.3 起支持 |
| `@JvmOverloads` | ✅ | 自 1.0 起支持 |
| `@JvmStatic` | ✅ | 自 1.0 起支持 |
| `@JvmField` | ✅ | 自 1.0 起支持 |
| `object` 单例 | ✅ | 自 1.0 起支持 |
| `private suspend fun` | ✅ | 自 1.3 起支持 |
| `CoroutineScope(SupervisorJob() + ...)` | ✅ | 自 coroutines 1.0 起支持 |
| `Dispatchers.IO.limitedParallelism(1)` | ✅ | 自 coroutines 1.6 起支持（1.8.1 ≥ 1.6） |
| `Channel(Channel.UNLIMITED)` | ✅ | 自 coroutines 1.0 起支持 |
| SAM conversion (`Runnable { }`) | ✅ | 自 1.0 起支持 |

**结论：所有源码语法和 API 在 Kotlin 1.9.22 + coroutines 1.8.1 下完全兼容，无需修改。**

---

## 三、需要创建/修改的文件清单

### 3.1 全局配置修改（2 个文件）

| 文件 | 操作 | 说明 |
|------|:--:|------|
| `gradle/libs.versions.toml` | ✏️ 修改 | 添加 coroutines 1.8.1 版本和依赖声明 |
| `settings.gradle.kts` | ✏️ 修改 | 添加 2 个 include |

### 3.2 itg-coroutine-pools（9 个文件）

| 文件 | 操作 | 说明 |
|------|:--:|------|
| `itg-coroutine-pools/build.gradle.kts` | 🆕 新建 | 适配当前分支配置 |
| `itg-coroutine-pools/.gitignore` | 🆕 新建 | `/build` |
| `itg-coroutine-pools/consumer-rules.pro` | 🆕 新建 | ProGuard 消费者规则 |
| `itg-coroutine-pools/proguard-rules.pro` | 🆕 新建 | ProGuard 规则 |
| `itg-coroutine-pools/src/main/AndroidManifest.xml` | 🆕 新建 | 空清单 |
| `src/.../manager/CoroutineDispatcherManager.kt` | 🆕 新建 | 源码（和 new/release 一致） |
| `src/.../executor/CoroutineExecutor.kt` | 🆕 新建 | 源码（和 new/release 一致） |
| `src/.../channel/ChannelManager.kt` | 🆕 新建 | 源码（和 new/release 一致） |
| `src/.../utils/CoroutineUtils.kt` | 🆕 新建 | 源码（和 new/release 一致） |

### 3.3 itg-concurrent-core（8 个文件）

| 文件 | 操作 | 说明 |
|------|:--:|------|
| `itg-concurrent-core/build.gradle.kts` | 🆕 新建 | 适配当前分支配置 |
| `itg-concurrent-core/.gitignore` | 🆕 新建 | `/build` |
| `itg-concurrent-core/consumer-rules.pro` | 🆕 新建 | ProGuard 消费者规则 |
| `itg-concurrent-core/proguard-rules.pro` | 🆕 新建 | ProGuard 规则 |
| `itg-concurrent-core/src/main/AndroidManifest.xml` | 🆕 新建 | 空清单 |
| `src/.../Concurrent.kt` | 🆕 新建 | 源码（和 new/release 一致） |
| `src/.../ConcurrentFactory.kt` | 🆕 新建 | 源码（和 new/release 一致） |
| `src/.../TaskDispatcher.kt` | 🆕 新建 | 源码（和 new/release 一致） |
| `src/.../backend/ThreadPoolAdapter.kt` | 🆕 新建 | 源码（和 new/release 一致） |
| `src/.../backend/CoroutineAdapter.kt` | 🆕 新建 | 源码（和 new/release 一致） |
| `src/.../util/ConcurrentUtils.kt` | 🆕 新建 | 源码（和 new/release 一致） |

### 3.4 文档（可选）

| 文件 | 操作 |
|------|:--:|
| `itg-coroutine-pools/README.md` | 🆕 新建 |
| `itg-coroutine-pools/docs/*.md` (5 个) | 🆕 新建 |
| `itg-concurrent-core/README.md` | 🆕 新建 |
| `itg-concurrent-core/docs/*.md` (4 个) | 🆕 新建 |

---

## 四、详细变更内容

### 4.1 gradle/libs.versions.toml

添加协程版本和依赖（注意版本号：**1.8.1**，兼容 Kotlin 1.9.22）：

```toml
# [versions] 部分添加:
coroutines = "1.8.1"

# [libraries] 部分添加:
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
```

### 4.2 settings.gradle.kts

```kotlin
// 添加两行
include(":itg-coroutine-pools")
include(":itg-concurrent-core")
```

### 4.3 itg-coroutine-pools/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.itg.itg_coroutine_pools"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    publishing {
        repositories {
            maven { url = uri("${buildDir}/repo") }
        }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.itg"
                artifactId = "itg-coroutine-pools"
                version = "0.1.0"
                pom {
                    name = "ITG Coroutine Pools"
                    description = "A coroutine-based concurrency library for Android."
                    packaging = "aar"
                }
            }
        }
    }
}
```

### 4.4 itg-concurrent-core/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.itg.concurrent"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // compileOnly: 编译时需要，运行时由 app 选择提供
    compileOnly(project(":itg-thread-pools"))
    compileOnly(project(":itg-coroutine-pools"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    publishing {
        repositories {
            maven { url = uri("${buildDir}/repo") }
        }
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.itg"
                artifactId = "itg-concurrent-core"
                version = "0.1.0"
                pom {
                    name = "ITG Concurrent Core"
                    description = "Unified concurrency middleware for Android."
                    packaging = "aar"
                }
            }
        }
    }
}
```

### 4.5 源代码文件

所有 `.kt` 源文件内容与 `new/release` 分支**完全一致**，无需任何修改。原因见 [2.3 节](#23-源码语法兼容性)。

需要复制的 10 个源文件：

```
itg-coroutine-pools/src/main/java/com/itg/itg_coroutine_pools/
├── manager/CoroutineDispatcherManager.kt
├── executor/CoroutineExecutor.kt
├── channel/ChannelManager.kt
└── utils/CoroutineUtils.kt

itg-concurrent-core/src/main/java/com/itg/concurrent/
├── Concurrent.kt
├── ConcurrentFactory.kt
├── TaskDispatcher.kt
├── backend/ThreadPoolAdapter.kt
├── backend/CoroutineAdapter.kt
└── util/ConcurrentUtils.kt
```

---

## 五、new/release vs 当前分支 — build.gradle.kts 差异对照

### itg-coroutine-pools

| 行 | new/release | 当前分支（迁移后） | 原因 |
|----|-----------|-----------------|------|
| plugins | `android.library` | `android.library` + `kotlin.android` | 当前分支需要显式声明 Kotlin 插件 |
| compileSdk | `release(36) { ... }` | `= 34` | AGP 8.2.2 无 version catalog 函数 |
| minSdk | `24` | **`21`** | 目标分支要求 |
| kotlinOptions | 无 | `jvmTarget = "11"` | 当前分支统一规范 |
| coroutines 版本 | `1.9.0` | `1.8.1` (via libs.versions.toml) | Kotlin 1.9.22 兼容性 |

### itg-concurrent-core

| 行 | new/release | 当前分支（迁移后） | 原因 |
|----|-----------|-----------------|------|
| plugins | `android.library` | `android.library` + `kotlin.android` | 同上 |
| compileSdk | `release(36) { ... }` | `= 34` | 同上 |
| minSdk | `24` | **`21`** | 同上 |
| kotlinOptions | 无 | `jvmTarget = "11"` | 同上 |
| coroutines 版本 | `1.9.0` | `1.8.1` | 同上 |

---

## 六、实施步骤

### Step 1: 版本目录 + settings（改 2 个文件）

```bash
# 编辑 gradle/libs.versions.toml，添加：
#   coroutines = "1.8.1"
#   kotlinx-coroutines-core = ...
#   kotlinx-coroutines-android = ...

# 编辑 settings.gradle.kts，添加：
#   include(":itg-coroutine-pools")
#   include(":itg-concurrent-core")
```

### Step 2: 创建 build.gradle.kts（2 个文件）

按照 [第四节](#四详细变更内容) 中的 4.3 和 4.4 创建文件。

### Step 3: 复制源文件（10 个 .kt 文件）

从 `new/release` 分支直接复制所有源文件，内容完全不变。

```bash
# 从 new/release 分支获取源文件
git checkout new/release -- \
  itg-coroutine-pools/src/main/java/com/itg/itg_coroutine_pools/ \
  itg-concurrent-core/src/main/java/com/itg/concurrent/
```

### Step 4: 创建辅助文件（6 个文件）

```bash
# .gitignore, proguard-rules.pro, consumer-rules.pro, AndroidManifest.xml
# 内容简单，手动创建即可
```

### Step 5: 创建文档（可选，10 个 .md 文件）

从 `new/release` 分支复制 `README.md` 和 `docs/` 目录。

### Step 6: 编译验证

```bash
./gradlew :itg-coroutine-pools:compileReleaseKotlin
./gradlew :itg-concurrent-core:compileReleaseKotlin
```

---

## 七、关于 ChannelManager 中 Channel API 的注意事项

`Channel(Channel.UNLIMITED)` 在 kotlinx-coroutines 中标记为 `@DelicateCoroutinesApi`。在 1.8.1 版本中同样有这个警告，不影响编译，仅是 IDE 提示。如果希望消除警告，可以添加 `@OptIn(DelicateCoroutinesApi::class)` 注解，但这不是必需的。

**不需修改源码，仅注释性差异。**

---

## 八、现有模块零影响确认

| 模块 | 改动 |
|------|:--:|
| itg-thread-pools | **0 行改动** ✅ |
| itg-file | **0 行改动** ✅ |
| itg-encrypt | **0 行改动** ✅ |
| itg-string | **0 行改动** ✅ |
| itg-verification | **0 行改动** ✅ |
| itg-bitmap | **0 行改动** ✅ |
| itg-base | **0 行改动** ✅ |
| itg-ui | **0 行改动** ✅ |
| outter | **0 行改动** ✅ |
| app | **0 行改动** ✅ |

---

## 九、总结

| 维度 | 结论 |
|------|------|
| 源文件改动 | **0 个文件需要修改**（仅复制） |
| 新增文件 | **~27 个**（2 build.gradle.kts + 10 .kt + 6 config + ~10 docs） |
| 修改文件 | **2 个**（libs.versions.toml + settings.gradle.kts） |
| 现有模块影响 | **0** |
| minSdk 兼容 | ✅ coroutines 最低支持 API 14 < 21 |
| Kotlin 兼容 | ✅ 1.9.22 + coroutines 1.8.1 |
| AGP 兼容 | ✅ 8.2.2 + `compileSdk = 34` |
| 编译风险 | 🟢 低（源文件语法完全兼容，版本仅需降至 1.8.1） |
