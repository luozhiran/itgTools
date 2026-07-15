# itg-concurrent-core 使用指南

`itg-concurrent-core` 是项目里的统一并发入口。它把业务代码和具体执行后端隔离开：同一套 `Concurrent` API 可以运行在线程池后端，也可以运行在协程后端。

当前文档只覆盖本模块真实存在的 API，不包含未实现的周期调度、批量等待等能力。

## 使用场景总览

| 使用场景 | 推荐 API | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- |
| 主线程更新 UI | `Concurrent.main { }` | 需要从后台线程切回 UI 线程，或者当前不确定是否在主线程 | 内部会判断当前线程；已在主线程时直接执行，否则分发到 `DispatcherType.MAIN` |
| 普通 I/O 阻塞任务 | `Concurrent.io { }` | 文件读写、同步网络调用、数据库同步读写等普通非 suspend 任务 | 返回 `Future<T>`，底层通过当前 IO 分发器执行，可随后等待或取消 |
| CPU 密集计算 | `Concurrent.compute { }` | 图片压缩、加解密、大量数据转换、排序等计算任务 | 使用 `DispatcherType.COMPUTE`，和 I/O 任务隔离，避免计算任务占满 I/O 通道 |
| 通用后台任务 | `Concurrent.background { }` | 不明显属于 I/O 或计算的后台清理、统计、预处理任务 | 使用 `DispatcherType.BACKGROUND`，语义独立，便于后端混合配置 |
| 后台任务需要结果 | `Concurrent.io/compute/background<T> { }` | 调用方需要拿到任务结果，或需要保存任务句柄 | 统一返回 `Future<T>`，可以配合 `ConcurrentUtils.await/cancel` 使用 |
| 主线程延迟执行 | `Concurrent.mainDelayed({ }, delayMs)` | 延迟显示 UI、延迟回调、主线程防抖 | 通过 MAIN 分发器延迟执行，并返回 `Future<*>` 便于取消 |
| I/O 延迟执行 | `Concurrent.ioDelayed({ }, delayMs)` | 延迟同步、搜索防抖、延迟读取或清理文件 | 通过 IO 分发器延迟执行，任务体仍是普通非 suspend lambda |
| 后台延迟执行 | `Concurrent.backgroundDelayed({ }, delayMs)` | 延迟埋点、后台清理、非关键异步任务 | 通过 BACKGROUND 分发器延迟执行，和主线程及 I/O 语义分开 |
| 取消 Future 任务 | `ConcurrentUtils.cancel(future)` | 页面退出、请求过期、防抖替换旧任务 | `Future.cancel` 是同步任务 API 的统一取消句柄，线程池和协程后端都做了适配 |
| 阻塞等待结果 | `ConcurrentUtils.await(future, timeoutMs)` | 已经在后台线程中，需要等待 `Future` 完成 | 封装 `Future.get/get(timeout)`，中断或取消返回 `null`；不能在主线程调用 |
| 判断当前线程 | `ConcurrentUtils.isMainThread/isBackgroundThread` | 需要决定是否直接更新 UI 或切线程 | 基于 Android `Looper` 判断，和具体后端无关 |
| 线程调用约束 | `ConcurrentUtils.assertMainThread/assertBackgroundThread` | API 要求必须在主线程或后台线程调用 | 不满足时立即抛出异常，能尽早暴露调用错误 |
| 后台阻塞 sleep | `ConcurrentUtils.sleep(ms)` | 普通后台线程里需要短暂阻塞等待 | 封装 `Thread.sleep` 并处理中断；主线程调用会告警并返回，避免 ANR |
| 已在协程中切到 I/O | `Concurrent.ioSuspend { }` | `lifecycleScope/viewModelScope/launch` 内执行 suspend 或阻塞 I/O 包装 | 使用当前 IO 分发器的协程上下文；线程池后端会桥接成 `CoroutineDispatcher` |
| 已在协程中切到计算线程 | `Concurrent.computeSuspend { }` | 协程内执行 CPU 密集计算 | 使用 COMPUTE 分发器，避免把计算放在主线程或 I/O 通道 |
| 已在协程中切到后台线程 | `Concurrent.backgroundSuspend { }` | 协程内执行普通后台逻辑 | 使用 BACKGROUND 分发器，保留任务语义并支持后端切换 |
| 已在协程中切回主线程 | `Concurrent.mainSuspend { }` | suspend 流程中需要更新 UI 或调用主线程 API | 使用 MAIN 分发器，以 suspend 方式切回主线程，不需要嵌套普通回调 |
| 普通类一次性启动 suspend 任务 | `Concurrent.launchIo/launchCompute/launchBackground { }` | 不在 Activity、Fragment、ViewModel 中，没有 `lifecycleScope`，但要调用 suspend 函数 | 内部创建临时 `ConcurrentScope` 并返回 `Job`；任务完成或取消后释放临时 scope |
| 普通类一次性启动主线程 suspend 任务 | `Concurrent.launchMain { }` | 没有外部 scope，但需要启动主线程协程，例如只做 UI 回调或主线程 API 调用 | 使用 MAIN 分发器创建协程；任务体和 `collect` 下游默认在主线程执行 |
| 指定任意分发器启动 suspend 任务 | `Concurrent.launch(type, name) { }` | 运行时才决定任务类型，或者需要给任务命名便于调试 | 统一走 `getCoroutineDispatcher(type)`，支持协程后端和线程池后端 |
| 普通类长期管理多个 suspend 任务 | `Concurrent.createScope(type, name)` | Manager、Repository、SDK 组件会多次启动任务，需要统一释放 | 返回 `ConcurrentScope`，内部使用 `SupervisorJob`，owner 调用 `cancel()` 可取消全部子任务 |
| Flow 收集 | `Concurrent.launchIo { flow.collect { } }` 或 `scope.launch { }` | 需要调用 `Flow.collect`，例如网络 Flow、订阅消息流 | `collect` 是 suspend 函数，必须在协程中调用；`launchIo/createScope` 提供协程上下文 |
| Flow 结果更新 UI | `Concurrent.launchIo { ... Concurrent.mainSuspend { } }` | Flow 上游或数据处理在后台，最终只把 UI 更新放主线程 | 上游收集和处理不占主线程，UI 部分通过 MAIN 分发器切回 |
| 线程池后端运行 suspend/Flow | `launch*/createScope/ioSuspend` | 全局切到 `THREAD_POOL`，但业务仍然使用 suspend 或 Flow | 线程池 `TaskDispatcher` 会桥接成 `CoroutineDispatcher`，所以 suspend API 仍能运行 |
| 按任务类型混合后端 | `ConcurrentFactory.useMixed(...)` | 希望 IO 用协程、计算用线程池，或不同类型任务使用不同后端 | `DispatcherType` 到后端的映射在工厂层完成，业务层调用方式不变 |
| 全局切换后端 | `ConcurrentFactory.switchTo(...)` | App 初始化、性能实验、按版本切换线程池或协程实现 | `Concurrent` 只依赖 `TaskDispatcher` 抽象，切换后端不会改变业务调用 API |
| 自定义分发器 | `ConcurrentFactory.register(type, dispatcher)` | 需要埋点、限流、替换某类任务执行器、接入特殊线程模型 | 注册表优先级高于默认后端，可只覆盖某个 `DispatcherType` |
| 获取底层任务分发器 | `Concurrent.get(type)` | 高级场景需要直接访问 `TaskDispatcher` | 暴露统一分发器抽象，可直接 `execute/submit/schedule` |
| 获取协程 Dispatcher | `Concurrent.getCoroutineDispatcher(type)` | 需要和原生 Kotlin 协程 API、第三方协程库组合 | 协程后端返回原生 dispatcher；线程池后端自动桥接成 dispatcher |
| 进程或测试清理 | `ConcurrentFactory.shutdown()` | 测试结束、进程级资源清理、重置手动注册分发器 | 会关闭注册表中支持 `AutoCloseable` 的分发器并清空注册表 |
| Java 侧普通异步任务 | `Concurrent.io(() -> ...)` | Java 代码需要提交后台任务或拿 `Future` | 普通函数 API 对 Java 友好；suspend API 不适合 Java 直接调用 |

## 目录

1. [先选 API](#1-先选-api)
2. [依赖和后端](#2-依赖和后端)
3. [Application 初始化](#3-application-初始化)
4. [普通后台任务](#4-普通后台任务)
5. [主线程任务](#5-主线程任务)
6. [Future 返回值](#6-future-返回值)
7. [延迟执行](#7-延迟执行)
8. [取消和等待 Future](#8-取消和等待-future)
9. [线程检测和断言](#9-线程检测和断言)
10. [在协程里切线程](#10-在协程里切线程)
11. [没有 lifecycleScope 时启动 suspend 任务](#11-没有-lifecyclescope-时启动-suspend-任务)
12. [长期持有的 ConcurrentScope](#12-长期持有的-concurrentscope)
13. [Flow collect 场景](#13-flow-collect-场景)
14. [线程池后端下运行 suspend/Flow](#14-线程池后端下运行-suspendflow)
15. [并发组合](#15-并发组合)
16. [混合后端模式](#16-混合后端模式)
17. [自定义 TaskDispatcher](#17-自定义-taskdispatcher)
18. [获取底层 Dispatcher](#18-获取底层-dispatcher)
19. [生命周期和释放](#19-生命周期和释放)
20. [异常处理](#20-异常处理)
21. [Java 调用](#21-java-调用)
22. [常见错误](#22-常见错误)
23. [API 速查表](#23-api-速查表)

---

## 1. 先选 API

根据任务类型先选入口：

| 场景 | 推荐 API | 返回 | 说明 |
| --- | --- | --- | --- |
| 普通阻塞 I/O | `Concurrent.io { }` | `Future<T>` | 文件、网络同步调用、数据库同步调用 |
| CPU 计算 | `Concurrent.compute { }` | `Future<T>` | 图片处理、加解密、格式化大数据 |
| 通用后台任务 | `Concurrent.background { }` | `Future<T>` | 不明确属于 I/O 或计算的后台任务 |
| 切回主线程 | `Concurrent.main { }` | `Unit` | UI 更新或主线程回调 |
| 延迟任务 | `mainDelayed/ioDelayed/backgroundDelayed` | `Future<*>` | 防抖、延迟提示、延迟清理 |
| 已经在协程内 | `ioSuspend/computeSuspend/backgroundSuspend/mainSuspend` | `T` | 在 `lifecycleScope/viewModelScope/launch` 中切线程 |
| 不在 Activity/Fragment，但要跑 suspend/Flow | `Concurrent.launchIo { }` | `Job` | 一次性异步任务，可取消 |
| 普通类长期持有任务 | `Concurrent.createScope(...)` | `ConcurrentScope` | Manager/Repository/SDK 组件，owner 释放时 cancel |

原则：

- `Concurrent.io { }` 接收普通 lambda，不能直接调用 `suspend` 函数。
- `Flow.collect` 是 `suspend` 函数，必须放在协程入口里，例如 `launchIo { flow.collect { } }`。
- 长生命周期任务要保存 `Job` 或 `ConcurrentScope`，在不需要时取消。

## 2. 依赖和后端

模块依赖关系通常是：

```kotlin
dependencies {
    implementation(project(":itg-concurrent-core"))

    // 至少提供一个后端。两者都提供时，AUTO 默认优先协程后端。
    implementation(project(":itg-coroutine-pools"))
    implementation(project(":itg-thread-pools"))
}
```

`itg-concurrent-core` 支持三种后端选择：

| 后端 | 说明 |
| --- | --- |
| `AUTO` | 自动检测 classpath，优先使用协程后端，其次使用线程池后端 |
| `COROUTINE` | 强制使用 `itg-coroutine-pools` |
| `THREAD_POOL` | 强制使用 `itg-thread-pools` |

检测当前环境：

```kotlin
val backends = ConcurrentFactory.getAvailableBackends()
val current = ConcurrentFactory.currentBackend
val hasCoroutine = ConcurrentFactory.isCoroutineAvailable()
val hasThreadPool = ConcurrentFactory.isThreadPoolAvailable()
```

## 3. Application 初始化

不配置时默认是 `AUTO`。如果业务希望明确后端，可以在 `Application.onCreate()` 中配置。

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
    }
}
```

切到线程池：

```kotlin
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)
```

恢复自动检测：

```kotlin
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
```

## 4. 普通后台任务

I/O 任务：

```kotlin
Concurrent.io {
    val config = loadConfigFromDisk()
    Concurrent.main {
        render(config)
    }
}
```

计算任务：

```kotlin
Concurrent.compute {
    val digest = md5(bytes)
    Concurrent.main {
        showDigest(digest)
    }
}
```

通用后台任务：

```kotlin
Concurrent.background {
    cleanupExpiredCache()
}
```

注意：这三个 API 都返回 `Future<T>`。如果不关心返回值，可以忽略。

## 5. 主线程任务

```kotlin
Concurrent.main {
    textView.text = "done"
}
```

如果当前已经在主线程，`Concurrent.main { }` 会直接执行；否则会分发到主线程执行。

## 6. Future 返回值

```kotlin
val future: Future<User> = Concurrent.io {
    api.getUser(userId)
}

Concurrent.io {
    val user = ConcurrentUtils.await(future, timeoutMs = 5_000L)
    Concurrent.main {
        if (user != null) showUser(user) else showError()
    }
}
```

`ConcurrentUtils.await` 是阻塞等待，不要在主线程调用。

## 7. 延迟执行

当前真实签名是 `task` 在前，`delayMs` 在后：

```kotlin
val future = Concurrent.ioDelayed({
    syncCache()
}, delayMs = 3_000L)
```

主线程延迟：

```kotlin
Concurrent.mainDelayed({
    showTooltip()
}, delayMs = 500L)
```

后台延迟：

```kotlin
val pending = Concurrent.backgroundDelayed({
    reportEvent()
}, delayMs = 1_000L)
```

搜索防抖：

```kotlin
private var searchFuture: Future<*>? = null

fun onSearchTextChanged(keyword: String) {
    searchFuture?.let { ConcurrentUtils.cancel(it) }
    searchFuture = Concurrent.ioDelayed({
        val result = search(keyword)
        Concurrent.main { render(result) }
    }, delayMs = 300L)
}
```

## 8. 取消和等待 Future

取消：

```kotlin
val future = Concurrent.io { upload() }
ConcurrentUtils.cancel(future)
```

等待：

```kotlin
val result = ConcurrentUtils.await(future, timeoutMs = 5_000L)
```

无超时等待：

```kotlin
val result = ConcurrentUtils.await(future)
```

`await` 返回可空值：

- 成功时返回任务结果。
- 被中断或取消时返回 `null`。
- `Future.get()` 抛出的其他异常会继续向外抛出，例如 `ExecutionException`。

## 9. 线程检测和断言

```kotlin
if (ConcurrentUtils.isMainThread()) {
    updateUi()
}

ConcurrentUtils.assertBackgroundThread()
ConcurrentUtils.assertMainThread()

val desc = ConcurrentUtils.getCurrentThreadDescription()
val info = ConcurrentUtils.getCurrentThreadInfo()
```

阻塞 sleep：

```kotlin
Concurrent.io {
    ConcurrentUtils.sleep(200L)
}
```

`ConcurrentUtils.sleep` 不应该在主线程调用；如果在主线程调用，它会记录警告并直接返回。

## 10. 在协程里切线程

在 `lifecycleScope`、`viewModelScope` 或任意已有协程中，使用 `Suspend` API 切到对应分发器。

```kotlin
lifecycleScope.launch {
    val user = Concurrent.ioSuspend {
        api.getUser(userId)
    }

    val viewState = Concurrent.computeSuspend {
        buildViewState(user)
    }

    Concurrent.mainSuspend {
        render(viewState)
    }
}
```

这些 API 不会创建新的顶层生命周期，只是在当前协程结构里切换执行上下文。

## 11. 没有 lifecycleScope 时启动 suspend 任务

普通类中没有 `lifecycleScope`，但又要调用 `suspend` 或 `Flow.collect`，可以用一次性 `launch` API。

```kotlin
val job = Concurrent.launchIo(name = "preload-config") {
    val config = api.fetchConfig()
    Concurrent.mainSuspend {
        callback(config)
    }
}
```

也可以指定分发类型：

```kotlin
val job = Concurrent.launch(DispatcherType.COMPUTE, name = "format-data") {
    val result = formatLargeData(data)
    Concurrent.mainSuspend { render(result) }
}
```

取消：

```kotlin
job.cancel()
```

一次性 `launch` 内部会创建临时 `ConcurrentScope`，任务完成或取消后会释放该临时 scope。

## 12. 长期持有的 ConcurrentScope

如果一个普通类会多次启动任务，推荐持有 `ConcurrentScope`，并在 owner 释放时取消。

```kotlin
class PreloadManager {
    private val scope = Concurrent.createScope(
        type = DispatcherType.IO,
        name = "preload-manager"
    )

    fun preload() {
        scope.launch {
            val config = api.fetchConfig()
            cache.save(config)
        }
    }

    fun release() {
        scope.cancel()
    }
}
```

`ConcurrentScope` 使用 `SupervisorJob`，同一 scope 下一个子任务失败，不会自动取消其他兄弟任务。

## 13. Flow collect 场景

错误写法：

```kotlin
Concurrent.io {
    flow.collect { value ->
        handle(value)
    }
}
```

原因：`Concurrent.io { }` 是普通函数，不是 `suspend` lambda。

正确写法：

```kotlin
Concurrent.launchIo {
    flow.collect { value ->
        handle(value)
    }
}
```

长期任务写法：

```kotlin
class MessageSubscriber {
    private val scope = Concurrent.createScope(DispatcherType.IO, "message-subscriber")

    fun start() {
        scope.launch {
            messageFlow.collect { message ->
                saveMessage(message)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
```

网络 Flow 示例：

```kotlin
Concurrent.launchIo("preload-url") {
    val type = object : TypeToken<PreloadConfig>() {}.type

    Net.instance.get()
        .path("launch-hub/open/noauth/bcop/getPreloadPageUrls")
        .monitorExtra("web-cache-url")
        .flowResponse { raw ->
            GsonNetConverter<PreloadConfig>(type = type).convert(raw)
        }
        .collect { config ->
            handlePreloadConfig(config)
        }
}
```

## 14. 线程池后端下运行 suspend/Flow

即使全局切到线程池后端，`launchIo`、`ioSuspend`、`getCoroutineDispatcher` 仍然可以运行 suspend 任务。

```kotlin
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)

Concurrent.launchIo {
    flow.collect { value ->
        handle(value)
    }
}
```

实现逻辑是：线程池 `TaskDispatcher` 会被桥接成 `CoroutineDispatcher`。所以调用方式不变，但仍然依赖 Kotlin Coroutines 来执行 `suspend` 函数。

限制：

- 取消是协作式取消；已经进入阻塞 I/O 的代码不一定马上停止。
- `flowOn(...)` 会改变 Flow 上游运行的 dispatcher。
- 不要把长时间阻塞代码放到 `mainSuspend` 或 `launchMain` 中。

## 15. 并发组合

在协程中可以用标准 `async/await` 组合多个任务。

```kotlin
Concurrent.launchBackground("load-home") {
    val userDeferred = async {
        Concurrent.ioSuspend { api.getUser() }
    }
    val configDeferred = async {
        Concurrent.ioSuspend { api.getConfig() }
    }

    val user = userDeferred.await()
    val config = configDeferred.await()

    val state = Concurrent.computeSuspend {
        buildHomeState(user, config)
    }

    Concurrent.mainSuspend {
        render(state)
    }
}
```

如果使用 `Future` 风格，也可以手动保存多个 `Future`，逐个 `await`：

```kotlin
val userFuture = Concurrent.io { api.getUser() }
val configFuture = Concurrent.io { api.getConfig() }

Concurrent.io {
    val user = ConcurrentUtils.await(userFuture, 5_000L)
    val config = ConcurrentUtils.await(configFuture, 5_000L)
    Concurrent.main { render(user, config) }
}
```

当前 `ConcurrentUtils` 没有 `awaitAll` 或 `awaitAny`，需要业务自己组合。

## 16. 混合后端模式

可以按任务类型选择不同后端。

```kotlin
ConcurrentFactory.useMixed(
    mapOf(
        DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
        DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL,
        DispatcherType.BACKGROUND to ConcurrentFactory.BackendType.COROUTINE,
        DispatcherType.MAIN to ConcurrentFactory.BackendType.COROUTINE
    )
)
```

之后业务调用不用变化：

```kotlin
Concurrent.io { readFile() }          // 使用 IO 配置的后端
Concurrent.compute { resizeBitmap() } // 使用 COMPUTE 配置的后端
Concurrent.launchIo { flow.collect { } }
```

## 17. 自定义 TaskDispatcher

可以注册自定义分发器覆盖某个 `DispatcherType`。

```kotlin
class LoggingDispatcher(
    private val delegate: TaskDispatcher
) : TaskDispatcher {
    override val name: String = "logging-${delegate.name}"
    override val supportsCoroutineNative: Boolean = delegate.supportsCoroutineNative

    override fun execute(task: () -> Unit) {
        delegate.execute {
            val start = System.currentTimeMillis()
            try {
                task()
            } finally {
                Log.d("Concurrent", "$name cost=${System.currentTimeMillis() - start}ms")
            }
        }
    }

    override fun <T> submit(task: () -> T): Future<T> {
        return delegate.submit(task)
    }

    override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
        return delegate.schedule(task, delayMs)
    }
}

val origin = Concurrent.get(DispatcherType.IO)
ConcurrentFactory.register(DispatcherType.IO, LoggingDispatcher(origin))
```

如果自定义分发器也要原生支持 `suspend`，需要实现 `CoroutineTaskDispatcher`。

## 18. 获取底层 Dispatcher

获取统一分发器：

```kotlin
val dispatcher: TaskDispatcher = Concurrent.get(DispatcherType.IO)
dispatcher.execute { doWork() }
```

获取协程 dispatcher：

```kotlin
val ioDispatcher = Concurrent.getCoroutineDispatcher(DispatcherType.IO)

CoroutineScope(SupervisorJob() + ioDispatcher).launch {
    doSuspendWork()
}
```

`getCoroutineDispatcher` 在协程后端下返回原生 dispatcher；在线程池后端下返回由线程池桥接出来的 dispatcher。

## 19. 生命周期和释放

页面场景优先用 AndroidX 生命周期：

```kotlin
class Page : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val data = Concurrent.ioSuspend { repository.load() }
            render(data)
        }
    }
}
```

ViewModel 场景：

```kotlin
class PageViewModel : ViewModel() {
    fun load() {
        viewModelScope.launch {
            val data = Concurrent.ioSuspend { repository.load() }
            _state.value = data
        }
    }
}
```

普通类场景：

```kotlin
class WorkerOwner {
    private val scope = Concurrent.createScope(DispatcherType.BACKGROUND, "worker-owner")

    fun start() {
        scope.launch { doWork() }
    }

    fun destroy() {
        scope.cancel()
    }
}
```

进程退出或测试清理时，可以关闭已注册的分发器：

```kotlin
ConcurrentFactory.shutdown()
```

`shutdown()` 只会关闭 `ConcurrentFactory` 注册表中的分发器。业务自己创建的 `ConcurrentScope` 仍然应该由业务 owner 调用 `cancel()`。

## 20. 异常处理

普通 `Future` 任务：

```kotlin
val future = Concurrent.io {
    api.getUser()
}

Concurrent.io {
    try {
        val user = future.get()
        Concurrent.main { render(user) }
    } catch (e: ExecutionException) {
        val realCause = e.cause
        Concurrent.main { showError(realCause) }
    }
}
```

`suspend` 任务：

```kotlin
Concurrent.launchIo {
    try {
        val user = api.getUser()
        Concurrent.mainSuspend { render(user) }
    } catch (e: IOException) {
        Concurrent.mainSuspend { showError(e) }
    }
}
```

`ConcurrentScope` 默认使用 `SupervisorJob`，一个子任务失败不会取消同一个 scope 下的其他子任务。需要统一处理异常时，在每个 `launch` 里 `try/catch`，或者在业务层添加 `CoroutineExceptionHandler` 后自行创建协程作用域。

## 21. Java 调用

Kotlin 函数类型在 Java 中需要返回 `Unit.INSTANCE`。

```java
import com.itg.concurrent.Concurrent;
import com.itg.concurrent.ConcurrentFactory;
import com.itg.concurrent.DispatcherType;
import com.itg.concurrent.util.ConcurrentUtils;
import java.util.concurrent.Future;
import kotlin.Unit;

ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL);

Future<String> future = Concurrent.io(() -> api.fetch());

Concurrent.io(() -> {
    String result = ConcurrentUtils.await(future, 5000L);
    Concurrent.main(() -> {
        render(result);
        return Unit.INSTANCE;
    });
    return Unit.INSTANCE;
});

Concurrent.mainDelayed(() -> {
    showTip();
    return Unit.INSTANCE;
}, 300L);
```

Java 调用 `suspend` 风格 API 不方便，Java 侧优先使用 `Future` 风格 API。

## 22. 常见错误

### 在 `Concurrent.io` 中调用 `collect`

错误：

```kotlin
Concurrent.io {
    flow.collect { }
}
```

正确：

```kotlin
Concurrent.launchIo {
    flow.collect { }
}
```

### 在主线程 `await`

错误：

```kotlin
val result = ConcurrentUtils.await(future)
```

如果这段代码运行在主线程，会阻塞 UI。应该放到后台：

```kotlin
Concurrent.io {
    val result = ConcurrentUtils.await(future)
    Concurrent.main { render(result) }
}
```

### 忘记取消长期 scope

错误：

```kotlin
class Manager {
    private val scope = Concurrent.createScope(DispatcherType.IO)
}
```

正确：

```kotlin
class Manager {
    private val scope = Concurrent.createScope(DispatcherType.IO)

    fun release() {
        scope.cancel()
    }
}
```

### 把阻塞任务放到主线程

错误：

```kotlin
Concurrent.launchMain {
    Thread.sleep(3000L)
}
```

正确：

```kotlin
Concurrent.launchIo {
    val data = blockingLoad()
    Concurrent.mainSuspend { render(data) }
}
```

## 23. API 速查表

### Concurrent

| API | 返回 | 说明 |
| --- | --- | --- |
| `main(task)` | `Unit` | 主线程执行，已在主线程时直接执行 |
| `io<T>(task)` | `Future<T>` | I/O 分发器执行普通任务 |
| `compute<T>(task)` | `Future<T>` | 计算分发器执行普通任务 |
| `background<T>(task)` | `Future<T>` | 后台分发器执行普通任务 |
| `mainDelayed(task, delayMs)` | `Future<*>` | 主线程延迟执行 |
| `ioDelayed(task, delayMs)` | `Future<*>` | I/O 延迟执行 |
| `backgroundDelayed(task, delayMs)` | `Future<*>` | 后台延迟执行 |
| `createScope(type, name)` | `ConcurrentScope` | 创建可手动取消的托管协程 scope |
| `launch(type, name, block)` | `Job` | 一次性启动 suspend 任务 |
| `launchIo(name, block)` | `Job` | 在 IO 分发器启动 suspend 任务 |
| `launchCompute(name, block)` | `Job` | 在 COMPUTE 分发器启动 suspend 任务 |
| `launchBackground(name, block)` | `Job` | 在 BACKGROUND 分发器启动 suspend 任务 |
| `launchMain(name, block)` | `Job` | 在 MAIN 分发器启动 suspend 任务 |
| `ioSuspend(task)` | `T` | suspend 环境中切到 IO |
| `computeSuspend(task)` | `T` | suspend 环境中切到 COMPUTE |
| `backgroundSuspend(task)` | `T` | suspend 环境中切到 BACKGROUND |
| `mainSuspend(task)` | `T` | suspend 环境中切到 MAIN |
| `get(type)` | `TaskDispatcher` | 获取底层任务分发器 |
| `getCoroutineDispatcher(type)` | `CoroutineDispatcher` | 获取或桥接协程 dispatcher |

### ConcurrentScope

| API | 返回 | 说明 |
| --- | --- | --- |
| `launch(block)` | `Job` | 在 scope 内启动 suspend 任务 |
| `cancel()` | `Unit` | 取消 scope 及其子任务 |
| `close()` | `Unit` | 等同于 `cancel()` |

### ConcurrentFactory

| API | 说明 |
| --- | --- |
| `switchTo(backend)` | 全局切换后端，并清空手动注册表 |
| `useMixed(config)` | 按 `DispatcherType` 使用不同后端 |
| `register(type, dispatcher)` | 手动注册指定类型的分发器 |
| `isCoroutineAvailable()` | 检查协程后端是否可用 |
| `isThreadPoolAvailable()` | 检查线程池后端是否可用 |
| `getAvailableBackends()` | 返回当前 classpath 可用后端 |
| `shutdown()` | 关闭已注册且支持关闭的分发器，并清空注册表 |

### ConcurrentUtils

| API | 说明 |
| --- | --- |
| `isMainThread()` | 当前是否主线程 |
| `isBackgroundThread()` | 当前是否后台线程 |
| `assertMainThread(message)` | 断言主线程，否则抛异常 |
| `assertBackgroundThread(message)` | 断言后台线程，否则抛异常 |
| `await(future, timeoutMs)` | 阻塞等待 Future，取消或中断时返回 null |
| `cancel(future, mayInterrupt)` | 取消 Future |
| `sleep(ms)` | 后台线程阻塞 sleep，主线程调用会告警并返回 |
| `getCurrentThreadDescription()` | 当前线程描述 |
| `getCurrentThreadInfo()` | 当前线程信息 Map |