# Concurrent 完整使用指南

覆盖全部 20 个使用场景。

## 目录
1. [基础任务提交](#1-基础任务提交)
2. [有返回值 Future](#2-有返回值-future)
3. [延迟执行](#3-延迟执行)
4. [定时周期任务](#4-定时周期任务)
5. [取消任务](#5-取消任务)
6. [等待任务完成](#6-等待任务完成)
7. [后端切换](#7-后端切换)
8. [混合后端模式](#8-混合后端模式)
9. [Suspend 原生 API](#9-suspend-原生-api)
10. [自定义分发器](#10-自定义分发器)
11. [获取底层 Dispatcher](#11-获取底层-dispatcher)
12. [生命周期管理](#12-生命周期管理)
13. [生命周期感知任务](#13-生命周期感知任务)
14. [异常处理](#14-异常处理)
15. [ViewModel 集成](#15-viewmodel-集成)
16. [与存量代码共存](#16-与存量代码共存)
17. [Java 调用](#17-java-调用)
18. [调试监控](#18-调试监控)
19. [完整实战](#19-完整实战)
20. [API 速查表](#20-api-速查表)

---

## 1. 基础任务提交

```kotlin
import com.itg.concurrent.Concurrent

Concurrent.io { }         // I/O — 网络、文件
Concurrent.compute { }    // CPU — 图片、加解密
Concurrent.background { } // 通用后台
Concurrent.single { }     // 严格串行
Concurrent.main { }       // 主线程 UI
```

## 2. 有返回值 Future

```kotlin
val f = Concurrent.io<Int> { calculate() }
Concurrent.io {
    val r = ConcurrentUtils.await(f, timeoutMs = 5000)
    Concurrent.main { display(r) }
}
```

## 3. 延迟执行

```kotlin
Concurrent.mainDelayed(2000L) { showTooltip() }
val f = Concurrent.ioDelayed(5000L) { sync() }
ConcurrentUtils.cancel(f)

// 搜索防抖
var pending: Future<*>? = null
fun onSearchChanged(q: String) {
    pending?.let { ConcurrentUtils.cancel(it) }
    pending = Concurrent.backgroundDelayed(300L) { search(q) }
}
```

## 4. 定时周期任务

```kotlin
// 每30s心跳
val hb = Concurrent.scheduleAtFixedRate(periodMs = 30_000L) { heartbeat() }
// 每5s轮询
val poll = Concurrent.scheduleWithFixedDelay(delayMs = 5_000L) { check() }
ConcurrentUtils.cancel(hb)
```

## 5. 取消任务

```kotlin
ConcurrentUtils.cancel(future)
// 页面离开时批量取消
override fun onDestroy() {
    tasks.forEach { ConcurrentUtils.cancel(it) }
}
```

## 6. 等待任务完成

```kotlin
ConcurrentUtils.await(future, timeoutMs = 5000L)
ConcurrentUtils.awaitAll(futures, timeoutMs = 30_000L)

// 竞速
val idx = ConcurrentUtils.awaitAny(futures, timeoutMs = 10_000L)
val result = futures[idx].get()
futures.forEachIndexed { i, f -> if (i != idx) ConcurrentUtils.cancel(f) }
```

## 7. 后端切换

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
    }
}

// 检测可用后端
if (ConcurrentFactory.isCoroutineAvailable()) { /* suspend API */ }
println(ConcurrentFactory.getAvailableBackends()) // [COROUTINE, THREAD_POOL]
```

## 8. 混合后端模式

```kotlin
ConcurrentFactory.useMixed(mapOf(
    DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
    DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL
))
// Concurrent.io → 协程, Concurrent.compute → 线程池
```

## 9. Suspend 原生 API

```kotlin
lifecycleScope.launch {
    val data = Concurrent.ioSuspend { api.fetch() }
    Concurrent.mainSuspend { updateUI(data) }
}

// 并发
val a = async { Concurrent.ioSuspend { api.fetchA() } }
val b = async { Concurrent.ioSuspend { api.fetchB() } }
val (ra, rb) = a.await() to b.await()
```

## 10. 自定义分发器

```kotlin
class LoggingDispatcher(private val d: TaskDispatcher) : TaskDispatcher {
    override val name = "log-${d.name}"
    override val supportsCoroutineNative = d.supportsCoroutineNative
    override fun execute(task: () -> Unit) {
        val t = System.nanoTime()
        d.execute { task(); Log.d("T", "[$name] ${(System.nanoTime()-t)/1_000_000}ms") }
    }
    override fun <T> submit(task: () -> T): Future<T> = d.submit(task)
    override fun schedule(task: () -> Unit, ms: Long): Future<*> = d.schedule(task, ms)
}
ConcurrentFactory.register(DispatcherType.IO, LoggingDispatcher(Concurrent.get(DispatcherType.IO)))
```

## 11. 获取底层 Dispatcher

```kotlin
val io = Concurrent.getCoroutineDispatcher(DispatcherType.IO)
viewModelScope.launch(io) { fetch() }

val disp = Concurrent.get(DispatcherType.IO)
if (disp is CoroutineTaskDispatcher) {
    disp.executeSuspend { fetch() }
}
```

## 12. 生命周期管理

```kotlin
// 全局 — 不受页面影响
Concurrent.io { uploadLogs() }

// 页面 Scope — Activity 销毁自动取消
lifecycleScope.launch { Concurrent.ioSuspend { fetch() } }

// ViewModel Scope
viewModelScope.launch { Concurrent.ioSuspend { load() } }

// Application 关闭
ConcurrentFactory.shutdown()
```

## 13. 生命周期感知任务

```kotlin
class PageTaskManager(owner: LifecycleOwner) {
    private val tasks = mutableListOf<Future<*>>()
    init {
        owner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(src: LifecycleOwner, e: Lifecycle.Event) {
                if (e == Lifecycle.Event.ON_DESTROY) {
                    tasks.forEach { ConcurrentUtils.cancel(it) }
                    tasks.clear()
                }
            }
        })
    }
    fun <T> submit(task: () -> T): Future<T> {
        val f = Concurrent.io(task)
        tasks.add(f)
        return f
    }
}
```

## 14. 异常处理

```kotlin
// Fire-and-forget — 内部 try-catch
Concurrent.io {
    try { val d = api.fetch(); Concurrent.main { display(d) } }
    catch (e: IOException) { Concurrent.main { showError() } }
}

// Future
try { ConcurrentUtils.await(f) }
catch (e: ExecutionException) { /* e.cause 是原始异常 */ }

// Suspend — 直接 try-catch
try { val d = Concurrent.ioSuspend { api.fetch() } }
catch (e: IOException) { showError() }
```

## 15. ViewModel 集成

```kotlin
class ProfileVM(private val api: UserApi) : ViewModel() {
    private val _state = MutableLiveData<UiState>()
    val state: LiveData<UiState> = _state

    fun load(userId: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val cached = Concurrent.ioSuspend { db.getUser(userId) }
                if (cached != null) _state.value = UiState.Cached(cached)
                val fresh = Concurrent.ioSuspend { api.fetchUser(userId) }
                val processed = Concurrent.computeSuspend { format(fresh) }
                Concurrent.ioSuspend { db.saveUser(processed) }
                _state.value = UiState.Success(processed)
            } catch (e: IOException) { _state.value = UiState.Error(e.message) }
        }
    }
}
```

## 16. 与存量代码共存

```kotlin
// 旧模块不改 (itg-file)
import com.itg.itg_thread_pools.executor.TaskExecutor
TaskExecutor.io { readFile() }

// 新模块用中间件
import com.itg.concurrent.Concurrent
Concurrent.io { newFeature() }

// 两者共存，互不干扰
```

## 17. Java 调用

```java
import com.itg.concurrent.Concurrent;
import com.itg.concurrent.ConcurrentFactory;
import com.itg.concurrent.util.ConcurrentUtils;

ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE);
Concurrent.io(() -> { doWork(); return Unit.INSTANCE; });

Future<String> f = Concurrent.io(() -> api.fetch());
Concurrent.io(() -> {
    String r = ConcurrentUtils.await(f, 5000L);
    Concurrent.main(() -> display(r));
    return Unit.INSTANCE;
});
```

## 18. 调试监控

```kotlin
println(ConcurrentFactory.currentBackend)
println(ConcurrentFactory.getAvailableBackends())
println(ConcurrentUtils.getCurrentThreadDescription())

// A/B 性能对比
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
val t1 = measureTimeMillis { /* 100 requests */ }
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)
val t2 = measureTimeMillis { /* 100 requests */ }
Log.d("Perf", "Coroutine:${t1}ms ThreadPool:${t2}ms")
```

## 19. 完整实战

### 图片批量加载
```kotlin
fun loadImages(urls: List<String>, onComplete: (List<Bitmap>) -> Unit) {
    Concurrent.io {
        val futures = urls.map { url -> Concurrent.io<Bitmap?> {
            try {
                val bytes = httpClient.call(url).body?.bytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size)
                ConcurrentUtils.await(Concurrent.compute<Bitmap> { scale(bmp!!, 600) }, 5000L)
            } catch (e: Exception) { null }
        } }
        ConcurrentUtils.awaitAll(futures, 30_000L)
        val results = futures.mapNotNull { if (it.isDone) try { it.get() } catch (_: Exception) { null } else null }
        Concurrent.main { onComplete(results) }
    }
}
```

### 文件下载+校验
```kotlin
fun downloadAndVerify(url: String, dest: String, md5: String, onDone: (Boolean) -> Unit) {
    Concurrent.io {
        val file = Concurrent.ioSuspend { api.download(url, dest) }
        val actualMd5 = Concurrent.computeSuspend { verifier.md5(file) }
        Concurrent.main { onDone(actualMd5 == md5) }
    }
}
```

## 20. API 速查表

### Concurrent — 统一入口

| 方法 | 返回 | 说明 |
|------|------|------|
| `Concurrent.io { }` | Unit | I/O 执行 |
| `Concurrent.compute { }` | Unit | 计算执行 |
| `Concurrent.background { }` | Unit | 后台执行 |
| `Concurrent.single { }` | Unit | 串行执行 |
| `Concurrent.main { }` | Unit | 主线程 |
| `Concurrent.io<T> { }: Future<T>` | Future\<T\> | I/O + 返回值 |
| `Concurrent.mainDelayed(ms) { }` | Future\<*\> | 主线程延迟 |
| `Concurrent.ioDelayed(ms) { }` | Future\<*\> | I/O 延迟 |
| `Concurrent.scheduleAtFixedRate(...)` | Future\<*\> | 固定频率 |
| `Concurrent.scheduleWithFixedDelay(...)` | Future\<*\> | 固定延迟 |
| `Concurrent.ioSuspend { }: T` | T | suspend I/O |
| `Concurrent.computeSuspend { }: T` | T | suspend 计算 |
| `Concurrent.get(type)` | TaskDispatcher | 分发器 |
| `Concurrent.getCoroutineDispatcher(type)` | CoroutineDispatcher | 协程 Dispatcher |

### ConcurrentFactory

| 方法 | 说明 |
|------|------|
| `switchTo(backend)` | 全局切换 |
| `useMixed(config)` | 混合模式 |
| `register(type, disp)` | 自定义分发器 |
| `isCoroutineAvailable()` / `isThreadPoolAvailable()` | 检测 |
| `shutdown()` | 关闭全部 |
