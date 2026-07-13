# Concurrent — 统一入口 + 工厂

`Concurrent` 是中间件门面，API 与 `TaskExecutor`/`CoroutineExecutor` 一致。`ConcurrentFactory` 负责后端注册、切换、生命周期。

## Concurrent 教程

### 基础用法

```kotlin
Concurrent.io { }         // I/O
Concurrent.compute { }    // 计算
Concurrent.background { } // 后台
Concurrent.single { }     // 串行
Concurrent.main { }       // 主线程
```

### Future

```kotlin
val f = Concurrent.io<Int> { calculate() }
val r = ConcurrentUtils.await(f, timeoutMs = 5000)
```

### 延迟

```kotlin
Concurrent.mainDelayed(2000L) { show() }
Concurrent.ioDelayed(5000L) { sync() }
```

### Suspend

```kotlin
lifecycleScope.launch {
    val d = Concurrent.ioSuspend { api.fetch() }
    updateUI(d)
}
```

## ConcurrentFactory 教程

### 后端切换

```kotlin
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
ConcurrentFactory.useMixed(mapOf(
    DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
    DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL
))
```

### 自定义分发器

```kotlin
ConcurrentFactory.register(DispatcherType.IO, myCustomDispatcher)
```

### 检测后端

```kotlin
ConcurrentFactory.isCoroutineAvailable()
ConcurrentFactory.getAvailableBackends()
```

## API

### Concurrent

| 方法 | 说明 |
|------|------|
| `io { }` / `compute { }` / `background { }` / `single { }` / `main { }` | Fire-and-forget |
| `io<T> { }: Future<T>` / `compute<T> { }` / `background<T> { }` | 有返回值 |
| `mainDelayed(ms) { }` / `ioDelayed(ms) { }` / `backgroundDelayed(ms) { }` | 延迟 |
| `scheduleAtFixedRate(...)` / `scheduleWithFixedDelay(...)` | 定时 |
| `ioSuspend { }` / `computeSuspend { }` / `mainSuspend { }` | suspend |
| `get(type)` / `getCoroutineDispatcher(type)` | 分发器 |

### ConcurrentFactory

| 方法 | 说明 |
|------|------|
| `switchTo(backend)` | 全局切换 |
| `useMixed(config)` | 混合模式 |
| `register(type, disp)` | 注册自定义 |
| `isCoroutineAvailable()` / `isThreadPoolAvailable()` | 可用性 |
| `getAvailableBackends()` | 列表 |
| `shutdown()` | 关闭 |
