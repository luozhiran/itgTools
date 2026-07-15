# ITG Thread Pools

`itg-thread-pools` 是 Java 线程池与 Android Handler 工具模块，提供预置线程池、任务提交、优先级任务、延迟/定时任务、主线程切换、HandlerThread 管理和线程诊断能力。模块 minSdk 21。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [执行 I/O、计算、后台任务](./docs/01-task-executor.md) | `TaskExecutor.io/compute/background` | Java `Future`，回调需自己切主线程 | 文件、网络、图片处理、加密等后台任务 | 内部使用预置线程池，按任务类型隔离资源 |
| [切回主线程或延迟执行](./docs/01-task-executor.md) | `TaskExecutor.main/mainDelayed` | Android 主线程 | 后台任务完成后更新 UI | 使用主线程 `Handler`，当前已在主线程则直接执行 |
| [创建自定义线程池](./docs/02-pool-manager.md) | `ThreadPoolManager.newFixedPool/newCustomPool` | 需要自己管理生命周期 | 业务需要独立线程池、命名和队列 | 工厂方法封装线程命名、优先级和拒绝策略 |
| [提交优先级任务](./docs/02-pool-manager.md) | `TaskExecutor.background(Priority.HIGH)` | 适合后台优先级队列 | 当前页面任务要优先于预加载 | 内置 priorityPool 按优先级和提交顺序排序 |
| [管理 HandlerThread](./docs/03-handler-thread-utils.md) | `HandlerManager` | 需要 Looper/Message 模型 | 老代码或 Android Message 机制 | 命名 HandlerThread 可复用、可退出 |
| [线程判断和诊断](./docs/03-handler-thread-utils.md) | `ThreadUtils` | 所有线程 | 断言主线程、打印线程信息、Looper 检查 | 封装 Looper、线程优先级和栈信息 |
| [查看 API 和生命周期](./docs/04-api-lifecycle.md) | API 表 | 所有使用者 | 查方法名、关闭线程池 | 汇总当前源码公开 API 和关闭规则 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. TaskExecutor](./docs/01-task-executor.md) | 任务提交、主线程、延迟/定时、等待和取消 |
| [02. ThreadPoolManager](./docs/02-pool-manager.md) | 预置线程池、自定义线程池、优先级 |
| [03. Handler 与线程工具](./docs/03-handler-thread-utils.md) | `HandlerManager`、`ThreadUtils` |
| [04. API 与生命周期](./docs/04-api-lifecycle.md) | API 速查、关闭和注意事项 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-thread-pools"))
}
```