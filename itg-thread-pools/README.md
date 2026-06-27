# ITG Thread Pools — Android 线程池工具库

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/android-5.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](./LICENSE)

ITG Thread Pools 是一个专为 Android 设计的线程池管理库，提供预配置线程池、语义化任务提交 API、优先级调度、HandlerThread/Looper 管理、线程工具函数等一站式多线程解决方案。同时支持 Java Executor 线程池和 Android Handler/Looper 消息机制，API 简洁统一。

---

## 目录

- [核心概念](#核心概念)
- [快速开始](#快速开始)
  - [1. 添加依赖](#1-添加依赖)
  - [2. 基本使用](#2-基本使用)
- [架构概览](#架构概览)
- [详细教程](#详细教程)
  - [1. 线程池管理 — ThreadPoolManager](#1-线程池管理--threadpoolmanager)
  - [2. Handler 线程管理 — HandlerManager](#2-handler-线程管理--handlermanager)
  - [3. 任务执行器 — TaskExecutor](#3-任务执行器--taskexecutor)
  - [4. 线程工具 — ThreadUtils](#4-线程工具--threadutils)
- [两种并发模型对比](#两种并发模型对比)
- [线程池选择指南](#线程池选择指南)
- [最佳实践](#最佳实践)
- [线程安全与注意事项](#线程安全与注意事项)
- [常见问题](#常见问题)
- [API 参考](#api-参考)
- [更新日志](#更新日志)
- [许可证](#许可证)

---

## 核心概念

### 为什么需要线程池管理库？

Android 开发中，多线程管理面临以下挑战：

| 痛点 | 传统做法 | 本库方案 |
|------|----------|----------|
| 线程创建开销大 | `new Thread { }.start()` | 预创建线程，复用，零开销 |
| 线程数失控导致 OOM | 手动管理 | 统一管理，自动限流 |
| 任务难以取消 | 维护标志位 | `future.cancel()` 一行搞定 |
| 线程切换代码冗长 | Handler/LiveData层层嵌套 | 语义化 API: `io {}` → `main {}` |
| 缺乏优先级控制 | 单独实现 | 内置 Priority，队列自动排序 |
| 调试困难 | 线程名 `Thread-42` | 有意义的命名 `itg-io-3` |

### 四大组件

```
┌─────────────────────────────────────────────────┐
│                  TaskExecutor                     │
│   语义化 API: io { }, compute { }, main { }      │
│   优先级 / 延迟 / 定时 / Future / 批量等待        │
└─────┬───────────────────────────────┬───────────┘
      │ 基于 Executor                  │ 基于 Looper
┌─────▼──────────────────┐  ┌─────────▼───────────┐
│   ThreadPoolManager    │  │    HandlerManager    │
│   I/O池·计算池·后台池   │  │  HandlerThread管理  │
│   自定义工厂·生命周期    │  │  Message/消息传递   │
└─────┬──────────────────┘  └─────────┬───────────┘
      └──────────────┬───────────────┘
                     │ 辅助
      ┌──────────────▼───────────────┐
      │          ThreadUtils         │
      │ 线程检测·断言·Looper·优先级   │
      └──────────────────────────────┘
```

---

## 快速开始

### 1. 添加依赖

在 `settings.gradle.kts` 中确认已包含模块：

```kotlin
include(":itg-thread-pools")
```

在 app 模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(project(":itg-thread-pools"))
}
```

### 2. 基本使用

#### 线程切换（最常用）

```kotlin
import com.itg.itg_thread_pools.executor.TaskExecutor

// 在 I/O 线程加载数据，完成后切回主线程更新 UI
TaskExecutor.io {
    val data = api.fetchUserProfile()
    TaskExecutor.main {
        nameTextView.text = data.name
        avatarImageView.setImageBitmap(data.avatar)
    }
}
```

#### 计算密集型任务

```kotlin
// 图片处理用计算线程池
TaskExecutor.compute {
    val blurred = BitmapEffectUtils.stackBlur(originalBitmap, 20)
    TaskExecutor.main {
        imageView.setImageBitmap(blurred)
    }
}
```

#### 有返回值 + 超时控制

```kotlin
TaskExecutor.io {
    val future = TaskExecutor.compute<Int> {
        // 耗时计算
        fibonacci(40)
    }
    try {
        val result = TaskExecutor.await(future, timeout = 5, unit = TimeUnit.SECONDS)
        TaskExecutor.main { showResult(result) }
    } catch (e: TimeoutException) {
        TaskExecutor.main { showTimeoutError() }
    } catch (e: ExecutionException) {
        TaskExecutor.main { showError(e.cause?.message) }
    }
}
```

---

## 架构概览

### 📂 按功能快速跳转

> 点击下方链接，直达你需要的工具类教程。

| 你想做什么？ | 工具类 |
|-------------|--------|
| 🏭 **获取线程池** (I/O/计算/后台/单线程) | [ThreadPoolManager — 预置线程池](#11-预置线程池一览) |
| 🏗️ **创建自定义线程池** | [ThreadPoolManager — 自定义线程池](#13-创建自定义线程池) |
| ▶️ **提交任务** `io{}` / `compute{}` / `main{}` | [TaskExecutor — 语义化 API](#31-语义化-api-fire-and-forget) |
| 🔮 **提交有返回值任务** → `Future<T>` | [TaskExecutor — 有返回值](#32-有返回值-future) |
| ⏳ **延迟执行 / 定时任务** | [TaskExecutor — 延迟/定时](#34-延迟执行) |
| ⏸️ **取消任务** | [TaskExecutor — 取消](#取消-1) |
| ⏱️ **超时等待 Future 结果** | [TaskExecutor — 等待](#等待) |
| 🎯 **按优先级执行任务** | [TaskExecutor — 优先级](#33-优先级任务) |
| 🔁 **创建 HandlerThread** (带 Looper) | [HandlerManager — 创建获取](#22-创建和获取-handler) |
| 📨 **发送 Message** (what/arg1/arg2/obj) | [HandlerManager — 发送 Message](#24-发送-message传统-handler-风格) |
| 💤 **队列空闲时执行** (IdleHandler) | [HandlerManager — IdleHandler](#26-idlehandler队列空闲时执行) |
| 🧵 **判断当前线程** (主线程? 后台?) | [ThreadUtils — 线程检测](#41-线程检测) |
| 🛡️ **断言线程** (debug 期强制检查) | [ThreadUtils — 线程断言](#42-线程断言开发期检查) |
| 🔍 **调试线程信息** (名称/ID/栈) | [ThreadUtils — 线程信息](#43-线程信息调试用) |
| 🔄 **手动管理 Looper** (prepare/loop/quit) | [ThreadUtils — Looper](#46-looper-检测与操作) |
| 📊 **监控线程池状态** | [ThreadPoolManager — 监控](#14-线程池监控) |
| 🧹 **优雅关闭线程池/HandlerThread** | [ThreadPoolManager.shutdown](#15-生命周期管理) / [HandlerManager.quit](#28-生命周期管理) |

### 📦 包结构

```
com.itg.itg_thread_pools
├── manager/
│   ├── ThreadPoolManager.kt     — 线程池创建、配置、生命周期
│   └── HandlerManager.kt        — HandlerThread/Looper/Message 管理
├── executor/
│   └── TaskExecutor.kt          — 任务提交、调度、优先级、Future
└── utils/
    └── ThreadUtils.kt           — 线程检测、断言、Looper 工具
```

---

## 详细教程

### 1. 线程池管理 — ThreadPoolManager

`ThreadPoolManager` 是核心管理器，提供 5 个预配置线程池和多种工厂方法。

#### 1.1 预置线程池一览

| 线程池 | 类型 | 适用场景 | 核心/最大线程 |
|--------|------|----------|--------------|
| `ioPool` | Cached | 网络请求、文件I/O、数据库 | 0 / 64 |
| `computePool` | Fixed | 图片处理、加解密、解析 | CPU数 / CPU数 |
| `backgroundPool` | Fixed | 通用后台任务 | max(2, CPU数) |
| `singlePool` | Single | 序列化操作、状态同步 | 1 / 1 |
| `scheduledPool` | Scheduled | 定时任务、心跳、轮询 | 2 / 4 |

```kotlin
// 直接使用预置线程池
ThreadPoolManager.ioPool.execute { downloadFile() }
ThreadPoolManager.computePool.execute { processImage() }
ThreadPoolManager.backgroundPool.execute { syncData() }
ThreadPoolManager.singlePool.execute { writeToDatabase() }  // 保证顺序执行
```

#### 1.2 主线程执行器

```kotlin
// 任意线程中切回主线程
ThreadPoolManager.mainExecutor.execute {
    // 这里在主线程运行
    textView.text = "Done"
}
```

#### 1.3 创建自定义线程池

```kotlin
// ===== Cached 池: 适合大量短期异步任务 =====
val uploadPool = ThreadPoolManager.newCachedPool(
    name = "upload",
    maxSize = 16,
    keepAlive = 30L
)

// ===== Fixed 池: 适合已知并发量的任务 =====
val downloadPool = ThreadPoolManager.newFixedPool(
    name = "download",
    threads = 4
)

// ===== Single 池: 确保串行执行 =====
val serialPool = ThreadPoolManager.newSinglePool(name = "db-writer")

// ===== Scheduled 池: 定时/周期任务 =====
val timerPool = ThreadPoolManager.newScheduledPool(
    name = "timer",
    coreSize = 2
)

// ===== 完全自定义 =====
val customPool = ThreadPoolManager.newCustomPool(
    name = "custom",
    corePoolSize = 2,
    maxPoolSize = 8,
    keepAliveSec = 30L,
    queue = PriorityBlockingQueue(11),  // 支持优先级排序的队列
    priority = Thread.NORM_PRIORITY
)
```

#### 1.4 线程池监控

```kotlin
// 获取单个池的统计
val stats = ThreadPoolManager.getPoolStats(ThreadPoolManager.ioPool)
stats.forEach { (key, value) -> println("$key = $value") }
// activeCount=3, poolSize=5, completedTaskCount=1247, queueSize=0, ...

// 打印所有预置池的统计
ThreadPoolManager.printAllStats()
// [ioPool] active=3, pool=5, completed=1247, queue=0
// [computePool] active=2, pool=8, completed=892, queue=5
// ...
```

#### 1.5 生命周期管理

```kotlin
// 应用退出时优雅关闭
class MyApplication : Application() {
    override fun onTerminate() {
        super.onTerminate()
        ThreadPoolManager.shutdown()
        ThreadPoolManager.awaitTermination(timeout = 10, unit = TimeUnit.SECONDS)
    }
}

// 紧急情况下强制关闭
val remaining = ThreadPoolManager.shutdownNow()
Log.w("App", "${remaining.size} tasks were not executed")
```

---

### 2. Handler 线程管理 — HandlerManager

`HandlerManager` 基于 Android 原生的 `HandlerThread` + `Looper` 机制，提供**严格串行**的消息/任务处理，适用于需要 Looper 或 Message 传递的场景。

#### 2.1 核心概念: 线程池 vs HandlerThread

| 特性 | ThreadPoolManager | HandlerManager |
|------|-------------------|----------------|
| 底层机制 | Java Executor | Android Looper/HandlerThread |
| 线程模型 | 线程池（复用线程） | 每个名称独享一个线程 |
| 任务排序 | 取决于队列策略 | 严格 FIFO |
| Message 支持 | 无 | what/arg1/arg2/obj |
| 延迟/定时 | ScheduledExecutor | Handler.postDelayed / sendMessageDelayed |
| IdleHandler | 不支持 | 支持（队列空闲回调） |
| 适用场景 | 通用并发任务 | 需要 Looper、序列化操作、Message 通信 |

#### 2.2 创建和获取 Handler

```kotlin
// 获取或创建命名 HandlerThread（自动启动）
val dbHandler = HandlerManager.getOrCreate("db-writer")

// 带优先级的 HandlerThread
val bgHandler = HandlerManager.getOrCreate(
    name = "background",
    priority = Process.THREAD_PRIORITY_BACKGROUND
)

// 获取已有实例（不存在返回 null）
val existing = HandlerManager.getHandler("db-writer")
val existingThread = HandlerManager.getHandlerThread("db-writer")
val looper = HandlerManager.getLooper("db-writer")
```

#### 2.3 提交 Runnable

```kotlin
// 立即执行
HandlerManager.post("db-writer") {
    database.insert(record)
}

// 延迟 500ms 执行
HandlerManager.postDelayed("db-writer", 500) {
    database.clearCache()
}

// 在指定时间点执行
HandlerManager.postAtTime("db-writer", uptimeMs) {
    scheduleNotification()
}

// 插入到队列最前端
HandlerManager.postAtFront("db-writer") {
    urgentOperation()
}
```

#### 2.4 发送 Message（传统 Handler 风格）

```kotlin
// 空 Message（仅 what）
HandlerManager.sendMessage("worker", MSG_START)

// 带参数的 Message
HandlerManager.sendMessage(
    name = "worker",
    what = MSG_PROCESS_DATA,
    arg1 = userId,
    arg2 = actionType,
    obj = payloadObject
)

// 延迟发送
HandlerManager.sendMessageDelayed(
    name = "worker",
    what = MSG_RETRY,
    delayMs = 5000,
    arg1 = retryCount
)

// 自定义 Message 对象
val msg = Message.obtain().apply {
    what = MSG_CUSTOM
    data = bundle
}
HandlerManager.sendCustomMessage("worker", msg)
```

#### 2.5 与自定义 Handler.Callback 配合

```kotlin
// 创建带 Callback 的 HandlerThread（处理 Message）
val thread = HandlerThread("worker").apply { start() }
val handler = object : Handler(thread.looper) {
    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MSG_SAVE -> saveData(msg.obj as Data)
            MSG_DELETE -> deleteData(msg.arg1)
            MSG_QUIT -> {
                cleanup()
                thread.quitSafely()
            }
        }
    }
}
// 手动注册到 HandlerManager
// （推荐直接用 HandlerManager.getOrCreate 自动管理）
```

#### 2.6 IdleHandler（队列空闲时执行）

```kotlin
// 在主线程空闲时预加载
HandlerManager.addIdleHandler("worker", object : MessageQueue.IdleHandler {
    override fun queueIdle(): Boolean {
        prefetchNextPage()
        return false  // false = 仅执行一次后自动移除
    }
})

// Lambda 风格
HandlerManager.addIdleHandler("worker") {
    // 主线程消息队列空闲时执行
    triggerGarbageCollection()
    false  // 执行一次
}
```

#### 2.7 取消任务

```kotlin
// 移除指定 Runnable
HandlerManager.removeCallbacks("db-writer", myRunnable)

// 移除指定 what 的所有 Message
HandlerManager.removeMessages("db-writer", MSG_SAVE)

// 移除所有 callback 和 message
HandlerManager.removeAll("db-writer")

// 按 token 移除
val token = Object()
HandlerManager.postDelayed("db-writer", 1000) { /* ... */ }
// ...
HandlerManager.removeAll("db-writer", token)
```

#### 2.8 生命周期管理

```kotlin
// 安全退出单个线程（等待当前消息处理完毕）
HandlerManager.quit("db-writer", safely = true)

// 立即退出（可能丢失消息）
HandlerManager.quit("db-writer", safely = false)

// 退出所有已注册的 HandlerThread
// 建议在 Application.onTerminate() 调用
HandlerManager.quitAll()
```

#### 2.9 主线程便捷方法

```kotlin
// 切换到主线程
HandlerManager.postToMain {
    textView.text = "Updated"
}

// 主线程延迟执行（返回 Runnable 可用于取消）
val runnable = HandlerManager.postToMainDelayed(1000) {
    showToast("Done")
}

// 主线程发送 Message
HandlerManager.sendMainMessage(MSG_REFRESH)
HandlerManager.sendMainMessage(MSG_UPDATE, arg1 = userId, obj = data)
```

#### 2.10 调试

```kotlin
// 获取单个线程信息
val info = HandlerManager.getInfo("db-writer")
// { name=db-writer, exists=true, isAlive=true, threadId=1234, ... }

// 打印所有线程状态
HandlerManager.printAllInfo()
// === HandlerManager (3 threads) ===
//   [db-writer] alive=true, threadId=1234, queueIdle=true
//   [network] alive=true, threadId=1235, queueIdle=false
//   [cache] alive=true, threadId=1236, queueIdle=true

// 检查存活状态
if (HandlerManager.isAlive("db-writer")) {
    // 可以继续使用
}

// 列出所有名称
println(HandlerManager.getAllNames())  // [db-writer, network, cache]
```

---

### 3. 任务执行器 — TaskExecutor

`TaskExecutor` 封装了 `ThreadPoolManager`，提供语义化、类型安全的任务提交 API。

#### 3.1 语义化 API (Fire-and-Forget)

```kotlin
// I/O 密集: 网络、文件、数据库
TaskExecutor.io {
    val json = File("/sdcard/data.json").readText()
}

// CPU 密集: 计算、转换、加解密
TaskExecutor.compute {
    val encrypted = AES.encrypt(data, key)
}

// 通用后台
TaskExecutor.background {
    preloadNextPageCache()
}

// 串行执行: 保证一次只有一个任务运行
TaskExecutor.single {
    database.insertSequentially(record)
}

// 主线程: 安全地更新 UI
TaskExecutor.main {
    progressBar.isVisible = false
    recyclerView.adapter?.notifyDataSetChanged()
}
```

#### 3.2 有返回值 (Future)

```kotlin
// 异步计算，稍后获取结果
val future = TaskExecutor.compute<Bitmap> {
    BitmapEffectUtils.blur(originalBitmap, 15)!!
}

// 在另一个线程中等待结果
TaskExecutor.io {
    val result = TaskExecutor.await(future, timeout = 3, unit = TimeUnit.SECONDS)
    TaskExecutor.main { imageView.setImageBitmap(result) }
}
```

#### 3.3 优先级任务

```kotlin
// 创建支持优先级的线程池
val priorityPool = ThreadPoolManager.newFixedPool(
    name = "priority-task",
    threads = 4,
    queue = PriorityBlockingQueue(64)
)

// 按优先级提交
TaskExecutor.execute(priorityPool, Priority.LOW) {
    prefetchNextPage()  // 低优先级预加载
}

TaskExecutor.execute(priorityPool, Priority.HIGH) {
    loadCurrentPageData()  // 高优先级: 用户正在看的内容
}

// 简化写法: 直接提交到后台池
TaskExecutor.background(Priority.HIGH) { /* 高优先级 */ }
TaskExecutor.background(Priority.LOW)  { /* 低优先级 */ }
```

#### 3.4 延迟执行

```kotlin
// 主线程延迟 500ms 执行
val runnable = TaskExecutor.mainDelayed(500) {
    showTooltip()
}
// 必要时取消
TaskExecutor.cancelMain(runnable)

// 后台延迟执行
val future = TaskExecutor.ioDelayed(2000) {
    syncToServer()
}

// 取消延迟任务
TaskExecutor.cancel(future)
```

#### 3.5 定时/周期性任务

```kotlin
// 心跳: 每 30 秒执行一次（从任务开始时间算）
val heartbeat = TaskExecutor.scheduleAtFixedRate(
    initialDelayMs = 0,
    periodMs = 30_000
) {
    sendHeartbeat()
}

// 轮询: 每次执行完等待 5 秒再执行下一次
val polling = TaskExecutor.scheduleWithFixedDelay(
    initialDelayMs = 5000,
    delayMs = 5000
) {
    checkForNewMessages()
}

// 停止定时任务
TaskExecutor.cancel(heartbeat)
TaskExecutor.cancel(polling)
```

#### 3.6 批量等待

```kotlin
TaskExecutor.io {
    val futures = (1..5).map { index ->
        TaskExecutor.io<String> {
            api.fetchItem(index)
        }
    }

    try {
        // 等待全部完成（超时 10 秒）
        TaskExecutor.awaitAll(futures, timeout = 10, unit = TimeUnit.SECONDS)

        // 等待任意一个完成
        val firstIndex = TaskExecutor.awaitAny(futures, timeout = 5, unit = TimeUnit.SECONDS)
        Log.d("Result", "Task $firstIndex finished first")
    } catch (e: TimeoutException) {
        Log.e("Result", "Tasks timed out")
    }
}
```

#### 3.7 组合实战: 图片列表加载

```kotlin
fun loadImageList(urls: List<String>, onComplete: (List<Bitmap>) -> Unit) {
    TaskExecutor.io {
        val futures = urls.map { url ->
            TaskExecutor.io<Bitmap?> {
                try {
                    val bitmap = BitmapDecodeUtils.decodeFromUrl(url)
                    // 后台缩放到统一尺寸
                    bitmap?.let { BitmapTransformUtils.scaleToWidth(it, 600) }
                } catch (e: Exception) {
                    null  // 单张失败不影响整体
                }
            }
        }

        // 等待所有图片加载完成（最多等 30 秒）
        try {
            TaskExecutor.awaitAll(futures, timeout = 30, unit = TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            Log.w("ImageLoader", "Some images timed out")
        }

        // 收集成功的结果，切回主线程
        val results = futures.mapNotNull { f ->
            if (f.isDone) {
                try { f.get() } catch (_: Exception) { null }
            } else null
        }

        TaskExecutor.main {
            onComplete(results)
        }
    }
}
```

---

### 4. 线程工具 — ThreadUtils

`ThreadUtils` 提供线程检测、断言、信息获取以及 Android Looper 操作等辅助功能。

#### 4.1 线程检测

```kotlin
// 判断当前线程
if (ThreadUtils.isMainThread()) {
    // 直接更新 UI
    view.invalidate()
}

if (ThreadUtils.isBackgroundThread()) {
    // 可以执行耗时操作
    heavyComputation()
}
```

#### 4.2 线程断言（开发期检查）

```kotlin
// 确保在正确的线程被调用
class MyView : View {
    fun updateContent(text: String) {
        ThreadUtils.assertMainThread("updateContent must be on main thread")
        // ... 更新 UI
    }
}

class DataRepository {
    fun loadFromNetwork(): Data {
        ThreadUtils.assertBackgroundThread("Network call must be on background thread")
        // ... 网络请求
        return data
    }
}
```

#### 4.3 线程信息（调试用）

```kotlin
// 快速获取当前线程描述
println(ThreadUtils.getCurrentThreadDescription())
// 输出: itg-io-3 (id=142, main=false)

// 获取完整信息
val info = ThreadUtils.getCurrentThreadInfo()
// { name=itg-compute-2, id=145, priority=5, isMain=false, ... }

// 打印调用栈
ThreadUtils.logStackTrace("MyTag")
// D/MyTag: Thread: itg-io-5
//   at com.example.MyClass.myMethod(MyClass.kt:42)
//   at ...
```

#### 4.4 线程休眠

```kotlin
// 安全休眠（仅后台线程可用，主线程会打印警告）
ThreadUtils.sleep(1000)

// 可中断休眠
try {
    ThreadUtils.sleepInterruptibly(5000)
} catch (e: InterruptedException) {
    // 被中断，清理资源
    return
}
```

#### 4.5 线程优先级

```kotlin
// 在任务开始时降低优先级
TaskExecutor.io {
    ThreadUtils.setBackgroundPriority()  // 设置为后台进程优先级
    // 执行低优先级的预缓存任务
    precacheData()
}

// 只改变 Java 线程优先级
ThreadUtils.setCurrentThreadPriority(Thread.MIN_PRIORITY)
```

#### 4.6 Looper 检测与操作

```kotlin
// 检查当前线程是否有 Looper
if (!ThreadUtils.hasLooper()) {
    // 当前线程没有 Looper，不能创建 Handler
}

// 检查是否在指定 Looper 的线程上
val bgLooper = HandlerManager.getLooper("worker")
if (ThreadUtils.isCurrentLooper(bgLooper)) {
    // 当前正运行在 worker 线程上
}

// 获取 Looper 信息
val info = ThreadUtils.getLooperInfo()
// { hasLooper=true, thread=itg-io-3, isMainLooper=false, queueIdle=true }

// 获取主线程 Looper
val mainLooper = ThreadUtils.getMainLooper()

// 获取当前线程的 Looper（可能为 null）
val myLooper = ThreadUtils.getMyLooper()
```

#### 4.7 Looper 生命周期控制

```kotlin
// 方案 A: 手动管理 Looper 生命周期
thread {
    ThreadUtils.prepareLooper()
    val handler = Handler(Looper.myLooper()) { msg ->
        // 处理消息
        true
    }
    // 5 秒后自动退出
    handler.postDelayed({ ThreadUtils.quitLooper() }, 5000)
    ThreadUtils.loop()  // 阻塞直到 quit
}

// 方案 B: runWithLooper 一站式封装
thread {
    ThreadUtils.runWithLooper(timeoutMs = 30_000) { handler ->
        handler.post {
            doWork()
            ThreadUtils.quitLooper()  // 完成后退出
        }
    }
}

// 带空闲回调的用法
thread {
    ThreadUtils.runWithLooper(
        timeoutMs = 60_000,
        onIdle = {
            Log.d("Worker", "Message queue is idle")
        }
    ) { handler ->
        handler.post { task1() }
        handler.postDelayed({ task2() }, 1000)
    }
}
```

#### 4.8 综合实战: HandlerManager + ThreadUtils 协奏

```kotlin
// 场景: 串行处理网络响应，每 5 秒检查一次，空闲时预加载
class OrderProcessor {
    private val handlerName = "order-processor"

    fun start() {
        // 设置后台进程优先级（在 HandlerThread 内部调用）
        HandlerManager.post(handlerName) {
            ThreadUtils.setBackgroundPriority()
        }

        // 定时检查新订单
        HandlerManager.post(handlerName) {
            // 利用 Handler 延迟循环实现定时
            val checkRunnable = object : Runnable {
                override fun run() {
                    val orders = fetchNewOrders()
                    processOrders(orders)
                    // 5 秒后再次检查
                    HandlerManager.postDelayed(handlerName, 5000, this)
                }
            }
            checkRunnable.run()
        }

        // 空闲时预加载
        HandlerManager.addIdleHandler(handlerName) {
            prefetchNextPage()
            false  // 单次执行
        }
    }

    fun shutdown() {
        HandlerManager.quit(handlerName, safely = true)
    }
}
```

---

## 两种并发模型对比

ITG Thread Pools 提供两套互补的并发模型，可根据场景选择：

| 维度 | Executor (ThreadPoolManager) | Looper (HandlerManager) |
|------|------------------------------|--------------------------|
| **任务分发** | 池中任一空闲线程 | 固定绑定的线程 |
| **并发度** | 多任务并行 | 单线程串行 |
| **执行顺序** | 取决于队列，不保证顺序 | 严格 FIFO |
| **跨线程通信** | Future / 共享变量 | Message (what/arg1/arg2/obj) |
| **任务取消** | Future.cancel() | Handler.removeCallbacks/Messages |
| **延迟任务** | ScheduledExecutor | Handler.postDelayed / sendMessageDelayed |
| **空闲检测** | 不支持 | IdleHandler |
| **典型场景** | 网络并发请求、图片批处理 | 数据库串行写入、消息序列化、心跳 |

### 选择决策树

```
你的任务需要什么？
│
├─ 需要并行执行来提高吞吐量？
│  └─ 是 → ThreadPoolManager / TaskExecutor
│     ├─ I/O 密集型 → ioPool
│     ├─ CPU 密集型 → computePool
│     └─ 通用 → backgroundPool
│
├─ 需要严格顺序执行？
│  ├─ 且需要 Message 传递 → HandlerManager
│  └─ 仅需顺序执行 → singlePool 或 HandlerManager
│
├─ 需要 Looper？（如创建 Handler、监听消息队列）
│  └─ 是 → HandlerManager
│
├─ 需要 IdleHandler？（队列空闲回调）
│  └─ 是 → HandlerManager
│
└─ 不确定？
   └─ 默认: TaskExecutor.io {} / TaskExecutor.background {}
```

---

## 线程池选择指南

```
你的任务是什么？
│
├─ 涉及 I/O 操作？（网络、文件、数据库）
│  └─ 是 → ioPool / TaskExecutor.io { }
│     └─ 还要并行执行很多个？→ ioPool (Cached 池会自动扩容)
│
├─ 涉及 CPU 密集型计算？（图像处理、加解密、JSON解析）
│  └─ 是 → computePool / TaskExecutor.compute { }
│     └─ 线程数 = CPU 核心数，多一个都是浪费
│
├─ 需要严格按顺序执行？
│  └─ 是 → singlePool / TaskExecutor.single { }
│     └─ 如: 数据库 Write-Ahead Log
│
├─ 定时/周期执行？
│  └─ 是 → scheduledPool / TaskExecutor.scheduleXXX()
│     └─ 如: 心跳、轮询、定时刷新
│
└─ 不知道用什么？
   └─ 用 backgroundPool / TaskExecutor.background { }
      └─ 通用后备方案，覆盖面广
```

---

## 最佳实践

### 1. 使用 `io { }` → `main { }` 模式

```kotlin
// ✅ 推荐: 清晰的线程切换
fun loadData() {
    showLoading()  // 主线程
    TaskExecutor.io {
        val data = repository.fetchData()
        TaskExecutor.main {
            hideLoading()
            displayData(data)
        }
    }
}

// ❌ 避免: 在回调中手动管理线程
fun loadData() {
    thread {
        val data = repository.fetchData()
        runOnUiThread {
            displayData(data)
        }
    }
}
```

### 2. 合理使用 `singlePool`

```kotlin
// ✅ 推荐: 确保数据库写入顺序
class OrderRepository {
    fun saveOrder(order: Order) {
        TaskExecutor.single {
            database.orderDao().insert(order)
        }
    }
}
// 即使多个地方同时调用 saveOrder()，也会按提交顺序依次写入
```

### 3. 总是指定超时

```kotlin
// ✅ 推荐: 有超时的等待
TaskExecutor.io {
    val future = TaskExecutor.compute<Result> { heavyWork() }
    try {
        val result = TaskExecutor.await(future, timeout = 10, unit = TimeUnit.SECONDS)
        TaskExecutor.main { showResult(result) }
    } catch (e: TimeoutException) {
        TaskExecutor.main { showTimeoutFallback() }
    }
}

// ❌ 避免: 无限等待
TaskExecutor.await(future)  // 可能永远阻塞
```

### 4. 及时取消不需要的任务

```kotlin
class MyViewModel : ViewModel() {
    private var loadFuture: Future<*>? = null

    fun loadData() {
        loadFuture?.let { TaskExecutor.cancel(it) }  // 取消上一次
        loadFuture = TaskExecutor.io<List<Item>> {
            repository.fetchItems()
        }
    }

    override fun onCleared() {
        loadFuture?.let { TaskExecutor.cancel(it) }
    }
}
```

### 5. 应用退出时关闭线程池

```kotlin
class MyApplication : Application() {
    override fun onTerminate() {
        super.onTerminate()
        ThreadPoolManager.shutdown()
        try {
            if (!ThreadPoolManager.awaitTermination(5, TimeUnit.SECONDS)) {
                ThreadPoolManager.shutdownNow()
            }
        } catch (e: InterruptedException) {
            ThreadPoolManager.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
```

---

## 线程安全与注意事项

### 线程安全

| 组件 | 线程安全性 |
|------|-----------|
| `ThreadPoolManager` | ✅ 安全 — 无状态单例，线程池本身线程安全 |
| `TaskExecutor` | ✅ 安全 — 无状态，所有状态由线程池管理 |
| `ThreadUtils` | ✅ 安全 — 纯函数，无共享可变状态 |
| `PriorityRunnable` | ✅ 安全 — 不可变对象 |
| `PriorityFutureTask` | ✅ 安全 — 继承自 FutureTask |

### 注意事项

1. **不要在主线程调用 `await()`** — 会阻塞 UI 导致 ANR
   ```kotlin
   // ❌ 错误: 主线程调用 await
   override fun onCreate() {
       val future = TaskExecutor.compute<Int> { 42 }
       val result = TaskExecutor.await(future)  // ANR!
   }

   // ✅ 正确: 在后台线程调用 await
   TaskExecutor.io {
       val future = TaskExecutor.compute<Int> { 42 }
       val result = TaskExecutor.await(future)
   }
   ```

2. **`sleep()` 不能在主线程调用** — 库内已做保护，会打印警告日志

3. **长时间运行的任务避免占用 computePool** — computePool 线程数固定，一个耗时任务会占用一个线程

4. **注意 Future 引用的生命周期** — 长时间持有的 Future 可能导致其关联的对象无法 GC

5. **关闭线程池时任务可能丢失** — `shutdownNow()` 会取消正在执行的任务，确保重要任务先完成再关闭

---

## 常见问题

### Q: 和 Kotlin Coroutines 有什么区别？什么时候用这个库？

A: 本库是基于 Java Executor 框架的传统线程池封装，适用于：
- 不使用 Coroutines 的项目
- 需要精细控制线程池参数（核心数、最大数、队列策略等）
- Java 项目或混合项目
- 已有大量 `Thread`/`Executor` 代码需要迁移

如果你已在用 Coroutines，推荐直接用 `Dispatchers.IO` / `Dispatchers.Default` 等，功能等价。

### Q: computePool 的线程数为什么等于 CPU 核心数？

A: CPU 密集型任务主要消耗的是计算资源，线程数超过 CPU 核心数会导致频繁的上下文切换，反而降低吞吐量。

### Q: ioPool 为什么最大可以到 64 个线程？

A: I/O 密集型任务大部分时间在等待网络/磁盘，线程处于阻塞状态，不消耗 CPU。更多的线程可以并发等待更多 I/O，提高吞吐量。64 是经验值上限。

### Q: 如何取消一个正在执行的任务？

A:
```kotlin
val future = TaskExecutor.io<Int> {
    while (!Thread.interrupted()) {  // 需要任务内部配合检查
        // 分批处理
    }
    result
}
// 取消
TaskExecutor.cancel(future, mayInterrupt = true)
```
注意：`cancel()` + `mayInterrupt = true` 会设置线程的中断标志，但任务需要自己检查 `Thread.interrupted()` 才能响应。

### Q: Future 任务异常了怎么办？

A: `await()` 会抛出 `ExecutionException`，其中 `cause` 是原始异常：
```kotlin
try {
    val result = TaskExecutor.await(future, timeout = 5, unit = TimeUnit.SECONDS)
} catch (e: ExecutionException) {
    val originalError = e.cause  // 任务内部抛出的原始异常
    Log.e("App", "Task failed", originalError)
}
```

---

## API 参考

### ThreadPoolManager

| 方法 | 说明 |
|------|------|
| `ioPool` | I/O 密集型线程池 (Cached) |
| `computePool` | 计算密集型线程池 (Fixed=CPU数) |
| `backgroundPool` | 通用后台线程池 (Fixed) |
| `singlePool` | 单线程串行池 |
| `scheduledPool` | 定时调度线程池 |
| `mainExecutor` | 主线程 Executor |
| `newCachedPool(name, maxSize, ...)` | 创建 Cached 池 |
| `newFixedPool(name, threads, ...)` | 创建 Fixed 池 |
| `newSinglePool(name, ...)` | 创建 Single 池 |
| `newScheduledPool(name, coreSize, ...)` | 创建 Scheduled 池 |
| `newCustomPool(name, core, max, ...)` | 创建自定义池 |
| `getPoolStats(pool)` | 获取池统计信息 |
| `printAllStats()` | 打印所有预置池统计 |
| `shutdown()` | 优雅关闭所有预置池 |
| `shutdownNow()` | 立即关闭所有预置池 |
| `awaitTermination(timeout, unit)` | 等待所有池终止 |

### HandlerManager

| 方法 | 说明 |
|------|------|
| `mainHandler` | 主线程 Handler |
| `getOrCreate(name, priority)` | 获取/创建 HandlerThread 的 Handler |
| `getHandlerThread(name)` | 获取 HandlerThread 实例 |
| `getHandler(name)` | 获取 Handler 实例 |
| `getLooper(name)` | 获取 Looper |
| `post(name, task)` | 提交 Runnable |
| `postDelayed(name, delayMs, task)` | 延迟提交 Runnable |
| `postAtTime(name, uptimeMs, task)` | 指定时间点执行 |
| `postAtFront(name, task)` | 插入消息队列最前端 |
| `sendMessage(name, what)` | 发送空 Message |
| `sendMessage(name, what, arg1, arg2, obj)` | 发送带参数 Message |
| `sendMessageDelayed(name, what, delayMs, ...)` | 延迟发送 Message |
| `sendCustomMessage(name, message)` | 发送自定义 Message |
| `removeCallbacks(name, task)` | 移除指定 Runnable |
| `removeMessages(name, what)` | 移除特定 what 的 Message |
| `removeAll(name, token)` | 移除所有回调与消息 |
| `addIdleHandler(name, idleHandler)` | 添加队列空闲处理器 |
| `removeIdleHandler(name, idleHandler)` | 移除空闲处理器 |
| `quit(name, safely)` | 退出指定 HandlerThread |
| `quitAll()` | 退出所有 HandlerThread |
| `isAlive(name)` | 检查 HandlerThread 是否存活 |
| `getAllNames()` | 获取所有已注册名称 |
| `getCount()` | 获取已注册线程数 |
| `getInfo(name)` | 获取线程详细信息 |
| `printAllInfo()` | 打印所有线程状态 |
| `postToMain(task)` | 切换到主线程执行 |
| `postToMainDelayed(task, delayMs)` | 主线程延迟执行 |
| `sendMainMessage(what, ...)` | 发送主线程 Message |

### TaskExecutor

| 方法 | 说明 |
|------|------|
| `io(task)` | I/O 池执行（无返回值） |
| `io<T>(task): Future<T>` | I/O 池执行（有返回值） |
| `compute(task)` | 计算池执行（无返回值） |
| `compute<T>(task): Future<T>` | 计算池执行（有返回值） |
| `background(task)` | 后台池执行（无返回值） |
| `background<T>(task): Future<T>` | 后台池执行（有返回值） |
| `single(task)` | 单线程池执行 |
| `main(task)` | 主线程执行 |
| `execute(pool, priority, task)` | 按优先级提交 |
| `background(priority, task)` | 后台池按优先级提交 |
| `submitWithPriority(pool, priority, task)` | 提交优先级 Future |
| `mainDelayed(task, delayMs)` | 主线程延迟执行 |
| `ioDelayed(task, delayMs)` | I/O 池延迟执行 |
| `backgroundDelayed(task, delayMs)` | 后台池延迟执行 |
| `scheduleAtFixedRate(task, initial, period)` | 固定频率定时 |
| `scheduleWithFixedDelay(task, initial, delay)` | 固定延迟定时 |
| `cancel(future, mayInterrupt)` | 取消 Future |
| `cancelMain(runnable)` | 取消主线程延迟任务 |
| `cancelMainAll(token)` | 取消主线程所有匹配任务 |
| `await<T>(future, timeout, unit)` | 等待 Future 结果 |
| `awaitAll(futures, timeout, unit)` | 等待所有 Future |
| `awaitAny(futures, timeout, unit)` | 等待任一 Future |

### ThreadUtils

| 方法 | 说明 |
|------|------|
| `isMainThread()` | 判断是否主线程 |
| `isBackgroundThread()` | 判断是否后台线程 |
| `assertMainThread(msg)` | 断言主线程 |
| `assertBackgroundThread(msg)` | 断言后台线程 |
| `runOnUiThread(task)` | 切换到主线程执行 |
| `runOnUiThreadDelayed(task, delayMs)` | 延迟主线程执行 |
| `getCurrentThreadInfo()` | 获取线程详细信息 |
| `getCurrentThreadDescription()` | 获取线程简要描述 |
| `logStackTrace(tag, maxDepth)` | 打印当前调用栈 |
| `sleep(ms)` | 安全休眠 |
| `sleepInterruptibly(ms)` | 可中断休眠 |
| `setCurrentThreadPriority(priority)` | 设置 Java 线程优先级 |
| `setLowPriority()` | 设为最低优先级 |
| `setProcessThreadPriority(priority)` | 设置 Android 进程优先级 |
| `setBackgroundPriority()` | 设为后台进程优先级 |
| `getActiveThreadSummary()` | 获取活跃线程概要 |
| `hasLooper()` | 当前线程是否有 Looper |
| `isCurrentLooper(looper)` | 是否在指定 Looper 线程上 |
| `prepareLooper()` | 为当前线程准备 Looper |
| `loop()` | 启动 Looper 消息循环（阻塞） |
| `quitLooper(safely)` | 退出当前线程的 Looper |
| `runWithLooper(timeoutMs, onIdle, body)` | 一站式 Looper 生命周期 |
| `getLooperInfo()` | 获取当前 Looper 信息 |
| `getMainLooper()` | 获取主线程 Looper |
| `getMyLooper()` | 获取当前线程 Looper |

### Priority

| 枚举值 | 数值 | 说明 |
|--------|------|------|
| `LOW` | 0 | 低优先级 — 预加载、日志、缓存 |
| `NORMAL` | 1 | 默认优先级 — 常规任务 |
| `HIGH` | 2 | 高优先级 — 用户可见的异步操作 |
| `CRITICAL` | 3 | 最高优先级 — 紧急任务 |

---

## 更新日志

### v1.0.0 (2026-06)
- 🎉 初始版本发布
- ✨ 5 个预置线程池 (I/O、计算、后台、单线程、定时调度)
- ✨ 语义化任务提交 API（io/compute/background/single/main）
- ✨ 带优先级的任务调度 (PriorityBlockingQueue)
- ✨ Future 返回 + 超时控制 + 批量等待
- ✨ 延迟执行与定时任务
- ✨ HandlerThread/Looper 管理 (HandlerManager)
- ✨ Message 消息传递支持 (what/arg1/arg2/obj)
- ✨ IdleHandler 空闲处理支持
- ✨ Looper 生命周期控制与工具方法
- ✨ 带命名的线程工厂（便于调试）
- ✨ 线程检测、断言、信息获取工具
- ✨ CallerRunsPolicy 背压机制
- ✨ 线程池统计监控

---

## 许可证

```
MIT License

Copyright (c) 2026 ITG Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
