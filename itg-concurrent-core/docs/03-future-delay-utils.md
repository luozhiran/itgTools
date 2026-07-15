# 03. Future、延迟与工具

本节解决普通任务的返回值、取消、等待、延迟执行和线程工具。

## Future 返回值

`io`、`compute`、`background` 都返回 `Future<T>`：

```kotlin
val future: Future<User> = Concurrent.io {
    api.getUser(userId)
}
```

在后台线程等待结果：

```kotlin
Concurrent.io {
    val user = ConcurrentUtils.await(future, timeoutMs = 5_000L)
    Concurrent.main {
        if (user != null) showUser(user) else showError()
    }
}
```

`ConcurrentUtils.await` 会阻塞当前线程，不要在主线程调用。

## 取消任务

```kotlin
val future = Concurrent.io { upload() }
ConcurrentUtils.cancel(future)
```

取消常用于：

- 页面退出。
- 搜索防抖替换旧任务。
- 请求过期。

## 延迟执行

当前真实签名是 `task` 在前，`delayMs` 在后：

```kotlin
val future = Concurrent.ioDelayed({
    syncCache()
}, delayMs = 3_000L)
```

主线程延迟：

```kotlin
Concurrent.mainDelayed({
    showTooltip()
}, delayMs = 500L)
```

搜索防抖：

```kotlin
private var searchFuture: Future<*>? = null

fun onSearchTextChanged(keyword: String) {
    searchFuture?.let { ConcurrentUtils.cancel(it) }
    searchFuture = Concurrent.ioDelayed({
        val result = search(keyword)
        Concurrent.main { render(result) }
    }, delayMs = 300L)
}
```

## 线程检测和断言

```kotlin
ConcurrentUtils.isMainThread()
ConcurrentUtils.isBackgroundThread()
ConcurrentUtils.assertMainThread()
ConcurrentUtils.assertBackgroundThread()
```

调试线程信息：

```kotlin
val desc = ConcurrentUtils.getCurrentThreadDescription()
val info = ConcurrentUtils.getCurrentThreadInfo()
```

## 阻塞 sleep

```kotlin
Concurrent.io {
    ConcurrentUtils.sleep(200L)
}
```

`ConcurrentUtils.sleep` 不应该在主线程调用。主线程调用时会记录警告并直接返回。

[返回 README](../README.md)