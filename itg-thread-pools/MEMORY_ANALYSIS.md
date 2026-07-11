# 中间件+协程方案：线程池是否会创建？内存分析

## 核心问题

> 使用中间件 + 协程后端，`ThreadPoolManager` 的 6 个线程池实例还会创建吗？

## 答案：取决于依赖配置，分三种情况

---

## 情况1：只依赖 core + coroutine-pools（不含 thread-pools）

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":itg-concurrent-core"))
    implementation(project(":itg-coroutine-pools"))
    // 没有 itg-thread-pools
}
```

### 结果：✅ 零线程池、零线程

```
APK 中的类:
  ✅ itg-concurrent-core 的类
  ✅ itg-coroutine-pools 的类
  ❌ ThreadPoolManager      ← 不在 APK 中
  ❌ TaskExecutor           ← 不在 APK 中
  ❌ HandlerManager         ← 不在 APK 中

运行时:
  线程池实例: 0 个
  线程数:     0（协程挂起不占线程）
  内存占用:   仅协程 CoroutineScope（几KB）
```

**原因：`itg-thread-pools` 根本没被打包进 APK。** `compileOnly` 意味着编译时需要，但运行时不传递。

---

## 情况2：三个模块都依赖，但后端设为 COROUTINE

```kotlin
// build.gradle.kts — 三个都依赖
dependencies {
    implementation(project(":itg-concurrent-core"))
    implementation(project(":itg-thread-pools"))     // 也在 APK 中
    implementation(project(":itg-coroutine-pools"))
}

// Application.onCreate()
ConcurrentFactory.switchTo(BackendType.COROUTINE)
```

### 结果：⚠️ 线程池类在 APK 中，但实例不创建

```
APK 中的类:
  ✅ 三个模块的类全在 APK 中（占用 DEX 空间 ~50KB）

运行时:
  线程池实例: 0 个 ← Kotlin object 的懒加载特性
  线程数:     0
  内存占用:   只有 DEX 中的类定义（无堆内存占用）
```

### 为什么实例不创建？

关键在 Kotlin `object` 的初始化时机：

```kotlin
// ThreadPoolManager 是 Kotlin object
object ThreadPoolManager {
    // 这些属性只在对象首次被访问时才初始化！
    val ioPool: ThreadPoolExecutor = ThreadPoolExecutor(...)   // 懒初始化
    val computePool: ThreadPoolExecutor = ThreadPoolExecutor(...)
    val backgroundPool: ThreadPoolExecutor = ThreadPoolExecutor(...)
    val singlePool: ThreadPoolExecutor = ThreadPoolExecutor(...)
    val scheduledPool: ScheduledExecutorService = ScheduledThreadPoolExecutor(...)
}
```

Kotlin `object` 使用 **Holder 模式** 实现线程安全的懒加载：

```java
// 反编译后的 Java 代码
public final class ThreadPoolManager {
    public static ThreadPoolManager INSTANCE;  // 初始为 null

    static {
        INSTANCE = new ThreadPoolManager();  // 类加载时初始化
    }
}
```

`ThreadPoolManager` 类的静态初始化块只在 **第一次访问该类时** 触发。触发条件：
- 访问 `ThreadPoolManager.ioPool`
- 调用 `ThreadPoolManager.shutdown()`
- 调用 `ThreadPoolManager.newFixedPool(...)`

**仅在以下情况不会被触发：**
- `import com.itg.itg_thread_pools.manager.ThreadPoolManager` ← import 不会触发
- 类在 APK 的 DEX 中 ← 存在不触发
- `Class.forName("...ThreadPoolManager")` 未被调用 ← OK

### 中间件如何保证不触发？

```kotlin
// ThreadPoolAdapter.kt — 只在 backend = THREAD_POOL 时才被调用
object ThreadPoolAdapter {
    fun create(type: DispatcherType): TaskDispatcher {
        // 这一行才会触发 ThreadPoolManager 初始化
        return ThreadPoolDispatcher("io", ThreadPoolManager.ioPool)
        //                                 ↑ 这里才首次访问 ThreadPoolManager
    }
}

// ConcurrentFactory.kt — 当 backend = COROUTINE 时，走另一条分支
private fun resolveDefault(type: DispatcherType): TaskDispatcher {
    return when (_currentBackend) {
        BackendType.COROUTINE   -> CoroutineAdapter.create(type)  // ← 走这里
        BackendType.THREAD_POOL -> ThreadPoolAdapter.create(type) // ← 不走这里
        // ...
    }
}
```

**backend = COROUTINE 时，执行路径完全不会触及 `ThreadPoolAdapter` 或 `ThreadPoolManager`。**

---

## 情况3：存量模块仍直接引用 itg-thread-pools（最大问题）

```kotlin
// itg-file/build.gradle.kts — 不改代码
dependencies {
    api(project(":itg-thread-pools"))  // 存量依赖
}

// itg-file/src/.../FileUtils.kt — 不改代码
import com.itg.itg_thread_pools.executor.TaskExecutor

fun exists(path: String, onResult: (Boolean) -> Unit): Future<*> {
    return TaskExecutor.io { onResult(File(path).exists()) }
    //     ↑ 首次调用时，触发 TaskExecutor → ThreadPoolManager 初始化
    //     → 6 个线程池全部创建 → 70+ 线程
}
```

### 结果：❌ 线程池会被创建

只要 **任何代码路径** 调用了 `TaskExecutor.io()` 或任何引用 `ThreadPoolManager` 的方法，就会触发全部线程池的初始化。

**这是存量模块不修改代码的代价。** 只要 `itg-file`、`itg-encrypt` 等模块还在 import `TaskExecutor`，它们的首次调用就会创建线程池。

---

## 解决方案：不修改代码前提下，避免线程池创建

### 方案A：Gradle 依赖替换（推荐，最干净）

利用 Gradle 的 `dependencySubstitution`，将 `itg-thread-pools` 替换为一个**同包名、同类名的协程壳模块**：

```
                  编译时                           运行时
itg-file ──api──▶ itg-thread-pools      itg-file ──▶ itg-thread-pools-coro-stub
                   (被替换)                           (协程实现的替身)
```

#### Step 1：创建替身模块 `itg-thread-pools-coro-stub`

```kotlin
// itg-thread-pools-coro-stub/build.gradle.kts
// 包名和 Maven artifact 和原模块相同，但内部用协程实现
android {
    namespace = "com.itg.itg_thread_pools"  // ← 相同包名！
}
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "com.itg"
                artifactId = "itg-thread-pools"  // ← 相同 artifactId！
                version = "0.1.0-coroutine"       // ← 版本号区分
            }
        }
    }
}
```

```kotlin
// 替身模块中的 TaskExecutor.kt — 同包名、同类名、同方法签名
package com.itg.itg_thread_pools.executor  // ← 和原模块完全相同的包名

import kotlinx.coroutines.*
import java.util.concurrent.*

object TaskExecutor {
    // 内部用协程，但 API 签名和原版完全一致
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @JvmStatic fun io(task: () -> Unit) { scope.launch(Dispatchers.IO) { task() } }
    @JvmStatic fun compute(task: () -> Unit) { scope.launch(Dispatchers.Default) { task() } }
    @JvmStatic fun background(task: () -> Unit) { scope.launch(Dispatchers.Default) { task() } }
    @JvmStatic fun single(task: () -> Unit) { scope.launch(Dispatchers.IO.limitedParallelism(1)) { task() } }
    @JvmStatic fun main(task: () -> Unit) { /* 同原版 */ }

    @JvmStatic fun <T> io(task: () -> T): Future<T> = scope.async(Dispatchers.IO) { task() }.asFuture()
    // ... 其余方法保持一致 ...

    @JvmStatic fun cancel(future: Future<*>, mayInterrupt: Boolean = true) = future.cancel(mayInterrupt)
    @JvmStatic fun <T> await(future: Future<T>, timeout: Long = 0, unit: TimeUnit = TimeUnit.MILLISECONDS) = ...
}
```

```kotlin
// 替身模块中的 ThreadPoolManager.kt — 同样包名，但内部无线程池
package com.itg.itg_thread_pools.manager  // ← 相同包名

import kotlinx.coroutines.*
import java.util.concurrent.Executor

object ThreadPoolManager {
    // 对外暴露 Executor 接口（兼容性），内部用 Dispatcher
    // 注意：不创建任何 ThreadPoolExecutor 实例！
    @JvmField val ioPool: Executor = Dispatchers.IO.asExecutor()
    @JvmField val computePool: Executor = Dispatchers.Default.asExecutor()
    @JvmField val backgroundPool: Executor = Dispatchers.Default.asExecutor()
    @JvmField val singlePool: Executor = Dispatchers.IO.limitedParallelism(1).asExecutor()
    @JvmField val mainExecutor: Executor = Dispatchers.Main.asExecutor()

    // scheduledPool 用协程替代
    @JvmStatic fun newCachedPool(...) = Dispatchers.IO.asExecutor()
    // ...
}
```

#### Step 2：在 app 的 build.gradle.kts 中替换

```kotlin
// app/build.gradle.kts
configurations.all {
    resolutionStrategy.dependencySubstitution {
        // 将原 itg-thread-pools 替换为协程替身
        substitute(project(":itg-thread-pools"))
            .using(project(":itg-thread-pools-coro-stub"))
    }
}
```

#### 结果

```
APK 中的类:
  ✅ itg-thread-pools-coro-stub 的类（包名伪装成 com.itg.itg_thread_pools）
  ❌ 原 itg-thread-pools 的类（被替换掉了）
  ❌ ThreadPoolExecutor（无任何线程池实例）
  ❌ PriorityBlockingQueue（无任何阻塞队列）

运行时:
  线程池实例: 0
  线程数:     0（仅协程挂起）
  内存:       协程 CoroutineScope（几KB）

itg-file 代码: 完全不用改！
  TaskExecutor.io { }  → 实际调用替身模块的协程实现
```

### 方案B：exclude + 替身（更彻底）

```kotlin
// app/build.gradle.kts
dependencies {
    // 排除所有传递依赖中的 itg-thread-pools
    implementation(project(":itg-file")) {
        exclude(group = "com.itg", module = "itg-thread-pools")
    }
    implementation(project(":itg-encrypt")) {
        exclude(group = "com.itg", module = "itg-thread-pools")
    }
    // ... 其余同理

    // 用替身模块填补
    implementation(project(":itg-thread-pools-coro-stub"))
}
```

### 方案C：仅用中间件隔离存量模块（部分解决方案）

存量模块不迁移时，中间件只能隔离 **新代码**：

```
新代码 → Concurrent.io { } → CoroutineAdapter → 协程（0线程）
旧代码 → TaskExecutor.io { } → 仍然走线程池（70+线程）

结果：两种后端同时运行，但线程池仍被创建
```

这需要逐步将旧模块迁移到中间件 API。

---

## 总结对比

| 方案 | 线程池实例 | 线程数 | DEX体积 | 存量代码改动 | 复杂度 |
|------|:---:|:---:|:---:|:---:|:---:|
| **仅 core+coroutine-pools** (不含 thread-pools) | 0 | 0 | 最小 | 旧模块需迁移 | 低 |
| **三模块都依赖 + COROUTINE** | 0 | 0 | 稍大 | 旧模块需迁移 | 低 |
| **三模块 + 旧模块不改** | 6 个池 (~70线程) | ~70 | 大 | 无 | 低 |
| **Gradle 替身替换** | 0 | 0 | 中等 | **零改动** ✅ | 中 |
| **exclude + 替身** | 0 | 0 | 小 | **零改动** ✅ | 中 |

---

## 推荐方案

### 如果存量模块可以后续迁移

```
Phase 1: 引入 itg-concurrent-core + itg-coroutine-pools
         → 新代码用中间件，旧代码暂时不改
         → 线程池仍被创建（旧模块触发），但新模块用协程

Phase 2: 逐步将 itg-file、itg-encrypt 等改为 import Concurrent
         → 线程池创建逐步减少

Phase 3: 全部迁移完成后，移除 itg-thread-pools 依赖
         → 零线程池
```

### 如果存量模块完全不想改

```
使用方案A（Gradle 替身替换）
  → 创建 itg-thread-pools-coro-stub（同包名、同类名、协程实现）
  → Gradle dependencySubstitution 全局替换
  → itg-file 等模块零感知，但实际运行在协程上
  → 零线程池、零线程
```

---

## 关键保证：Kotlin object 的懒加载

即使 `itg-thread-pools` 在 classpath 上，只要以下代码路径不被触发，线程池就不会创建：

```kotlin
// 不会触发 ThreadPoolManager 初始化：
import com.itg.itg_thread_pools.manager.ThreadPoolManager   // import 安全
val cls = Class.forName("...ThreadPoolManager")              // 不访问成员就安全

// 会触发 ThreadPoolManager 初始化（6个池全部创建）：
ThreadPoolManager.ioPool            // ← 触发点
ThreadPoolManager.shutdown()        // ← 触发点
TaskExecutor.io { }                 // ← 触发点（内部引用 ThreadPoolManager）
ThreadPoolManager.INSTANCE          // ← 触发点
```

中间件的设计确保 backend=COROUTINE 时，执行路径完全绕开 `ThreadPoolManager`，因此：
- **实例不创建**
- **线程不启动**
- **内存不占用（除 DEX 中的类定义）**
