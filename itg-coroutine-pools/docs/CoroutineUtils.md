# CoroutineUtils — 协程工具类

对标 `ThreadUtils`。关键区别：`delay()` 非阻塞挂起，优于 `Thread.sleep()`。

## 教程

### 线程检测

```kotlin
CoroutineUtils.isMainThread()
CoroutineUtils.isBackgroundThread()
CoroutineUtils.assertMainThread("UI only")
CoroutineUtils.assertBackgroundThread("Network on background")
```

### 主线程切换

```kotlin
CoroutineUtils.runOnUiThread { updateUI() }
CoroutineUtils.runOnUiThreadDelayed(1000L) { showToast() }
```

### 非阻塞 delay vs 阻塞 sleep

```kotlin
// ✅ 协程挂起（非阻塞）— 1 个线程可承载 1000+ 协程
CoroutineExecutor.io {
    CoroutineUtils.delay(1000)  // suspend，不占线程
}

// ⚠️ 阻塞线程 — 仅后台可用
CoroutineUtils.sleep(500)  // 主线程自动跳过 + 警告
```

### 优先级

```kotlin
CoroutineUtils.setBackgroundPriority()       // Android 后台优先级
CoroutineUtils.setLowPriority()              // Java 最低优先级
CoroutineUtils.setCurrentThreadPriority(Thread.MIN_PRIORITY)
```

### 调试

```kotlin
println(CoroutineUtils.getCurrentThreadDescription())
// DefaultDispatcher-worker-1 (id=142, main=false)
CoroutineUtils.logStackTrace("MyTag", maxDepth = 10)
println(CoroutineUtils.getActiveThreadSummary())
```

## API

| 方法 | 说明 |
|------|------|
| `isMainThread()` / `isBackgroundThread()` | 线程检测 |
| `assertMainThread(msg)` / `assertBackgroundThread(msg)` | 断言 |
| `runOnUiThread(task)` / `runOnUiThreadDelayed(ms, task)` | 主线程 |
| `delay(ms)` | **suspend** 非阻塞延迟 |
| `sleep(ms)` | 阻塞休眠 |
| `setCurrentThreadPriority(p)` / `setLowPriority()` | Java 优先级 |
| `setProcessThreadPriority(p)` / `setBackgroundPriority()` | Android 优先级 |
| `getCurrentThreadInfo()` / `getCurrentThreadDescription()` | 调试 |
| `logStackTrace(tag, depth)` | 打印栈 |
