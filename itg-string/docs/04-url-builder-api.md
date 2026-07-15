# 04. URL 构建与 API

本节说明 `UrlBuilder` 和常用 API 速查。

## 适用条件

- 不想手写字符串拼接 URL。
- 需要在已有 URL 上追加、覆盖或删除 query 参数。
- 需要把结果构建为字符串、`URI` 或 `UrlComponents`。

## 推荐做法

```kotlin
val url = UrlBuilder("https://api.example.com")
    .appendPath("v1")
    .appendPath("users")
    .setQueryParam("page", "1")
    .buildString()
```

## 可复制 Demo

```kotlin
import com.itg.itg_string.builder.UrlBuilder

val url = UrlBuilder("https://api.example.com/search?q=old")
    .setQueryParam("q", "kotlin")
    .addQueryParam("tag", "android")
    .fragment("top")
    .buildString()

check(url == "https://api.example.com/search?q=kotlin&tag=android#top")
```

## API 速查

| 能力 | API |
| --- | --- |
| 解析 URL | `UrlParser.parse`、`parseOrThrow`、`getHost`、`getPath`、`isHttps` |
| 查询参数 | `QueryParams.parse`、`getFirst`、`getAll`、`build`、`buildMulti` |
| Bundle/Intent | `UrlIntentBuilder.toBundle`、`putQueryExtras`、`putExtras`、`mergeBundles` |
| 链式 Intent | `IntentBuilder.fromUrl`、`fromMap`、`fromBundle`、`put`、`build`、`into` |
| URL 构建 | `UrlBuilder.scheme`、`host`、`appendPath`、`setQueryParam`、`buildString` |

## 关键说明

- `UrlBuilder.buildString()` 失败返回 `null`。
- `setQueryParam` 覆盖同名 key，`addQueryParam` 追加多值 key。
- `clearQueryParams()` 会清空当前 builder 中所有 query 参数。
- `reset()` 会清空 builder 状态，适合复用实例前调用。

## 验证方式

- 构建后的 URL 再交给 `UrlParser.parse()` 应能解析成功。
- 重复 key 场景用 `QueryParams.parse(UrlParser.getQuery(url))` 校验多值是否保留。

[返回 README](../README.md)