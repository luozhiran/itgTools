# ITG Concurrent Core — 统一并发中间件

**一次编码，任意后端。** 提供统一并发 API，底层可在线程池和协程之间自由切换，无需修改业务代码。

## 快速开始

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":itg-concurrent-core"))
    implementation(project(":itg-coroutine-pools"))  // 或 itg-thread-pools
}

// 使用
import com.itg.concurrent.Concurrent
import com.itg.concurrent.ConcurrentFactory

ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)

Concurrent.io {
    val data = api.fetchData()
    Concurrent.main { updateUI(data) }
}
```

## 后端切换

```kotlin
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)

// 混合：IO→协程, Compute→线程池
ConcurrentFactory.useMixed(mapOf(
    DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
    DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL
))
```

## 架构

```
Concurrent (统一入口)
    │
ConcurrentFactory (后端注册/切换)
    ├── THREAD_POOL → ThreadPoolAdapter → itg-thread-pools
    ├── COROUTINE   → CoroutineAdapter  → itg-coroutine-pools
    └── AUTO        → 自动检测 classpath
```

## 详细文档

| 文档 | 说明 |
|------|------|
| [完整使用指南](./docs/UsageGuide.md) | 🔥 20 个场景全覆盖 |
| [Concurrent](./docs/Concurrent.md) | 统一入口 + 工厂教程 |
| [BackendAdapters](./docs/BackendAdapters.md) | 适配器原理 + 自定义 |
| [ConcurrentUtils](./docs/ConcurrentUtils.md) | 工具方法 |
