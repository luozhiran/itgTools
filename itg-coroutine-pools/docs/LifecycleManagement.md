# 协程生命周期管理方案

## 核心结论

**协程的生命周期管理天然优于线程池，且不需要修改现有业务代码。** 关键：将任务从全局 Scope 迁移到 Android 生命周期感知的 Scope（如 `viewModelScope`、`lifecycleScope`），任务随页面销毁自动取消。

---

## 一、现有线程池的生命周期 vs 协程

### 线程池模式（当前 itg-thread-pools）

```
Application 启动
  │
  ├── ThreadPoolManager.ioPool 创建 (64 线程)
  ├── ThreadPoolManager.computePool 创建 (8 线程)
  │   ...共 6 个池，~70+ 线程...
  │
  ├── Activity A 打开
  │     TaskExecutor.io { loadData() }    ← 提交任务到全局池
  │     TaskExecutor.io { loadImage() }
  │          │
  │   Activity A 关闭 ───────────────┐
  │          │                       │ 任务仍在线程池中运行！
  │          ▼                       │ （可能已经不需要了）
  │     内存泄漏风险！                │
  │     onResult 回调持有 Activity 引用│
  │                                   │
  ├── Activity B 打开                  │
  │     TaskExecutor.io { ... }       │
  │                                   │
  │    ...整个进程生命周期...           │
  │                                   ▼
  └── Application.onTerminate()
        ThreadPoolManager.shutdown()   ← 唯一一次的全局关闭
```

**问题：**
- 任务和页面生命周期完全脱钩
- Activity 销毁后任务仍在运行 → 回调可能触发已销毁的 View 操作
- 唯一"取消"手段：`TaskExecutor.cancel(future)`，需要手动保存每个 Future 引用
- 全局线程池一直存活到进程终止

### 协程模式（itg-coroutine-pools 的两种用法）

#### 用法A：Fire-and-forget（和线程池行为一致，全局 Scope）

```kotlin
// 行为完全对标 TaskExecutor.io { }
CoroutineExecutor.io {
    val data = api.fetchData()
    CoroutineExecutor.main { textView.text = data }
}
// 问题：和线程池一样，页面销毁后任务继续跑
```

#### 用法B：suspend + 生命周期 Scope（协程独有，无内存泄漏）

```kotlin
// 在 ViewModel 中
viewModelScope.launch {
    val data = CoroutineExecutor.ioSuspend { api.fetchData() }
    // 自动在主线程，无需手动切换
    updateUI(data)
}
// ViewModel 清除 → viewModelScope 自动 cancel → 协程取消 → 网络请求取消
```

**区别：**

| 维度 | 全局 Scope（用法A） | 生命周期 Scope（用法B） |
|------|:---:|:---:|
| 对标线程池行为 | ✅ 完全一致 | - |
| 页面销毁后任务取消 | ❌ 继续运行 | ✅ 自动取消 |
| 需要手动保存引用取消 | ❌ 需要 | ✅ 不需要 |
| 需要修改业务代码 | 改 import | 改 import + 用 suspend |

---

## 二、生命周期管理方案（不修改现有代码）

### 方案1：仅改 import → 行为不变（零风险，最小改动）

```kotlin
// 旧代码 (itg-file/FileUtils.kt)
import com.itg.itg_thread_pools.executor.TaskExecutor

fun exists(path: String, onResult: (Boolean) -> Unit): Future<*> {
    return TaskExecutor.io { onResult(File(path).exists()) }
}

// ↓ 只改 import，不改任何逻辑
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor

fun exists(path: String, onResult: (Boolean) -> Unit): Future<*> {
    return CoroutineExecutor.io { onResult(File(path).exists()) }
}
```

**生命周期行为和原来完全一致：**
- 全局 Scope 管理，任务执行到完
- Application.onTerminate() 时 `CoroutineExecutor.shutdown()` 全局关闭
- 内存占用从 ~70 线程降到 0 线程（协程挂起不占线程）

```kotlin
class MyApplication : Application() {
    override fun onTerminate() {
        super.onTerminate()
        CoroutineExecutor.shutdown()     // 取消所有协程任务
        ChannelManager.quitAll()         // 关闭所有消息通道
    }
}
```

### 方案2：不改现有代码 + 新增生命周期感知调用

**存量代码不改**，**新功能用生命周期 Scope**：

```kotlin
// === 存量代码：不改 ===
// itg-file/FileUtils.kt
fun exists(path: String, onResult: (Boolean) -> Unit): Future<*> {
    return CoroutineExecutor.io { onResult(File(path).exists()) }
}

// === 新功能：直接用 ViewModel + 生命周期 Scope ===
class NewFeatureViewModel : ViewModel() {

    fun loadUserProfile(userId: String) {
        // viewModelScope 绑定 ViewModel 生命周期
        viewModelScope.launch {
            try {
                val profile = CoroutineExecutor.ioSuspend { api.fetchProfile(userId) }
                _profileState.value = profile  // 自动主线程
            } catch (e: CancellationException) {
                // ViewModel 清除时自动触发，无需手动处理
            }
        }
    }
}
```

### 方案3：通过中间件统一管理 + 按页面注册 Scope

不改现有库代码，在 Application 层添加生命周期感知的 Scope 管理：

```kotlin
// === 新增一个轻量工具类（不修改任何现有代码）===

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*

/**
 * 生命周期感知的协程任务管理器
 *
 * 不修改 itg-coroutine-pools 任何代码，纯扩展。
 */
object LifecycleTasks {

    private val scopeMap = ConcurrentHashMap<String, CoroutineScope>()

    /**
     * 为 LifecycleOwner 创建/获取专属 Scope
     *
     * Scope 随 Lifecycle ON_DESTROY 自动取消，
     * 该页面提交的所有任务自动清理。
     */
    fun forLifecycle(owner: LifecycleOwner): CoroutineScope {
        val key = owner.toString()
        return scopeMap.getOrPut(key) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope ->
                owner.lifecycle.addObserver(object : LifecycleEventObserver {
                    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            scope.cancel()
                            scopeMap.remove(key)
                        }
                    }
                })
            }
        }
    }
}

// === 使用示例（Activity/Fragment 中）===

class MyActivity : AppCompatActivity() {

    private val taskScope = LifecycleTasks.forLifecycle(this)

    fun loadData() {
        // 方式1：用页面 Scope 提交（Activity 销毁自动取消）
        taskScope.launch {
            val data = CoroutineExecutor.ioSuspend { api.fetchData() }
            updateUI(data)
        }

        // 方式2：仍然用全局 fire-and-forget（和旧行为一致）
        CoroutineExecutor.io {
            val data = api.fetchData()
            CoroutineExecutor.main { updateUI(data) }
        }
    }

    // onDestroy 不需要手动清理 — LifecycleTasks 自动处理
}
```

---

## 三、对比总结

### 三种 Scope 策略

```
全局 Scope (CoroutineExecutor 内置)
  │
  ├── 优点: 零学习成本，和 TaskExecutor 行为完全一致
  ├── 缺点: 生命周期不感知，页面销毁后任务继续运行
  └── 适用: 全局任务、不需要取消的任务、存量代码迁移

生命周期 Scope (viewModelScope / lifecycleScope)
  │
  ├── 优点: 自动取消，零泄漏，结构化并发
  ├── 缺点: 需要用 suspend 函数（改调用方式）
  └── 适用: 新功能、ViewModel 中的数据加载

LifecycleTasks (自定义 Scope 注册器)
  │
  ├── 优点: 不改任何现有代码，按页面隔离
  ├── 缺点: 需要额外注册
  └── 适用: 需要页面级任务管理的场景
```

### 生命周期对比表

| 场景 | 线程池 | 协程（全局Scope） | 协程（生命周期Scope） |
|------|:---:|:---:|:---:|
| Activity 销毁，任务自动取消 | ❌ | ❌ (兼容行为) | ✅ |
| 需要手动保存 Future 引用取消 | ✅ 需要 | ✅ 需要 | ❌ 不需要 |
| Application 退出清理 | `shutdown()` | `shutdown()` | 自动 + `shutdown()` |
| 取消传播到子任务 | ❌ | ❌ (SupervisorJob) | ✅ (Job 层级) |
| 取消网络请求(OkHttp call.cancel) | ❌ 手动 | ❌ 手动 | ✅ 自动 (kotlinx cancel) |
| 线程泄漏风险 | 有（线程不释放） | 无 | 无 |
| 内存泄漏风险 | 有（回调持有引用） | 有（回调模式同左） | 无（suspend 无回调） |

---

## 四、最佳实践建议

### 存量代码（不改）

```kotlin
// 原来的代码完全不动，只改 build.gradle 依赖
import com.itg.itg_thread_pools.executor.TaskExecutor  // 老 import
// 改为
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor  // 新 import

// 代码逻辑不变
CoroutineExecutor.io { onResult(work()) }
```

**收获：** 内存从 ~64MB（线程栈）降到几 MB，0 代码改动。

### 新功能代码（推荐）

```kotlin
class MyViewModel : ViewModel() {
    fun loadData() {
        viewModelScope.launch {                       // ← 生命周期绑定
            val data = CoroutineExecutor.ioSuspend {   // ← suspend 非阻塞
                api.fetchData()
            }
            _state.value = data                        // ← 自动主线程
        }
    }
}
```

**收获：** 自动取消 + 零泄漏 + 更简洁的代码。

### 混合场景

```kotlin
// 全局任务（日志上报、心跳）→ 全局 Scope（和旧行为一致）
CoroutineExecutor.io { reportAnalytics() }

// 页面任务（加载用户数据）→ 生命周期 Scope
viewModelScope.launch {
    val data = CoroutineExecutor.ioSuspend { api.fetchUser() }
    updateUI(data)
}
```

---

## 五、ChannelManager 生命周期

```kotlin
// 每个通道有独立的 CoroutineScope
ChannelManager.getOrCreate("db-writer")

// 通道关闭：
ChannelManager.quit("db-writer")   // 关闭单个通道
ChannelManager.quitAll()           // 关闭所有通道

// 对比 HandlerManager:
// HandlerManager.quit("db-writer")    ← HandlerThread.quitSafely()
// ChannelManager.quit("db-writer")    ← Channel.close() + scope.cancel()
```

两者 API 完全一致，但协程版本更轻量——不需要退出物理线程，只需要取消协程。

---

## 六、CoroutineExecutor.shutdown() 的精确行为

```kotlin
object CoroutineExecutor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun shutdown() {
        scope.cancel()  // 取消所有从此 Scope 启动的协程
    }
}
```

- `SupervisorJob`：一个子协程失败不影响其他协程（和线程池行为一致）
- `scope.cancel()`：取消所有活跃协程，不等待完成
- 如需优雅关闭，可先 `scope.cancel()` 再等待：
  ```kotlin
  fun shutdownGracefully() {
      scope.cancel()
      runBlocking { scope.coroutineContext[Job]?.join() }
  }
  ```

**对标线程池：**

| 线程池操作 | 协程等价 |
|-----------|---------|
| `pool.shutdown()` | `scope.cancel()` |
| `pool.shutdownNow()` | `scope.cancel()` + `CancellationException` |
| `pool.awaitTermination(t)` | `runBlocking { job.join() }` |
| `pool.execute { }` | `scope.launch { }` |
| `future.cancel(true)` | `job.cancel()` |
