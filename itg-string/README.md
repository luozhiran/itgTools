# ITG String — Android URL 解析工具库

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat/android-7.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![JDK](https://img.shields.io/badge/Engine-java.net.URI-orange.svg)](https://docs.oracle.com/javase/8/docs/api/java/net/URI.html)

ITG String 是 ItgTools 项目中的字符串处理模块，首期提供 **URL 解析** 能力。基于 `java.net.URI` 严格遵循 RFC 3986，零外部依赖，覆盖全量解析、单独获取、查询参数处理、流式 URL 构造等场景。所有操作同步/异步双模式，异步基于 [itg-thread-pools](../itg-thread-pools/)。

---

## 目录

- [设计原则](#设计原则)
- [快速开始](#快速开始)
- [模块结构](#模块结构)
- [核心 API：UrlParser](#核心-apiurlparser)
  - [全量解析](#全量解析)
  - [单独获取](#单独获取)
  - [便捷方法](#便捷方法)
  - [异步接口](#异步接口)
- [查询参数：QueryParams](#查询参数queryparams)
  - [解析查询字符串](#解析查询字符串)
  - [从完整 URL 提取参数](#从完整-url-提取参数)
  - [构建查询字符串](#构建查询字符串)
  - [编码/解码](#编码解码)
- [流式构造：UrlBuilder](#流式构造urlbuilder)
  - [从已有 URL 修改](#从已有-url-修改)
  - [从空白构造](#从空白构造)
  - [查询参数操作](#查询参数操作)
- [数据模型：UrlComponents](#数据模型urlcomponents)
- [实战场景](#实战场景)
  - [场景 A：解析 URL 提取关键信息](#场景-a解析-url-提取关键信息)
  - [场景 B：深度链接路由分发](#场景-b深度链接路由分发)
  - [场景 C：API 基础地址动态切换](#场景-capi-基础地址动态切换)
  - [场景 D：URL 参数注入与追踪](#场景-durl-参数注入与追踪)
  - [场景 E：从 URL 提取查询参数](#场景-e从-url-提取查询参数)
- [API 完整参考](#api-完整参考)
- [线程模型](#线程模型)
- [许可证](#许可证)

---

## 设计原则

- 引擎选用 `java.net.URI`，严格遵循 RFC 3986，无需 Android Context，可在 JVM 单测中直接运行
- 解析失败返回 `null`，不抛异常（与项目其他模块一致）
- 所有方法无共享可变状态，天然线程安全
- `@JvmStatic` + `@JvmOverloads` 全支持，Java 调用方无感知
- 同步 + 异步双模式，异步通过 `TaskExecutor.io` 驱动

---

## 快速开始

### 依赖

模块内部已经依赖 `itg-thread-pools`，调用方只要依赖 `:itg-string` 即可：

```kotlin
implementation(project(":itg-string"))
```

### 30 秒上手

```kotlin
import com.itg.itg_string.core.UrlParser

val url = "https://user:pass@www.example.com:8080/path?query=param#fragment"

// 全量获取——一次解析，所有字段可用
val c = UrlParser.parse(url)
println(c?.scheme)     // "https"
println(c?.host)       // "www.example.com"
println(c?.port)       // 8080
println(c?.path)       // "/path"

// 单独获取——只要一个字段
UrlParser.getHost(url)      // "www.example.com"
UrlParser.getScheme(url)    // "https"
UrlParser.getPath(url)      // "/path"
UrlParser.getQuery(url)     // "query=param"
UrlParser.getFragment(url)  // "fragment"
UrlParser.getPort(url)      // 8080
```

---

## 模块结构

```
itg-string/
└── src/main/java/com/itg/itg_string/
    ├── core/
    │   ├── UrlComponents.kt      ← 不可变数据类：承载全量解析结果
    │   └── UrlParser.kt          ← object 工具类：核心解析入口 (20+ 方法)
    ├── query/
    │   └── QueryParams.kt        ← object 工具类：查询参数拆解/构建/编解码 (14 方法)
    └── builder/
        └── UrlBuilder.kt         ← class：流式修改/构造 URL (20+ 方法)
```

**总计: 3 个工具 class/object, 1 个数据类, 60+ 个公开方法, 全部 @JvmStatic 支持 Java 调用。**

---

## 核心 API：UrlParser

**位置**: `com.itg.itg_string.core.UrlParser`

### 全量解析

返回 `UrlComponents` 数据类，一次解析后所有字段同时可用。

```kotlin
val url = "https://user:pass@www.example.com:8080/path/to/resource?query=param#fragment"
val c = UrlParser.parse(url) ?: return  // 解析失败返回 null

// c 的每个字段都是不可变的
c.scheme        // "https"
c.userInfo      // "user:pass"
c.host          // "www.example.com"
c.port          // 8080
c.authority     // "user:pass@www.example.com:8080"
c.path          // "/path/to/resource"
c.query         // "query=param"
c.fragment      // "fragment"
c.rawUrl        // 原始输入字符串
c.isAbsolute    // true
c.isOpaque      // false

// 派生属性
c.schemeHostPath  // "https://www.example.com/path/to/resource"
c.hostPort        // "www.example.com:8080"
c.isHttps         // true
c.isDefaultPort   // false (8080 不是 HTTPS 默认端口)
```

> 需要抛异常的场景用 `UrlParser.parseOrThrow(url)`，解析失败抛 `IllegalArgumentException`。

### 单独获取

只需要 URL 中某一个字段时，直接用便捷方法，无需取 `UrlComponents`：

```kotlin
// scheme
UrlParser.getScheme("https://example.com")       // "https"
UrlParser.getScheme("ftp://files.example.com")    // "ftp"

// host
UrlParser.getHost("https://www.example.com/path")   // "www.example.com"
UrlParser.getHost("https://192.168.1.1:8080/api")   // "192.168.1.1"

// port
UrlParser.getPort("https://example.com:8080/path")   // 8080
UrlParser.getPort("https://example.com/path")        // -1 (未指定)

// path
UrlParser.getPath("https://example.com/search?q=1")  // "/search"

// query
UrlParser.getQuery("https://example.com?q=kotlin&page=1")  // "q=kotlin&page=1"

// fragment
UrlParser.getFragment("https://example.com/doc#intro")     // "intro"

// userInfo
UrlParser.getUserInfo("https://admin:secret@example.com")  // "admin:secret"

// authority
UrlParser.getAuthority("https://user:pass@example.com:8080/path")
// "user:pass@example.com:8080"
```

### 便捷方法

```kotlin
// 合法性校验
UrlParser.isValidUrl("https://example.com")   // true
UrlParser.isValidUrl("not a url")             // false

// HTTPS 判断
UrlParser.isHttps("https://example.com")      // true
UrlParser.isHttps("http://example.com")       // false

// 提取基础 URL (scheme://host)
UrlParser.getBase("https://example.com:8080/path?q=1")  // "https://example.com"
```

### 异步接口

所有同步方法均有对应的 `*Async` 版本，通过 `TaskExecutor.io` 在 I/O 线程池执行：

```kotlin
// 全量解析异步
UrlParser.parseAsync(url) { components ->
    components?.let { c ->
        TaskExecutor.main { textView.text = c.host }
    }
}

// 单独获取异步
UrlParser.getHostAsync(url) { host ->
    host?.let { TaskExecutor.main { updateUI(it) } }
}

UrlParser.getSchemeAsync(url) { scheme -> /* ... */ }
UrlParser.getPathAsync(url) { path -> /* ... */ }
UrlParser.getQueryAsync(url) { query -> /* ... */ }
UrlParser.getFragmentAsync(url) { fragment -> /* ... */ }
UrlParser.getPortAsync(url) { port -> /* ... */ }
UrlParser.getUserInfoAsync(url) { userInfo -> /* ... */ }
UrlParser.getAuthorityAsync(url) { authority -> /* ... */ }
```

---

## 查询参数：QueryParams

**位置**: `com.itg.itg_string.query.QueryParams`

独立处理 `?key1=value1&key2=value2` 子语言，支持多值参数和编解码。

### 解析查询字符串

```kotlin
val params = QueryParams.parse("q=kotlin&tag=android&tag=jvm&flag")
// {
//   "q"    → ["kotlin"],
//   "tag"  → ["android", "jvm"],
//   "flag" → [""]
// }

// 取第一个值
QueryParams.getFirst("q=kotlin&page=2", "q")      // "kotlin"
QueryParams.getFirst("q=kotlin&page=2", "size")    // null

// 取所有值（多值参数）
QueryParams.getAll("tag=a&tag=b&tag=c", "tag")     // ["a", "b", "c"]

// 判断 key 是否存在
QueryParams.containsKey("q=kotlin&page=1", "q")    // true
QueryParams.containsKey("q=kotlin&page=1", "size")  // false

// 参数个数（去重 key 数）
QueryParams.count("a=1&b=2&c=3")                   // 3
QueryParams.count("tag=a&tag=b&tag=c")             // 1 (同一个 key)
```

### 从完整 URL 提取参数

无需手动分离 query 部分：

```kotlin
val url = "https://example.com/search?q=kotlin&page=1&tag=a&tag=b"

// 单值快捷
QueryParams.getParam(url, "q")        // "kotlin"
QueryParams.getParam(url, "page")     // "1"
QueryParams.getParam(url, "missing")  // null

// 多值
QueryParams.getParams(url, "tag")     // ["a", "b"]
```

### 构建查询字符串

```kotlin
// 单值参数 → 构建
val qs = QueryParams.build(mapOf(
    "q" to "kotlin",
    "page" to "1",
    "sort" to "desc"
))
// "q=kotlin&page=1&sort=desc"

// 多值参数 → 构建
val qsMulti = QueryParams.buildMulti(linkedMapOf(
    "tag" to listOf("android", "kotlin"),
    "q" to listOf("search term")
))
// "tag=android&tag=kotlin&q=search+term"
```

### 编码/解码

```kotlin
QueryParams.encode("hello world")     // "hello+world"
QueryParams.encode("a&b=c")           // "a%26b%3Dc"

QueryParams.decode("hello+world")     // "hello world"
QueryParams.decode("a%26b%3Dc")       // "a&b=c"

// 往返一致性
QueryParams.decode(QueryParams.encode("name=John"))  // "name=John"
```

---

## 流式构造：UrlBuilder

**位置**: `com.itg.itg_string.builder.UrlBuilder`

用于**修改已有 URL 的某个字段**或**从空白构造**新 URL。链式调用，每次 `build()` / `buildString()` 基于当前状态创建新值。

### 从已有 URL 修改

```kotlin
// 修改 scheme + port
val url = UrlBuilder("https://www.example.com:8080/api/v1?q=old#sec")
    .scheme("http")
    .port(3000)
    .buildString()
// "http://www.example.com:3000/api/v1?q=old#sec"

// 追加路径段
val url2 = UrlBuilder("https://api.example.com")
    .appendPath("v2")
    .appendPath("users")
    .appendPath("profile")
    .buildString()
// "https://api.example.com/v2/users/profile"

// 修改 fragment
UrlBuilder("https://example.com/doc#old").fragment("new").buildString()
// "https://example.com/doc#new"
```

### 从空白构造

```kotlin
val url = UrlBuilder()
    .scheme("https")
    .userInfo("admin:secret")
    .host("api.example.com")
    .port(8443)
    .path("/v1/users")
    .setQueryParam("page", "1")
    .fragment("summary")
    .buildString()
// "https://admin:secret@api.example.com:8443/v1/users?page=1#summary"
```

### 查询参数操作

Builder 内置了查询参数管理，无需手动拼接字符串：

```kotlin
// 设置参数（覆盖同 key）
val url1 = UrlBuilder("https://example.com?q=old")
    .setQueryParam("q", "new")
    .buildString()
// "https://example.com?q=new"

// 添加参数（保留同 key，追加新值）
val url2 = UrlBuilder("https://example.com/search")
    .addQueryParam("tag", "android")
    .addQueryParam("tag", "kotlin")
    .buildString()
// "https://example.com/search?tag=android&tag=kotlin"

// 批量设置
val url3 = UrlBuilder("https://example.com/search")
    .setQueryParams(mapOf("q" to "kotlin", "page" to "2", "size" to "20"))
    .buildString()
// "https://example.com/search?q=kotlin&page=2&size=20"

// 删除参数
val url4 = UrlBuilder("https://example.com?q=kotlin&page=1&sort=desc")
    .removeQueryParam("sort")
    .buildString()
// "https://example.com?q=kotlin&page=1"

// 清空所有查询参数
val url5 = UrlBuilder("https://example.com?q=kotlin&page=1")
    .clearQueryParams()
    .buildString()
// "https://example.com"
```

构建输出方式：

```kotlin
builder.buildString()    // → String?
builder.build()          // → UrlComponents? (可进一步用 UrlParser 的方法)
builder.buildUri()       // → java.net.URI?
builder.reset()          // 重置所有字段，复用 builder 实例
```

---

## 数据模型：UrlComponents

**位置**: `com.itg.itg_string.core.UrlComponents`

不可变数据类，包含以下字段：

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `rawUrl` | `String` | 原始输入 | `"https://..."` |
| `scheme` | `String?` | 协议（小写） | `"https"` |
| `userInfo` | `String?` | 用户信息 | `"user:pass"` |
| `host` | `String?` | 主机名（小写） | `"www.example.com"` |
| `port` | `Int` | 端口号，未指定为 -1 | `8080` |
| `authority` | `String?` | 授权部分 | `"user:pass@host:8080"` |
| `path` | `String?` | 路径（原始未解码） | `"/path/to/resource"` |
| `query` | `String?` | 查询字符串 | `"query=param"` |
| `fragment` | `String?` | 片段标识符 | `"fragment"` |
| `isAbsolute` | `Boolean` | 是否为绝对 URL | `true` |
| `isOpaque` | `Boolean` | 是否为 opaque URL | `false` |

**派生属性**（计算字段，不占用额外存储）：

| 属性 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `schemeHostPath` | `String?` | scheme://host/path | `"https://www.example.com/path"` |
| `hostPort` | `String?` | host:port（无端口省略） | `"www.example.com:8080"` |
| `isHttps` | `Boolean` | scheme == "https" | `true` |
| `isDefaultPort` | `Boolean` | 使用标准端口 | `false` |
| `toString()` | `String` | 规范化重建的 URL | 等价于原始输入 |

---

## 实战场景

### 场景 A：解析 URL 提取关键信息

```kotlin
fun extractPageInfo(url: String): PageInfo? {
    val c = UrlParser.parse(url) ?: return null
    return PageInfo(
        scheme = c.scheme ?: "",
        host = c.host ?: "",
        path = c.path ?: "/",
        queryString = c.query,
        isSecure = c.isHttps,
        usesDefaultPort = c.isDefaultPort
    )
}

val info = extractPageInfo("https://shop.example.com/product?id=12345")
// PageInfo(scheme="https", host="shop.example.com", path="/product",
//          queryString="id=12345", isSecure=true, usesDefaultPort=true)
```

### 场景 B：深度链接路由分发

```kotlin
object DeepLinkRouter {

    fun route(url: String) {
        val c = UrlParser.parse(url) ?: run {
            showError("Invalid deep link: $url")
            return
        }

        when {
            c.host == "product" && c.path == "/detail" -> {
                val productId = QueryParams.getParam(url, "id")
                openProductDetail(productId)
            }
            c.host == "order" && c.path == "/list" -> {
                val status = QueryParams.getParam(url, "status") ?: "all"
                openOrderList(status)
            }
            c.path?.startsWith("/promo/") == true -> {
                val promoCode = c.path!!.removePrefix("/promo/")
                openPromotion(promoCode)
            }
            else -> openHomePage()
        }
    }
}
```

### 场景 C：API 基础地址动态切换

```kotlin
fun switchApiBase(originalUrl: String, newHost: String, newPort: Int): String? {
    return UrlBuilder(originalUrl)
        .host(newHost)
        .port(newPort)
        .buildString()
}

val devUrl = switchApiBase(
    "https://api.prod.com:443/v2/users?active=true",
    "api.dev.com", 8080
)
// "https://api.dev.com:8080/v2/users?active=true"
```

### 场景 D：URL 参数注入与追踪

```kotlin
fun addTrackingParams(url: String, channel: String, campaign: String): String? {
    return UrlBuilder(url)
        .addQueryParam("utm_source", channel)
        .addQueryParam("utm_campaign", campaign)
        .addQueryParam("utm_timestamp", System.currentTimeMillis().toString())
        .buildString()
}

val trackedUrl = addTrackingParams(
    "https://store.example.com/item?id=42",
    channel = "push",
    campaign = "summer_sale"
)
// "https://store.example.com/item?id=42&utm_source=push&utm_campaign=summer_sale&utm_timestamp=..."
```

### 场景 E：从 URL 提取查询参数

```kotlin
fun buildSearchRequest(url: String): SearchRequest {
    return SearchRequest(
        query = QueryParams.getParam(url, "q") ?: "",
        page = QueryParams.getParam(url, "page")?.toIntOrNull() ?: 1,
        sortBy = QueryParams.getParam(url, "sort") ?: "relevance",
        tags = QueryParams.getParams(url, "tag"),
        filters = QueryParams.parse(UrlParser.getQuery(url) ?: "")
            .filterKeys { it.startsWith("filter_") }
    )
}
```

---

## API 完整参考

### UrlParser

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `parse(url: String?)` | `UrlComponents?` | 全量解析，失败返回 null |
| `parseOrThrow(url: String?)` | `UrlComponents` | 全量解析，失败抛异常 |
| `parseAsync(url, onResult)` | `Future<*>` | 异步全量解析 |
| `getScheme(url: String?)` | `String?` | 协议（小写），如 "https" |
| `getSchemeAsync(url, onResult)` | `Future<*>` | 异步获取 scheme |
| `getHost(url: String?)` | `String?` | 主机名（小写） |
| `getHostAsync(url, onResult)` | `Future<*>` | 异步获取 host |
| `getPort(url: String?)` | `Int` | 端口号，未指定返回 -1 |
| `getPortAsync(url, onResult)` | `Future<*>` | 异步获取 port |
| `getPath(url: String?)` | `String?` | 路径（原始未解码） |
| `getPathAsync(url, onResult)` | `Future<*>` | 异步获取 path |
| `getQuery(url: String?)` | `String?` | 查询字符串 |
| `getQueryAsync(url, onResult)` | `Future<*>` | 异步获取 query |
| `getFragment(url: String?)` | `String?` | 片段标识符 |
| `getFragmentAsync(url, onResult)` | `Future<*>` | 异步获取 fragment |
| `getUserInfo(url: String?)` | `String?` | 用户信息 "user:pass" |
| `getUserInfoAsync(url, onResult)` | `Future<*>` | 异步获取 userInfo |
| `getAuthority(url: String?)` | `String?` | 授权部分 "user@host:port" |
| `getAuthorityAsync(url, onResult)` | `Future<*>` | 异步获取 authority |
| `isValidUrl(url: String?)` | `Boolean` | 是否为合法 URL |
| `isHttps(url: String?)` | `Boolean` | 是否为 HTTPS |
| `getBase(url: String?)` | `String?` | "scheme://host" |

### QueryParams

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `parse(queryString: String?)` | `Map<String, List<String>>` | 解析查询字符串为有序映射 |
| `parseAsync(queryString, onResult)` | `Future<*>` | 异步解析 |
| `getFirst(queryString, key)` | `String?` | 获取 key 的第一个值 |
| `getFirstAsync(queryString, key, onResult)` | `Future<*>` | 异步获取 |
| `getAll(queryString, key)` | `List<String>` | 获取 key 的所有值 |
| `getParam(url, key)` | `String?` | 从完整 URL 提取单值参数 |
| `getParamAsync(url, key, onResult)` | `Future<*>` | 异步提取 |
| `getParams(url, key)` | `List<String>` | 从完整 URL 提取多值参数 |
| `build(params: Map<String, String>)` | `String` | 构建查询字符串（单值） |
| `buildMulti(params: Map<String, List<String>>)` | `String` | 构建查询字符串（多值） |
| `containsKey(queryString, key)` | `Boolean` | 是否包含指定 key |
| `count(queryString: String?)` | `Int` | 参数个数（去重 key） |
| `encode(value: String)` | `String` | URL 编码（UTF-8） |
| `decode(value: String)` | `String` | URL 解码（UTF-8） |

### UrlBuilder

| 方法签名 | 返回 | 说明 |
|----------|------|------|
| `UrlBuilder(baseUrl: String?)` | — | 从已有 URL 构造 |
| `UrlBuilder(components: UrlComponents?)` | — | 从 UrlComponents 构造 |
| `UrlBuilder()` | — | 从空白构造 |
| `scheme(scheme: String?)` | `UrlBuilder` | 设置协议 |
| `host(host: String?)` | `UrlBuilder` | 设置主机名 |
| `port(port: Int)` | `UrlBuilder` | 设置端口号（-1 移除） |
| `path(path: String?)` | `UrlBuilder` | 设置路径 |
| `appendPath(segment: String)` | `UrlBuilder` | 追加路径段（自动处理 `/`） |
| `userInfo(userInfo: String?)` | `UrlBuilder` | 设置用户信息 |
| `fragment(fragment: String?)` | `UrlBuilder` | 设置片段标识符 |
| `query(queryString: String?)` | `UrlBuilder` | 设置原始查询字符串 |
| `setQueryParam(key, value)` | `UrlBuilder` | 设置单个参数（覆盖同 key） |
| `addQueryParam(key, value)` | `UrlBuilder` | 添加参数（追加同 key） |
| `removeQueryParam(key)` | `UrlBuilder` | 移除指定 key |
| `clearQueryParams()` | `UrlBuilder` | 清空所有查询参数 |
| `setQueryParams(params)` | `UrlBuilder` | 批量设置参数 |
| `getQueryParams()` | `Map<String, List<String>>` | 获取当前所有参数 |
| `buildString()` | `String?` | 构建 URL 字符串 |
| `build()` | `UrlComponents?` | 构建为 UrlComponents |
| `buildUri()` | `URI?` | 构建为 java.net.URI |
| `reset()` | `UrlBuilder` | 重置所有字段到空白状态 |

---

## 线程模型

| 操作类型 | 线程 | 说明 |
|----------|------|------|
| 同步方法 | 调用线程 | 纯 CPU 操作（字符串解析），可安全在主线程调用 |
| 异步方法 | `TaskExecutor.io` | I/O 线程池执行，回调在 I/O 线程 |
| 切回 UI | `TaskExecutor.main` | 在异步回调中使用 |

```kotlin
UrlParser.parseAsync(url) { components ->
    // 这里在 I/O 线程
    if (components != null) {
        TaskExecutor.main {
            // 这里在 UI 线程
            textView.text = components.host
        }
    }
}
```

> 由于 URL 解析本身是纯 CPU 操作，大多数场景直接调用同步方法即可，无需异步。

---

## 许可证

```
MIT License — Copyright (c) 2026 ITG Team
```
