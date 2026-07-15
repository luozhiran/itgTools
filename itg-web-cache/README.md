# ITG Web Cache

`itg-web-cache` 是 WebView 缓存预热与正式容器缓存策略模块，提供远程配置模型、URL 预热规则、隐藏 WebView 预热、正式 WebView cacheMode 应用、缓存清理、事件和日志回调。模块 minSdk 21。

## 使用场景总览

| 使用场景 | 推荐 API/配置 | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [初始化 WebCacheRuntime](./docs/01-runtime-config.md) | `WebCacheRuntime.configure` | Application 或 Web 容器初始化 | 需要统一读取配置、上报事件和日志 | Runtime 通过 provider 懒读配置，事件和日志安全回调 |
| [给正式 WebView 应用缓存策略](./docs/02-container-policy.md) | `applyToContainer` | URL/scene 命中白名单 | 正式页面希望使用 `LOAD_DEFAULT` 或缓存优先 | Resolver 根据开关、白名单、黑名单和 cacheMode 返回 policy |
| [跟踪页面加载事件](./docs/02-container-policy.md) | `onContainerPageStarted/Finished/detachContainer` | WebView 生命周期回调中调用 | 需要统计首屏耗时或清理弱引用状态 | Runtime 记录 page start 时间并发出事件 |
| [配置预热 URL](./docs/03-preload-rules.md) | `preloadUrls`、`preloadUrlRules` | 只允许业务域名 | 首页稳定后预热 H5 资源 | Resolver 按 enable、黑名单、网络、登录、优先级筛选规则 |
| [控制并行预热和资源预算](./docs/03-preload-rules.md) | `preloadParallelEnable/count` | 实验功能，高内存设备慎用 | 多个低风险页面需要更快预热 | 并发 WebView 会增加内存峰值，配置中有 WiFi/高内存约束 |
| [学习复杂配置字段](./docs/05-config-field-guide.md) | `WebCacheConfig` 字段分组 | 远程配置、本地兜底、默认值都适用 | 不确定某个字段该怎么配、和其他字段怎么组合 | 字段按源码生效链路拆分，说明优先级、默认值、远程 key 和验证方式 |
| [接入运行态状态](./docs/06-runtime-state.md) | `WebCacheRuntimeState`、`WebCacheStateProvider` | 预热启动前读取状态快照 | 需要把前后台、首页就绪、登录态、网络、低内存、低电量消息接入预热判断 | `startNow()` 会读取 stateProvider，Resolver 用状态字段筛选规则和决定是否并行 |
| [生产监控与命中率采集](./docs/07-metrics-monitoring.md) | `WebCacheEventListener`、`WebCacheLogger` | 生产灰度和线上监控 | 需要统计策略命中率、预热成功率、预热覆盖率、耗时收益和异常原因 | 模块所有关键路径都会发出 `WebCacheEvent`，可按事件名、reason、scene、ruleId 聚合指标 |
| [清理 WebView 缓存](./docs/04-clean-clear-api.md) | `WebCacheCleaner`、`clearCacheVersion` | 应用级 WebView 缓存 | 缓存污染、灰度回滚、关闭预热后按策略清理 | 版本号保证一次性执行，策略避免误清理 |
| [查看 API 速查](./docs/04-clean-clear-api.md) | API 表 | 所有使用者 | 查配置字段和运行时 API | 汇总源码中的公开数据类和入口 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. Runtime 初始化](./docs/01-runtime-config.md) | configure、provider、事件和日志 |
| [02. 正式容器策略](./docs/02-container-policy.md) | applyToContainer、cacheMode、scene/url 白名单 |
| [03. 预热规则](./docs/03-preload-rules.md) | preloadUrls、PreloadUrlRule、并行预热限制 |
| [04. 清理与 API](./docs/04-clean-clear-api.md) | 缓存清理、版本号、API 速查 |
| [05. 复杂配置字段学习](./docs/05-config-field-guide.md) | WebCacheConfig 字段分组、优先级、配置 Demo 和常见误区 |
| [06. 运行态状态接入](./docs/06-runtime-state.md) | WebCacheRuntimeState 字段用途、消息更新示例和常见误区 |
| [07. 生产监控与命中率采集](./docs/07-metrics-monitoring.md) | eventListener/logger 采集方案、指标口径、可复制 Demo 和告警建议 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-web-cache"))
}
```
