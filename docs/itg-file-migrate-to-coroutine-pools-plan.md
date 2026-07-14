# itg-file 替换并发后端方案

## 一、需求概述

将 `itg-file` 中硬编码的 `itg-thread-pools` 依赖替换为可切换的并发后端，支持：
1. 用户自由选择使用**协程**（`itg-coroutine-pools`）还是**线程池**（`itg-thread-pools`）
2. 两个库都引入时，用户通过 API 控制实际使用的后端
3. 默认自动检测：优先协程 → fallback 线程池

## 二、现状分析

### 2.1 当前依赖关系

```
itg-file
  └── api(project(":itg-thread-pools"))   ← 硬编码、传递性依赖
        ├── TaskExecutor                  ← 唯一实际使用的类
        ├── ThreadPoolManager             ← 未被 itg-file 直接使用
        ├── ThreadUtils                   ← 未被 itg-file 直接使用
        └── HandlerManager                ← 未被 itg-file 直接使用
```

### 2.2 itg-file 中的 TaskExecutor 使用情况

| 用法 | 说明 | 出现频次 |
|------|------|----------|
| `TaskExecutor.io { }` | IO 线程池 fire-and-forget | ~55 处 |
| `TaskExecutor.main { }` | 切换到主线程 | ~3 处 |
| `TaskExecutor.ioDelayed(task, delayMs)` | IO 线程池延迟执行 | 1 处 |
| `TaskExecutor.await(future, timeoutMs, unit)` | 阻塞等待 Future | 1 处 |

**涉及 11 个源文件**（位于 `itg-file/src/main/java/com/itg/itg_file/`）:

| 包 | 文件 | TaskExecutor 调用数 |
|----|------|-------------------|
| core | `FileUtils.kt` | 16 |
| core | `OkioFileUtils.kt` | 10（含 await） |
| write | `FileWriteUtils.kt` | 6 |
| write | `OkioWriteUtils.kt` | 8 |
| read | `FileReadUtils.kt` | 5 |
| read | `OkioReadUtils.kt` | 7 |
| hash | `FileHashUtils.kt` | 6 |
| hash | `OkioHashUtils.kt` | 5 |
| resource | `AssetUtils.kt` | 16 |
| resource | `OkioAssetUtils.kt` | 14 |
| cleanup | `FileCleanupManager.kt` | 3（io / ioDelayed / main） |

### 2.3 已有基础设施：itg-concurrent-core

项目中已存在 `itg-concurrent-core` 中间件模块，它提供了：

```
itg-concurrent-core
  ├── Concurrent           — 统一门面（API 与 TaskExecutor 对齐）
  ├── ConcurrentFactory    — 后端切换工厂
  ├── TaskDispatcher       — 抽象接口
  ├── ConcurrentUtils      — 后端无关工具类（含 await）
  ├── ThreadPoolAdapter    — 适配 itg-thread-pools
  └── CoroutineAdapter     — 适配 itg-coroutine-pools
```

依赖声明（compileOnly 模式）：
```kotlin
// itg-concurrent-core/build.gradle.kts
compileOnly(project(":itg-thread-pools"))      // 编译时需要，运行时可选
compileOnly(project(":itg-coroutine-pools"))   // 编译时需要，运行时可选
```

### 2.4 API 兼容性对照

| TaskExecutor API | Concurrent API | 兼容性 |
|-----------------|----------------|--------|
| `TaskExecutor.io { }` | `Concurrent.io { }` | ✅ 完全一致 |
| `TaskExecutor.main { }` | `Concurrent.main { }` | ✅ 完全一致 |
| `TaskExecutor.io<T> { }` | `Concurrent.io<T> { }` | ✅ 完全一致 |
| `TaskExecutor.ioDelayed(task, delayMs)` | `Concurrent.ioDelayed(task, delayMs)` | ✅ 完全一致 |
| `TaskExecutor.await(future, timeoutMs, unit)` | `ConcurrentUtils.await(future, timeoutMs)` | ⚠️ 参数略不同 |

> **关于 await**: `TaskExecutor.await` 签名为 `(Future<T>, Long, TimeUnit)`，`ConcurrentUtils.await` 签名为 `(Future<T>, Long)`（timeout 单位固定为毫秒）。itg-file 中唯一调用处传的是 `TimeUnit.MILLISECONDS`，语义等价。

## 三、推荐方案：基于 itg-concurrent-core 中间件

### 3.1 架构图

```
┌─────────────────────────────────────────┐
│  用户 App (application)                  │
│                                          │
│  // 选择后端                              │
│  ConcurrentFactory.switchTo(COROUTINE)    │
│                                          │
│  dependencies {                          │
│    implementation(project(":itg-file"))  │
│    implementation(project(              │
│      ":itg-coroutine-pools"))  // 任选   │
│  }                                       │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  itg-file                                │
│                                          │
│  implementation(project(                │
│    ":itg-concurrent-core"))              │
│                                          │
│  Concurrent.io { }       ← 门面调用      │
│  Concurrent.main { }                     │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  itg-concurrent-core (中间件)             │
│                                          │
│  ConcurrentFactory.switchTo(...)         │
│    ├── COROUTINE  → CoroutineAdapter     │
│    ├── THREAD_POOL → ThreadPoolAdapter   │
│    └── AUTO: 优先协程, fallback 线程池    │
│                                          │
│  compileOnly(project(                   │
│    ":itg-thread-pools"))                 │
│  compileOnly(project(                   │
│    ":itg-coroutine-pools"))              │
└──────────────┬──────────────────────────┘
               │      (运行时可选)
     ┌─────────┴──────────┐
     │                    │
┌────▼──────────┐  ┌─────▼───────────┐
│ itg-thread-   │  │ itg-coroutine-  │
│ pools         │  │ pools           │
│ (线程池后端)   │  │ (协程后端)       │
└───────────────┘  └─────────────────┘
```

### 3.2 itg-file 侧变更

#### 3.2.1 依赖变更（1 行）

**文件**: `itg-file/build.gradle.kts`

```diff
- api(project(":itg-thread-pools"))
+ implementation(project(":itg-concurrent-core"))
```

> 改用 `implementation` 而非 `api`，避免将并发后端暴露为 itg-file 的传递性 API。

#### 3.2.2 源代码变更（11 个文件，机械替换）

每个文件的变更模式完全相同：

**Step 1**: 替换 import
```diff
- import com.itg.itg_thread_pools.executor.TaskExecutor
+ import com.itg.concurrent.Concurrent
```

**Step 2**: 替换所有 `TaskExecutor.` → `Concurrent.`（API 完全一致）
```diff
- TaskExecutor.io { ... }
+ Concurrent.io { ... }

- TaskExecutor.main { ... }
+ Concurrent.main { ... }

- TaskExecutor.ioDelayed(task = { ... }, delayMs = delayMs)
+ Concurrent.ioDelayed(task = { ... }, delayMs = delayMs)
```

**Step 3**: 仅 `OkioFileUtils.kt` 有 `await` 调用，需要额外处理：
```diff
- import com.itg.itg_thread_pools.executor.TaskExecutor
+ import com.itg.concurrent.Concurrent
+ import com.itg.concurrent.util.ConcurrentUtils

- TaskExecutor.await(future, timeoutMs, TimeUnit.MILLISECONDS)
+ ConcurrentUtils.await(future, timeoutMs)
```

**影响文件清单**:

| # | 文件路径 | 变更内容 |
|---|---------|---------|
| 1 | `itg-file/.../core/FileUtils.kt` | import + `s/TaskExecutor/Concurrent/g` |
| 2 | `itg-file/.../core/OkioFileUtils.kt` | import ×2 + `s/TaskExecutor/Concurrent/g` + `TaskExecutor.await` → `ConcurrentUtils.await` |
| 3 | `itg-file/.../write/FileWriteUtils.kt` | import + `s/TaskExecutor/Concurrent/g` |
| 4 | `itg-file/.../write/OkioWriteUtils.kt` | import + `s/TaskExecutor/Concurrent/g` |
| 5 | `itg-file/.../read/FileReadUtils.kt` | import + `s/TaskExecutor/Concurrent/g` |
| 6 | `itg-file/.../read/OkioReadUtils.kt` | import + `s/TaskExecutor/Concurrent/g` |
| 7 | `itg-file/.../hash/FileHashUtils.kt` | import + `s/TaskExecutor/Concurrent/g` |
| 8 | `itg-file/.../hash/OkioHashUtils.kt` | import + `s/TaskExecutor/Concurrent/g` |
| 9 | `itg-file/.../resource/AssetUtils.kt` | import + `s/TaskExecutor/Concurrent/g` |
| 10 | `itg-file/.../resource/OkioAssetUtils.kt` | import + `s/TaskExecutor/Concurrent/g` |
| 11 | `itg-file/.../cleanup/FileCleanupManager.kt` | import + `s/TaskExecutor/Concurrent/g` |

### 3.3 用户侧（App 模块）使用方式

#### 模式一：仅使用协程

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":itg-file"))
    implementation(project(":itg-coroutine-pools"))   // 只引入协程库
}
```

```kotlin
// Application.onCreate()
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
```

#### 模式二：仅使用线程池

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":itg-file"))
    implementation(project(":itg-thread-pools"))   // 只引入线程池库
}
```

```kotlin
// Application.onCreate()
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)
```

#### 模式三：两个都引入，运行时切换

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":itg-file"))
    implementation(project(":itg-thread-pools"))
    implementation(project(":itg-coroutine-pools"))
}
```

```kotlin
// 根据业务需求切换
if (useCoroutine) {
    ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
} else {
    ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)
}
```

#### 模式四：自动检测（默认，无需配置）

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":itg-file"))
    implementation(project(":itg-coroutine-pools"))  // 存在则优先使用
    // 如果也引入了 itg-thread-pools，作为 fallback
}
```

不调用 `switchTo()`，系统自动选择：
- 有协程库 → 自动用协程
- 无协程库 → fallback 线程池
- 两者都无 → 抛异常

#### 模式五：混合模式（按任务类型分配）

```kotlin
ConcurrentFactory.useMixed(mapOf(
    DispatcherType.IO        to ConcurrentFactory.BackendType.COROUTINE,   // IO 高并发 → 协程
    DispatcherType.COMPUTE   to ConcurrentFactory.BackendType.THREAD_POOL, // 计算密集 → 线程池
    DispatcherType.BACKGROUND to ConcurrentFactory.BackendType.COROUTINE,
    // SINGLE 和 MAIN 未指定，走全局设置或自动检测
))
```

### 3.4 切换到协程后的新增能力

切换到 `Concurrent` 后，itg-file 的调用方还可以利用协程原生 API（在已有协程上下文中避免阻塞）：

```kotlin
// 协程中使用 suspend API（非阻塞）
lifecycleScope.launch {
    val hash = Concurrent.ioSuspend { FileHashUtils.hashFile(path) }
    updateUI(hash)  // 已在主线程
}
```

## 四、备选方案：直接依赖 itg-coroutine-pools

如果想简化，也可以将 itg-file 直接改为依赖 `itg-coroutine-pools`：

```diff
- api(project(":itg-thread-pools"))
+ api(project(":itg-coroutine-pools"))
```

然后全局替换 `TaskExecutor` → `CoroutineExecutor`。

**优点**：
- 改动量相同（同样是 import + 类名替换）
- 去掉线程池依赖，包体积更小

**缺点**：
- 失去灵活性：用户无法选择线程池后端
- 无法利用 `itg-concurrent-core` 的后端切换能力
- 如果有场景必须用线程池（如某些兼容性要求），无法满足

## 五、风险评估

| 风险 | 等级 | 说明 | 缓解措施 |
|------|------|------|---------|
| API 兼容性 | 低 | `Concurrent` API 与 `TaskExecutor` 高度一致 | 仅 `await` 参数略有差异，且实际调用处语义等价 |
| 运行时类加载 | 低 | `compileOnly` 模式下，未引入的后端类不会被加载 | `ConcurrentFactory` 使用 `Class.forName` 预检，避免 `NoClassDefFoundError` |
| 行为差异 | 低 | 协程的 `delay()` 是非阻塞的，线程池的 `sleep()` 是阻塞的 | 不影响 itg-file，因为 itg-file 不使用 `sleep`/`delay` |
| Future.get() | 中 | 协程后端 `Future.get()` 内部使用 `runBlocking` | 行为与原 `TaskExecutor` 一致：都是阻塞等待，但协程版创建额外协程作用域 |
| 测试覆盖 | 中 | itg-file 的测试目前依赖 `TaskExecutor` | 需要确保测试在两种后端下都能通过 |

## 六、实施步骤

1. **修改 itg-file/build.gradle.kts** — 替换依赖声明（1 行）
2. **修改 11 个源文件** — import + 类名替换（机械式变更）
3. **修改 OkioFileUtils.kt** — 额外处理 `await` 调用
4. **编译验证** — `./gradlew :itg-file:compileReleaseKotlin`
5. **运行现有测试** — 验证两种后端下测试均通过
6. **更新文档** — 在 itg-file 的 README 中说明新的后端切换方式
