# CoroutineExecutor — 协程任务执行器

对标 `TaskExecutor`，API 完全一致，内部基于 Kotlin 协程。

## 目录
1. [基础线程切换](#1-基础线程切换)
2. [五种分发器](#2-五种分发器)
3. [有返回值 Future](#3-有返回值-future)
4. [延迟执行](#4-延迟执行)
5. [定时/周期任务](#5-定时周期任务)
6. [批量等待](#6-批量等待)
7. [等待任意一个完成](#7-等待任意一个完成)
8. [suspend 原生 API](#8-suspend-原生-api)
9. [实战：图片列表加载](#9-实战图片列表加载)
10. [API 参考](#10-api-参考)

---

## 1. 基础线程切换

```kotlin
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor

fun loadData() {
    showLoading()
    CoroutineExecutor.io {
        val data = api.fetchData()      // I/O 线程
        CoroutineExecutor.main {
            hideLoading()                // 主线程
            displayData(data)
        }
    }
}
```

## 2. 五种分发器

```kotlin
CoroutineExecutor.io { }         // 网络、文件、数据库
CoroutineExecutor.compute { }    // 图片处理、加解密
CoroutineExecutor.background { } // 通用后台
CoroutineExecutor.single { }     // 严格串行
CoroutineExecutor.main { }       // 主线程/UI
```

## 3. 有返回值 Future

```kotlin
val future = CoroutineExecutor.io<Int> { calculate() }
CoroutineExecutor.io {
    val result = CoroutineExecutor.await(future, timeoutMs = 5000)
    CoroutineExecutor.main { display(result) }
}
```

## 4. 延迟执行

```kotlin
// 主线程延迟 2s
val r = CoroutineExecutor.mainDelayed(delayMs = 2000L) { showTooltip() }

// 后台延迟 5s
val f = CoroutineExecutor.ioDelayed(delayMs = 5000L) { syncToServer() }
CoroutineExecutor.cancel(f)  // 取消

// 搜索防抖
private var pending: Future<*>? = null
fun onSearchChanged(query: String) {
    pending?.let { CoroutineExecutor.cancel(it) }
    pending = CoroutineExecutor.backgroundDelayed(delayMs = 300L) {
        performSearch(query)
    }
}
```

## 5. 定时/周期任务

```kotlin
// 每 30s 心跳（固定频率）
val hb = CoroutineExecutor.scheduleAtFixedRate(periodMs = 30_000L) { sendHeartbeat() }

// 每 5s 轮询（固定延迟）
val poll = CoroutineExecutor.scheduleWithFixedDelay(delayMs = 5_000L) { checkMessages() }

CoroutineExecutor.cancel(hb)
CoroutineExecutor.cancel(poll)
```

## 6. 批量等待

```kotlin
val futures = urls.map { url -> CoroutineExecutor.io<Data> { api.fetch(url) } }
CoroutineExecutor.io {
    CoroutineExecutor.awaitAll(futures, timeoutMs = 30_000L)
    val results = futures.mapNotNull { if (it.isDone) it.get() else null }
    CoroutineExecutor.main { display(results) }
}
```

## 7. 等待任意一个完成

```kotlin
val futures = listOf(
    CoroutineExecutor.io<String> { api.fetchMirror1() },
    CoroutineExecutor.io<String> { api.fetchMirror2() }
)
val idx = CoroutineExecutor.awaitAny(futures, timeoutMs = 10_000L)
val result = futures[idx].get()
// 取消其余
futures.forEachIndexed { i, f -> if (i != idx) CoroutineExecutor.cancel(f) }
```

## 8. suspend 原生 API

```kotlin
lifecycleScope.launch {
    val user = CoroutineExecutor.ioSuspend { api.fetchUser() }
    val orders = CoroutineExecutor.ioSuspend { api.fetchOrders(user.id) }
    updateUI(user, orders)  // 自动主线程
}

// 并发
val userDef = async { CoroutineExecutor.ioSuspend { api.fetchUser() } }
val cfgDef = async { CoroutineExecutor.ioSuspend { api.fetchConfig() } }
val user = userDef.await(); val cfg = cfgDef.await()
```

## 9. 实战：图片列表加载

```kotlin
fun loadImages(urls: List<String>, onComplete: (List<Bitmap>) -> Unit) {
    CoroutineExecutor.io {
        val futures = urls.map { url ->
            CoroutineExecutor.io<Bitmap?> {
                try {
                    val bitmap = loadFromNetwork(url)
                    CoroutineExecutor.await(CoroutineExecutor.compute<Bitmap> {
                        scaleToWidth(bitmap!!, 600)
                    }, timeoutMs = 5000)
                } catch (e: Exception) { null }
            }
        }
        CoroutineExecutor.awaitAll(futures, timeoutMs = 30_000L)
        val results = futures.mapNotNull { if (it.isDone) try { it.get() } catch (_: Exception) { null } else null }
        CoroutineExecutor.main { onComplete(results) }
    }
}
```

## 10. API 参考

### Fire-and-Forget
| 方法 | 说明 |
|------|------|
| `io(task)` | I/O Dispatcher 执行 |
| `compute(task)` | 计算 Dispatcher 执行 |
| `background(task)` | 后台 Dispatcher 执行 |
| `single(task)` | 串行 Dispatcher 执行 |
| `main(task)` | 主线程执行 |

### 有返回值
| 方法 | 返回 |
|------|------|
| `io<T>(task)` | `Future<T>` |
| `compute<T>(task)` | `Future<T>` |
| `background<T>(task)` | `Future<T>` |

### 延迟/定时
| 方法 | 说明 |
|------|------|
| `mainDelayed(task, ms)` | 主线程延迟 |
| `ioDelayed(task, ms)` | I/O 延迟 |
| `backgroundDelayed(task, ms)` | 后台延迟 |
| `scheduleAtFixedRate(task, init, period)` | 固定频率 |
| `scheduleWithFixedDelay(task, init, delay)` | 固定延迟 |

### 取消/等待
| 方法 | 说明 |
|------|------|
| `cancel(future)` | 取消 Future |
| `cancelMain(runnable)` | 取消主线程延迟 |
| `await(future, timeoutMs)` | 阻塞等待 |
| `awaitAll(futures, timeoutMs)` | 等待全部 |
| `awaitAny(futures, timeoutMs)` | 等待任一 |

### suspend
| 方法 | 说明 |
|------|------|
| `ioSuspend(task): T` | I/O 执行 suspend |
| `computeSuspend(task): T` | 计算执行 suspend |
| `backgroundSuspend(task): T` | 后台执行 suspend |
| `mainSuspend(task): T` | 主线程执行 suspend |
