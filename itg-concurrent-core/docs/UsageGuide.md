# Concurrent 完整使用指南

本文档覆盖 `itg-concurrent-core` 中间件的**全部使用场景**，从最简单的 fire-and-forget 到高级混合后端配置。

---

## 目录

1. [基础任务提交](#1-基础任务提交)
2. [有返回值 Future](#2-有返回值-future)
3. [延迟执行](#3-延迟执行)
4. [定时与周期任务](#4-定时与周期任务)
5. [取消任务](#5-取消任务)
6. [等待任务完成](#6-等待任务完成)
7. [后端切换](#7-后端切换)
8. [混合后端模式](#8-混合后端模式)
9. [Suspend 原生 API](#9-suspend-原生-api)
10. [自定义分发器](#10-自定义分发器)
11. [获取底层 Dispatcher](#11-获取底层-dispatcher)
12. [生命周期管理](#12-生命周期管理)
13. [生命周期感知的任务提交](#13-生命周期感知的任务提交)
14. [异常处理](#14-异常处理)
15. [ViewModel 集成](#15-viewmodel-集成)
16. [与存量代码共存](#16-与存量代码共存)
17. [Java 调用](#17-java-调用)
18. [调试与监控](#18-调试与监控)
19. [完整实战示例](#19-完整实战示例)
20. [API 速查表](#20-api-速查表)

---

## 1. 基础任务提交

### 1.1 五种分发器

```kotlin
import com.itg.concurrent.Concurrent

// I/O 密集型 — 网络请求、文件读写、数据库操作
Concurrent.io {
    val json = api.fetchData()
    saveToDatabase(json)
}

// CPU 密集型 — 图片处理、加解密、复杂计算
Concurrent.compute {
    val result = fibonacci(40)
    Concurrent.main { displayResult(result) }
}

// 通用后台 — 不确定类型时的默认选择
Concurrent.background {
    preloadCache()
}

// 单线程串行 — 保证执行顺序
Concurrent.single {
    database.insertSequentially(record1)
    database.insertSequentially(record2)  // 一定在 record1 之后
}

// 主线程 — 安全更新 UI
Concurrent.main {
    progressBar.isVisible = false
    recyclerView.adapter?.notifyDataSetChanged()
}
```

### 1.2 分发器选择决策

```
你的任务是什么？
├─ 网络、文件、数据库 I/O → Concurrent.io { }
├─ 图片处理、加解密、复杂计算 → Concurrent.compute { }
├─ 需要严格顺序执行 → Concurrent.single { }
├─ 更新 UI → Concurrent.main { }
└─ 不确定 → Concurrent.background { }
```

### 1.3 经典线程切换模式

```kotlin
fun loadData() {
    showLoading()                            // 主线程
    Concurrent.io {
        val data = repository.fetchData()    // I/O 线程
        Concurrent.main {
            hideLoading()                    // 主线程
            displayData(data)                // 主线程
        }
    }
}
```

---

## 2. 有返回值 Future

### 2.1 提交并稍后获取

```kotlin
// 提交有返回值的任务
val future = Concurrent.io<Int> {
    (1..1000).sum()
}

// 稍后在另一个线程中获取结果
Concurrent.io {
    val result = ConcurrentUtils.await(future, timeoutMs = 5000)
    Concurrent.main { displayResult(result) }
}
```

### 2.2 并发多个请求

```kotlin
// 同时发起 3 个网络请求
val userFuture = Concurrent.io<User> { api.fetchUser(userId) }
val orderFuture = Concurrent.io<List<Order>> { api.fetchOrders(userId) }
val configFuture = Concurrent.io<Config> { api.fetchConfig() }

// 等待全部完成
Concurrent.io {
    try {
        ConcurrentUtils.await(userFuture, timeoutMs = 10_000L)
        ConcurrentUtils.await(orderFuture, timeoutMs = 10_000L)
        ConcurrentUtils.await(configFuture, timeoutMs = 10_000L)

        val user = userFuture.get()
        val orders = orderFuture.get()
        val config = configFuture.get()

        Concurrent.main { displayAll(user, orders, config) }
    } catch (e: TimeoutException) {
        Concurrent.main { showTimeoutError() }
    }
}
```

### 2.3 awaitAll — 批量等待

```kotlin
Concurrent.io {
    val futures = (1..10).map { index ->
        Concurrent.io<String> { api.fetchItem(index) }
    }

    try {
        // 等待全部完成，超时 30 秒
        ConcurrentUtils.awaitAll(futures, timeoutMs = 30_000L)

        val results = futures.mapNotNull { f ->
            if (f.isDone) try { f.get() } catch (_: Exception) { null }
            else null
        }
        Concurrent.main { displayResults(results) }
    } catch (e: TimeoutException) {
        Concurrent.main { showPartialResults(futures) }
    }
}
```

### 2.4 awaitAny — 竞速请求

```kotlin
// 同时请求多个镜像源，取最快返回的
val futures = listOf(
    Concurrent.io<Data> { api.fetchFromMirror1() },
    Concurrent.io<Data> { api.fetchFromMirror2() },
    Concurrent.io<Data> { api.fetchFromMirror3() }
)

Concurrent.io {
    try {
        val firstIndex = ConcurrentUtils.awaitAny(futures, timeoutMs = 10_000L)
        val result = futures[firstIndex].get()

        // 取消其余请求
        futures.forEachIndexed { index, future ->
            if (index != firstIndex) ConcurrentUtils.cancel(future)
        }

        Concurrent.main { display(result) }
    } catch (e: TimeoutException) {
        Concurrent.main { showTimeoutError() }
    }
}
```

---

## 3. 延迟执行

### 3.1 主线程延迟

```kotlin
// 2 秒后显示 Tooltip
val runnable = Concurrent.mainDelayed(delayMs = 2000L) {
    showTooltip()
}

// 必要时取消
// handler.removeCallbacks(runnable)
```

### 3.2 后台延迟

```kotlin
// 5 秒后同步数据
val future = Concurrent.ioDelayed(delayMs = 5000L) {
    syncToServer()
}

// 取消
ConcurrentUtils.cancel(future)

// 等待延迟任务完成
Concurrent.io {
    ConcurrentUtils.await(future)
    Concurrent.main { showSyncComplete() }
}
```

### 3.3 场景：搜索防抖

```kotlin
class SearchViewModel : ViewModel() {
    private var pendingSearch: Future<*>? = null

    fun onSearchTextChanged(query: String) {
        // 取消上一次的延迟搜索
        pendingSearch?.let { ConcurrentUtils.cancel(it) }

        // 300ms 后执行搜索（用户停止输入后）
        pendingSearch = Concurrent.backgroundDelayed(delayMs = 300L) {
            val results = searchRepository.search(query)
            Concurrent.main { _searchResults.value = results }
        }
    }
}
```

---

## 4. 定时与周期任务

### 4.1 固定频率（scheduleAtFixedRate）

```kotlin
// 每 30 秒上报心跳（从任务开始时间算间隔）
val heartbeat = Concurrent.scheduleAtFixedRate(
    initialDelayMs = 0L,
    periodMs = 30_000L
) {
    sendHeartbeat()
}

// 停止
ConcurrentUtils.cancel(heartbeat)
```

### 4.2 固定延迟（scheduleWithFixedDelay）

```kotlin
// 每 5 秒轮询一次（等上次任务完成后再等 5 秒）
val polling = Concurrent.scheduleWithFixedDelay(
    initialDelayMs = 5_000L,
    delayMs = 5_000L
) {
    checkForNewMessages()
}

// 停止
ConcurrentUtils.cancel(polling)
```

### 4.3 场景：定时刷新

```kotlin
class DashboardViewModel : ViewModel() {
    private var refreshJob: Future<*>? = null

    fun startAutoRefresh() {
        refreshJob = Concurrent.scheduleAtFixedRate(
            initialDelayMs = 0L,
            periodMs = 60_000L  // 每分钟刷新
        ) {
            val data = repository.fetchDashboard()
            Concurrent.main { _dashboardState.value = data }
        }
    }

    override fun onCleared() {
        refreshJob?.let { ConcurrentUtils.cancel(it) }
        super.onCleared()
    }
}
```

---

## 5. 取消任务

### 5.1 取消 Future

```kotlin
val future = Concurrent.io<Data> { loadLargeFile() }
// ...
ConcurrentUtils.cancel(future, mayInterrupt = true)
```

### 5.2 取消主线程延迟

```kotlin
val runnable = Concurrent.mainDelayed(delayMs = 5000L) {
    showDelayedNotification()
}
// ...
// 使用 Android Handler 原生 API 取消
import android.os.Handler
import android.os.Looper
Handler(Looper.getMainLooper()).removeCallbacks(runnable)
```

### 5.3 场景：页面离开时取消

```kotlin
class MyActivity : AppCompatActivity() {
    private val pendingTasks = mutableListOf<Future<*>>()

    fun loadAllData() {
        pendingTasks += Concurrent.io<Unit> { loadUser() }
        pendingTasks += Concurrent.io<Unit> { loadConfig() }
        pendingTasks += Concurrent.io<Unit> { loadMessages() }
    }

    override fun onDestroy() {
        // 取消所有未完成的任务
        pendingTasks.forEach { ConcurrentUtils.cancel(it) }
        super.onDestroy()
    }
}
```

---

## 6. 等待任务完成

### 6.1 阻塞等待单个

```kotlin
// ⚠️ 不可在主线程调用！
Concurrent.io {
    val future = Concurrent.compute<Int> { fibonacci(40) }
    try {
        val result = ConcurrentUtils.await(future, timeoutMs = 5000L)
        Concurrent.main { displayResult(result) }
    } catch (e: TimeoutException) {
        Concurrent.main { showTimeoutError() }
    }
}
```

### 6.2 等待所有

```kotlin
val futures = listOf(
    Concurrent.io<Data> { api.fetchA() },
    Concurrent.io<Data> { api.fetchB() },
    Concurrent.io<Data> { api.fetchC() }
)

Concurrent.io {
    ConcurrentUtils.awaitAll(futures, timeoutMs = 15_000L)
    // 全部完成或超时
}
```

### 6.3 等待任意一个

```kotlin
val futures = listOf(
    Concurrent.io<String> { api.fetchFromCDN1() },
    Concurrent.io<String> { api.fetchFromCDN2() }
)

Concurrent.io {
    val index = ConcurrentUtils.awaitAny(futures, timeoutMs = 8000L)
    val result = futures[index].get()
    Concurrent.main { display(result) }
}
```

---

## 7. 后端切换

### 7.1 全局切换（推荐在 Application 中配置）

```kotlin
import com.itg.concurrent.ConcurrentFactory

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 全部用协程
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)

        // 或全部用线程池
        // ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)

        // 或不配置 — 自动检测（优先协程，fallback 线程池）
        // ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
    }
}
```

### 7.2 运行时动态切换

```kotlin
// 根据用户设置切换
if (settings.useCoroutineBackend) {
    ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
} else {
    ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)
}
```

### 7.3 检测可用后端

```kotlin
// 检查哪些后端在 classpath 上
val available = ConcurrentFactory.getAvailableBackends()
println(available)  // [COROUTINE, THREAD_POOL]

if (ConcurrentFactory.isCoroutineAvailable()) {
    // 协程后端可用 — 可使用 suspend API
    lifecycleScope.launch {
        val data = Concurrent.ioSuspend { api.fetchData() }
    }
}

if (ConcurrentFactory.isThreadPoolAvailable()) {
    // 线程池后端可用
}
```

---

## 8. 混合后端模式

### 8.1 按任务类型分配后端

```kotlin
ConcurrentFactory.useMixed(mapOf(
    // IO 用协程 — 高并发，不占线程
    DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
    // 计算用线程池 — 线程数固定，性能可预测
    DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL,
    // 串行也走协程 — 轻量
    DispatcherType.SINGLE to ConcurrentFactory.BackendType.COROUTINE
    // MAIN 不指定 — 自动检测
))

// 结果：
Concurrent.io { }        // → 协程执行
Concurrent.compute { }   // → 线程池执行
Concurrent.single { }    // → 协程执行
Concurrent.main { }      // → 自动检测
Concurrent.background { } // → 自动检测
```

### 8.2 场景：为什么混合？

| 任务类型 | 推荐后端 | 原因 |
|---------|---------|------|
| IO（网络/文件） | COROUTINE | 高并发协程挂起，不占线程 |
| 计算/加解密 | THREAD_POOL | 线程数=CPU核心数，避免协程切换开销 |
| 串行（数据库写入） | COROUTINE | 轻量级单线程 |
| 主线程更新 | 任意 | 都是 post 到 Main Looper |

---

## 9. Suspend 原生 API

### 9.1 基础用法

```kotlin
// 在已有协程作用域中使用
lifecycleScope.launch {
    val user = Concurrent.ioSuspend { api.fetchUser() }
    val orders = Concurrent.ioSuspend { api.fetchOrders(user.id) }
    // 自动在主线程，直接更新 UI
    _userState.value = user
    _orderState.value = orders
}
```

### 9.2 四种 Suspend 分发器

```kotlin
suspend fun loadAll() {
    val data = Concurrent.ioSuspend { api.fetchData() }            // I/O
    val result = Concurrent.computeSuspend { processData(data) }    // 计算
    val cached = Concurrent.backgroundSuspend { loadCache() }      // 后台
    Concurrent.mainSuspend { updateUI(result, cached) }             // 主线程
}
```

### 9.3 并发 Suspend（结构化并发）

```kotlin
lifecycleScope.launch {
    // 同时执行两个请求
    val userDeferred = async { Concurrent.ioSuspend { api.fetchUser() } }
    val configDeferred = async { Concurrent.ioSuspend { api.fetchConfig() } }

    val user = userDeferred.await()
    val config = configDeferred.await()
    // 两个请求都完成后才继续

    _uiState.value = UiState(user, config)
}
```

### 9.4 Suspend + 异常处理

```kotlin
lifecycleScope.launch {
    try {
        val data = Concurrent.ioSuspend { api.fetchData() }
        updateUI(data)
    } catch (e: IOException) {
        showNetworkError()
    } catch (e: CancellationException) {
        // 协程被取消（如 ViewModel 清除）— 正常情况，不需要处理
    }
}
```

### 9.5 Suspend 在线程池后端的表现

```kotlin
// 即使后端是 THREAD_POOL，suspend 函数也能正常工作
// 内部在线程池线程上执行 suspend 块（通过 asCoroutineDispatcher 桥接）
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)

lifecycleScope.launch {
    val data = Concurrent.ioSuspend { api.fetchData() }
    // ↑ 在线程池线程上执行，功能正常，但没有协程挂起的并发优势
}
```

---

## 10. 自定义分发器

### 10.1 实现 TaskDispatcher 接口

```kotlin
import com.itg.concurrent.TaskDispatcher
import java.util.concurrent.*

class LoggingDispatcher(
    private val delegate: TaskDispatcher
) : TaskDispatcher {
    override val name = "logging-${delegate.name}"
    override val supportsCoroutineNative = delegate.supportsCoroutineNative

    override fun execute(task: () -> Unit) {
        val start = System.nanoTime()
        delegate.execute {
            task()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            Log.d("Concurrent", "[$name] task completed in ${elapsed}ms")
        }
    }

    override fun <T> submit(task: () -> T): Future<T> {
        return delegate.submit(task)
    }

    override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
        return delegate.schedule(task, delayMs)
    }
}

// 注册
val original = Concurrent.get(DispatcherType.IO)
ConcurrentFactory.register(DispatcherType.IO, LoggingDispatcher(original))
```

### 10.2 场景：Virtual Threads 适配器（Java 21+）

```kotlin
class VirtualThreadAdapter : TaskDispatcher {
    override val name = "virtual-thread-io"
    override val supportsCoroutineNative = false

    override fun execute(task: () -> Unit) {
        Thread.startVirtualThread { task() }
    }

    override fun <T> submit(task: () -> T): Future<T> {
        val future = FutureTask(Callable { task() })
        Thread.startVirtualThread { future.run() }
        return future
    }

    override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
        val future = FutureTask<Void>(Callable {
            Thread.sleep(delayMs)
            task()
            null
        })
        Thread.startVirtualThread { future.run() }
        return future
    }
}

ConcurrentFactory.register(DispatcherType.IO, VirtualThreadAdapter())
```

---

## 11. 获取底层 Dispatcher

### 11.1 获取 CoroutineDispatcher

```kotlin
import kotlinx.coroutines.*

// 获取底层 CoroutineDispatcher，用于集成到已有协程代码中
val ioDispatcher = Concurrent.getCoroutineDispatcher(DispatcherType.IO)
val computeDispatcher = Concurrent.getCoroutineDispatcher(DispatcherType.COMPUTE)

// 在任意协程作用域中使用
viewModelScope.launch(ioDispatcher) {
    val data = api.fetchData()
}

withContext(computeDispatcher) {
    processImage()
}
```

### 11.2 获取 TaskDispatcher

```kotlin
// 获取 TaskDispatcher 实例，用于更灵活的控制
val ioDispatcher = Concurrent.get(DispatcherType.IO)

// 检查是否支持协程原生
if (ioDispatcher is CoroutineTaskDispatcher) {
    // 协程后端 — 支持 executeSuspend
    val data = ioDispatcher.executeSuspend { fetchData() }
}

// 直接使用 TaskDispatcher 接口
ioDispatcher.execute { doWork() }
val future = ioDispatcher.submit { calculate() }
val scheduledFuture = ioDispatcher.schedule(delayMs = 5000L) { syncData() }
```

---

## 12. 生命周期管理

### 12.1 全局 Scope 模式（和线程池行为一致）

```kotlin
// 全局任务 — 不受页面生命周期影响
Concurrent.io {
    uploadLogs()  // 即使页面关闭，日志仍要上传
}
Concurrent.io {
    syncDatabase()  // 全局同步，不应因页面切换中断
}
```

### 12.2 页面 Scope 模式（推荐）

```kotlin
class MyActivity : AppCompatActivity() {

    fun loadPageData() {
        // 使用 Android 生命周期感知的 Scope
        lifecycleScope.launch {
            val data = Concurrent.ioSuspend { api.fetchPageData() }
            updateUI(data)
        }
        // Activity 销毁 → lifecycleScope 自动取消 → 协程取消 → 请求取消
    }
}
```

### 12.3 ViewModel Scope

```kotlin
class MyViewModel : ViewModel() {

    fun loadData() {
        viewModelScope.launch {
            val data = Concurrent.ioSuspend { repository.fetchData() }
            _state.value = data
        }
        // ViewModel 清除 → viewModelScope 自动取消 → 协程取消
    }
}
```

### 12.4 手动 Scope 管理

```kotlin
class DataLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start() {
        scope.launch {
            val data = Concurrent.ioSuspend { loadData() }
            onDataReady(data)
        }
    }

    fun stop() {
        scope.cancel()  // 取消所有由该 Scope 启动的协程
    }
}
```

### 12.5 Application 级关闭

```kotlin
class MyApplication : Application() {
    override fun onTerminate() {
        super.onTerminate()
        // 关闭中间件管理的所有分发器
        ConcurrentFactory.shutdown()
    }
}
```

---

## 13. 生命周期感知的任务提交

### 13.1 用 Lifecycle 自动取消 Future

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * 为 LifecycleOwner 提交任务，页面销毁时自动取消
 */
fun LifecycleOwner.launchAutoCancel(task: () -> Unit): Future<*> {
    val future = Concurrent.io<Unit> { task() }
    lifecycle.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_DESTROY) {
                ConcurrentUtils.cancel(future)
            }
        }
    })
    return future
}

// 使用
class MyActivity : AppCompatActivity() {
    fun loadData() {
        launchAutoCancel {
            val data = api.fetchData()
            Concurrent.main { updateUI(data) }
        }
        // Activity 销毁 → Future 自动取消
    }
}
```

### 13.2 页面级任务注册表

```kotlin
class PageTaskManager(private val owner: LifecycleOwner) {
    private val tasks = mutableListOf<Future<*>>()

    init {
        owner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    tasks.forEach { ConcurrentUtils.cancel(it) }
                    tasks.clear()
                }
            }
        })
    }

    fun <T> submit(task: () -> T): Future<T> {
        val future = Concurrent.io(task)
        tasks.add(future)
        return future
    }
}

// 使用
class MyFragment : Fragment() {
    private lateinit var taskManager: PageTaskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskManager = PageTaskManager(this)
    }

    fun loadData() {
        taskManager.submit { api.fetchData() }
        // Fragment 销毁 → 所有任务自动取消
    }
}
```

---

## 14. 异常处理

### 14.1 Fire-and-Forget 的异常

```kotlin
// fire-and-forget 中的异常不会传播到调用方
// 需要通过 try-catch 在任务内部处理
Concurrent.io {
    try {
        val data = api.fetchData()
        Concurrent.main { display(data) }
    } catch (e: IOException) {
        Concurrent.main { showNetworkError() }
    }
}
```

### 14.2 Future 的异常

```kotlin
val future = Concurrent.io<Data> {
    api.fetchData()  // 如果抛异常
}

Concurrent.io {
    try {
        val result = ConcurrentUtils.await(future)
    } catch (e: ExecutionException) {
        // 任务内部异常被包装为 ExecutionException
        val originalError = e.cause
        Concurrent.main { showError(originalError?.message) }
    } catch (e: TimeoutException) {
        Concurrent.main { showTimeoutError() }
    }
}
```

### 14.3 Suspend 的异常（协程原生）

```kotlin
lifecycleScope.launch {
    try {
        val data = Concurrent.ioSuspend { api.fetchData() }
        updateUI(data)
    } catch (e: IOException) {
        // 直接捕获原始异常，无需 unwrap
        showNetworkError()
    } catch (e: CancellationException) {
        // 协程取消 — 正常情况，不需要处理
        throw e  // 重新抛出以正确传播取消
    }
}
```

### 14.4 带 fallback 的请求

```kotlin
lifecycleScope.launch {
    val data = try {
        Concurrent.ioSuspend { api.fetchFromPrimary() }
    } catch (e: IOException) {
        // 主源失败，尝试备用源
        try {
            Concurrent.ioSuspend { api.fetchFromFallback() }
        } catch (e2: IOException) {
            showNetworkError()
            return@launch
        }
    }
    updateUI(data)
}
```

---

## 15. ViewModel 集成

### 15.1 完整 ViewModel 示例

```kotlin
class UserProfileViewModel(
    private val api: UserApi,
    private val database: UserDatabase
) : ViewModel() {

    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    fun loadProfile(userId: String) {
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                // 1. 先从数据库加载缓存
                val cached = Concurrent.ioSuspend { database.getUser(userId) }
                if (cached != null) {
                    _uiState.value = UiState.CachedData(cached)
                }

                // 2. 从网络获取最新数据
                val fresh = Concurrent.ioSuspend { api.fetchUser(userId) }

                // 3. 在计算线程处理数据
                val processed = Concurrent.computeSuspend { formatUserData(fresh) }

                // 4. 保存到数据库
                Concurrent.ioSuspend { database.saveUser(processed) }

                // 5. 更新 UI
                _uiState.value = UiState.Success(processed)

            } catch (e: IOException) {
                _uiState.value = UiState.Error("Network error: ${e.message}")
            } catch (e: CancellationException) {
                // ViewModel 清除，正常取消 — 不更新 UI
            }
        }
    }

    fun refreshOrders(userId: String) {
        viewModelScope.launch {
            // 并发加载多项数据
            val profileDeferred = async { Concurrent.ioSuspend { api.fetchUser(userId) } }
            val ordersDeferred = async { Concurrent.ioSuspend { api.fetchOrders(userId) } }
            val statsDeferred = async { Concurrent.ioSuspend { api.fetchStats(userId) } }

            val profile = profileDeferred.await()
            val orders = ordersDeferred.await()
            val stats = statsDeferred.await()

            _uiState.value = UiState.FullData(profile, orders, stats)
        }
    }

    fun searchWithDebounce(query: String) {
        // 使用 Concurrent 延迟实现防抖
        viewModelScope.launch {
            delay(300)  // 等待 300ms 用户停止输入
            if (query.isNotBlank()) {
                val results = Concurrent.ioSuspend { api.search(query) }
                _uiState.value = UiState.SearchResults(results)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope 自动取消所有协程，无需手动清理
    }
}
```

### 15.2 ViewModel + Future 模式（兼容旧风格）

```kotlin
class LegacyViewModel(
    private val repository: DataRepository
) : ViewModel() {

    private val _data = MutableLiveData<List<Item>>()
    val data: LiveData<List<Item>> = _data

    private var pendingFuture: Future<*>? = null

    fun loadData() {
        // 取消上一次请求
        pendingFuture?.let { ConcurrentUtils.cancel(it) }

        pendingFuture = Concurrent.io<List<Item>> {
            repository.fetchItems()
        }

        Concurrent.io {
            try {
                val items = ConcurrentUtils.await(pendingFuture!!, timeoutMs = 10_000L)
                Concurrent.main { _data.value = items }
            } catch (e: TimeoutException) {
                Concurrent.main { _data.value = emptyList() }
            }
        }
    }

    override fun onCleared() {
        pendingFuture?.let { ConcurrentUtils.cancel(it) }
        super.onCleared()
    }
}
```

---

## 16. 与存量代码共存

### 16.1 同一项目中并存

```kotlin
// === 旧模块 (itg-file/FileUtils.kt) — 不改代码 ===
import com.itg.itg_thread_pools.executor.TaskExecutor

fun readFile(path: String, onResult: (String) -> Unit): Future<*> {
    return TaskExecutor.io { onResult(File(path).readText()) }
}

// === 新模块 (app/NewFeature.kt) — 用中间件 ===
import com.itg.concurrent.Concurrent

fun loadNewFeature() {
    Concurrent.io {
        val data = api.fetchNewData()
        Concurrent.main { displayNewFeature(data) }
    }
}

// === 两个共存，互不干扰 ===
// TaskExecutor.io → 全局线程池
// Concurrent.io   → 你配置的后端（协程或线程池）
```

### 16.2 渐进迁移路径

```
Phase 1: 引入 itg-concurrent-core + itg-coroutine-pools
         → 新代码用 Concurrent.io { }, 旧代码不改

Phase 2: 逐步将旧模块改为 import Concurrent
         → 改 import，不改逻辑，测试通过即上线

Phase 3: 全部迁移完成后
         → 移除 itg-thread-pools 依赖
         → 零线程池、零额外线程
```

---

## 17. Java 调用

### 17.1 Fire-and-Forget

```java
import com.itg.concurrent.Concurrent;
import com.itg.concurrent.ConcurrentFactory;

// 基础任务提交
Concurrent.io(() -> {
    String data = api.fetchData();
    Concurrent.main(() -> updateUI(data));
    return Unit.INSTANCE;
});

// 计算任务
Concurrent.compute(() -> {
    processImage();
    return Unit.INSTANCE;
});
```

### 17.2 有返回值

```java
import java.util.concurrent.Future;
import com.itg.concurrent.util.ConcurrentUtils;

Future<String> future = Concurrent.io(() -> api.fetchData());

// 在其他地方等待结果
Concurrent.io(() -> {
    try {
        String result = ConcurrentUtils.await(future, 5000L);
        Concurrent.main(() -> display(result));
    } catch (TimeoutException e) {
        Concurrent.main(() -> showError());
    }
    return Unit.INSTANCE;
});
```

### 17.3 后端切换

```java
// Application.onCreate()
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE);
```

### 17.4 延迟和定时

```java
// 延迟
Future<?> delayedFuture = Concurrent.ioDelayed(() -> {
    syncToServer();
    return Unit.INSTANCE;
}, 5000L);

// 定时
Future<?> periodic = Concurrent.scheduleAtFixedRate(
    () -> { sendHeartbeat(); return Unit.INSTANCE; },
    0L,
    30_000L
);
```

---

## 18. 调试与监控

### 18.1 检查后端状态

```kotlin
// 当前全局后端
println("Backend: ${ConcurrentFactory.currentBackend}")

// 可用后端列表
println("Available: ${ConcurrentFactory.getAvailableBackends()}")

// 逐个检查
println("Coroutine available: ${ConcurrentFactory.isCoroutineAvailable()}")
println("ThreadPool available: ${ConcurrentFactory.isThreadPoolAvailable()}")
```

### 18.2 分发器信息

```kotlin
// 获取分发器实例
val dispatcher = Concurrent.get(DispatcherType.IO)
println("IO Dispatcher: name=${dispatcher.name}, coroutineNative=${dispatcher.supportsCoroutineNative}")

if (dispatcher is CoroutineTaskDispatcher) {
    println("  CoroutineDispatcher: ${dispatcher.coroutineDispatcher}")
}
```

### 18.3 线程信息

```kotlin
import com.itg.concurrent.util.ConcurrentUtils

// 当前线程描述
println(ConcurrentUtils.getCurrentThreadDescription())
// 输出: DefaultDispatcher-worker-1 (id=142, main=false)

// 完整线程信息
val info = ConcurrentUtils.getCurrentThreadInfo()
info.forEach { (key, value) -> println("$key = $value") }
```

### 18.4 性能对比（A/B 测试）

```kotlin
// 用协程后端跑一次
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
val coroutineTime = measureTimeMillis {
    runBlocking {
        repeat(100) {
            Concurrent.ioSuspend { api.fetchItem(it) }
        }
    }
}

// 用线程池后端跑一次
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)
val threadPoolTime = measureTimeMillis {
    val futures = repeat(100) { Concurrent.io<Unit> { api.fetchItem(it) } }
    futures.forEach { it.get() }
}

Log.d("Perf", "Coroutine: ${coroutineTime}ms, ThreadPool: ${threadPoolTime}ms")
```

---

## 19. 完整实战示例

### 19.1 图片批量加载

```kotlin
class ImageLoader {
    fun loadImages(urls: List<String>, onComplete: (List<Bitmap>) -> Unit) {
        Concurrent.io {
            val futures = urls.map { url ->
                Concurrent.io<Bitmap?> {
                    try {
                        val bytes = okHttpClient.newCall(Request(url)).execute().body?.bytes()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes?.size ?: 0)
                        // 在计算线程缩放
                        ConcurrentUtils.await(
                            Concurrent.compute<Bitmap> { scaleToWidth(bitmap, 600) },
                            timeoutMs = 5000L
                        )
                    } catch (e: Exception) {
                        null  // 单张失败不影响整体
                    }
                }
            }

            try {
                ConcurrentUtils.awaitAll(futures, timeoutMs = 30_000L)
            } catch (_: TimeoutException) { }

            val results = futures.mapNotNull { f ->
                if (f.isDone) try { f.get() } catch (_: Exception) { null }
                else null
            }

            Concurrent.main { onComplete(results) }
        }
    }
}
```

### 19.2 文件下载 + 校验

```kotlin
class FileDownloader(
    private val api: DownloadApi,
    private val verifier: FileVerifier
) {
    fun downloadAndVerify(
        url: String,
        destPath: String,
        expectedMd5: String,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        Concurrent.io {
            // 1. 下载文件
            val file = Concurrent.ioSuspend { api.download(url, destPath) { progress ->
                Concurrent.main { onProgress(progress) }
            } }

            // 2. 计算 MD5（CPU 密集）
            val actualMd5 = Concurrent.computeSuspend { verifier.calculateMd5(file) }

            // 3. 校验
            val isValid = actualMd5 == expectedMd5

            Concurrent.main { onComplete(isValid) }
        }
    }
}
```

### 19.3 数据库批量操作

```kotlin
class OrderBatchProcessor(
    private val database: OrderDatabase,
    private val api: OrderApi
) {
    fun processBatch(orderIds: List<Int>, onComplete: (Int) -> Unit) {
        val futures = orderIds.map { id ->
            Concurrent.io<Boolean> {
                try {
                    // 串行处理每个订单的子步骤
                    val order = api.fetchOrder(id)
                    val validated = validateOrder(order)
                    if (validated) {
                        database.orderDao().insert(order)
                    }
                    validated
                } catch (e: Exception) {
                    false
                }
            }
        }

        Concurrent.io {
            ConcurrentUtils.awaitAll(futures, timeoutMs = 60_000L)
            val successCount = futures.count { f ->
                f.isDone && try { f.get() == true } catch (_: Exception) { false }
            }
            Concurrent.main { onComplete(successCount) }
        }
    }
}
```

---

## 20. API 速查表

### Concurrent — 统一入口

| 方法 | 返回 | 说明 |
|------|------|------|
| `Concurrent.io { }` | Unit | I/O 分发器执行 |
| `Concurrent.compute { }` | Unit | 计算分发器执行 |
| `Concurrent.background { }` | Unit | 后台分发器执行 |
| `Concurrent.single { }` | Unit | 串行分发器执行 |
| `Concurrent.main { }` | Unit | 主线程执行 |
| `Concurrent.io<T> { }: Future<T>` | Future\<T\> | I/O 执行有返回值 |
| `Concurrent.compute<T> { }: Future<T>` | Future\<T\> | 计算执行有返回值 |
| `Concurrent.background<T> { }: Future<T>` | Future\<T\> | 后台执行有返回值 |
| `Concurrent.mainDelayed(ms) { }` | Future\<*\> | 主线程延迟 |
| `Concurrent.ioDelayed(ms) { }` | Future\<*\> | I/O 延迟 |
| `Concurrent.backgroundDelayed(ms) { }` | Future\<*\> | 后台延迟 |
| `Concurrent.scheduleAtFixedRate(...)` | Future\<*\> | 固定频率定时 |
| `Concurrent.scheduleWithFixedDelay(...)` | Future\<*\> | 固定延迟定时 |
| `Concurrent.ioSuspend { }: T` | T | **suspend** I/O 执行 |
| `Concurrent.computeSuspend { }: T` | T | **suspend** 计算执行 |
| `Concurrent.backgroundSuspend { }: T` | T | **suspend** 后台执行 |
| `Concurrent.mainSuspend { }: T` | T | **suspend** 主线程执行 |
| `Concurrent.get(type)` | TaskDispatcher | 获取分发器实例 |
| `Concurrent.getCoroutineDispatcher(type)` | CoroutineDispatcher | 获取协程 Dispatcher |

### ConcurrentFactory — 工厂

| 方法 | 说明 |
|------|------|
| `switchTo(backend)` | 全局切换后端 |
| `useMixed(config)` | 混合模式分配后端 |
| `register(type, dispatcher)` | 手动注册自定义分发器 |
| `isCoroutineAvailable()` | 检测协程后端可用性 |
| `isThreadPoolAvailable()` | 检测线程池后端可用性 |
| `getAvailableBackends()` | 获取所有可用后端 |
| `getDispatcher(type)` | 获取分发器（内部调用） |
| `shutdown()` | 关闭所有分发器 |
| `currentBackend` | 当前全局后端类型 |

### ConcurrentUtils — 工具

| 方法 | 说明 |
|------|------|
| `isMainThread()` | 判断是否主线程 |
| `isBackgroundThread()` | 判断是否后台线程 |
| `assertMainThread(msg)` | 断言主线程 |
| `assertBackgroundThread(msg)` | 断言后台线程 |
| `await(future, timeoutMs)` | 阻塞等待 Future |
| `awaitAll(futures, timeoutMs)` | 等待所有 Future |
| `awaitAny(futures, timeoutMs)` | 等待任一 Future |
| `cancel(future, mayInterrupt)` | 取消 Future |
| `sleep(ms)` | 安全阻塞休眠 |
| `getCurrentThreadDescription()` | 线程描述 |
| `getCurrentThreadInfo()` | 线程信息 Map |

### BackendType 枚举

| 值 | 说明 |
|------|------|
| `COROUTINE` | 协程后端 (itg-coroutine-pools) |
| `THREAD_POOL` | 线程池后端 (itg-thread-pools) |
| `AUTO` | 自动检测（优先协程，fallback 线程池） |

### DispatcherType 枚举

| 值 | 说明 | 推荐后端 |
|------|------|:---:|
| `IO` | I/O 密集型 | COROUTINE |
| `COMPUTE` | CPU 密集型 | THREAD_POOL |
| `BACKGROUND` | 通用后台 | COROUTINE |
| `SINGLE` | 单线程串行 | COROUTINE |
| `MAIN` | 主线程/UI | 任意 |
