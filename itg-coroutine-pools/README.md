# ITG Coroutine Pools — Android 协程并发库

基于 **Kotlin Coroutines** 的 Android 并发工具库，API 与 [itg-thread-pools](../itg-thread-pools) 保持一致，改 import 即可从线程池无缝切换到协程。

---

## 快速开始

```kotlin
// build.gradle.kts
dependencies { implementation(project(":itg-coroutine-pools")) }

// 使用
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor

CoroutineExecutor.io {
    val data = api.fetchData()
    CoroutineExecutor.main { updateUI(data) }
}
```

## 架构

| 组件 | 对标 | 说明 |
|------|------|------|
| `CoroutineExecutor` | `TaskExecutor` | io/compute/main/single、Future、延迟、定时、suspend |
| `CoroutineDispatcherManager` | `ThreadPoolManager` | IO/Default/串行/Main Dispatcher、自定义工厂 |
| `ChannelManager` | `HandlerManager` | Channel 消息通道、Message 支持、串行任务 |
| `CoroutineUtils` | `ThreadUtils` | 线程检测、断言、delay、优先级 |

## 详细文档

| 文档 | 说明 |
|------|------|
| [CoroutineExecutor](./docs/CoroutineExecutor.md) | 10 个教程：线程切换、Future、延迟、定时、批量等待、suspend |
| [CoroutineDispatcherManager](./docs/CoroutineDispatcherManager.md) | 6 个教程：预置 Dispatcher、自定义、Java桥接、ViewModel |
| [ChannelManager](./docs/ChannelManager.md) | 7 个教程：通道创建、Message、实战串行处理 |
| [CoroutineUtils](./docs/CoroutineUtils.md) | 8 个教程：检测、断言、delay vs sleep、优先级 |
| [生命周期管理](./docs/LifecycleManagement.md) | 全局Scope、viewModelScope、对比线程池 |
