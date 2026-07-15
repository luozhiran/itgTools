# API 速查表

本文件只列出当前源码中真实存在的 API。

## Concurrent

| API | 返回 | 说明 |
| --- | --- | --- |
| `main(task)` | `Unit` | 主线程执行，已在主线程时直接执行 |
| `io<T>(task)` | `Future<T>` | I/O 分发器执行普通任务 |
| `compute<T>(task)` | `Future<T>` | COMPUTE 分发器执行普通任务 |
| `background<T>(task)` | `Future<T>` | BACKGROUND 分发器执行普通任务 |
| `mainDelayed(task, delayMs)` | `Future<*>` | 主线程延迟执行 |
| `ioDelayed(task, delayMs)` | `Future<*>` | I/O 延迟执行 |
| `backgroundDelayed(task, delayMs)` | `Future<*>` | 后台延迟执行 |
| `createScope(type, name)` | `ConcurrentScope` | 创建可手动取消的托管协程 scope |
| `launch(type, name, block)` | `Job` | 一次性启动 suspend 任务 |
| `launchIo(name, block)` | `Job` | 在 IO 分发器启动 suspend 任务 |
| `launchCompute(name, block)` | `Job` | 在 COMPUTE 分发器启动 suspend 任务 |
| `launchBackground(name, block)` | `Job` | 在 BACKGROUND 分发器启动 suspend 任务 |
| `launchMain(name, block)` | `Job` | 在 MAIN 分发器启动 suspend 任务 |
| `ioSuspend(task)` | `T` | suspend 环境中切到 IO |
| `computeSuspend(task)` | `T` | suspend 环境中切到 COMPUTE |
| `backgroundSuspend(task)` | `T` | suspend 环境中切到 BACKGROUND |
| `mainSuspend(task)` | `T` | suspend 环境中切到 MAIN |
| `get(type)` | `TaskDispatcher` | 获取底层任务分发器 |
| `getCoroutineDispatcher(type)` | `CoroutineDispatcher` | 获取或桥接协程 dispatcher |

## ConcurrentScope

| API | 返回 | 说明 |
| --- | --- | --- |
| `launch(block)` | `Job` | 在 scope 内启动 suspend 任务 |
| `cancel()` | `Unit` | 取消 scope 及其子任务 |
| `close()` | `Unit` | 等同于 `cancel()` |

## ConcurrentFactory

| API | 说明 |
| --- | --- |
| `switchTo(backend)` | 全局切换后端，并清空手动注册表 |
| `useMixed(config)` | 按 `DispatcherType` 使用不同后端 |
| `register(type, dispatcher)` | 手动注册指定类型的分发器 |
| `isCoroutineAvailable()` | 检查协程后端是否可用 |
| `isThreadPoolAvailable()` | 检查线程池后端是否可用 |
| `getAvailableBackends()` | 返回当前 classpath 可用后端 |
| `shutdown()` | 关闭已注册且支持关闭的分发器，并清空注册表 |

## DispatcherType

| 类型 | 说明 |
| --- | --- |
| `IO` | 网络、文件、数据库等 I/O 密集任务 |
| `COMPUTE` | CPU 密集计算 |
| `BACKGROUND` | 通用后台任务 |
| `SINGLE` | 单线程串行分发器类型，当前 `Concurrent` 门面没有单独快捷方法 |
| `MAIN` | Android 主线程 |

## 可复制 Demo

下面是一个组合使用 demo：普通后台任务、计算任务、主线程更新、托管 scope 都包含在内。需要替换 `binding.resultText`。

```kotlin
import com.itg.concurrent.Concurrent
import com.itg.concurrent.DispatcherType

class ConcurrentApiDemo {
    private val scope = Concurrent.createScope(DispatcherType.IO, "api-demo")

    fun run() {
        val future = Concurrent.io {
            "raw-data"
        }

        Concurrent.launchBackground {
            val raw = future.get()
            val formatted = Concurrent.computeSuspend {
                raw.uppercase()
            }

            Concurrent.mainSuspend {
                // TODO: 替换成你的 UI 更新逻辑
                binding.resultText.text = formatted
            }
        }
    }

    fun release() {
        scope.cancel()
    }
}
```

## 验证方式

- 只使用本文件列出的 API，避免使用不存在的历史文档 API。
- 如果 IDE 找不到某个方法，先确认 `itg-concurrent-core` 版本是否包含该 API。
- `DispatcherType.SINGLE` 是枚举类型，不代表 `Concurrent.single { }` 快捷方法存在。

[返回 README](../README.md)