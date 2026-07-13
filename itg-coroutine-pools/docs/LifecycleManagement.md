# 协程生命周期管理

## vs 线程池

| 场景 | 线程池 | 协程(全局Scope) | 协程(生命周期Scope) |
|------|:---:|:---:|:---:|
| Activity 销毁自动取消 | ❌ | ❌ | ✅ |
| 需手动保存 Future 取消 | ✅ | ✅ | ❌ |
| 回调内存泄漏风险 | 有 | 有 | 无 |

## 三种方式

### 1. 全局 Scope（和线程池行为一致）

```kotlin
CoroutineExecutor.io { uploadLogs() }  // 不受页面生命周期影响
// Application.onTerminate() 时 shutdown
CoroutineExecutor.shutdown()
```

### 2. 生命周期 Scope（推荐）

```kotlin
viewModelScope.launch {
    val data = CoroutineExecutor.ioSuspend { api.fetchData() }
    updateUI(data)
}
// ViewModel 清除 → 协程自动取消
```

### 3. 页面 Scope 注册

```kotlin
class MyActivity : AppCompatActivity() {
    fun load() {
        lifecycleScope.launch {
            val data = CoroutineExecutor.ioSuspend { api.fetchPageData() }
            updateUI(data)
        }
    }
}
```

## 生命周期映射

| 线程池操作 | 协程等价 |
|-----------|---------|
| `pool.shutdown()` | `scope.cancel()` |
| `pool.shutdownNow()` | `scope.cancel()` |
| `pool.awaitTermination(t)` | `runBlocking { job.join() }` |
| `future.cancel(true)` | `job.cancel()` |
