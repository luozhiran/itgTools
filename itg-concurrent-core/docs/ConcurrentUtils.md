# ConcurrentUtils — 通用并发工具

后端无关的公共方法。无论底层是线程池还是协程，行为一致。

## 教程

### 线程检测

```kotlin
ConcurrentUtils.isMainThread()
ConcurrentUtils.isBackgroundThread()
ConcurrentUtils.assertMainThread("UI only")
ConcurrentUtils.assertBackgroundThread("Network on background")
```

### Future 辅助

```kotlin
val r = ConcurrentUtils.await(future, timeoutMs = 5000L)
ConcurrentUtils.cancel(future)
```

### 安全阻塞休眠

```kotlin
// 后台线程正常阻塞，主线程自动跳过+警告
ConcurrentUtils.sleep(1000)
```

### 线程信息

```kotlin
println(ConcurrentUtils.getCurrentThreadDescription())
// main (id=1, main=true)
val info = ConcurrentUtils.getCurrentThreadInfo()
```

## API

| 方法 | 说明 |
|------|------|
| `isMainThread()` / `isBackgroundThread()` | 检测 |
| `assertMainThread(msg)` / `assertBackgroundThread(msg)` | 断言 |
| `await(f, ms)` / `cancel(f)` | Future 辅助 |
| `sleep(ms)` | 安全休眠 |
| `getCurrentThreadDescription()` / `getCurrentThreadInfo()` | 调试 |
