# 07. 生命周期、异常与 Java

本节说明任务取消边界、异常处理和 Java 调用方式。

## 推荐做法

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

Future 异常处理：

```kotlin
try {
    val user = future.get()
} catch (e: ExecutionException) {
    val realCause = e.cause
}
```

## 可复制 Demo

下面示例展示一个可释放的普通类 worker，包含启动、取消和异常处理。需要替换 `repository.sync()`。

```kotlin
import com.itg.concurrent.Concurrent
import com.itg.concurrent.DispatcherType
import kotlinx.coroutines.Job
import java.io.IOException

class SyncWorker(
    private val repository: Repository,
    private val onState: (String) -> Unit
) {
    private val scope = Concurrent.createScope(DispatcherType.IO, "sync-worker")
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            try {
                repository.sync() // TODO: 替换成你的同步逻辑
                Concurrent.mainSuspend { onState("success") }
            } catch (e: IOException) {
                Concurrent.mainSuspend { onState("network error: ${e.message}") }
            } catch (e: Exception) {
                Concurrent.mainSuspend { onState("error: ${e.message}") }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun release() {
        scope.cancel()
    }
}
```

## Java 调用 Demo

Java 侧优先使用 Future 风格 API。需要把 `api.fetch()` 和 `render(result)` 替换成真实逻辑。

```java
import com.itg.concurrent.Concurrent;
import com.itg.concurrent.util.ConcurrentUtils;
import java.util.concurrent.Future;
import kotlin.Unit;

public class JavaConcurrentDemo {
    public void load(Api api) {
        Future<String> future = Concurrent.io(() -> api.fetch());

        Concurrent.io(() -> {
            String result = ConcurrentUtils.await(future, 5000L);
            Concurrent.main(() -> {
                render(result);
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }
}
```

## 关键说明

- 页面或对象释放时，取消持有的 `Job` 或 `ConcurrentScope`。
- `Future.get()` 的业务异常通常包在 `ExecutionException.cause` 中。
- suspend 任务直接用 `try/catch` 处理异常。
- Java 侧不建议直接调用 suspend 风格 API。

## 验证方式

- 调用 `stop()` 后当前任务不再回调成功状态。
- 调用 `release()` 后该 worker 不应继续启动新任务。
- Java demo 中不要在主线程直接 `await`。

[返回 README](../README.md)