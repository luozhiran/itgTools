# ConcurrentUtils — 通用并发工具

## 概述

后端无关的公共工具方法。无论底层是线程池还是协程，这些方法的行为一致。

---

## 教程

### 1. 线程检测

```kotlin
import com.itg.concurrent.util.ConcurrentUtils

// 运行时不依赖任何后端，纯 Android API
if (ConcurrentUtils.isMainThread()) {
    view.invalidate()
}

if (ConcurrentUtils.isBackgroundThread()) {
    heavyWork()
}
```

### 2. 线程断言

```kotlin
class UiComponent {
    fun updateView() {
        // Debug 期间在错误线程调用会抛异常
        ConcurrentUtils.assertMainThread("updateView: UI thread required")
        textView.text = "Updated"
    }
}

class DataLayer {
    fun fetchFromServer(): Data {
        ConcurrentUtils.assertBackgroundThread("Network on background only")
        return httpClient.get("https://api.example.com/data")
    }
}
```

### 3. Future 辅助

```kotlin
// 阻塞等待（不可在主线程调用）
val future = Concurrent.io<Int> { calculate() }
val result = ConcurrentUtils.await(future, timeoutMs = 5000)

// 取消
ConcurrentUtils.cancel(future, mayInterrupt = true)
```

### 4. 安全阻塞休眠

```kotlin
// 后台线程：正常阻塞
CoroutineExecutor.io {
    ConcurrentUtils.sleep(1000)  // 阻塞 1 秒
}

// 主线程：自动跳过 + 打印警告（防止 ANR）
ConcurrentUtils.sleep(1000)
// W/ConcurrentUtils: sleep() called on main thread — this will cause ANR!
```

### 5. 线程信息调试

```kotlin
// 简要描述
println(ConcurrentUtils.getCurrentThreadDescription())
// 输出: main (id=1, main=true)

// 完整信息
val info = ConcurrentUtils.getCurrentThreadInfo()
// {
//   name=DefaultDispatcher-worker-3,
//   id=145,
//   priority=5,
//   isMain=false,
//   isDaemon=true,
//   isAlive=true,
//   isInterrupted=false,
//   state=RUNNABLE,
//   threadGroup=main
// }
```

---

## API 参考

| 方法 | 说明 |
|------|------|
| `isMainThread()` | 判断是否主线程 |
| `isBackgroundThread()` | 判断是否后台线程 |
| `assertMainThread(msg)` | 断言主线程（否则抛异常） |
| `assertBackgroundThread(msg)` | 断言后台线程（否则抛异常） |
| `await(future, timeoutMs)` | 阻塞等待 Future 结果 |
| `cancel(future, mayInterrupt)` | 取消 Future |
| `sleep(ms)` | 安全阻塞休眠（主线程跳过） |
| `getCurrentThreadDescription()` | 获取线程简要描述 |
| `getCurrentThreadInfo()` | 获取线程详细 Map |
