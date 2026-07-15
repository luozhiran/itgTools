# ITG Log

`itg-log` 是轻量级步骤计时日志库，提供 `TestLogger` 门面、线程安全步骤计时、可插拔 Formatter、可组合 Output 和全局生产开关。模块 minSdk 21，仅依赖 Android SDK。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [记录测试步骤耗时](./docs/01-logger-basics.md) | `TestLogger.beginTest/log/endTest` | 开发、测试、Demo 页面 | 需要看每一步耗时和总耗时 | `StepTimer` 记录步骤序号、增量耗时、累计耗时 |
| [输出到 Logcat 和 UI](./docs/01-logger-basics.md) | `outputToLogcat`、`outputToCallback` | Android UI 或调试面板 | 想把同一份日志同时显示在控制台和页面 | `LogOutput` 可组合多个输出目标 |
| [自定义格式和输出](./docs/02-format-output.md) | `LogFormatter`、`LogOutput` | 需要 JSON、文件或自定义样式 | 日志要进入文件、分析系统或特殊 UI | Formatter 和 Output 都是接口/策略对象 |
| [生产环境关闭](./docs/03-production-threading.md) | `ItgLog.globalEnabled` | release 构建 | 避免生产产生测试日志和 I/O | 全局开关关闭后不做格式化和输出 |
| [多线程并发记录](./docs/03-production-threading.md) | `logger.d/ok/fail` | 多线程、线程池、协程 | 并发任务仍要保持步骤顺序 | 核心计时方法同步，步骤号严格递增 |
| [查看 API 速查](./docs/04-api-reference.md) | API 表 | 所有使用者 | 已知道场景，只查方法 | 汇总源码公开类型和常用配置 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. Logger 基础](./docs/01-logger-basics.md) | `TestLogger` 基础用法和 UI 输出 |
| [02. 格式化与输出](./docs/02-format-output.md) | 自定义 Formatter、Output、多目标输出 |
| [03. 生产开关与线程安全](./docs/03-production-threading.md) | release 关闭、并发记录、协程配合 |
| [04. API 速查](./docs/04-api-reference.md) | `ItgLog`、`TestLogger`、`LogConfig` 等 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-log"))
}
```