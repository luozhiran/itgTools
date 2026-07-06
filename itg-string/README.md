# ITG String — Android URL 解析工具库

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat/android-7.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![JDK](https://img.shields.io/badge/Engine-java.net.URI-orange.svg)](https://docs.oracle.com/javase/8/docs/api/java/net/URI.html)

ITG String 是 ItgTools 项目中的字符串处理模块，提供 **URL 解析**、**查询参数处理**、**URL/Map → Bundle/Intent 桥接**、**Bundle 合并**、**流式 Intent 构造器** 五大能力。基于 `java.net.URI` 严格遵循 RFC 3986，内置非 ASCII 字符自动编码、多字符集解码、双重编码检测。支持 URL 和 Map 双入口，所有操作同步/异步双模式，异步基于 [itg-thread-pools](../itg-thread-pools/)。

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
- [4. UrlIntentBuilder — URL / Map → Bundle / Intent / Map](#4-urlintentbuilder--url--map--bundle--intent--map)
  - [URL 入口：创建 Bundle](#url-入口创建-bundle)
  - [URL 入口：提取为 Map](#url-入口提取为-map)
  - [URL 入口：写入 Intent extras](#url-入口写入-intent-extras)
  - [Map 入口：单值 Map → Bundle](#map-入口单值-map--bundle)
  - [Map 入口：多值 Map → Bundle](#map-入口多值-map--bundle)
  - [Map 入口：异构 Map → Bundle（类型感知）](#map-入口异构-map--bundle类型感知)
  - [Map 入口：写入 Intent extras](#map-入口写入-intent-extras)
  - [Bundle 合并](#bundle-合并)
  - [key 前缀保护](#key-前缀保护)
  - [先取 Bundle 加工再注入](#先取-bundle-加工再注入)
  - [双重编码检测](#双重编码检测)
- [5. IntentBuilder — 流式 Intent 构造器](#5-intentbuilder--流式-intent-构造器)
  - [快速上手](#快速上手-1)
  - [fromUrl — URL 接入](#fromurl--url-接入)
  - [fromMap — Map 接入](#frommap--map-接入)
  - [fromBundle — Bundle 接入](#frombundle--bundle-接入)
  - [put — 单键值对](#put--单键值对)
  - [目标组件、action、data/type、flags、category](#目标组件actiondatatypeflagscategory)
  - [前缀隔离与覆盖规则](#前缀隔离与覆盖规则)
  - [错误处理与安全机制](#错误处理与安全机制)
  - [build / into / buildBundle — 三种输出](#build--into--buildbundle--三种输出)
  - [reset / sourceCount — 生命周期](#reset--sourcecount--生命周期)
- [6. 实战场景](#6-实战场景)
- [7. API 完整参考](#7-api-完整参考)
- [8. 线程模型](#8-线程模型)
- [9. 字符编码说明](#9-字符编码说明)
- [10. 错误处理策略](#10-错误处理策略)

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

// Map → Bundle（异构类型自动感知）
val b = UrlIntentBuilder.toBundle(mapOf<String, Any?>("id" to 12345, "active" to true))
b.getInt("id")       // 12345
b.getBoolean("active")  // true

// Map → Intent extras（带前缀保护）
UrlIntentBuilder.putExtras(mapOf("from" to "config"), intent, keyPrefix = "cfg_")
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
├── intent/
│   ├── UrlIntentBuilder.kt   ← object 工具类：URL / Map → Bundle / Intent 桥接 + Bundle 合并
│   └── IntentBuilder.kt      ← class 流式构造器：多源链式合并 → Intent（含错误捕获）
└── builder/
    └── UrlBuilder.kt         ← class 流式构造器：URL 创建与修改
```

**4 个工具类，1 个数据类，90+ 公开方法，全部 `@JvmStatic` 支持 Java 调用。**

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

## 4. UrlIntentBuilder — URL / Map → Bundle / Intent / Map

**包**：`com.itg.itg_string.intent`

提供 **两条数据入口**，汇聚到同一套 Bundle/Intent 构建引擎：

| 入口 | 输入 | 适用场景 |
|---|---|---|
| **URL 入口** | URL 字符串 → 自动解析 query 参数 | 深度链接、服务端下发的 URL |
| **Map 入口** | `Map<String, String>` / `Map<String, List<String>>` / `Map<String, *>` | 本地配置、JSON 解析结果、程序化参数 |

内置双重编码检测（URL / `Map<String, List<String>>` 入口）和 key 前缀保护（所有入口）。

### URL 入口：创建 Bundle

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

**指定字符集解码**：

```kotlin
val bundle = UrlIntentBuilder.toBundle(
    "https://example.com?q=%D6%D0%CE%C4",
    Charset.forName("GBK")
)
bundle?.getString("q")  // "中文" (而非 UTF-8 解码产生的乱码)
```

### URL 入口：提取为 Map

直接从 URL 查询参数提取为 `Map`，免去手动调用 `UrlParser` + `QueryParams` 两步操作。

**`toMap(url)` — 单值 Map**（每个 key 取第一个值）：

```kotlin
val map = UrlIntentBuilder.toMap("https://api.com?active=true&role=admin&tag=a&tag=b")
// map["active"]  → "true"
// map["role"]    → "admin"
// map["tag"]     → "a"  (多值参数只取第一个)
```

**`toMultiMap(url)` — 多值 Map**（保留所有重复 key）：

```kotlin
val map = UrlIntentBuilder.toMultiMap("https://api.com?tag=a&tag=b&q=kotlin")
// map["tag"]  → ["a", "b"]
// map["q"]    → ["kotlin"]
```

**返回值语义**：

| 输入 | `toMap` 返回 | `toMultiMap` 返回 |
|---|---|---|
| URL 含查询参数 | `Map<String, String>` | `Map<String, List<String>>` |
| URL 无 query | 空 `Map`（非 null） | 空 `Map`（非 null） |
| URL 为 null / 非法 | `null` | `null` |

**指定字符集**：

```kotlin
val map = UrlIntentBuilder.toMap("https://s.com?q=%D6%D0%CE%C4", Charset.forName("GBK"))
map["q"]  // "中文"
```

**实战用法**：

```kotlin
// 快速取值（toMap + Kotlin 解构）
val params = UrlIntentBuilder.toMap(deeplinkUrl) ?: return
val page = params["page"]?.toIntOrNull() ?: 1
val keyword = params["q"] ?: ""

// 参数校验
if (UrlIntentBuilder.toMap(url)?.containsKey("token") == true) { /* ... */ }

// 参数过滤后重建 URL
val filtered = UrlIntentBuilder.toMultiMap(url)!!
    .filterKeys { !it.startsWith("utm_") }
```

### URL 入口：写入 Intent extras

```kotlin
val intent = UrlIntentBuilder.putQueryExtras(
    "https://api.com?active=true&debug=1",
    Intent(context, DetailActivity::class.java)
)
context.startActivity(intent)
// intent.getStringExtra("active")  → "true"
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

### Map 入口：单值 Map → Bundle

适用于 SharedPreferences、程序化配置等场景。每个 entry 写入为 `putString`。

```kotlin
val bundle = UrlIntentBuilder.toBundle(mapOf(
    "env" to "production",
    "timeout" to "30",
    "debug" to "true"
))
bundle.getString("env")      // "production"
bundle.getString("timeout")  // "30"
```

空 Map 返回空 Bundle（非 null）。

### Map 入口：多值 Map → Bundle

与 `QueryParams.parse()` 返回的 `Map<String, List<String>>` 类型一致，可直接桥接：

```kotlin
// QueryParams 输出 → toBundle 直接桥接
val parsed = QueryParams.parse("tag=a&tag=b&q=kotlin")
val bundle = UrlIntentBuilder.toBundle(parsed)

bundle.getString("q")                // "kotlin"
bundle.getStringArrayList("tag")     // ["a", "b"]
```

加工后再注入：

```kotlin
val params: Map<String, List<String>> = QueryParams.parse("tag=a&tag=b&q=kotlin")
// 追加自定义参数
val merged = params.toMutableMap()
merged["source"] = listOf("deep_link")

UrlIntentBuilder.putExtras(merged, intent, keyPrefix = "url_")
```

### Map 入口：异构 Map → Bundle（类型感知）

接受 `Map<String, *>`，按实际类型自动选择最合适的 Bundle API：

```kotlin
val bundle = UrlIntentBuilder.toBundle(mapOf<String, Any?>(
    "id" to 12345,
    "active" to true,
    "score" to 4.5,
    "ratio" to 0.75f,
    "name" to "John",
    "tags" to listOf("premium", "verified"),
    "ids" to listOf(1, 2, 3),
    "ignored" to null
))

bundle.getInt("id")               // 12345
bundle.getBoolean("active")       // true
bundle.getDouble("score")         // 4.5
bundle.getFloat("ratio")          // 0.75
bundle.getString("name")          // "John"
bundle.getStringArrayList("tags") // ["premium", "verified"]
bundle.getIntegerArrayList("ids") // [1, 2, 3]
bundle.containsKey("ignored")     // false  (null 被跳过)
```

**类型路由规则**：

| 输入 value 类型 | Bundle API | 示例 |
|---|---|---|
| `String` | `putString` | `"active" → "true"` |
| `Int` | `putInt` | `"count" → 5` |
| `Long` | `putLong` | `"ts" → 1699000000000L` |
| `Boolean` | `putBoolean` | `"debug" → true` |
| `Float` | `putFloat` | `"ratio" → 0.75f` |
| `Double` | `putDouble` | `"score" → 4.5` |
| `List<String>` | `putStringArrayList` | `"tags" → ["a","b"]` |
| `List<Int>` | `putIntegerArrayList` | `"ids" → [1,2,3]` |
| `null` | **跳过**（不写入 Bundle） | — |
| 其他类型 | `putString(key, toString())` | 退化兜底 |

> Java 调用方通过 `@JvmName` 使用 `toBundleFromStringMap` / `toBundleFromListMap` / `toBundleFromWildcardMap` 等方法名避免歧义。

### Map 入口：写入 Intent extras

三种 Map 类型均有对应的 `putExtras` 方法，命名与 `toBundle` 对应：

```kotlin
// 单值 Map → Intent
UrlIntentBuilder.putExtras(mapOf("key1" to "val1"), intent)

// 多值 Map → Intent
UrlIntentBuilder.putExtras(parsed, intent, keyPrefix = "url_")

// 异构 Map → Intent（带前缀时保留精确类型）
UrlIntentBuilder.putExtras(
    mapOf<String, Any?>("count" to 10, "enabled" to true, "label" to "hello"),
    intent,
    keyPrefix = "cfg_"
)
// intent.getIntExtra("cfg_count", -1)      → 10
// intent.getBooleanExtra("cfg_enabled")     → true
// intent.getStringExtra("cfg_label")        → "hello"
```

> 带前缀时 `putExtras(Map<String, *>, ...)` 直接按 Map 条目逐项写入 Intent，避免 Bundle 拆包时的类型丢失。

### Bundle 合并

将两个 Bundle 合并为一个，支持简单覆盖合并和前缀隔离合并。

**简单合并 — `mergeBundles(first, second)`**：同名 key 时 `second` 覆盖 `first`，返回独立新 Bundle。

```kotlin
val urlBundle = UrlIntentBuilder.toBundle("https://api.com?source=deeplink")!!
val cfgBundle = UrlIntentBuilder.toBundle(mapOf("userId" to 12345, "isVip" to true))

val merged = UrlIntentBuilder.mergeBundles(urlBundle, cfgBundle)
// merged 包含两者所有 key，同名则后者覆盖
```

**前缀合并 — `mergeBundles(first, second, keyPrefix)`**：为 `second` 的 key 加前缀，保留值类型。

```kotlin
val merged = UrlIntentBuilder.mergeBundles(urlBundle, cfgBundle, "cfg_")
merged.getString("source")       // "deeplink"
merged.getInt("cfg_userId")     // 12345     (类型保留)
merged.getBoolean("cfg_isVip")  // true      (类型保留)
```

**异步版本**：`mergeBundlesAsync(first, second, onResult)` / `mergeBundlesAsync(first, second, prefix, onResult)`。

### key 前缀保护

所有入口（URL / Map）均支持 `keyPrefix` 参数，避免覆盖 Intent 已有 extras：

```kotlin
val intent = Intent(context, DetailActivity::class.java)
intent.putExtra("id", 12345)           // 业务已有的 extra

// URL 入口
UrlIntentBuilder.putQueryExtras("https://api.com?id=deep_link", intent, keyPrefix = "url_")

// Map 入口
UrlIntentBuilder.putExtras(mapOf("id" to "from_map"), intent, keyPrefix = "map_")

// 最终 extras:
// "id"      → 12345        (原有值保留)
// "url_id"  → "deep_link"  (URL 参数)
// "map_id"  → "from_map"   (Map 参数)
```

不带前缀时保持向后兼容（覆盖同名 key）。

### 先取 Bundle 加工再注入

```kotlin
val bundle = UrlIntentBuilder.toBundle(mapOf("env" to "staging", "region" to "us-east-1"))
bundle.putString("extra", "added")                     // 追加自定义参数
bundle.putInt("timestamp", System.currentTimeMillis())

intent.putExtras(bundle)
context.startActivity(intent)
```

### 双重编码检测

URL 入口和 `Map<String, List<String>>` 入口解码后，若值仍残留 `%XX` 模式，自动输出 `Log.w` 警告，tag 为 `UrlIntentBuilder`：

```
W/UrlIntentBuilder: Possible double-encoding detected: key="name" value="John%20Doe"
    contains residual percent-encoded sequences (%XX).
    The upstream caller may have double-encoded this value or used a different charset.
```

无需额外配置，自动生效，不影响流程。

### 异步方法

所有同步方法均有 `*Async` 版本：

```kotlin
// URL 入口异步
UrlIntentBuilder.toBundleAsync(url) { bundle -> /* ... */ }
UrlIntentBuilder.putQueryExtrasAsync(url, intent) { result -> /* ... */ }

// Map 入口异步
UrlIntentBuilder.toBundleAsync(mapOf("k" to "v")) { bundle -> /* ... */ }
UrlIntentBuilder.putExtrasAsync(mapOf("id" to 42), intent, "cfg_") { result -> /* ... */ }

// Bundle 合并异步
UrlIntentBuilder.mergeBundlesAsync(b1, b2) { merged -> /* ... */ }
UrlIntentBuilder.mergeBundlesAsync(b1, b2, "prefix_") { merged -> /* ... */ }
```

---

## 5. IntentBuilder — 流式 Intent 构造器

**包**：`com.itg.itg_string.intent`

将多个数据源（URL / Map / Bundle / 单键值对）**链式合并**到一个 Intent 或 Bundle。内置安全兜底：null/空输入静默跳过，URL 解析失败记录错误不中断链路，错误回调异常不影响构建流程。**同名 key 按添加顺序，后者覆盖前者**。

### 快速上手

```kotlin
val intent = IntentBuilder()
    .fromUrl("https://api.com?source=deeplink&campaign=summer", prefix = "url_")
    .fromMap(mapOf("userId" to 12345, "isVip" to true), prefix = "cfg_")
    .fromBundle(savedState)
    .action(Intent.ACTION_VIEW)
    .build()
// intent 中:
// "url_source" → "deeplink", "url_campaign" → "summer"
// "cfg_userId" → 12345, "cfg_isVip" → true
// + savedState 中的所有 key
```

### fromUrl — URL 接入

从 URL 查询参数提取键值对，委托 `UrlIntentBuilder.toBundle` 完成解析。

```kotlin
IntentBuilder()
    .fromUrl("https://api.com?q=kotlin&page=1")                        // 直接写入
    .fromUrl("https://a.com?x=1", prefix = "a_")                       // 加前缀隔离
    .fromUrl("https://s.com?q=%D6%D0%CE%C4", Charset.forName("GBK"),   // 指定字符集
             prefix = "gbk_")
```

**`fromUrl` 行为说明**：

| 输入 | 行为 | 错误记录 |
|------|------|----------|
| `"https://api.com?q=kotlin"` | 解析参数，加入管线 | — |
| `"https://example.com/path"`（无 query） | 静默跳过 | — |
| `null` / `""` / `"   "` | 静默跳过 | — |
| `"not a valid url"`（含空格等非法字符） | 解析失败，跳过 | ✅ 记录到 `errors()` |

```kotlin
// 多个 URL 聚合，任意一个失败不影响其他
val builder = IntentBuilder()
    .fromUrl(null)                                // ← 静默跳过
    .fromUrl("https://a.com?x=1", prefix = "a_")  // ← 正常
    .fromUrl("!!! invalid url")                    // ← 记录错误，继续
    .fromUrl("https://b.com?y=2", prefix = "b_")  // ← 正常
// builder.hasErrors() == true
// builder.build() 仍包含 a_x → "1", b_y → "2"
```

### fromMap — Map 接入

三组精确重载，编译期区分类型，空 Map 静默跳过：

```kotlin
IntentBuilder()
    .fromMap(mapOf("env" to "production"))                       // Map<String, String>
    .fromMap(linkedMapOf("tags" to listOf("a", "b")))            // Map<String, List<String>>
    .fromMap(mapOf<String, Any?>("id" to 42, "vip" to true),
             prefix = "cfg_")                                    // Map<String, *> 类型感知
```

### fromBundle — Bundle 接入

已有 Bundle 加入管线，**内部做防御性拷贝**（`Bundle(bundle)`），外部后续修改原 Bundle 不影响构建结果。null / 空 Bundle 静默跳过。

```kotlin
IntentBuilder()
    .fromBundle(existingBundle)                                   // 合并已有 Bundle
    .fromBundle(Bundle().apply { putString("k", "v") }, "p_")    // 带前缀
```

### put — 单键值对

6 种类型重载，快捷添加单个 key-value：

```kotlin
IntentBuilder()
    .put("name", "Alice")      // String
    .put("age", 25)            // Int
    .put("timestamp", 1700L)   // Long
    .put("vip", true)          // Boolean
    .put("ratio", 0.75f)       // Float
    .put("score", 3.14)        // Double
```

### 目标组件、action、data/type、flags、category

除 extras 数据源外，IntentBuilder 支持设置 Intent 的标准配置项，用于构造显式 Intent 启动 Activity。

**目标组件 — `component()`**：

```kotlin
IntentBuilder()
    .component(ComponentName(context, DetailActivity::class.java))  // ComponentName
    .component("com.example", "com.example.DetailActivity")          // 包名 + 类名
    .build()
// 等价于 Intent().setComponent(...)
```

**action / data / type — `action()` `data()` `type()` `dataAndType()`**：

```kotlin
IntentBuilder()
    .action(Intent.ACTION_VIEW)
    .data(Uri.parse("https://example.com/product/123"))
    .build()

// 同时设置 data + type（原子操作，避免互斥）
IntentBuilder()
    .action(Intent.ACTION_SEND)
    .dataAndType(imageUri, "image/png")
    .build()
```

| 方法 | 对应 Intent API | 说明 |
|------|-----------------|------|
| `action(action)` | `setAction` | 设置 action |
| `data(uri)` | `setData` | 设置 data URI |
| `type(type)` | `setType` | 设置 MIME type |
| `dataAndType(uri, type)` | `setDataAndType` | 同时设置 data + type（推荐） |

**flags — `flags()` / `addFlags()`**：

```kotlin
IntentBuilder()
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)       // 追加模式（常用）
    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)       // 再次追加
    .build()

// 或覆盖模式
IntentBuilder()
    .flags(Intent.FLAG_ACTIVITY_NEW_TASK)            // setFlags（替换所有）
    .build()
```

| 方法 | 对应 Intent API | 说明 |
|------|-----------------|------|
| `addFlags(flags)` | `addFlags` | 叠加到已有 flags（可多次调用） |
| `flags(flags)` | `setFlags` | 覆盖所有 flags |

**category — `addCategory()`**：

```kotlin
IntentBuilder()
    .action(Intent.ACTION_VIEW)
    .data(uri)
    .addCategory(Intent.CATEGORY_DEFAULT)            // 可多次调用
    .addCategory(Intent.CATEGORY_BROWSABLE)           // 自动去重
    .build()
```

### 前缀隔离与覆盖规则

每个数据源独立前缀，按添加顺序合并，**同名 key 后者覆盖**：

```kotlin
IntentBuilder()
    .fromUrl("https://a.com?id=1", prefix = "a_")  // a_id → "1"
    .fromUrl("https://b.com?id=2", prefix = "b_")  // b_id → "2"
    .put("id", 3)                                   // id   → "3"
    .build()
// 三个 "id" 互不冲突，因为前缀不同
```

无前缀时后者直接覆盖：

```kotlin
IntentBuilder()
    .put("key", "first")
    .put("key", "second")   // 覆盖 → "second"
    .build()
```

### 错误处理与安全机制

**安全默认**：异常不中断链路，事后查询错误列表：

```kotlin
val builder = IntentBuilder()
    .fromUrl("!!! bad url")             // 解析失败，记录错误
    .fromUrl("https://api.com?q=ok")    // 正常
    .fromMap(mapOf("fallback" to "works"))

if (builder.hasErrors()) {
    for (err in builder.errors()) {
        Log.w(TAG, "$err")
        // [IntentBuilder] fromUrl 失败: URL 解析失败 | input=!!! bad url
    }
}
val intent = builder.build()  // "q" → "ok", "fallback" → "works"
```

**实时回调**：`onError { e -> tracker.log(e.toString()) }`。回调中抛异常被 try-catch 兜底，不影响构建。

| 方法 | 说明 |
|------|------|
| `hasErrors()` | 是否存在错误 |
| `errors()` | 返回错误列表（只读副本） |
| `onError(handler)` | 注册实时回调，传 null 清除 |

### build / into / buildBundle — 三种输出

```kotlin
val builder = IntentBuilder().put("key", "value")

// 方式 1：产出新 Intent
val intent = builder.build()

// 方式 2：注入已有 Intent（会覆盖同名 key）
val result = builder.into(existingIntent)  // 返回同一 Intent，支持链式

// 方式 3：只取合并后的 Bundle，二次加工
val bundle = builder.buildBundle()
bundle.putLong("timestamp", System.currentTimeMillis())
intent.putExtras(bundle)
```

| 方法 | 返回 | 说明 |
|------|------|------|
| `build()` | `Intent` | 新 Intent，action 由 `action()` 配置 |
| `into(intent)` | `Intent` | 合并到已有 Intent |
| `buildBundle()` | `Bundle` | 只返回合并后 Bundle，无 Intent 包装 |

### reset / sourceCount — 生命周期

```kotlin
val builder = IntentBuilder().put("first", "A").action(Intent.ACTION_VIEW)
builder.build()          // 第一次
builder.reset()          // 清空 sources / errors / handler / action
    .put("second", "B")
    .build()             // 第二次，全新
builder.sourceCount()    // 当前数据源数量（调试用）
```

---

## 6. 实战场景

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

### 场景 I：SharedPreferences → Bundle 跳转

```kotlin
// 从 SharedPreferences 读取配置，注入 Intent
val prefs = mapOf(
    "env" to "staging",
    "feature_flag" to "true",
    "timeout_ms" to "5000"
)
val intent = UrlIntentBuilder.putExtras(prefs, Intent(context, ConfigActivity::class.java))
context.startActivity(intent)
```

### 场景 J：已解析参数二次加工后注入

```kotlin
// 解析深度链接，选择性保留参数并追加业务字段
val params = QueryParams.parse("id=42&utm_source=push&internal_trace=xyz")
val safe = params.filterKeys { !it.startsWith("internal_") }
    .mapValues { it.value }
    .toMutableMap()
safe["channel"] = listOf("android")

UrlIntentBuilder.putExtras(safe, intent, keyPrefix = "url_")
// "url_id" → "42", "url_utm_source" → "push", "url_channel" → "android"
// "internal_trace" 被过滤掉
```

### 场景 K：JSON payload → 异构 Map → Intent

```kotlin
// 推送消息携带的 JSON 转为异构 Map 注入 Intent
val json = """{"product_id":42,"is_vip":true,"discount":0.15,"tags":["new","sale"]}"""
val map: Map<String, *> = Gson().fromJson(json, object : TypeToken<Map<String, *>>() {}.type)

val intent = UrlIntentBuilder.putExtras(map, Intent(context, ProductActivity::class.java), "json_")
// intent.getIntExtra("json_product_id")     → 42
// intent.getBooleanExtra("json_is_vip")     → true
// intent.getDoubleExtra("json_discount")    → 0.15
// intent.getStringArrayListExtra("json_tags") → ["new", "sale"]
```

### 场景 L：从配置 Map 创建 Fragment arguments

```kotlin
val fragment = DetailFragment().apply {
    arguments = UrlIntentBuilder.toBundle(mapOf(
        "item_id" to "42",
        "display_mode" to "fullscreen"
    ))
}
// fragment.arguments.getString("item_id") → "42"
```

### 场景 M：多 Bundle 前缀合并

```kotlin
val urlBundle = UrlIntentBuilder.toBundle("https://api.com?source=deeplink")!!
val cfgBundle = UrlIntentBuilder.toBundle(mapOf("userId" to 12345, "isVip" to true))
val stateBundle = Bundle().apply { putString("session", "abc"); putBoolean("loggedIn", true) }

var merged = UrlIntentBuilder.mergeBundles(urlBundle, cfgBundle, "cfg_")
merged = UrlIntentBuilder.mergeBundles(merged, stateBundle, "state_")
intent.putExtras(merged)
// "source" → "deeplink", "cfg_userId" → 12345, "state_session" → "abc"
```

### 场景 N：IntentBuilder 多源安全聚合（含失败降级）

```kotlin
val builder = IntentBuilder()
    .onError { e -> Log.w(TAG, "IntentBuilder: $e") }
    .fromUrl(dynamicUrl1, prefix = "a_")    // 可能为 null / 非法
    .fromUrl(dynamicUrl2, prefix = "b_")    // 同上报错
    .fromMap(localConfig, prefix = "cfg_")  // 本地配置兜底
    .action(Intent.ACTION_VIEW)

if (builder.hasErrors()) showDegradedModeNotice()
context.startActivity(builder.build())
// 即使两个 URL 都解析失败，localConfig 依然写入 Intent
```

### 场景 O：IntentBuilder 先取 Bundle 加工再注入

```kotlin
val bundle = IntentBuilder()
    .fromUrl("https://api.com?q=kotlin", prefix = "url_")
    .put("version", 2)
    .buildBundle()              // ← 先拿到合并后的 Bundle

bundle.putLong("client_ts", System.currentTimeMillis())  // 二次加工
intent.putExtras(bundle)
```

### 场景 P：IntentBuilder 单键值快速构造（替代逐个 putExtra）

```kotlin
val intent = IntentBuilder()
    .put("userId", 12345)
    .put("userName", "Alice")
    .put("isVip", true)
    .put("score", 4.5)
    .action(Intent.ACTION_VIEW)
    .build()
// 等价于 Intent(...).apply { putExtra(...); putExtra(...) ... }
```

---

## 7. API 完整参考

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

**URL 入口 — Bundle / Map：**

| 方法 | 返回 | 说明 |
|------|------|------|
| `toBundle(url)` | `Bundle?` | URL 参数 → Bundle（UTF-8） |
| `toBundle(url, charset)` | `Bundle?` | URL 参数 → Bundle（指定字符集） |
| `toMap(url)` | `Map<String, String>?` | URL 参数 → 单值 Map（UTF-8） |
| `toMap(url, charset)` | `Map<String, String>?` | URL 参数 → 单值 Map（指定字符集） |
| `toMultiMap(url)` | `Map<String, List<String>>?` | URL 参数 → 多值 Map（UTF-8） |
| `toMultiMap(url, charset)` | `Map<String, List<String>>?` | URL 参数 → 多值 Map（指定字符集） |
| (以上各有对应 `xxxAsync` 版本) | | |

**URL 入口 — Intent：**

| 方法 | 返回 | 说明 |
|------|------|------|
| `putQueryExtras(url, intent)` | `Intent` | 写入 Intent extras（无前缀） |
| `putQueryExtras(url, intent, keyPrefix)` | `Intent` | 写入 Intent extras（带前缀） |
| (各有对应 `xxxAsync` 版本) | | |

**Map 入口 — 单值 `Map<String, String>`：**

| 方法 | 返回 | 说明 |
|------|------|------|
| `toBundle(params)` | `Bundle` | 单值 Map → Bundle |
| `toBundleAsync(params, onResult)` | `Future<*>` | 异步版 |
| `putExtras(params, intent)` | `Intent` | 写入 Intent extras（无前缀） |
| `putExtras(params, intent, keyPrefix)` | `Intent` | 写入 Intent extras（带前缀） |
| `putExtrasAsync(params, intent, prefix, onResult)` | `Future<*>` | 异步版 |

**Map 入口 — 多值 `Map<String, List<String>>`：**

| 方法 | 返回 | 说明 |
|------|------|------|
| `toBundle(params)` | `Bundle` | 多值 Map → Bundle（可直接桥接 QueryParams 输出） |
| `toBundleAsync(params, onResult)` | `Future<*>` | 异步版 |
| `putExtras(params, intent)` | `Intent` | 写入 Intent extras（无前缀） |
| `putExtras(params, intent, keyPrefix)` | `Intent` | 写入 Intent extras（带前缀） |
| `putExtrasAsync(params, intent, prefix, onResult)` | `Future<*>` | 异步版 |

**Map 入口 — 异构 `Map<String, *>`：**

| 方法 | 返回 | 说明 |
|------|------|------|
| `toBundle(params)` | `Bundle` | 异构 Map → Bundle（类型感知） |
| `toBundleAsync(params, onResult)` | `Future<*>` | 异步版 |
| `putExtras(params, intent)` | `Intent` | 写入 Intent extras（无前缀） |
| `putExtras(params, intent, keyPrefix)` | `Intent` | 写入 Intent extras（带前缀） |
| `putExtrasAsync(params, intent, prefix, onResult)` | `Future<*>` | 异步版 |

> **Java 调用注意**：Kotlin 的 Map 重载在 JVM 上会擦除为相同签名。通过 `@JvmName` 提供 Java 专用方法名：`toBundleFromStringMap` / `toBundleFromListMap` / `toBundleFromWildcardMap`，`putExtrasFromStringMap` / `putExtrasFromListMap` / `putExtrasFromWildcardMap`。

**Bundle 合并：**

| 方法 | 返回 | 说明 |
|------|------|------|
| `mergeBundles(first, second)` | `Bundle` | 合并两个 Bundle（second 覆盖） |
| `mergeBundles(first, second, keyPrefix)` | `Bundle` | 合并并给 second 的 key 加前缀 |
| `mergeBundlesAsync(first, second, onResult)` | `Future<*>` | 异步版 |
| `mergeBundlesAsync(first, second, prefix, onResult)` | `Future<*>` | 异步版（带前缀） |

### IntentBuilder

链式构造器，将 URL / Map / Bundle / 单键值对合并到 Intent 或 Bundle。

**数据源（均返回 `IntentBuilder` 支持链式）：**

| 方法 | 说明 |
|------|------|
| `fromUrl(url, prefix)` | URL 查询参数 → 管线（UTF-8，null/空/非法自动兜底） |
| `fromUrl(url, charset, prefix)` | URL 查询参数 → 管线（指定字符集） |
| `fromMap(Map<String, String>, prefix)` | 单值 Map → 管线 |
| `fromMap(Map<String, List<String>>, prefix)` | 多值 Map → 管线 |
| `fromMap(Map<String, *>, prefix)` | 异构 Map → 管线（类型感知） |
| `fromBundle(bundle, prefix)` | Bundle → 管线（防御性拷贝） |
| `put(key, value)` | 单键值对（String / Int / Long / Boolean / Float / Double） |

**Intent 配置（均返回 `IntentBuilder` 支持链式）：**

| 方法 | 说明 |
|------|------|
| `component(cn)` / `component(pkg, cls)` | 设置显式目标组件 |
| `action(action)` | 设置 Intent action |
| `data(uri)` | 设置 data URI |
| `type(type)` | 设置 MIME type |
| `dataAndType(uri, type)` | 同时设置 data + type（原子操作） |
| `flags(flags)` | 覆盖所有 flags |
| `addFlags(flags)` | 追加 flags（可多次调用叠加） |
| `addCategory(category)` | 添加 category（可多次调用，自动去重） |

**错误处理：**

| 方法 | 返回 | 说明 |
|------|------|------|
| `onError(handler)` | `IntentBuilder` | 注册实时回调，传 null 清除 |
| `errors()` | `List<BuildError>` | 只读副本 |
| `hasErrors()` | `Boolean` | 是否有错误 |

**构建输出：**

| 方法 | 返回 | 说明 |
|------|------|------|
| `build()` | `Intent` | 构建新 Intent（应用所有数据源 + 配置） |
| `into(intent)` | `Intent` | 合并到已有 Intent |
| `buildBundle()` | `Bundle` | 只返回合并后 Bundle |

**生命周期：**

| 方法 | 返回 | 说明 |
|------|------|------|
| `reset()` | `IntentBuilder` | 清空全部状态 |
| `sourceCount()` | `Int` | 已收集数据源个数 |

**数据结构：**

| 类型 | 说明 |
|------|------|
| `BuildError(source, input, message)` | `data class` — 错误信息 |
| `ErrorHandler` | `fun interface` — 错误回调（Kotlin SAM） |

---

## 8. 线程模型

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

## 9. 字符编码说明

| 环节 | 输入 | 处理 |
|------|------|------|
| `UrlParser.parse()` | 含非 ASCII 的 URL | 自动 UTF-8 percent-encode 后解析 |
| `UrlParser.parse()` | 已正确编码的 URL | 直接解析，不二次编码 |
| `QueryParams.decode()` | percent-encoded 字符串 | UTF-8 解码（默认），可指定 charset |
| `UrlIntentBuilder.toBundle()` | 含非 ASCII 的 URL | UrlParser sanitize → QueryParams 解码 → Bundle |
| 双重编码 (`%2520`) | 解码一次 → `%20` | 输出 `Log.w` 警告 |

> 已编码的 `%XX` 序列不会被二次编码——`%` 字符本身是 ASCII（U+0025），sanitize 只处理 U+0080 以上字符。BMP 外字符（emoji）通过 `String.codePointAt()` 按 code point 粒度编码，不会错误拆分 surrogate pair。

---

## 10. 错误处理策略

| 场景 | 行为 |
|------|------|
| 非法 URL 格式 | `UrlParser.parse()` 返回 `null`，不抛异常 |
| `null` / 空字符串输入 | 返回 `null` 或空集合，不抛异常 |
| URL 解码失败 | 静默返回原始编码字符串 |
| 无查询参数 | `toBundle(url)` 返回空 `Bundle`（非 null） |
| 空 Map 输入 | `toBundle(map)` 返回空 `Bundle`（非 null） |
| 异构 Map 中 null 值 | 跳过不写入，其他 key 正常处理 |
| 异构 Map 中未知类型 | `putString(key, value.toString())` 退化兜底 |
| 双重编码 | `Log.w` 警告，继续使用解码结果 |
| IntentBuilder 数据源失败 | 记录到 `errors()`，后续数据源正常处理 |
| IntentBuilder 错误回调异常 | try-catch 兜底，不影响构建 |
| IntentBuilder 全部数据源失败 | `build()` 返回无 extras 的 Intent，不抛异常 |
| Bundle 合并时 null 值 | `mergeBundles` 前缀合并跳过 null |
```
