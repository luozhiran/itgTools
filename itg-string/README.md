# ITG String — Android URL 解析工具库

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat/android-7.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![JDK](https://img.shields.io/badge/Engine-java.net.URI-orange.svg)](https://docs.oracle.com/javase/8/docs/api/java/net/URI.html)

ITG String 是 ItgTools 项目中的字符串处理模块，提供 **URL 解析**、**查询参数处理**、**URL → Android Bundle/Intent 桥接** 三大能力。基于 `java.net.URI` 严格遵循 RFC 3986，内置非 ASCII 字符自动编码、多字符集解码、双重编码检测。所有操作同步/异步双模式，异步基于 [itg-thread-pools](../itg-thread-pools/)。

---

## 目录

- [快速开始](#快速开始)
- [模块结构](#模块结构)
- [1. UrlParser — URL 解析](#1-urlparser--url-解析)
  - [全量解析](#全量解析)
  - [单独获取](#单独获取)
  - [便捷方法](#便捷方法)
  - [非 ASCII URL 自动处理](#非-ascii-url-自动处理)
  - [异步接口](#异步接口)
- [2. UrlComponents — 解析结果容器](#2-urlcomponents--解析结果容器)
- [3. QueryParams — 查询参数解析/构建](#3-queryparams--查询参数解析构建)
  - [解析查询字符串](#解析查询字符串)
  - [指定字符集解码](#指定字符集解码)
  - [从完整 URL 提取参数](#从完整-url-提取参数)
  - [构建查询字符串](#构建查询字符串)
  - [编码/解码](#编码解码)
- [4. UrlIntentBuilder — URL → Bundle / Intent](#4-urlintentbuilder--url--bundle--intent)
  - [创建 Bundle](#创建-bundle)
  - [指定字符集创建 Bundle](#指定字符集创建-bundle)
  - [写入 Intent extras](#写入-intent-extras)
  - [key 前缀保护](#key-前缀保护)
  - [先取 Bundle 加工再注入](#先取-bundle-加工再注入)
  - [双重编码检测](#双重编码检测)
- [5. 实战场景](#5-实战场景)
- [6. API 完整参考](#6-api-完整参考)
- [7. 线程模型](#7-线程模型)
- [8. 字符编码说明](#8-字符编码说明)
- [9. 错误处理策略](#9-错误处理策略)

---

## 快速开始

### 依赖

```kotlin
implementation(project(":itg-string"))
```

### 30 秒上手

```kotlin
// URL 全量解析
val c = UrlParser.parse("https://user:pass@www.example.com:8080/path?q=1#sec")
c?.scheme     // "https"
c?.host       // "www.example.com"
c?.port       // 8080

// 提取查询参数
QueryParams.getParam("https://example.com/search?q=kotlin&page=1", "q")  // "kotlin"

// URL 参数 → Intent extras
UrlIntentBuilder.putQueryExtras("https://api.com?active=true", intent)
```

---

## 模块结构

```
itg-string/src/main/java/com/itg/itg_string/
├── core/
│   ├── UrlComponents.kt      ← 不可变数据类：承载全量解析结果
│   └── UrlParser.kt          ← object 工具类：核心解析入口（含非 ASCII 自动编码）
├── query/
│   └── QueryParams.kt        ← object 工具类：查询参数解析/构建/编解码（含多字符集）
└── intent/
    └── UrlIntentBuilder.kt   ← object 工具类：URL → Bundle / Intent extras 桥接
```

**3 个工具 object，1 个数据类，50+ 公开方法，全部 `@JvmStatic` 支持 Java 调用。**

---

## 1. UrlParser — URL 解析

**包**：`com.itg.itg_string.core`

基于 `java.net.URI`（RFC 3986），解析整条 URL 为结构化组件。**内置非 ASCII 字符自动 UTF-8 percent-encoding**，支持中文、emoji 等 Unicode 字符的 URL，不会因为含中文而返回 null。

### 全量解析

```kotlin
val url = "https://user:pass@api.example.com:8080/v2/users?active=true&page=1#top"

val c = UrlParser.parse(url) ?: return  // 解析失败返回 null

c.scheme        // "https"
c.userInfo      // "user:pass"
c.host          // "api.example.com"
c.port          // 8080
c.authority     // "user:pass@api.example.com:8080"
c.path          // "/v2/users"
c.query         // "active=true&page=1"      (原始未解码)
c.fragment      // "top"
c.rawUrl        // 原始输入字符串
c.isAbsolute    // true
c.isOpaque      // false
```

> 需要抛异常的场景用 `UrlParser.parseOrThrow(url)`，解析失败抛 `IllegalArgumentException`。

### 单独获取

```kotlin
UrlParser.getScheme("https://example.com")        // "https"
UrlParser.getHost("https://www.example.com/path")  // "www.example.com"
UrlParser.getPort("https://example.com:8080/api")  // 8080
UrlParser.getPort("https://example.com/api")       // -1 (未显式指定)
UrlParser.getPath("https://example.com/search?q=1") // "/search"
UrlParser.getQuery("https://example.com?q=kotlin&p=1") // "q=kotlin&p=1"
UrlParser.getFragment("https://example.com/doc#intro")   // "intro"
UrlParser.getUserInfo("https://admin:pwd@example.com")   // "admin:pwd"
UrlParser.getAuthority("https://u:p@example.com:8443/p") // "u:p@example.com:8443"
```

### 便捷方法

```kotlin
UrlParser.isValidUrl("https://example.com")    // true
UrlParser.isValidUrl("not a url")              // false

UrlParser.isHttps("https://example.com")       // true
UrlParser.isHttps("http://example.com")        // false

UrlParser.getBase("https://example.com:8080/path?q=1")  // "https://example.com"
```

### 非 ASCII URL 自动处理

URL 中的中文、emoji、拉丁扩展等非 ASCII 字符会被自动 UTF-8 percent-encode 后再解析，不会因含 Unicode 而返回 null：

```kotlin
// 中文
UrlParser.parse("https://搜索.com?q=中文&type=1")           // ✅ 正常解析

// Emoji
UrlParser.parse("https://example.com?emoji=😀")             // ✅ 正常解析

// 拉丁扩展字符
UrlParser.parse("https://example.com?name=José&city=München") // ✅ 正常解析

// 已正确编码的 URL 不受影响（不会二次编码）
UrlParser.parse("https://example.com?q=%E4%B8%AD%E6%96%87")  // ✅ 原样保留
```

> 内部实现使用 `String.codePointAt()` 迭代，正确处理 BMP 外的 supplementary characters（如 emoji），避免拆分 surrogate pair。

### 异步接口

所有同步方法均有 `*Async` 版本：

```kotlin
UrlParser.parseAsync(url) { components -> /* ... */ }
UrlParser.getSchemeAsync(url) { scheme -> /* ... */ }
UrlParser.getHostAsync(url) { host -> /* ... */ }
UrlParser.getPortAsync(url) { port -> /* ... */ }
UrlParser.getPathAsync(url) { path -> /* ... */ }
UrlParser.getQueryAsync(url) { query -> /* ... */ }
UrlParser.getFragmentAsync(url) { fragment -> /* ... */ }
UrlParser.getUserInfoAsync(url) { userInfo -> /* ... */ }
UrlParser.getAuthorityAsync(url) { authority -> /* ... */ }
```

返回 `Future<*>` 可用于取消。

---

## 2. UrlComponents — 解析结果容器

**包**：`com.itg.itg_string.core`

不可变 `data class`，一次解析所有字段同时可用。

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `rawUrl` | `String` | 原始输入 | `"https://..."` |
| `scheme` | `String?` | 协议（小写） | `"https"` |
| `userInfo` | `String?` | 用户信息 | `"user:pass"` |
| `host` | `String?` | 主机名（小写） | `"www.example.com"` |
| `port` | `Int` | 端口号，未指定为 -1 | `8080` |
| `authority` | `String?` | 授权部分 | `"user:pass@host:8080"` |
| `path` | `String?` | 路径（原始未解码） | `"/path/to/resource"` |
| `query` | `String?` | 查询字符串（原始未解码） | `"query=param"` |
| `fragment` | `String?` | 片段标识符 | `"fragment"` |
| `isAbsolute` | `Boolean` | 是否为绝对 URL | `true` |
| `isOpaque` | `Boolean` | 是否为 opaque URL | `false` |

**派生属性**（不占用额外存储）：

| 属性 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `schemeHostPath` | `String?` | `scheme://host/path` | `"https://example.com/path"` |
| `hostPort` | `String?` | `host:port`（默认端口省略） | `"example.com:8080"` |
| `isHttps` | `Boolean` | `scheme == "https"` | `true` |
| `isDefaultPort` | `Boolean` | 是否使用标准端口 | `false` |
| `toString()` | `String` | 规范化重建的 URL | 等价于原始输入 |

```kotlin
val c = UrlParser.parse("https://example.com:8443/path?q=1#sec")!!

c.schemeHostPath  // "https://example.com/path"   (不含端口、query、fragment)
c.hostPort        // "example.com:8443"
c.isHttps         // true
c.isDefaultPort   // false  (8443 ≠ 443)
```

```kotlin
// 标准端口判断
UrlParser.parse("https://example.com")!!.isDefaultPort      // true  (443 是默认)
UrlParser.parse("http://example.com")!!.isDefaultPort       // true  (80 是默认)
UrlParser.parse("http://example.com:8080")!!.isDefaultPort  // false
UrlParser.parse("https://example.com:443")!!.isDefaultPort  // true  (显式 443)
```

---

## 3. QueryParams — 查询参数解析/构建

**包**：`com.itg.itg_string.query`

独立处理 `?key1=value1&key2=value2` 子语言。支持多值参数、多字符集解码、编码/解码、查询字符串构建。

### 解析查询字符串

```kotlin
val params = QueryParams.parse("q=kotlin&tag=android&tag=jvm&flag")
// 返回 LinkedHashMap（保留插入顺序）:
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

// 参数个数（去重 key 数）
QueryParams.count("a=1&b=2&c=3")                   // 3
QueryParams.count("tag=a&tag=b&tag=c")             // 1 (同一个 key)
```

**输入格式兼容**：前导 `?` 自动去除，`"?q=1"` 与 `"q=1"` 等效。

### 指定字符集解码

适用于非 UTF-8 编码的 URL（GBK/GB2312、Shift_JIS、EUC-KR 等）：

```kotlin
// GBK 编码的中文
QueryParams.parse("q=%D6%D0%CE%C4", Charset.forName("GBK"))["q"]     // ["中文"]

// Shift_JIS 编码的日文
QueryParams.parse("q=%93%FA%96%7B%8C%EA", Charset.forName("Shift_JIS"))["q"]  // ["日本語"]
```

### 从完整 URL 提取参数

无需手动分离 query 部分：

```kotlin
val url = "https://example.com/search?q=kotlin&page=1&tag=a&tag=b"

// 单值
QueryParams.getParam(url, "q")        // "kotlin"
QueryParams.getParam(url, "missing")  // null

// 多值
QueryParams.getParams(url, "tag")     // ["a", "b"]
```

### 构建查询字符串

```kotlin
// 单值构建（自动编码特殊字符）
QueryParams.build(mapOf("q" to "kotlin", "page" to "1", "sort" to "desc"))
// "q=kotlin&page=1&sort=desc"

// 多值构建
QueryParams.buildMulti(linkedMapOf(
    "tag" to listOf("android", "kotlin"),
    "q" to listOf("search term")
))
// "tag=android&tag=kotlin&q=search+term"
```

### 编码/解码

```kotlin
// UTF-8 编码
QueryParams.encode("hello world")     // "hello+world"
QueryParams.encode("a&b=c")           // "a%26b%3Dc"

// UTF-8 解码
QueryParams.decode("hello+world")     // "hello world"
QueryParams.decode("a%26b%3Dc")       // "a&b=c"

// 指定字符集解码
QueryParams.decode("%D6%D0%CE%C4", Charset.forName("GBK"))  // "中文"

// 往返一致性
QueryParams.decode(QueryParams.encode("name=John"))  // "name=John"
```

> 编解码失败时静默返回原始字符串，不抛异常。

### 异步方法

```kotlin
QueryParams.parseAsync("q=kotlin&page=1") { params -> /* ... */ }
QueryParams.getFirstAsync("q=kotlin&page=2", "q") { value -> /* ... */ }
QueryParams.getParamAsync("https://example.com?q=kotlin", "q") { value -> /* ... */ }
```

---

## 4. UrlIntentBuilder — URL → Bundle / Intent

**包**：`com.itg.itg_string.intent`

将 URL 查询参数转换为 Android 组件可直接使用的 `Bundle` 和 `Intent` extras。内置双重编码检测和 key 前缀保护。

### 创建 Bundle

```kotlin
val bundle = UrlIntentBuilder.toBundle(
    "https://api.prod.com:443/v2/users?active=true&role=admin&tag=premium&tag=verified"
)

bundle?.getString("active")                     // "true"
bundle?.getString("role")                       // "admin"
bundle?.getStringArrayList("tag")               // ["premium", "verified"]
```

**参数写入规则**：

| URL 参数形式 | Bundle 方法 | 示例 |
|---|---|---|
| `?key=value` | `putString(key, value)` | `"active" → "true"` |
| `?tag=a&tag=b` | `putStringArrayList(key, list)` | `"tag" → ["a","b"]` |
| `?flag` （无值） | `putString(key, "")` | `"flag" → ""` |
| `?key=` （空值） | `putString(key, "")` | `"key" → ""` |

> 所有值均以 `String` 存储。`"5"` 不会自动转为 `Int`，`"007"` 不会丢失前导零。类型推断留给业务层。

**返回值语义**：

| 输入 | 返回 | 含义 |
|---|---|---|
| URL 含查询参数 | 包含所有参数的 `Bundle` | 正常 |
| URL 无 query 部分 | 空 `Bundle`（非 null） | URL 合法但无参数 |
| URL 为 `null` / `""` / 非法 | `null` | 无法解析 |

> 区分"合法但无参数"（空 Bundle）与"URL 非法"（null），便于调用方分别处理。

### 指定字符集创建 Bundle

```kotlin
// GBK 编码的 URL → 正确解码为中文
val bundle = UrlIntentBuilder.toBundle(
    "https://example.com?q=%D6%D0%CE%C4&type=%B2%E2%CA%D4",
    Charset.forName("GBK")
)
bundle?.getString("q")    // "中文"
bundle?.getString("type")  // "测试"
```

### 写入 Intent extras

```kotlin
val intent = UrlIntentBuilder.putQueryExtras(
    "https://api.com?active=true&debug=1",
    Intent(context, DetailActivity::class.java)
)
context.startActivity(intent)

// DetailActivity 中:
// intent.getStringExtra("active")  → "true"
// intent.getStringExtra("debug")   → "1"
```

**链式调用**：

```kotlin
context.startActivity(
    UrlIntentBuilder.putQueryExtras(
        "https://api.com?from=deeplink",
        Intent(Intent.ACTION_VIEW)
    )
)
```

### key 前缀保护

URL 中的参数 key 可能与 Intent 已有 extras 同名。使用 `keyPrefix` 避免覆盖：

```kotlin
val intent = Intent(context, DetailActivity::class.java)
intent.putExtra("id", 12345)           // 业务已有的 extra
intent.putExtra("active", "original")  // 业务已有的 extra

UrlIntentBuilder.putQueryExtras(
    "https://api.com?id=deep_link&active=from_url",
    intent,
    keyPrefix = "url_"
)

// 最终 extras:
// "id"          → 12345         (原有值保留)
// "active"      → "original"    (原有值保留)
// "url_id"      → "deep_link"   (URL 参数，已加前缀)
// "url_active"  → "from_url"    (URL 参数，已加前缀)
```

不带前缀时保持向后兼容（覆盖同名 key）：

```kotlin
// 默认行为（keyPrefix = ""）
UrlIntentBuilder.putQueryExtras("https://api.com?active=new_value", intent)
// "active" → "new_value"  (同 key 被覆盖)
```

### 先取 Bundle 加工再注入

```kotlin
val bundle = UrlIntentBuilder.toBundle("https://api.com?q=kotlin&page=1")
bundle?.putString("extra_key", "extra_value")         // 追加自定义参数
bundle?.putInt("timestamp", System.currentTimeMillis())

intent.putExtras(bundle!!)
context.startActivity(intent)
```

### 双重编码检测

当解码后的值仍残留 `%XX` 模式时，自动输出 `Log.w` 警告，tag 为 `UrlIntentBuilder`：

```
W/UrlIntentBuilder: Possible double-encoding detected: key="name" value="John%20Doe"
    contains residual percent-encoded sequences (%XX).
    The upstream caller may have double-encoded this value or used a different charset.
```

无需额外配置，自动生效。不影响流程，仅辅助排查。

### 异步方法

```kotlin
UrlIntentBuilder.toBundleAsync(url) { bundle -> /* ... */ }
UrlIntentBuilder.toBundleAsync(url, Charset.forName("GBK")) { bundle -> /* ... */ }
UrlIntentBuilder.putQueryExtrasAsync(url, intent) { result -> /* ... */ }
UrlIntentBuilder.putQueryExtrasAsync(url, intent, "url_") { result -> /* ... */ }
```

---

## 5. 实战场景

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
            else -> openHomePage()
        }
    }
}
```

### 场景 C：深度链接参数注入 Activity（带前缀保护）

```kotlin
val deeplink = "https://myapp.com/product?id=12345&from=push&tag=hot&tag=new"

val intent = Intent(context, ProductActivity::class.java)
intent.putExtra("source", "notification")  // 业务自有参数
UrlIntentBuilder.putQueryExtras(deeplink, intent, keyPrefix = "url_")
context.startActivity(intent)

// ProductActivity 中:
// intent.getStringExtra("source")       → "notification"
// intent.getStringExtra("url_id")       → "12345"
// intent.getStringExtra("url_from")     → "push"
// intent.getStringArrayListExtra("url_tag") → ["hot", "new"]
```

### 场景 D：查询参数过滤与转发

```kotlin
val incomingUrl = "https://proxy.com/api?user_id=123&token=abc&internal_debug=1"

// 解析所有参数
val allParams = QueryParams.parse(UrlParser.getQuery(incomingUrl)!!)

// 过滤掉内部参数，重新构建
val safeParams = allParams.filterKeys { !it.startsWith("internal_") }
    .mapValues { it.value.first() }
val cleanQuery = QueryParams.build(safeParams)  // "user_id=123&token=abc"
```

### 场景 E：URL 参数校验

```kotlin
fun validateDeepLink(url: String): Boolean {
    val c = UrlParser.parse(url) ?: return false
    if (!c.isHttps) return false
    if (c.host !in listOf("myapp.com", "www.myapp.com")) return false
    if (!QueryParams.containsKey(c.query, "track_id")) return false
    return true
}
```

### 场景 F：非 UTF-8 编码 URL 处理

```kotlin
// 一些老旧中文站点可能使用 GBK 编码
val bundle = UrlIntentBuilder.toBundle(
    "https://legacy-search.com?q=%D6%D0%CE%C4",
    Charset.forName("GBK")
)
bundle?.getString("q")  // "中文" (而非 UTF-8 解码产生的乱码)
```

### 场景 G：构建带参数的 URL

```kotlin
val params = mapOf("q" to "kotlin coroutines", "page" to "1", "size" to "20")
val url = "https://api.example.com/search?${QueryParams.build(params)}"
// "https://api.example.com/search?q=kotlin+coroutines&page=1&size=20"
```

### 场景 H：中文 URL 直接解析

```kotlin
// 修复前会返回 null，修复后正常解析
val c = UrlParser.parse("https://搜索.com?q=中文&emoji=😀")
val bundle = UrlIntentBuilder.toBundle("https://api.com?name=José&city=München")
// 全部正常解码
```

---

## 6. API 完整参考

### UrlParser

| 方法 | 返回 | 说明 |
|------|------|------|
| `parse(url)` | `UrlComponents?` | 全量解析，失败返回 null |
| `parseOrThrow(url)` | `UrlComponents` | 全量解析，失败抛异常 |
| `parseAsync(url, onResult)` | `Future<*>` | 异步全量解析 |
| `getScheme(url)` | `String?` | 协议（小写） |
| `getHost(url)` | `String?` | 主机名（小写） |
| `getPort(url)` | `Int` | 端口号，未指定返回 -1 |
| `getPath(url)` | `String?` | 路径（原始未解码） |
| `getQuery(url)` | `String?` | 查询字符串 |
| `getFragment(url)` | `String?` | 片段标识符 |
| `getUserInfo(url)` | `String?` | 用户信息 |
| `getAuthority(url)` | `String?` | 授权部分 |
| `isValidUrl(url)` | `Boolean` | 是否为合法 URL |
| `isHttps(url)` | `Boolean` | 是否为 HTTPS |
| `getBase(url)` | `String?` | `"scheme://host"` |
| (各 getter 均有对应 `xxxAsync` 版本) | | |

### UrlComponents

| 属性 | 类型 | 说明 |
|------|------|------|
| `rawUrl` | `String` | 原始输入 |
| `scheme` | `String?` | 协议（小写） |
| `userInfo` | `String?` | 用户信息 |
| `host` | `String?` | 主机名（小写） |
| `port` | `Int` | 端口（-1 = 未指定） |
| `authority` | `String?` | 授权部分 |
| `path` | `String?` | 路径 |
| `query` | `String?` | 查询字符串 |
| `fragment` | `String?` | 片段 |
| `isAbsolute` | `Boolean` | 是否绝对 URL |
| `isOpaque` | `Boolean` | 是否 opaque |
| `schemeHostPath` | `String?` | 派生：`scheme://host/path` |
| `hostPort` | `String?` | 派生：`host:port` |
| `isHttps` | `Boolean` | 派生：scheme == "https" |
| `isDefaultPort` | `Boolean` | 派生：是否标准端口 |

### QueryParams

| 方法 | 返回 | 说明 |
|------|------|------|
| `parse(queryString)` | `Map<String, List<String>>` | 解析查询字符串（UTF-8） |
| `parse(queryString, charset)` | `Map<String, List<String>>` | 指定字符集解析 |
| `getFirst(queryString, key)` | `String?` | 获取第一个值 |
| `getAll(queryString, key)` | `List<String>` | 获取所有值 |
| `getParam(url, key)` | `String?` | 从完整 URL 获取第一个值 |
| `getParams(url, key)` | `List<String>` | 从完整 URL 获取所有值 |
| `build(params)` | `String` | 构建查询字符串（单值） |
| `buildMulti(params)` | `String` | 构建查询字符串（多值） |
| `containsKey(queryString, key)` | `Boolean` | 是否包含指定 key |
| `count(queryString)` | `Int` | 参数个数（去重） |
| `encode(value)` | `String` | URL 编码（UTF-8） |
| `decode(value)` | `String` | URL 解码（UTF-8） |
| `decode(value, charset)` | `String` | 指定字符集解码 |
| (各方法均有对应 `xxxAsync` 版本) | | |

### UrlIntentBuilder

| 方法 | 返回 | 说明 |
|------|------|------|
| `toBundle(url)` | `Bundle?` | URL 参数 → Bundle（UTF-8） |
| `toBundle(url, charset)` | `Bundle?` | URL 参数 → Bundle（指定字符集） |
| `toBundleAsync(url, onResult)` | `Future<*>` | 异步创建 Bundle |
| `toBundleAsync(url, charset, onResult)` | `Future<*>` | 异步创建 Bundle（指定字符集） |
| `putQueryExtras(url, intent)` | `Intent` | 写入 Intent extras（无前缀） |
| `putQueryExtras(url, intent, keyPrefix)` | `Intent` | 写入 Intent extras（带前缀） |
| `putQueryExtrasAsync(url, intent, onResult)` | `Future<*>` | 异步写入 extras |
| `putQueryExtrasAsync(url, intent, prefix, onResult)` | `Future<*>` | 异步写入 extras（带前缀） |

---

## 7. 线程模型

| 操作类型 | 线程 | 说明 |
|----------|------|------|
| 同步方法 | 调用线程 | 纯 CPU 操作（字符串解析），可安全在主线程调用 |
| 异步方法 | `TaskExecutor.io` | IO 线程池执行，回调在 IO 线程 |
| 切回主线程 | `runOnUiThread` / `Handler(Looper.getMainLooper())` | 在异步回调中使用 |

```kotlin
UrlParser.parseAsync(url) { components ->
    runOnUiThread {
        textView.text = components?.host
    }
}
```

> 由于 URL 解析本身是纯 CPU 操作，大多数场景直接调用同步方法即可，无需异步。

---

## 8. 字符编码说明

| 环节 | 输入 | 处理 |
|------|------|------|
| `UrlParser.parse()` | 含非 ASCII 的 URL | 自动 UTF-8 percent-encode 后解析 |
| `UrlParser.parse()` | 已正确编码的 URL | 直接解析，不二次编码 |
| `QueryParams.decode()` | percent-encoded 字符串 | UTF-8 解码（默认），可指定 charset |
| `UrlIntentBuilder.toBundle()` | 含非 ASCII 的 URL | UrlParser sanitize → QueryParams 解码 → Bundle |
| 双重编码 (`%2520`) | 解码一次 → `%20` | 输出 `Log.w` 警告 |

> 已编码的 `%XX` 序列不会被二次编码——`%` 字符本身是 ASCII（U+0025），sanitize 只处理 U+0080 以上字符。BMP 外字符（emoji）通过 `String.codePointAt()` 按 code point 粒度编码，不会错误拆分 surrogate pair。

---

## 9. 错误处理策略

| 场景 | 行为 |
|------|------|
| 非法 URL 格式 | `UrlParser.parse()` 返回 `null`，不抛异常 |
| `null` / 空字符串输入 | 返回 `null` 或空集合，不抛异常 |
| URL 解码失败 | 静默返回原始编码字符串 |
| 无查询参数 | `toBundle()` 返回空 `Bundle`（非 null） |
| 双重编码 | `Log.w` 警告，继续使用解码结果 |
```
