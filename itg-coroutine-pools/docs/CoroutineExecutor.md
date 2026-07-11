# CoroutineExecutor — 协程任务执行器

## 概述

对标 `TaskExecutor`，提供语义化、类型安全的任务提交 API。内部基于 Kotlin 协程实现，API 签名和 `TaskExecutor` 完全一致。

---

## 教程

### 1. 基础用法：线程切换

```kotlin
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor

// 最经典的模式: IO 线程干活 → Main 线程更新 UI
fun loadUserProfile() {
    showLoading()  // 主线程
    CoroutineExecutor.io {
        val profile = api.fetchUserProfile()   // I/O 线程
        val avatar = api.fetchAvatar(profile)   // I/O 线程
        CoroutineExecutor.main {
            hideLoading()                       // 主线程
            displayProfile(profile, avatar)     // 主线程
        }
    }
}
```

### 2. 不同场景的分发器

```kotlin
// I/O 密集型 — 网络、文件、数据库
CoroutineExecutor.io {
    val json = File("/sdcard/data.json").readText()
}

// CPU 密集型 — 图片处理、加解密、复杂计算
CoroutineExecutor.compute {
    val blurred = blurBitmap(originalBitmap, radius = 15)
    CoroutineExecutor.main { imageView.setImageBitmap(blurred) }
}

// 通用后台 — 不确定类型时的默认选择
CoroutineExecutor.background {
    preloadNextPageCache()
}

// 串行执行 — 保证一次只有一个任务运行
CoroutineExecutor.single {
    database.insertSequentially(record)
}

// 主线程 — 安全地切回 UI
CoroutineExecutor.main {
    recyclerView.adapter?.notifyDataSetChanged()
}
```

### 3. 有返回值 (Future)

```kotlin
// 提交有返回值的任务，稍后获取结果
val future = CoroutineExecutor.io<Bitmap> {
    loadBitmapFromUrl(url)
}

// 在另一个线程中等待结果
CoroutineExecutor.io {
    try {
        val bitmap = CoroutineExecutor.await(future, timeoutMs = 5000)
        CoroutineExecutor.main { imageView.setImageBitmap(bitmap) }
    } catch (e: TimeoutException) {
        CoroutineExecutor.main { showTimeoutError() }
    }
}
```

### 4. 延迟执行

```kotlin
// 主线程延迟 2 秒后显示 Tooltip
val runnable = CoroutineExecutor.mainDelayed(delayMs = 2000L) {
    showTooltip()
}
// 必要时取消
CoroutineExecutor.cancelMain(runnable)

// 后台延迟同步
val future = CoroutineExecutor.ioDelayed(delayMs = 5000L) {
    syncToServer()
}
// 取消延迟任务
CoroutineExecutor.cancel(future)
```

### 5. 定时/周期性任务

```kotlin
// 心跳：每 30 秒执行一次
val heartbeat = CoroutineExecutor.scheduleAtFixedRate(
    initialDelayMs = 0L,
    periodMs = 30_000L
) {
    sendHeartbeat()
}

// 轮询：每次执行完等待 5 秒
val polling = CoroutineExecutor.scheduleWithFixedDelay(
    initialDelayMs = 5_000L,
    delayMs = 5_000L
) {
    checkForNewMessages()
}

// 停止
CoroutineExecutor.cancel(heartbeat)
CoroutineExecutor.cancel(polling)
```

### 6. 批量等待

```kotlin
CoroutineExecutor.io {
    // 并发加载 5 个接口
    val futures = (1..5).map { index ->
        CoroutineExecutor.io<String> { api.fetchItem(index) }
    }

    // 等待全部完成（超时 10 秒）
    try {
        CoroutineExecutor.awaitAll(futures, timeoutMs = 10_000L)

        // 收集结果
        val results = futures.mapNotNull { f ->
            if (f.isDone) try { f.get() } catch (_: Exception) { null }
            else null
        }
        CoroutineExecutor.main { displayResults(results) }
    } catch (e: TimeoutException) {
        CoroutineExecutor.main { showTimeoutError() }
    }
}
```

### 7. 等待任意一个完成

```kotlin
CoroutineExecutor.io {
    val futures = listOf(
        CoroutineExecutor.io<String> { api.fetchFromMirror1() },
        CoroutineExecutor.io<String> { api.fetchFromMirror2() },
        CoroutineExecutor.io<String> { api.fetchFromMirror3() }
    )

    try {
        val firstIndex = CoroutineExecutor.awaitAny(futures, timeoutMs = 10_000L)
        val result = futures[firstIndex].get()
        // 取消其余请求
        futures.forEachIndexed { index, future ->
            if (index != firstIndex) CoroutineExecutor.cancel(future)
        }
        CoroutineExecutor.main { display(result) }
    } catch (e: TimeoutException) {
        CoroutineExecutor.main { showTimeoutError() }
    }
}
```

### 8. 协程原生 API（推荐新代码）

```kotlin
// suspend 函数 — 非阻塞、自动异常传播、天然支持取消
class MyViewModel : ViewModel() {

    fun loadData() {
        viewModelScope.launch {
            try {
                val user = CoroutineExecutor.ioSuspend { api.fetchUser() }
                val orders = CoroutineExecutor.ioSuspend { api.fetchOrders(user.id) }
                // 自动在主线程更新 UI
                _userState.value = user
                _orderState.value = orders
            } catch (e: Exception) {
                _errorState.value = e.message
            }
        }
    }
}
```

### 9. 实战：图片列表加载

```kotlin
fun loadImageList(urls: List<String>, onComplete: (List<Bitmap>) -> Unit) {
    CoroutineExecutor.io {
        val futures = urls.map { url ->
            CoroutineExecutor.io<Bitmap?> {
                try {
                    val bitmap = loadFromNetwork(url)
                    // 在计算线程做图片处理
                    CoroutineExecutor.await(
                        CoroutineExecutor.compute<Bitmap> { scaleToWidth(bitmap, 600) },
                        timeoutMs = 5000
                    )
                } catch (e: Exception) {
                    null  // 单张失败不影响整体
                }
            }
        }

        try {
            CoroutineExecutor.awaitAll(futures, timeoutMs = 30_000L)
        } catch (_: TimeoutException) { }

        val results = futures.mapNotNull { f ->
            if (f.isDone) try { f.get() } catch (_: Exception) { null }
            else null
        }

        CoroutineExecutor.main { onComplete(results) }
    }
}
```

### 10. 生命周期管理

```kotlin
// 应用退出时关闭
class MyApplication : Application() {
    override fun onTerminate() {
        super.onTerminate()
        CoroutineExecutor.shutdown()  // 取消所有未完成任务
    }
}
```

---

## API 参考

### Fire-and-Forget
| 方法 | 说明 |
|------|------|
| `io(task)` | I/O Dispatcher 执行 |
| `compute(task)` | 计算 Dispatcher 执行 |
| `background(task)` | 后台 Dispatcher 执行 |
| `single(task)` | 串行 Dispatcher 执行 |
| `main(task)` | 主线程执行 |

### 有返回值 (Future)
| 方法 | 返回 |
|------|------|
| `io<T>(task): Future<T>` | I/O 执行并返回 Future |
| `compute<T>(task): Future<T>` | 计算执行并返回 Future |
| `background<T>(task): Future<T>` | 后台执行并返回 Future |

### 延迟/定时
| 方法 | 说明 |
|------|------|
| `mainDelayed(task, delayMs)` | 主线程延迟执行 |
| `ioDelayed(task, delayMs)` | I/O 延迟执行 |
| `backgroundDelayed(task, delayMs)` | 后台延迟执行 |
| `scheduleAtFixedRate(task, initial, period)` | 固定频率定时 |
| `scheduleWithFixedDelay(task, initial, delay)` | 固定延迟定时 |

### 取消/等待
| 方法 | 说明 |
|------|------|
| `cancel(future, mayInterrupt)` | 取消 Future |
| `cancelMain(runnable)` | 取消主线程延迟任务 |
| `cancelMainAll(token)` | 取消主线程所有匹配回调 |
| `await(future, timeoutMs)` | 阻塞等待 Future 结果 |
| `awaitAll(futures, timeoutMs)` | 等待所有 Future |
| `awaitAny(futures, timeoutMs)` | 等待任一 Future |

### 协程原生 (suspend)
| 方法 | 说明 |
|------|------|
| `ioSuspend(task)` | I/O 执行 suspend 函数 |
| `computeSuspend(task)` | 计算执行 suspend 函数 |
| `backgroundSuspend(task)` | 后台执行 suspend 函数 |
| `mainSuspend(task)` | 主线程执行 suspend 函数 |

### 生命周期
| 方法 | 说明 |
|------|------|
| `shutdown()` | 关闭全局 Scope，取消所有任务 |
