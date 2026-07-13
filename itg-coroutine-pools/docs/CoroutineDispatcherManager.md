# CoroutineDispatcherManager — 协程调度器管理器

对标 `ThreadPoolManager`，提供预配置 `CoroutineDispatcher` 和工厂方法。

## 预置 Dispatcher

| Dispatcher | 底层 | 适用 |
|-----------|------|------|
| `ioDispatcher` | `Dispatchers.IO` | 网络、文件、DB |
| `computeDispatcher` | `Dispatchers.Default` | 图片、加解密 |
| `backgroundDispatcher` | `Dispatchers.Default` | 通用后台 |
| `singleDispatcher` | `limitedParallelism(1)` | 串行 |
| `mainDispatcher` | `Dispatchers.Main` | UI |

## 教程

### 创建自定义 Dispatcher

```kotlin
// 固定并发度 4
val img = CoroutineDispatcherManager.newFixedDispatcher("image", parallelism = 4)
// 单线程串行
val db = CoroutineDispatcherManager.newSingleDispatcher("db-writer")
// Java Executor 桥接
val legacy = CoroutineDispatcherManager.fromExecutor(Executors.newFixedThreadPool(8))
// 自定义参数
val custom = CoroutineDispatcherManager.newCustomDispatcher(2, 8, "upload", Thread.NORM_PRIORITY)
```

### ViewModel 集成

```kotlin
class MyViewModel : ViewModel() {
    fun load() = viewModelScope.launch(CoroutineDispatcherManager.ioDispatcher) {
        val data = repository.fetch()
        _state.value = data
    }
}
```

### 线程命名

```kotlin
val factory = CoroutineDispatcherManager.NamedThreadFactory("media-transcoder")
val pool = ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, LinkedBlockingQueue(), factory)
val dispatcher = CoroutineDispatcherManager.fromExecutor(pool)
// 线程名: media-transcoder-1, media-transcoder-2, ...
```

## API

| 属性/方法 | 说明 |
|------|------|
| `ioDispatcher` / `computeDispatcher` / `backgroundDispatcher` | 预置 Dispatcher |
| `singleDispatcher` / `mainDispatcher` | 串行/主线程 Dispatcher |
| `newFixedDispatcher(name, n)` | 固定并发度 |
| `newSingleDispatcher(name)` | 单线程 |
| `fromExecutor(executor)` | Java 桥接 |
| `newCustomDispatcher(core, max, name, pri)` | 自定义 |
| `getDispatcherInfo(d)` / `printAllInfo()` | 调试 |
