# ITG Coroutine Pools

`itg-coroutine-pools` 是协程调度与任务执行模块，提供语义化 Dispatcher、与 `TaskExecutor` 类似的 `CoroutineExecutor`、命名 `ChannelManager` 和线程/协程诊断工具。模块 minSdk 21，依赖 `kotlinx-coroutines-core/android`。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [按语义切换协程调度器](./docs/01-dispatchers-executor.md) | `CoroutineDispatcherManager.ioDispatcher` 等 | Kotlin 协程 | 需要 IO、计算、主线程、串行调度 | 基于 `Dispatchers.IO/Default/Main` 和 limitedParallelism |
| [用类似线程池的 API 执行任务](./docs/01-dispatchers-executor.md) | `CoroutineExecutor.io/compute/main` | 返回 `Future` 兼容旧代码 | 从线程池迁移但暂时保留 Future 模型 | 内部用 `Deferred`/`Job` 桥接 `Future` |
| [在 suspend 代码里执行后台任务](./docs/02-suspend-channel.md) | `ioSuspend/computeSuspend/mainSuspend` | 推荐新协程代码 | ViewModel 或协程作用域中切换上下文 | 使用 `withContext`，保留结构化调用方式 |
| [用协程 Channel 替代 HandlerThread](./docs/02-suspend-channel.md) | `ChannelManager` | 命名串行通道 | 需要 FIFO 任务或 Message 模型但不想独占线程 | Channel + 单并发 dispatcher 严格串行处理 |
| [线程诊断和优先级](./docs/03-utils-api.md) | `CoroutineUtils` | 调试和低层工具 | 判断主线程、打印线程信息、设置优先级 | 封装 Looper、Thread 和 Process 工具 |
| [查看 API 速查](./docs/03-utils-api.md) | API 表 | 所有使用者 | 查方法和关闭规则 | 汇总当前源码公开 API |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. 调度器与执行器](./docs/01-dispatchers-executor.md) | `CoroutineDispatcherManager`、`CoroutineExecutor` |
| [02. suspend API 与 Channel](./docs/02-suspend-channel.md) | 原生协程 API、`ChannelManager` |
| [03. 工具与 API 速查](./docs/03-utils-api.md) | `CoroutineUtils`、API、生命周期 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-coroutine-pools"))
}
```