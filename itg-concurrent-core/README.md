# ITG Concurrent Core

`itg-concurrent-core` 是统一并发入口。业务代码通过 `Concurrent`、`ConcurrentFactory`、`ConcurrentUtils` 访问并发能力，底层可以按配置运行在线程池后端或协程后端。

本文档是入口页：先用场景表帮你选择 API，再通过目录跳转到 `docs/` 下的详细教程。

## 使用场景总览

| 使用场景 | 推荐 API | 后端支持（协程/线程池） | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [快速接入模块](./docs/01-quick-start.md) | `implementation(project(":itg-concurrent-core"))` | 两者都支持 | 新模块第一次接入统一并发能力 | core 只依赖统一抽象，运行时由 app 提供 `itg-coroutine-pools` 或 `itg-thread-pools` 后端 |
| [App 初始化后端](./docs/01-quick-start.md) | `ConcurrentFactory.switchTo(...)` | 两者都支持 | 需要明确使用协程、线程池或自动检测 | `ConcurrentFactory` 统一管理后端选择，业务调用不用改 |
| [执行普通 I/O 任务](./docs/02-basic-tasks.md) | `Concurrent.io { }` | 两者都支持 | 文件、网络同步调用、数据库同步读写等普通非 suspend 任务 | 返回 `Future<T>`，通过当前 IO 分发器提交任务 |
| [执行 CPU 计算任务](./docs/02-basic-tasks.md) | `Concurrent.compute { }` | 两者都支持 | 图片处理、加解密、大量数据转换等 CPU 密集任务 | 使用 `DispatcherType.COMPUTE`，和 I/O 分发器隔离 |
| [执行通用后台任务](./docs/02-basic-tasks.md) | `Concurrent.background { }` | 两者都支持 | 后台清理、统计、预处理等不明确归类的任务 | 使用独立的 BACKGROUND 语义，方便混合后端配置 |
| [切回主线程更新 UI](./docs/02-basic-tasks.md) | `Concurrent.main { }` | 两者都支持 | 后台任务完成后需要更新 UI | 已在主线程时直接执行，否则分发到 MAIN 分发器 |
| [后台任务需要结果](./docs/03-future-delay-utils.md) | `Concurrent.io<T> { }` | 两者都支持 | 调用方要拿到结果、等待结果或取消任务 | 普通任务统一返回 `Future<T>` |
| [取消后台任务](./docs/03-future-delay-utils.md) | `ConcurrentUtils.cancel(future)` | 两者都支持 | 页面退出、搜索防抖、请求过期 | `Future` 是同步任务 API 的统一取消句柄 |
| [后台等待结果](./docs/03-future-delay-utils.md) | `ConcurrentUtils.await(future, timeoutMs)` | 两者都支持 | 已在后台线程中，需要等待另一个任务完成 | 封装 `Future.get/get(timeout)`；主线程不应阻塞等待 |
| [延迟执行任务](./docs/03-future-delay-utils.md) | `mainDelayed/ioDelayed/backgroundDelayed` | 两者都支持 | 延迟提示、搜索防抖、延迟同步或清理 | 分发器提供 `schedule(task, delayMs)`，返回 `Future<*>` 可取消 |
| [判断或断言线程](./docs/03-future-delay-utils.md) | `ConcurrentUtils.isMainThread/assert...` | 后端无关 | API 有主线程或后台线程调用要求 | 基于 Android `Looper` 判断，和后端实现无关 |
| [协程内切到 I/O](./docs/04-coroutine-suspend.md) | `Concurrent.ioSuspend { }` | 两者都支持 | 已在 `lifecycleScope`、`viewModelScope` 或任意协程中 | 协程后端直接切 dispatcher；线程池后端桥接成 `CoroutineDispatcher` |
| [协程内切到计算线程](./docs/04-coroutine-suspend.md) | `Concurrent.computeSuspend { }` | 两者都支持 | 协程内执行 CPU 密集任务 | 使用 COMPUTE 分发器，避免占用主线程或 I/O 通道 |
| [协程内切回主线程](./docs/04-coroutine-suspend.md) | `Concurrent.mainSuspend { }` | 两者都支持 | suspend 流程末尾更新 UI | 以 suspend 方式切 MAIN，不需要再嵌套普通回调 |
| [普通类启动 suspend 任务](./docs/05-managed-scope-flow.md) | `Concurrent.launchIo { }` | 两者都支持 | 不在 Activity、Fragment、ViewModel 中，没有 `lifecycleScope` | 内部创建临时 `ConcurrentScope`，返回 `Job`，任务完成后释放 |
| [普通类长期管理任务](./docs/05-managed-scope-flow.md) | `Concurrent.createScope(...)` | 两者都支持 | Manager、Repository、SDK 组件会多次启动任务 | 由 owner 持有 `ConcurrentScope` 并在释放时 `cancel()` |
| [收集 Flow](./docs/05-managed-scope-flow.md) | `Concurrent.launchIo { flow.collect { } }` | 两者都支持 | 需要调用 `Flow.collect`，但当前没有 lifecycleScope | `collect` 是 suspend 函数，必须在协程上下文中调用 |
| [Flow 结果更新 UI](./docs/05-managed-scope-flow.md) | `Concurrent.mainSuspend { }` | 两者都支持 | Flow 上游后台处理，最终只更新 UI | 后台收集不占主线程，UI 部分显式切 MAIN |
| [全局切换后端](./docs/06-backends.md) | `ConcurrentFactory.switchTo(...)` | 两者都支持 | 初始化、性能实验、灰度切换后端 | 业务依赖 `TaskDispatcher` 抽象，不依赖具体后端 |
| [混合后端配置](./docs/06-backends.md) | `ConcurrentFactory.useMixed(...)` | 两者组合支持 | IO 想用协程、计算想用线程池等场景 | 工厂按 `DispatcherType` 映射后端，业务 API 不变 |
| [线程池后端运行 suspend/Flow](./docs/06-backends.md) | `getCoroutineDispatcher(type)` | 线程池后端可桥接 | 全局切到线程池，但仍要跑 suspend 或 Flow | `TaskDispatcher.toCoroutineDispatcher()` 把线程池桥接为协程 dispatcher |
| [自定义分发器](./docs/BackendAdapters.md) | `ConcurrentFactory.register(...)` | 取决于自定义实现 | 需要埋点、限流、接入特殊线程模型 | 注册表优先级高于默认后端，可只覆盖某个类型 |
| [访问底层分发器](./docs/BackendAdapters.md) | `Concurrent.get(type)` | 两者都支持 | 高级场景要直接 `execute/submit/schedule` | 暴露统一 `TaskDispatcher` 抽象 |
| [获取协程 Dispatcher](./docs/BackendAdapters.md) | `Concurrent.getCoroutineDispatcher(type)` | 两者都支持 | 要和原生 Kotlin 协程 API 组合 | 协程后端返回原生 dispatcher，线程池后端返回桥接 dispatcher |
| [页面或对象释放时取消任务](./docs/07-lifecycle-errors-java.md) | `Job.cancel()` / `ConcurrentScope.cancel()` | 两者都支持 | 页面销毁、对象 release、订阅停止 | suspend/Flow 任务以 `Job` 或 scope 为取消边界 |
| [处理异常](./docs/07-lifecycle-errors-java.md) | `try/catch` / `ExecutionException` | 两者都支持 | 任务可能失败、网络异常、解析异常 | Future 和 coroutine 有不同异常承载方式，需要分别处理 |
| [Java 侧调用](./docs/07-lifecycle-errors-java.md) | `Concurrent.io(() -> ...)` | 两者都支持 | Java 代码提交普通后台任务 | Future 风格 API 对 Java 友好，suspend API 不适合 Java 直接调用 |
| [查看完整 API](./docs/Concurrent.md) | API 速查表 | 两者都支持 | 已知道场景，只想查方法签名 | 统一列出 `Concurrent`、`ConcurrentFactory`、`ConcurrentScope`、`ConcurrentUtils` |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. 快速接入](./docs/01-quick-start.md) | 依赖、初始化、最小可运行示例 |
| [02. 普通任务与主线程](./docs/02-basic-tasks.md) | `io`、`compute`、`background`、`main` 的选择和示例 |
| [03. Future、延迟与工具](./docs/03-future-delay-utils.md) | 返回值、取消、等待、延迟、线程检测 |
| [04. 协程内切线程](./docs/04-coroutine-suspend.md) | `ioSuspend`、`computeSuspend`、`backgroundSuspend`、`mainSuspend` |
| [05. 托管 Scope 与 Flow](./docs/05-managed-scope-flow.md) | 无 lifecycleScope 场景、`launch*`、`createScope`、Flow 收集 |
| [06. 后端选择与桥接](./docs/06-backends.md) | AUTO/COROUTINE/THREAD_POOL、混合模式、线程池桥接协程 |
| [07. 生命周期、异常与 Java](./docs/07-lifecycle-errors-java.md) | 取消边界、异常处理、Java 调用方式 |
| [BackendAdapters](./docs/BackendAdapters.md) | `TaskDispatcher`、适配器、桥接和自定义分发器 |
| [ConcurrentUtils](./docs/ConcurrentUtils.md) | 后端无关的工具方法 |
| [API 速查表](./docs/Concurrent.md) | 当前真实可用 API 列表 |

## 架构概览

```text
Concurrent
  -> ConcurrentFactory
      -> TaskDispatcher
          -> CoroutineAdapter  -> itg-coroutine-pools
          -> ThreadPoolAdapter -> itg-thread-pools
```

## 兼容入口

旧的 [UsageGuide.md](./docs/UsageGuide.md) 已改为拆分文档索引，新的阅读入口是当前 `README.md`。