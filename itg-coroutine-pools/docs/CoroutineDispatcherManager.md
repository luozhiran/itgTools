# CoroutineDispatcherManager — 协程调度器管理器

## 概述

对标 `ThreadPoolManager`，提供预配置的 `CoroutineDispatcher` 实例和工厂方法。基于 Kotlin 协程的 `Dispatchers`，无需创建和管理 Java 线程池。

---

## 预置 Dispatcher 一览

| Dispatcher | 对应线程池 | 底层实现 | 适用场景 |
|-----------|-----------|---------|---------|
| `ioDispatcher` | `ioPool` | `Dispatchers.IO` | 网络、文件I/O、数据库 |
| `computeDispatcher` | `computePool` | `Dispatchers.Default` | 图片处理、加解密、计算 |
| `backgroundDispatcher` | `backgroundPool` | `Dispatchers.Default` | 通用后台任务 |
| `singleDispatcher` | `singlePool` | `Dispatchers.IO.limitedParallelism(1)` | 串行操作、状态同步 |
| `mainDispatcher` | `mainExecutor` | `Dispatchers.Main` | 主线程/UI线程 |

---

## 教程

### 1. 获取预置 Dispatcher

```kotlin
import com.itg.itg_coroutine_pools.manager.CoroutineDispatcherManager
import kotlinx.coroutines.*

// 获取 I/O Dispatcher
val ioDispatcher = CoroutineDispatcherManager.ioDispatcher

// 在协程中使用
CoroutineScope(ioDispatcher).launch {
    val data = fetchDataFromNetwork()
}

// 或者通过 CoroutineExecutor（更简洁）
CoroutineExecutor.io { doWork() }
```

### 2. 创建自定义 Dispatcher

#### 固定并发度

```kotlin
// 创建具有 4 个并发度的 Dispatcher（对标 newFixedPool）
val imageDispatcher = CoroutineDispatcherManager.newFixedDispatcher(
    name = "image-processor",
    parallelism = 4
)

// 用于批量图片处理
List(100) { index ->
    CoroutineScope(imageDispatcher).launch {
        processImage(index)
    }
}
// 最多 4 个协程同时执行，其余排队
```

#### 单线程串行

```kotlin
// 确保数据库写入严格顺序
val dbDispatcher = CoroutineDispatcherManager.newSingleDispatcher(
    name = "db-writer"
)

fun saveOrder(order: Order) {
    CoroutineScope(dbDispatcher).launch {
        database.orderDao().insert(order)
    }
}
// 即使同时调用 100 次 saveOrder()，也会按顺序依次写入
```

#### 从 Java Executor 桥接

```kotlin
import java.util.concurrent.Executors

// 已有 Java 线程池 → 转 CoroutineDispatcher
val legacyPool = Executors.newFixedThreadPool(8)
val dispatcher = CoroutineDispatcherManager.fromExecutor(legacyPool)

// 在协程中使用 Java 线程池
CoroutineScope(dispatcher).launch {
    doWork()
}
```

#### 自定义线程参数

```kotlin
// 精细控制：核心线程数、最大线程数、线程名、优先级
val customDispatcher = CoroutineDispatcherManager.newCustomDispatcher(
    corePoolSize = 2,
    maxPoolSize = 8,
    name = "upload-worker",
    priority = Thread.NORM_PRIORITY
)
```

### 3. 在 ViewModel 中使用

```kotlin
class MyViewModel : ViewModel() {
    private val ioDispatcher = CoroutineDispatcherManager.ioDispatcher

    fun loadData() {
        viewModelScope.launch(ioDispatcher) {
            val result = repository.fetchData()
            // 自动回到主线程（因为 viewModelScope 默认在主线程）
            _uiState.value = result
        }
    }
}
```

### 4. 在 Java 中使用

```java
import com.itg.itg_coroutine_pools.manager.CoroutineDispatcherManager;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;

CoroutineScope scope = CoroutineScopeFactory.create();

// 获取 I/O Dispatcher
CoroutineDispatcher ioDispatcher = CoroutineDispatcherManager.getIoDispatcher();

// 不支持直接使用 launch（需要 Kotlin 协程 Builder）
// 推荐通过 CoroutineExecutor 桥接：
CoroutineExecutor.io(() -> {
    doWork();
    CoroutineExecutor.main(() -> updateUI());
    return Unit.INSTANCE;
});
```

### 5. 调试：打印所有 Dispatcher 信息

```kotlin
CoroutineDispatcherManager.printAllInfo()
// 输出:
// [ioDispatcher] type=LimitingDispatcher
// [computeDispatcher] type=DefaultScheduler
// [backgroundDispatcher] type=DefaultScheduler
// [singleDispatcher] type=LimitingDispatcher
// [mainDispatcher] type=HandlerContext
```

### 6. 带命名的线程

```kotlin
// 使用 NamedThreadFactory 创建具有有意义名称的线程
val factory = CoroutineDispatcherManager.NamedThreadFactory(
    prefix = "media-transcoder",
    priority = Thread.NORM_PRIORITY
)
val pool = ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS,
    LinkedBlockingQueue(), factory)
val dispatcher = CoroutineDispatcherManager.fromExecutor(pool)

// 线程名: media-transcoder-1, media-transcoder-2, ...
// 在 Android Studio Profiler 中可清晰识别
```

---

## API 参考

| 方法 | 说明 |
|------|------|
| `ioDispatcher` | I/O 密集型 Dispatcher |
| `computeDispatcher` | CPU 密集型 Dispatcher |
| `backgroundDispatcher` | 通用后台 Dispatcher |
| `singleDispatcher` | 单线程串行 Dispatcher |
| `mainDispatcher` | 主线程 Dispatcher |
| `newFixedDispatcher(name, parallelism)` | 创建固定并发度 Dispatcher |
| `newSingleDispatcher(name)` | 创建单线程 Dispatcher |
| `fromExecutor(executor)` | 从 Java Executor 桥接 |
| `newCustomDispatcher(core, max, name, priority)` | 自定义线程参数 Dispatcher |
| `getDispatcherInfo(dispatcher)` | 获取 Dispatcher 描述 |
| `printAllInfo()` | 打印所有预置 Dispatcher 信息 |
| `NamedThreadFactory(prefix, priority)` | 带命名线程工厂 |
