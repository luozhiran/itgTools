# CoroutineUtils — 协程工具类

## 概述

对标 `ThreadUtils`，提供线程检测、断言、延迟等辅助功能。与 `ThreadUtils` 的关键区别：`sleep()` 有**非阻塞版本** `delay()`（suspend 函数），不占用线程。

---

## 教程

### 1. 线程检测

```kotlin
import com.itg.itg_coroutine_pools.utils.CoroutineUtils

// 判断当前线程
if (CoroutineUtils.isMainThread()) {
    view.invalidate()  // 安全更新 UI
}

if (CoroutineUtils.isBackgroundThread()) {
    heavyComputation()  // 执行耗时操作
}
```

### 2. 线程断言（开发期检查）

```kotlin
class MyView : View {
    fun updateUI(text: String) {
        // Debug 期间确保在正确线程调用
        CoroutineUtils.assertMainThread("updateUI must be on main thread")
        textView.text = text
    }
}

class DataRepository {
    fun loadFromNetwork(): Data {
        CoroutineUtils.assertBackgroundThread("Network must be on background")
        return api.fetchData()
    }
}
```

### 3. 主线程切换

```kotlin
// 安全地切回主线程（如果已在主线程则直接执行）
CoroutineUtils.runOnUiThread {
    progressBar.visibility = View.GONE
    recyclerView.adapter?.notifyDataSetChanged()
}

// 主线程延迟执行
CoroutineUtils.runOnUiThreadDelayed(delayMs = 1000L) {
    showToast("Operation complete")
}
```

### 4. 非阻塞延迟（协程优势）

```kotlin
// ❌ 传统方式：阻塞线程
Thread.sleep(1000)   // 线程被占用 1000ms，什么都不能做
doWork()

// ✅ 协程方式：非阻塞挂起
CoroutineExecutor.io {
    CoroutineUtils.delay(1000)   // 协程挂起，线程被释放去处理其他任务
    doWork()
}

// 对比：
// sleep(1000): 物理线程阻塞 → 1个线程只能处理1个任务
// delay(1000): 协程挂起 → 1个线程可以同时承载1000个挂起的协程
```

### 5. 阻塞休眠（兼容旧代码）

```kotlin
// 阻塞休眠 — 仅后台线程可用
CoroutineExecutor.io {
    CoroutineUtils.sleep(500)  // 阻塞当前线程 500ms
    // sleep 可以在任意地方调用，不需要在协程中
}

// 主线程调用会打印警告，不会阻塞（防止 ANR）
CoroutineUtils.sleep(1000)  // 主线程调用 → 打印警告 + 跳过
```

### 6. 线程优先级

```kotlin
// 在后台任务开始时降低优先级
CoroutineExecutor.background {
    CoroutineUtils.setBackgroundPriority()  // Android 进程优先级
    CoroutineUtils.setLowPriority()         // Java 线程优先级

    // 低优先级任务：预缓存、日志清理等
    precacheNextPage()
}

// 精细控制
CoroutineUtils.setCurrentThreadPriority(Thread.MIN_PRIORITY)     // Java 优先 (1-10)
CoroutineUtils.setProcessThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)  // Android 优先级
```

### 7. 线程信息调试

```kotlin
// 快速获取当前线程描述
println(CoroutineUtils.getCurrentThreadDescription())
// 输出: DefaultDispatcher-worker-1 (id=142, main=false)

// 获取完整信息
val info = CoroutineUtils.getCurrentThreadInfo()
// {
//   name=DefaultDispatcher-worker-1,
//   id=142,
//   priority=5,
//   isMain=false,
//   isDaemon=true,
//   state=RUNNABLE,
//   threadGroup=main
// }

// 打印调用栈（调试死锁/卡顿）
CoroutineUtils.logStackTrace("MyDebug", maxDepth = 10)
// D/MyDebug: Thread: DefaultDispatcher-worker-1
//   at com.example.MyClass.myMethod(MyClass.kt:42)
//   at ...

// 获取活跃线程概要
println(CoroutineUtils.getActiveThreadSummary())
```

### 8. 在 Java 中使用

```java
import com.itg.itg_coroutine_pools.utils.CoroutineUtils;

// 线程检测
if (CoroutineUtils.isMainThread()) {
    updateUI();
}

// 线程断言
CoroutineUtils.assertMainThread("UI operations only");

// 切回主线程
CoroutineUtils.runOnUiThread(() -> {
    textView.setText("Updated");
    return Unit.INSTANCE;
});

// 阻塞休眠（后台线程）
CoroutineUtils.sleep(500L);
```

---

## API 参考

### 线程检测
| 方法 | 说明 |
|------|------|
| `isMainThread()` | 判断是否主线程 |
| `isBackgroundThread()` | 判断是否后台线程 |

### 线程断言
| 方法 | 说明 |
|------|------|
| `assertMainThread(msg)` | 断言当前在主线程 |
| `assertBackgroundThread(msg)` | 断言当前在后台线程 |

### 主线程切换
| 方法 | 说明 |
|------|------|
| `runOnUiThread(task)` | 切换到主线程执行 |
| `runOnUiThreadDelayed(task, delayMs)` | 延迟在主线程执行 |

### 延迟/休眠
| 方法 | 说明 |
|------|------|
| `delay(ms)` | **suspend** — 非阻塞挂起（协程中调用） |
| `sleep(ms)` | 阻塞休眠（后台线程，主线程自动跳过） |

### 线程优先级
| 方法 | 说明 |
|------|------|
| `setCurrentThreadPriority(priority)` | 设置 Java 线程优先级 (1-10) |
| `setLowPriority()` | 设为最低优先级 (MIN_PRIORITY) |
| `setProcessThreadPriority(priority)` | 设置 Android 进程优先级 |
| `setBackgroundPriority()` | 设为后台进程优先级 |

### 调试
| 方法 | 说明 |
|------|------|
| `getCurrentThreadInfo()` | 获取线程详细信息 Map |
| `getCurrentThreadDescription()` | 获取线程简要描述 |
| `logStackTrace(tag, maxDepth)` | 打印调用栈 |
| `getActiveThreadSummary()` | 获取活跃线程概要 |
