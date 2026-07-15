# 02. 查询参数

本节说明 `QueryParams` 如何解析、构建和编解码查询参数。

## 适用条件

- 只关心 query string，不需要完整 URL 结构。
- 参数可能有重复 key，例如 `tag=a&tag=b`。
- 需要处理非 UTF-8 编码参数。

## 推荐做法

```kotlin
val params = QueryParams.parse("q=kotlin&tag=android&tag=jvm")
val firstTag = params["tag"]?.firstOrNull()
```

## 可复制 Demo

```kotlin
import com.itg.itg_string.query.QueryParams
import java.nio.charset.Charset

val query = "q=kotlin&tag=android&tag=jvm"
val params = QueryParams.parse(query)
val keyword = QueryParams.getFirst(query, "q")
val tags = QueryParams.getAll(query, "tag")
val rebuilt = QueryParams.buildMulti(params)

val legacy = QueryParams.decode("%D6%D0%CE%C4", Charset.forName("GBK"))
```

## 关键说明

- `parse()` 返回 `Map<String, List<String>>`，同名 key 会保留所有值。
- `getParam(url, key)` 可直接从完整 URL 中取 query 参数。
- `build()` 适合单值 Map，`buildMulti()` 适合多值 Map。
- `encode/decode` 默认 UTF-8，`decode(value, charset)` 可指定字符集。
- 解码失败会返回原始字符串，不抛异常。

## 验证方式

- `QueryParams.getAll("tag=a&tag=b", "tag")` 应返回两个值。
- `QueryParams.build(mapOf("q" to "hello world"))` 应编码空格。

[返回 README](../README.md)