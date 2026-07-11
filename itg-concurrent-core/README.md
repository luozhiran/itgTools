# ITG Concurrent Core — 统一并发中间件

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat/android-7.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)

**一次编码，任意后端。** 提供统一的并发 API，底层可在线程池（[itg-thread-pools](../itg-thread-pools)）和协程（[itg-coroutine-pools](../itg-coroutine-pools)）之间自由切换，无需修改业务代码。

---

## 目录

- [核心概念](#核心概念)
- [快速开始](#快速开始)
- [架构概览](#架构概览)
- [详细文档](#详细文档)
- [依赖关系](#依赖关系)

---

## 核心概念

### 为什么需要中间件？

| 场景 | 无中间件 | 有中间件 |
|------|---------|---------|
| 切换后端 | 全局替换 import，修改所有文件 | `switchTo(COROUTINE)` 一行代码 |
| A/B 测试性能 | 无法对比 | 运行时切换，对比性能指标 |
| IO 协程 + 计算线程池 | 无法实现 | `useMixed()` 精细分配 |
| 渐进迁移 | 一次性全部迁移 | 旧模块不改，新模块用中间件 |

### 三模块协同

```
                    你的业务代码
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
  直接用旧 API      通过中间件编码       混合使用
  (不改代码)       (推荐新代码)        (灵活切换)
        │                │                │
        ▼                └────────┬───────┘
  ┌──────────────┐               │
  │itg-thread-pools│       ┌──────▼──────────┐
  │   (零修改)     │       │itg-concurrent-core│ ← 你在这里
  └──────────────┘       │   (统一 API)      │
                          └──┬──────────┬─────┘
                             │          │
                  ┌──────────┘          └──────────┐
                  ▼                                ▼
        线程池后端适配器                  协程后端适配器
   (桥接 itg-thread-pools)        (桥接 itg-coroutine-pools)
```

---

## 快速开始

### 依赖

```kotlin
// settings.gradle.kts
include(":itg-concurrent-core")

// build.gradle.kts — 中间件 + 至少一个后端
dependencies {
    implementation(project(":itg-concurrent-core"))
    implementation(project(":itg-coroutine-pools"))  // 协程后端
    // 和/或
    implementation(project(":itg-thread-pools"))     // 线程池后端
}
```

### 最简使用

```kotlin
import com.itg.concurrent.Concurrent
import com.itg.concurrent.ConcurrentFactory

// 可选：在 Application 中切换后端（默认自动检测）
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)

// 和 TaskExecutor 一样的写法
Concurrent.io {
    val data = api.fetchData()
    Concurrent.main { updateUI(data) }
}
```

### 后端切换

```kotlin
// 全局切换
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)

// 混合模式：IO→协程，Compute→线程池
ConcurrentFactory.useMixed(mapOf(
    DispatcherType.IO      to ConcurrentFactory.BackendType.COROUTINE,
    DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL
))
```

### 协程原生

```kotlin
lifecycleScope.launch {
    val data = Concurrent.ioSuspend { api.fetchData() }
    updateUI(data)
}
```

---

## 架构概览

### 包结构

```
com.itg.concurrent
├── Concurrent.kt                  — 统一入口（门面）
├── ConcurrentFactory.kt           — 工厂 + 后端注册/切换 + 自动检测
├── TaskDispatcher.kt              — 核心接口定义
├── backend/
│   ├── ThreadPoolAdapter.kt       — 线程池后端适配器
│   └── CoroutineAdapter.kt        — 协程后端适配器
└── util/
    └── ConcurrentUtils.kt         — 后端无关的通用工具
```

---

## 详细文档

| 文档 | 说明 |
|------|------|
| [Concurrent](./docs/Concurrent.md) | 统一入口 + 工厂 — 使用教程、后端切换、混合模式、suspend API |
| [BackendAdapters](./docs/BackendAdapters.md) | 适配器原理 — ThreadPoolAdapter/CoroutineAdapter、自定义适配器(Virtual Threads) |
| [ConcurrentUtils](./docs/ConcurrentUtils.md) | 工具方法 — 线程检测/断言、Future辅助、安全休眠 |

---

## 依赖关系

```
itg-concurrent-core
  ├─ compileOnly → itg-thread-pools      (编译时需要，运行时可选)
  ├─ compileOnly → itg-coroutine-pools   (编译时需要，运行时可选)
  └─ implementation → kotlinx-coroutines-core
  └─ implementation → kotlinx-coroutines-android
```

`compileOnly` 意味着：编译时两个模块在 classpath 上，运行时由 app 决定包含哪个。中间件通过 `Class.forName` 自动检测可用后端。
