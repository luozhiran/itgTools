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

## 推荐做法

取消：

```kotlin
val future = Concurrent.io { upload() }
ConcurrentUtils.cancel(future)
```

延迟执行，真实签名是 `task` 在前，`delayMs` 在后：

```kotlin
val future = Concurrent.ioDelayed({
    syncCache()
}, delayMs = 3_000L)
```

线程检测：

```kotlin
ConcurrentUtils.isMainThread()
ConcurrentUtils.assertBackgroundThread()
```

## 可复制 Demo

下面示例实现搜索防抖。需要替换 `search(keyword)` 和 `binding.resultText`。

```kotlin
import com.itg.concurrent.Concurrent
import com.itg.concurrent.util.ConcurrentUtils
import java.util.concurrent.Future

class SearchPresenter {
    private var searchFuture: Future<*>? = null

    fun onSearchTextChanged(keyword: String) {
        searchFuture?.let { ConcurrentUtils.cancel(it) }

        searchFuture = Concurrent.ioDelayed({
            ConcurrentUtils.assertBackgroundThread()

            val result = search(keyword) // TODO: 替换成你的搜索逻辑

            Concurrent.main {
                // TODO: 替换成你的 UI 更新逻辑
                binding.resultText.text = result.joinToString("\n")
            }
        }, delayMs = 300L)
    }

    fun release() {
        searchFuture?.let { ConcurrentUtils.cancel(it) }
        searchFuture = null
    }
}
```

## 关键说明

- `ConcurrentUtils.cancel` 适合页面退出、搜索防抖、请求过期。
- `ConcurrentUtils.await` 返回可空值；中断或取消时返回 `null`。
- `ConcurrentUtils.sleep` 只适合后台线程，主线程调用会告警并返回。

## 验证方式

- 快速连续输入时，只有最后一次搜索会执行。
- `assertBackgroundThread()` 不抛异常，说明搜索逻辑没有跑在主线程。
- 页面释放时调用 `release()`，旧任务被取消。

[返回 README](../README.md)