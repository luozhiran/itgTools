# 07. 生命周期、异常与 Java

本节说明任务取消边界、异常处理和 Java 调用方式。

## 生命周期和取消

页面场景优先用 AndroidX 生命周期：

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    val data = Concurrent.ioSuspend { repository.load() }
    render(data)
}
```

普通类持有 `ConcurrentScope`：

```kotlin
class WorkerOwner {
    private val scope = Concurrent.createScope(DispatcherType.BACKGROUND, "worker-owner")

    fun start() {
        scope.launch { doWork() }
    }

    fun destroy() {
        scope.cancel()
    }
}
```

一次性任务保存 `Job`：

```kotlin
private var job: Job? = null

fun start() {
    job = Concurrent.launchIo { doWork() }
}

fun stop() {
    job?.cancel()
}
```

## Future 异常处理

```kotlin
val future = Concurrent.io {
    api.getUser()
}

Concurrent.io {
    try {
        val user = future.get()
        Concurrent.main { render(user) }
    } catch (e: ExecutionException) {
        val realCause = e.cause
        Concurrent.main { showError(realCause) }
    }
}
```

## suspend 异常处理

```kotlin
Concurrent.launchIo {
    try {
        val user = api.getUser()
        Concurrent.mainSuspend { render(user) }
    } catch (e: IOException) {
        Concurrent.mainSuspend { showError(e) }
    }
}
```

## Java 调用

Java 侧优先用 Future 风格 API。Kotlin 函数类型需要返回 `Unit.INSTANCE`。

```java
Future<String> future = Concurrent.io(() -> api.fetch());

Concurrent.io(() -> {
    String result = ConcurrentUtils.await(future, 5000L);
    Concurrent.main(() -> {
        render(result);
        return Unit.INSTANCE;
    });
    return Unit.INSTANCE;
});
```

`suspend` 风格 API 不适合 Java 直接调用。

[返回 README](../README.md)