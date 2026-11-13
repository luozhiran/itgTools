# 06. 在线程中执行文件操作

`FileUtils`、`FileReadUtils`、`FileWriteUtils` 等没有 `Suspend` 后缀的方法是阻塞调用。它们在调用线程执行，因此可以直接提交给已有的线程池；`itg-file` 不创建线程池，也不负责关闭调用方的线程池。

## 选择方式

| 场景 | 调用方式 | 谁负责结束任务 |
| --- | --- | --- |
| 已经在工作线程 | 直接调用同步文件方法 | 调用方 |
| 已有 `ExecutorService` | `executor.submit { 同步文件方法() }` | 持有 `Future` 的 owner；线程池由创建者关闭 |
| 项目已使用 `itg-thread-pools` | `TaskExecutor.io<T> { 同步文件方法() }` | 持有 `Future` 的 owner |

## 可复制示例

```kotlin
import com.itg.itg_file.read.FileReadUtils
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class FileThreadReader(private val executor: ExecutorService) {
    fun readText(path: String): Future<String?> =
        executor.submit(Callable { FileReadUtils.readText(path) })
}
```

调用方创建并复用 `ExecutorService`，保存返回的 `Future`。页面或对象结束时可调用 `future.cancel(true)` 请求中断；如果线程池由该 owner 创建，也应在不再使用时调用 `executor.shutdown()`。不要在主线程调用 `Future.get()`，也不要在工作线程直接更新 Android View。

项目已依赖 `itg-thread-pools` 时，可直接写：

```kotlin
import com.itg.itg_file.read.FileReadUtils
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.util.concurrent.Future

val future: Future<String?> = TaskExecutor.io<String?> {
    FileReadUtils.readText(path)
}
```

这是调用方选择的执行方式，`itg-file` 本身不依赖 `itg-thread-pools`。`Future.cancel(true)` 只请求中断，无法撤销已经写入或删除的文件；阻塞操作是否立即停止取决于底层 I/O 是否响应中断。更多线程池生命周期说明见 [Android 文档](https://developer.android.com/develop/background-work/background-tasks/asynchronous/java-threads) 和 [ExecutorService 文档](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)。

[返回 README](../README.md)
