# 03. Bundle/Intent 桥接

本节说明如何把 URL、Map 或 Bundle 合并到页面跳转参数。

## 适用条件

- 深链 URL 的 query 参数要变成 Activity extras 或 Fragment arguments。
- 本地配置 Map 要注入 `Intent`。
- 多个来源合并时需要 key 前缀隔离。

## 推荐做法

```kotlin
UrlIntentBuilder.putQueryExtras(url, intent, keyPrefix = "url_")
```

## 可复制 Demo

```kotlin
import android.content.Context
import android.content.Intent
import com.itg.itg_string.intent.IntentBuilder
import com.itg.itg_string.intent.UrlIntentBuilder

fun openDetail(context: Context, url: String) {
    val intent = Intent(context, DetailActivity::class.java)
    UrlIntentBuilder.putQueryExtras(url, intent, keyPrefix = "url_")
    context.startActivity(intent)
}

val intent = IntentBuilder()
    .fromUrl("https://example.com/detail?id=42", prefix = "url_")
    .fromMap(mapOf("source" to "push"), prefix = "cfg_")
    .put("client", "android")
    .build()
```

## 关键说明

- URL 入口的值以字符串写入；多值参数写入 `StringArrayList`。
- 异构 Map 支持 `String`、数字、`Boolean`、`Float`、`Double`、字符串/整数列表等常见类型。
- `mergeBundles(first, second, keyPrefix)` 中 second 同名 key 会覆盖 first，传前缀可隔离。
- `IntentBuilder` 遇到坏 URL 会记录到 `errors()`，后续来源继续处理。
- Java 调用 Map 重载时使用源码中的 `@JvmName` 专用名称。

## 验证方式

- 打开目标 Activity 后检查 `intent.getStringExtra("url_id")`。
- 调用 `builder.hasErrors()` 可确认是否发生降级。

[返回 README](../README.md)