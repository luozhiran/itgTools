# ITG Coroutine Pools — Android 协程并发库

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat/android-7.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)

基于 **Kotlin Coroutines** 的 Android 并发工具库，API 与 [itg-thread-pools](../itg-thread-pools) 保持一致，用户只需改 import 即可从线程池无缝切换到协程。

---

## 目录

- [核心概念](#核心概念)
- [快速开始](#快速开始)
- [架构概览](#架构概览)
- [详细文档](#详细文档)
- [与 itg-thread-pools 的关系](#与-itg-thread-pools-的关系)

---

## 核心概念

### 为什么需要协程版？

| 场景 | 线程池 (itg-thread-pools) | 协程 (itg-coroutine-pools) |
|------|--------------------------|---------------------------|
| 1000 个并发 I/O 任务 | 最多 64 线程并行，其余排队 | **全部并发**，协程挂起不占线程 |
| 内存占用 (峰值) | ~64MB (64 线程栈) | **~几 MB** (协程轻量) |
| 任务切换开销 | ~1-10 μs (线程上下文切换) | **~0.1 μs** (协程挂起/恢复) |
| 取消任务 | 需配合 `Thread.interrupted()` | **结构化并发**，自动传播取消 |
| API 写法 | `TaskExecutor.io { onResult(x) }` | `CoroutineExecutor.io { onResult(x) }` (相同) |

---

## 快速开始

### 依赖

```kotlin
// settings.gradle.kts
include(":itg-coroutine-pools")

// build.gradle.kts
dependencies {
    implementation(project(":itg-coroutine-pools"))
}
```

### 最简使用

```kotlin
import com.itg.itg_coroutine_pools.executor.CoroutineExecutor

// 和 TaskExecutor 完全一致的写法
CoroutineExecutor.io {
    val data = api.fetchUserProfile()
    CoroutineExecutor.main { nameTextView.text = data.name }
}
```

### 协程原生写法（推荐新代码）

```kotlin
lifecycleScope.launch {
    val data = CoroutineExecutor.ioSuspend { api.fetchUserProfile() }
    updateUI(data) // 自动在主线程
}
```

---

## 架构概览

### 四大组件

```
┌─────────────────────────────────────────────────┐
│               CoroutineExecutor                  │
│   语义化 API: io { }, compute { }, main { }     │
│   suspend 原生: ioSuspend { }, mainSuspend { }  │
│   Future 兼容 / 延迟 / 定时 / 批量等待           │
└─────┬───────────────────────────────┬───────────┘
      │ 基于 Dispatchers               │ 基于 Channel
┌─────▼──────────────────┐  ┌─────────▼───────────┐
│CoroutineDispatcherMgr  │  │   ChannelManager     │
│  预置 Dispatcher        │  │  Channel 消息通道    │
│  IO/Default/串行/Main   │  │  Message 支持       │
│  自定义工厂             │  │  串行任务处理        │
└─────┬──────────────────┘  └──────────────────────┘
      └──────────────┬─────────────────────────────┐
                     │ 辅助                         │
      ┌──────────────▼───────────────┐              │
      │        CoroutineUtils        │              │
      │ 线程检测·断言·delay·优先级    │◄─────────────┘
      └──────────────────────────────┘
```

### 包结构

```
com.itg.itg_coroutine_pools
├── manager/
│   └── CoroutineDispatcherManager.kt
├── executor/
│   └── CoroutineExecutor.kt
├── channel/
│   └── ChannelManager.kt
└── utils/
    └── CoroutineUtils.kt
```

---

## 详细文档

| 文档 | 说明 |
|------|------|
| [CoroutineExecutor](./docs/CoroutineExecutor.md) | 任务执行器 — io/compute/main/single、Future、延迟、定时、批量等待、suspend API |
| [CoroutineDispatcherManager](./docs/CoroutineDispatcherManager.md) | Dispatcher 管理 — 预置/自定义/Java桥接/ViewModel集成 |
| [ChannelManager](./docs/ChannelManager.md) | 消息通道 — Channel/Message、串行任务、对标 HandlerManager |
| [CoroutineUtils](./docs/CoroutineUtils.md) | 工具方法 — 线程检测/断言、delay vs sleep、优先级、调试 |
| [生命周期管理](./docs/LifecycleManagement.md) | 协程生命周期 — 全局Scope、viewModelScope、自动取消、对比线程池 |

---

## 与 itg-thread-pools 的关系

| 维度 | itg-thread-pools | itg-coroutine-pools |
|------|:---:|:---:|
| 底层机制 | Java ThreadPoolExecutor | Kotlin Coroutines Dispatchers |
| 入口类 | `TaskExecutor` | `CoroutineExecutor` |
| 管理器 | `ThreadPoolManager` | `CoroutineDispatcherManager` |
| 消息通道 | `HandlerManager` | `ChannelManager` |
| 工具类 | `ThreadUtils` | `CoroutineUtils` |
| 返回值 | `Future<T>` | `Future<T>` (Deferred→Future桥接) |
| suspend 支持 | 无 | `ioSuspend { }` |
| 延迟 | `Thread.sleep()` (阻塞) | `delay()` (非阻塞) |

**两套 API 的方法名/参数一致，用户可无感切换。** 也可通过 [itg-concurrent-core](../itg-concurrent-core) 中间件在运行时动态切换后端。

### 依赖

- `kotlinx-coroutines-core` + `kotlinx-coroutines-android`
- `minSdk = 24`
