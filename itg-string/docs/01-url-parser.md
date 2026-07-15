# 01. URL 解析

本节说明如何用 `UrlParser` 把 URL 拆成结构化字段。

## 适用条件

- 需要读取 scheme、host、port、path、query、fragment。
- URL 可能包含中文、emoji 或其他非 ASCII 字符。
- 解析失败希望返回 `null`，而不是让业务崩溃。

## 推荐做法

```kotlin
val components = UrlParser.parse(url) ?: return
val host = components.host
val path = components.path
```

## 可复制 Demo

```kotlin
import com.itg.itg_string.core.UrlParser

fun parseDeepLink(url: String): String? {
    val c = UrlParser.parse(url) ?: return null
    if (!c.isHttps) return null
    return "host=${c.host}, path=${c.path}, query=${c.query}"
}

val info = parseDeepLink("https://example.com/product/detail?id=42#top")
```

## 关键说明

- `parse(url)` 失败返回 `null`。
- `parseOrThrow(url)` 失败抛 `IllegalArgumentException`。
- `getPort(url)` 未显式指定端口时返回 `-1`。
- `UrlComponents.isDefaultPort` 会把 `http:80`、`https:443` 识别为默认端口。
- 异步方法如 `parseAsync(url) { ... }` 返回 `Future<*>`，回调不在主线程保证。

## 验证方式

- 中文 URL 不应直接解析失败。
- 对非法 URL 调用 `parse()` 应返回 `null`。
- HTTPS URL 的 `components.isHttps` 应为 `true`。

[返回 README](../README.md)