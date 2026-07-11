# Concurrent — 统一并发入口 + 工厂

## 概述

`Concurrent` 是中间件的门面，API 与 `TaskExecutor` / `CoroutineExecutor` 完全一致，但底层后端可切换。

`ConcurrentFactory` 负责后端注册、切换和生命周期管理。

---

## 教程

### 1. 基础用法（和 TaskExecutor 一模一样）

```kotlin
import com.itg.concurrent.Concurrent

// Fire-and-forget
Concurrent.io {
    val data = api.fetchData()
    Concurrent.main { textView.text = data }
}

// 不同分发器
Concurrent.compute { processImage() }        // CPU 密集型
Concurrent.background { syncData() }         // 通用后台
Concurrent.single { db.insertSequentially() } // 串行
```

### 2. 后端切换

#### 全局切换（一行代码）

```kotlin
import com.itg.concurrent.ConcurrentFactory

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 全部用协程
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)

        // 或全部用线程池
        // ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)

        // 或不配置 — 自动检测 classpath
        // ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
    }
}
```

#### 混合模式（精细控制）

```kotlin
// IO 用协程（高并发），计算用线程池（可预测）
ConcurrentFactory.useMixed(mapOf(
    DispatcherType.IO      to ConcurrentFactory.BackendType.COROUTINE,
    DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL,
    DispatcherType.SINGLE  to ConcurrentFactory.BackendType.COROUTINE
))

// 结果：
// Concurrent.io { }        → 协程执行
// Concurrent.compute { }   → 线程池执行
// Concurrent.background { } → 自动检测（classpath）
```

### 3. 运行时可用的后端检测

```kotlin
// 检查哪些后端可用
val available = ConcurrentFactory.getAvailableBackends()
println(available)  // [COROUTINE, THREAD_POOL] 或 [COROUTINE] 等

if (ConcurrentFactory.isCoroutineAvailable()) {
    // 使用协程原生 API
    lifecycleScope.launch {
        val data = Concurrent.ioSuspend { api.fetchData() }
    }
}
```

### 4. 手动注册自定义分发器

```kotlin
// 创建自定义分发器
val customDispatcher: TaskDispatcher = object : TaskDispatcher {
    override val name = "custom"
    override val supportsCoroutineNative = false
    override fun execute(task: () -> Unit) { /* 自定义逻辑 */ }
    override fun <T> submit(task: () -> T): Future<T> { /* ... */ }
    override fun schedule(task: () -> Unit, delayMs: Long): Future<*> { /* ... */ }
}

// 注册 — 覆盖 IO 类型的默认分发器
ConcurrentFactory.register(DispatcherType.IO, customDispatcher)

// 此后 Concurrent.io { } 使用你的自定义实现
```

### 5. 获取底层 CoroutineDispatcher

```kotlin
// 获取协程 Dispatcher（用于集成到已有协程代码中）
val ioDispatcher = Concurrent.getCoroutineDispatcher(DispatcherType.IO)

// 在任何协程作用域中使用
viewModelScope.launch(ioDispatcher) {
    val data = api.fetchData()
}
```

### 6. 有返回值 (Future)

```kotlin
// 和 TaskExecutor 完全一致的用法
val future = Concurrent.io<Int> { calculate() }

// 等待结果
Concurrent.io {
    try {
        val result = ConcurrentUtils.await(future, timeoutMs = 5000)
        Concurrent.main { displayResult(result) }
    } catch (e: TimeoutException) {
        Concurrent.main { showError() }
    }
}
```

### 7. 延迟和定时

```kotlin
// 延迟
Concurrent.mainDelayed(delayMs = 2000L) { showTooltip() }
val future = Concurrent.ioDelayed(delayMs = 5000L) { syncToServer() }
Concurrent.cancel(future)  // 取消

// 定时（通过 get 返回的 TaskDispatcher）
val dispatcher = Concurrent.get(DispatcherType.BACKGROUND)
val periodicFuture = dispatcher.schedule(delayMs = 30_000L) {
    sendHeartbeat()
}
```

### 8. suspend 原生 API

```kotlin
// 协程后端：真正的非阻塞挂起
// 线程池后端：在线程池线程上执行 suspend 块

class MyViewModel : ViewModel() {
    fun loadAll() {
        viewModelScope.launch {
            // 并发执行两个请求
            val userDeferred = async { Concurrent.ioSuspend { api.fetchUser() } }
            val configDeferred = async { Concurrent.ioSuspend { api.fetchConfig() } }

            val user = userDeferred.await()
            val config = configDeferred.await()

            _uiState.value = UiState(user, config)
        }
    }
}
```

### 9. 生命周期管理

```kotlin
class MyApplication : Application() {
    override fun onTerminate() {
        super.onTerminate()
        // 关闭所有已注册分发器
        ConcurrentFactory.shutdown()
    }
}
```

---

## API 参考

### Concurrent（统一入口）

| 方法 | 说明 |
|------|------|
| `io(task)` | I/O 分发器执行 |
| `compute(task)` | 计算分发器执行 |
| `background(task)` | 后台分发器执行 |
| `single(task)` | 串行分发器执行 |
| `main(task)` | 主线程执行 |
| `io<T>(task): Future<T>` | I/O 执行并返回 Future |
| `compute<T>(task): Future<T>` | 计算执行并返回 Future |
| `background<T>(task): Future<T>` | 后台执行并返回 Future |
| `mainDelayed(task, delayMs)` | 主线程延迟执行 |
| `ioDelayed(task, delayMs)` | I/O 延迟执行 |
| `backgroundDelayed(task, delayMs)` | 后台延迟执行 |
| `ioSuspend(task): T` | **suspend** — I/O 执行 |
| `computeSuspend(task): T` | **suspend** — 计算执行 |
| `backgroundSuspend(task): T` | **suspend** — 后台执行 |
| `mainSuspend(task): T` | **suspend** — 主线程执行 |
| `get(type): TaskDispatcher` | 获取指定类型分发器 |
| `getCoroutineDispatcher(type)` | 获取 CoroutineDispatcher |

### ConcurrentFactory（工厂）

| 方法 | 说明 |
|------|------|
| `switchTo(backend)` | 全局切换后端 |
| `useMixed(config)` | 混合模式分配后端 |
| `register(type, dispatcher)` | 手动注册自定义分发器 |
| `isCoroutineAvailable()` | 检测协程后端可用性 |
| `isThreadPoolAvailable()` | 检测线程池后端可用性 |
| `getAvailableBackends()` | 获取所有可用后端列表 |
| `getDispatcher(type)` | 获取分发器（内部调用） |
| `shutdown()` | 关闭所有分发器 |
| `currentBackend` | 当前全局后端类型 |
