# ITG String

`itg-string` 是 Android 字符串与 URL 工具模块，覆盖 URL 解析、查询参数处理、URL/Map 到 Bundle/Intent 的桥接，以及链式 URL/Intent 构造。模块 minSdk 21，异步 API 依赖 `itg-thread-pools`。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [解析 URL 结构](./docs/01-url-parser.md) | `UrlParser.parse/getHost/getPath` | 同步 API，异步 `*Async` 回调在线程池 | 需要拿 scheme、host、path、query、fragment | 基于 `java.net.URI`，并对非 ASCII URL 做 UTF-8 percent-encoding |
| [处理查询参数](./docs/02-query-params.md) | `QueryParams.parse/build/getParam` | 支持 UTF-8 和指定 `Charset` | 需要读写 `?a=1&tag=x&tag=y` | 返回 `Map<String, List<String>>`，保留多值参数 |
| [把 URL 或 Map 注入 Bundle/Intent](./docs/03-intent-bridge.md) | `UrlIntentBuilder.toBundle/putExtras` | Android `Bundle`/`Intent` 场景 | 深链、推送、配置参数要传给页面 | 内置 URL、单值 Map、多值 Map、异构 Map 多入口 |
| [链式构建 Intent](./docs/03-intent-bridge.md) | `IntentBuilder().fromUrl().put().build()` | 需要合并多个参数来源 | 页面跳转参数来源复杂，需要错误兜底 | 构造器记录错误但不中断后续数据源 |
| [构建或改写 URL](./docs/04-url-builder-api.md) | `UrlBuilder` | 需要生成 URL 字符串或 `UrlComponents` | 给接口拼路径、参数、fragment | 内部复用 `QueryParams.buildMulti`，避免手写拼接 |
| [查看 API 速查](./docs/04-url-builder-api.md) | API 表 | 所有使用者 | 已知道能力，只查方法名 | 汇总当前源码公开类和核心方法 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. URL 解析](./docs/01-url-parser.md) | `UrlParser` 与 `UrlComponents` |
| [02. 查询参数](./docs/02-query-params.md) | `QueryParams` 解析、构建、编码 |
| [03. Bundle/Intent 桥接](./docs/03-intent-bridge.md) | `UrlIntentBuilder` 与 `IntentBuilder` |
| [04. URL 构建与 API](./docs/04-url-builder-api.md) | `UrlBuilder` 和 API 速查 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-string"))
}
```

## 包名

- `UrlParser` / `UrlComponents`：`com.itg.itg_string.core`
- `QueryParams`：`com.itg.itg_string.query`
- `UrlIntentBuilder` / `IntentBuilder`：`com.itg.itg_string.intent`
- `UrlBuilder`：`com.itg.itg_string.builder`