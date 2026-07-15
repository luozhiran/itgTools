# 01. 快速接入

本节解决第一次接入 `itg-concurrent-core` 时应该依赖什么、在哪里初始化、如何验证最小调用可用。

## 适用条件

- 模块需要统一提交后台任务、主线程任务、延迟任务或协程任务。
- app 至少提供一个后端：`itg-coroutine-pools` 或 `itg-thread-pools`。
- 希望业务代码不直接依赖具体线程池或协程池实现。

## 依赖配置

```kotlin
dependencies {
    implementation(project(":itg-concurrent-core"))

    // 至少提供一个后端。两者都提供时，AUTO 默认优先协程后端。
    implementation(project(":itg-coroutine-pools"))
    implementation(project(":itg-thread-pools"))
}
```

## 初始化后端

不配置时默认是 `AUTO`，会自动检测可用后端。需要明确策略时，在 `Application.onCreate()` 配置：

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
    }
}
```

可选值：

| 后端 | 说明 |
| --- | --- |
| `AUTO` | 自动检测 classpath，优先协程后端，其次线程池后端 |
| `COROUTINE` | 强制使用 `itg-coroutine-pools` |
| `THREAD_POOL` | 强制使用 `itg-thread-pools` |

## 推荐做法

```kotlin
Concurrent.io {
    val data = loadConfig()
    Concurrent.main {
        render(data)
    }
}
```

## 可复制 Demo

把下面代码放进任意 Activity 的 `onCreate` 中即可验证。需要把 `binding.statusText` 替换成你自己的 TextView 或 UI 更新逻辑。

```kotlin
import android.os.Bundle
import com.itg.concurrent.Concurrent
import com.itg.concurrent.ConcurrentFactory

class DemoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)

        Concurrent.io {
            val configText = "loaded on ${Thread.currentThread().name}"

            Concurrent.main {
                // TODO: 替换成你的 UI 更新逻辑
                binding.statusText.text = configText
            }
        }
    }
}
```

## 关键说明

- `ConcurrentFactory.switchTo(...)` 是进程级配置，通常放在 `Application.onCreate()`。
- `AUTO` 适合默认接入；如果要做性能对比或灰度，可以显式切 `COROUTINE` 或 `THREAD_POOL`。
- UI 更新必须放到 `Concurrent.main { }` 或 `Concurrent.mainSuspend { }`。

## 验证方式

```kotlin
val backends = ConcurrentFactory.getAvailableBackends()
val current = ConcurrentFactory.currentBackend
val hasCoroutine = ConcurrentFactory.isCoroutineAvailable()
val hasThreadPool = ConcurrentFactory.isThreadPoolAvailable()
```

确认点：

- `getAvailableBackends()` 至少返回一个后端。
- 后台任务没有在主线程执行耗时逻辑。
- UI 更新通过 `Concurrent.main { }` 或 `mainSuspend { }` 回到主线程。

[返回 README](../README.md)