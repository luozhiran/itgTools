package com.itg.itg_string.intent

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.itg.itg_string.core.UrlParser
import com.itg.itg_string.query.QueryParams
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.nio.charset.Charset
import java.util.concurrent.Future

/**
 * URL 查询参数 / Map → Android Bundle / Intent extras 转换工具类。
 *
 * 提供三条数据入口：
 * - **URL 入口**：[UrlParser] + [QueryParams] 解析 URL 中的查询参数
 * - **Map 入口**：直接接收 `Map<String, String>` / `Map<String, List<String>>` / `Map<String, *>`
 * - **Bundle 合并入口**：[mergeBundles] 合并两个 Bundle，支持 key 前缀隔离
 *
 * 所有入口最终汇聚到同一套 Bundle 构建引擎，行为一致。
 *
 * 设计要点：
 * - 所有参数值以各自最合适的类型存储（String / Int / Boolean / ArrayList 等）
 * - 支持自定义字符集解码（URL 入口），兼容 GBK/Shift_JIS 等非 UTF-8 编码
 * - 内置双重编码检测（URL 入口 + Map<String, List<String>> 入口）
 * - 支持 key 前缀，避免 URL 参数覆盖 Intent 已有 extras
 * - 支持 Bundle 合并：简单覆盖合并 + 前缀隔离合并，均保留值类型
 * - 同步 + 异步双模式，异步通过 [TaskExecutor] 驱动
 * - 所有方法无共享可变状态，天然线程安全
 *
 * 使用示例：
 * ```kotlin
 * // === URL 入口 ===
 * val bundle = UrlIntentBuilder.toBundle("https://api.com/v2/users?active=true&role=admin")
 * UrlIntentBuilder.putQueryExtras("https://api.com?active=true", intent, keyPrefix = "url_")
 *
 * // === Map 入口 ===
 * val b = UrlIntentBuilder.toBundle(mapOf("key1" to "val1", "key2" to "val2"))
 * UrlIntentBuilder.putExtras(mapOf("id" to 123, "active" to true), intent, keyPrefix = "cfg_")
 *
 * // === Bundle 合并 ===
 * val merged = UrlIntentBuilder.mergeBundles(urlBundle, cfgBundle)
 * val prefixed = UrlIntentBuilder.mergeBundles(urlBundle, cfgBundle, "cfg_")
 * ```
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("MemberVisibilityCanBePrivate", "unused", "DEPRECATION")
object UrlIntentBuilder {

    private const val TAG = "UrlIntentBuilder"

    /** 匹配 %XX 模式的 heuristic 正则（XX 为两个 hex 字符） */
    private val PERCENT_ENCODED_PATTERN = Regex("%[0-9A-Fa-f]{2}")

    // ==================================================================
    // 核心引擎：Map<String, List<String>> → Bundle
    // 所有 URL 路径和单值 Map 路径最终汇聚于此
    // ==================================================================

    /**
     * 将多值参数映射写入 [Bundle]。
     *
     * 这是 [toBundle] 所有重载最终汇聚的底层引擎。
     * - 单值 list → [Bundle.putString]
     * - 多值 list → [Bundle.putStringArrayList]
     * - 空 list  → [Bundle.putString](key, "")
     * - 同时执行双重编码检测
     */
    private fun bundleFromMultiValueParams(params: Map<String, List<String>>): Bundle {
        val bundle = Bundle()
        for ((key, values) in params) {
            detectDoubleEncoding(key, values)
            if (values.isEmpty()) {
                bundle.putString(key, "")
            } else if (values.size == 1) {
                bundle.putString(key, values[0])
            } else {
                bundle.putStringArrayList(key, ArrayList(values))
            }
        }
        return bundle
    }

    /**
     * 将 Bundle 中的键值对写入 Intent，支持 key 前缀。
     *
     * 仅适用于 Bundle 中只有 String / StringArrayList 类型值的场景
     * （即 URL 路径和 String/List<String> Map 路径产出的 Bundle）。
     */
    private fun putBundleToIntent(bundle: Bundle, intent: Intent, keyPrefix: String) {
        if (keyPrefix.isEmpty()) {
            intent.putExtras(bundle)
        } else {
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is ArrayList<*> -> intent.putStringArrayListExtra(
                        keyPrefix + key, value as ArrayList<String>
                    )
                    else -> intent.putExtra(keyPrefix + key, value as String?)
                }
            }
        }
    }

    // ==================================================================
    // 功能一：URL 参数 → Bundle
    // ==================================================================

    /**
     * 从 URL 查询参数创建 [Bundle]（默认 UTF-8 解码）。
     *
     * @param url 待解析的 URL 字符串
     * @return 包含所有查询参数的 Bundle；URL 无查询部分时返回空 Bundle；URL 非法或为 null 时返回 null
     */
    @JvmStatic
    fun toBundle(url: String?): Bundle? = toBundle(url, Charsets.UTF_8)

    /**
     * 从 URL 查询参数创建 [Bundle]，使用指定字符集解码。
     *
     * @param url     待解析的 URL 字符串
     * @param charset 解码字符集，例如 `Charset.forName("GBK")`
     * @return 包含所有查询参数的 Bundle
     */
    @JvmStatic
    fun toBundle(url: String?, charset: Charset): Bundle? {
        if (url.isNullOrBlank()) return null
        val components = UrlParser.parse(url) ?: return null
        val queryString = components.query
        if (queryString.isNullOrBlank()) return Bundle()
        val params = QueryParams.parse(queryString, charset)
        if (params.isEmpty()) return Bundle()
        return bundleFromMultiValueParams(params)
    }

    /** 异步从 URL 查询参数创建 [Bundle]（默认 UTF-8）。 */
    @JvmStatic
    fun toBundleAsync(url: String?, onResult: (Bundle?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toBundle(url)) }
    }

    /** 异步从 URL 查询参数创建 [Bundle]（指定字符集）。 */
    @JvmStatic
    fun toBundleAsync(url: String?, charset: Charset, onResult: (Bundle?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toBundle(url, charset)) }
    }

    // ==================================================================
    // 功能一（URL 版）：URL 参数 → Map
    // ==================================================================

    /**
     * 从 URL 查询参数提取为单值 [Map]（默认 UTF-8 解码）。
     *
     * 每个参数取第一个值；多值参数的其他值会被丢弃。
     * 如需保留所有重复 key 的值，使用 [toMultiMap]。
     *
     * @param url 待解析的 URL 字符串
     * @return 参数名 → 值的映射；URL 无 query 时返回空 Map；URL 非法或为 null 时返回 null
     *
     * 使用示例：
     * ```kotlin
     * val m = UrlIntentBuilder.toMap("https://api.com?active=true&role=admin&tag=a&tag=b")
     * m["active"]  // "true"
     * m["role"]    // "admin"
     * m["tag"]     // "a"  (只取第一个值)
     * ```
     */
    @JvmStatic
    fun toMap(url: String?): Map<String, String>? = toMap(url, Charsets.UTF_8)

    /**
     * 从 URL 查询参数提取为单值 [Map]，使用指定字符集解码。
     *
     * @param url     待解析的 URL 字符串
     * @param charset 解码字符集
     * @return 参数名 → 值的映射
     */
    @JvmStatic
    fun toMap(url: String?, charset: Charset): Map<String, String>? {
        val multi = toMultiMap(url, charset) ?: return null
        if (multi.isEmpty()) return emptyMap()
        return multi.mapValues { it.value.first() }
    }

    /** 异步版。 */
    @JvmStatic
    fun toMapAsync(url: String?, onResult: (Map<String, String>?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toMap(url)) }
    }

    /** 异步版（指定字符集）。 */
    @JvmStatic
    fun toMapAsync(url: String?, charset: Charset, onResult: (Map<String, String>?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toMap(url, charset)) }
    }

    /**
     * 从 URL 查询参数提取为多值 [Map]（默认 UTF-8 解码）。
     *
     * 每个参数对应一个值列表，保留所有重复 key。内部直接委托给 [QueryParams.parse]。
     *
     * @param url 待解析的 URL 字符串
     * @return 参数名 → 值列表 的映射；URL 无 query 时返回空 Map；URL 非法或为 null 时返回 null
     *
     * 使用示例：
     * ```kotlin
     * val m = UrlIntentBuilder.toMultiMap("https://api.com?tag=a&tag=b&q=kotlin")
     * m["tag"]  // ["a", "b"]
     * m["q"]    // ["kotlin"]
     * ```
     */
    @JvmStatic
    fun toMultiMap(url: String?): Map<String, List<String>>? = toMultiMap(url, Charsets.UTF_8)

    /**
     * 从 URL 查询参数提取为多值 [Map]，使用指定字符集解码。
     *
     * @param url     待解析的 URL 字符串
     * @param charset 解码字符集
     * @return 参数名 → 值列表 的映射
     */
    @JvmStatic
    fun toMultiMap(url: String?, charset: Charset): Map<String, List<String>>? {
        if (url.isNullOrBlank()) return null
        val components = UrlParser.parse(url) ?: return null
        val queryString = components.query
        if (queryString.isNullOrBlank()) return emptyMap()
        return QueryParams.parse(queryString, charset)
    }

    /** 异步版。 */
    @JvmStatic
    fun toMultiMapAsync(url: String?, onResult: (Map<String, List<String>>?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toMultiMap(url)) }
    }

    /** 异步版（指定字符集）。 */
    @JvmStatic
    fun toMultiMapAsync(
        url: String?, charset: Charset, onResult: (Map<String, List<String>>?) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(toMultiMap(url, charset)) }
    }

    // ==================================================================
    // 功能一（Map 版）：单值 Map → Bundle
    // ==================================================================

    /**
     * 将单值参数映射 [Map] 转换为 [Bundle]，每个 entry 写入为 `putString`。
     *
     * 适用于 SharedPreferences、程序化配置等场景。
     *
     * @param params 参数映射；空 Map 返回空 Bundle
     * @return 包含所有键值对的 Bundle（非 null）
     *
     * 使用示例：
     * ```kotlin
     * val b = UrlIntentBuilder.toBundle(mapOf("env" to "production", "timeout" to "30"))
     * b.getString("env")      // "production"
     * b.getString("timeout")  // "30"
     * ```
     */
    @JvmStatic
    @JvmName("toBundleFromStringMap")
    fun toBundle(params: Map<String, String>): Bundle {
        if (params.isEmpty()) return Bundle()
        // 转换为多值 Map，统一走核心引擎
        val multi = LinkedHashMap<String, List<String>>(params.size)
        for ((k, v) in params) {
            multi[k] = listOf(v)
        }
        return bundleFromMultiValueParams(multi)
    }

    /** 异步版。 */
    @JvmStatic
    @JvmName("toBundleFromStringMapAsync")
    fun toBundleAsync(params: Map<String, String>, onResult: (Bundle) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toBundle(params)) }
    }

    // ==================================================================
    // 功能一（Map 版）：多值 Map → Bundle
    // ==================================================================

    /**
     * 将多值参数映射 [Map] 转换为 [Bundle]。
     *
     * 与 [QueryParams.parse] 返回类型一致，可直接桥接。
     * 单值 list → `putString`，多值 list → `putStringArrayList`。
     *
     * @param params 参数映射；空 Map 返回空 Bundle
     * @return 包含所有键值对的 Bundle（非 null）
     *
     * 使用示例：
     * ```kotlin
     * val parsed = QueryParams.parse("tag=a&tag=b&q=kotlin")
     * val b = UrlIntentBuilder.toBundle(parsed)
     * b.getString("q")                // "kotlin"
     * b.getStringArrayList("tag")     // ["a", "b"]
     * ```
     */
    @JvmStatic
    @JvmName("toBundleFromListMap")
    fun toBundle(params: Map<String, List<String>>): Bundle {
        if (params.isEmpty()) return Bundle()
        return bundleFromMultiValueParams(params)
    }

    /** 异步版。 */
    @JvmStatic
    @JvmName("toBundleFromListMapAsync")
    fun toBundleAsync(params: Map<String, List<String>>, onResult: (Bundle) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toBundle(params)) }
    }

    // ==================================================================
    // 功能一（Map 版）：异构值 Map → Bundle
    // ==================================================================

    /**
     * 将异构值参数映射 [Map] 转换为 [Bundle]，按实际类型选择最合适的 Bundle 写入方法。
     *
     * 类型路由规则：
     * - [String]           → [Bundle.putString]
     * - [Int]              → [Bundle.putInt]
     * - [Long]             → [Bundle.putLong]
     * - [Boolean]          → [Bundle.putBoolean]
     * - [Float]            → [Bundle.putFloat]
     * - [Double]           → [Bundle.putDouble]
     * - `List<String>`     → [Bundle.putStringArrayList]
     * - `List<Int>`        → [Bundle.putIntegerArrayList]
     * - `null`             → 跳过，不写入
     * - 其他类型            → [Bundle.putString](key, value.toString())  ← 退化兜底
     *
     * @param params 异构参数映射；空 Map 返回空 Bundle
     * @return 包含所有键值对的 Bundle（非 null）
     *
     * 使用示例：
     * ```kotlin
     * val b = UrlIntentBuilder.toBundle(mapOf<String, Any?>(
     *     "id" to 12345, "active" to true, "score" to 4.5,
     *     "name" to "John", "tags" to listOf("premium", "verified"),
     *     "ignored" to null
     * ))
     * b.getInt("id")               // 12345
     * b.getBoolean("active")       // true
     * b.getDouble("score")         // 4.5
     * b.containsKey("ignored")     // false
     * ```
     */
    @JvmStatic
    @JvmName("toBundleFromWildcardMap")
    fun toBundle(params: Map<String, *>): Bundle {
        if (params.isEmpty()) return Bundle()
        val bundle = Bundle()
        for ((key, value) in params) {
            @Suppress("UNCHECKED_CAST")
            when (value) {
                null -> { /* skip */ }
                is String  -> bundle.putString(key, value)
                is Int     -> bundle.putInt(key, value)
                is Long    -> bundle.putLong(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is Float   -> bundle.putFloat(key, value)
                is Double  -> bundle.putDouble(key, value)
                is List<*> -> {
                    if (value.isEmpty()) {
                        bundle.putStringArrayList(key, ArrayList())
                    } else {
                        when (value.first()) {
                            is String -> bundle.putStringArrayList(
                                key, ArrayList(value.filterIsInstance<String>())
                            )
                            is Int -> bundle.putIntegerArrayList(
                                key, ArrayList(value.filterIsInstance<Int>())
                            )
                            else -> bundle.putStringArrayList(
                                key, ArrayList(value.map { it.toString() })
                            )
                        }
                    }
                }
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }

    /** 异步版。 */
    @JvmStatic
    @JvmName("toBundleFromWildcardMapAsync")
    fun toBundleAsync(params: Map<String, *>, onResult: (Bundle) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toBundle(params)) }
    }

    // ==================================================================
    // 功能二：URL 参数 → Intent extras
    // ==================================================================

    /**
     * 将 URL 查询参数批量写入 [Intent] 的 extras 中（无前缀）。
     */
    @JvmStatic
    fun putQueryExtras(url: String?, intent: Intent): Intent {
        return putQueryExtras(url, intent, "")
    }

    /**
     * 将 URL 查询参数写入 [Intent] extras，可选 key 前缀。
     *
     * @param url       待解析的 URL 字符串
     * @param intent    目标 Intent
     * @param keyPrefix 追加到每个参数 key 前的前缀，空字符串表示不加前缀
     * @return 传入的 Intent（支持链式调用）
     */
    @JvmStatic
    fun putQueryExtras(url: String?, intent: Intent, keyPrefix: String): Intent {
        val bundle = toBundle(url) ?: return intent
        if (bundle.isEmpty) return intent
        putBundleToIntent(bundle, intent, keyPrefix)
        return intent
    }

    /** 异步版。 */
    @JvmStatic
    fun putQueryExtrasAsync(url: String?, intent: Intent, onResult: (Intent) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(putQueryExtras(url, intent)) }
    }

    /** 异步版（带前缀）。 */
    @JvmStatic
    fun putQueryExtrasAsync(
        url: String?, intent: Intent, keyPrefix: String, onResult: (Intent) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(putQueryExtras(url, intent, keyPrefix)) }
    }

    // ==================================================================
    // 功能二（Map 版）：单值 Map → Intent extras
    // ==================================================================

    /**
     * 将单值参数映射 [Map] 批量写入 [Intent] extras，可选 key 前缀。
     *
     * @param params    参数映射；空 Map 时原样返回 intent
     * @param intent    目标 Intent
     * @param keyPrefix 追加到每个参数 key 前的前缀
     * @return 传入的 Intent（支持链式调用）
     */
    @JvmStatic
    @JvmName("putExtrasFromStringMap")
    fun putExtras(params: Map<String, String>, intent: Intent, keyPrefix: String = ""): Intent {
        val bundle = toBundle(params)
        if (bundle.isEmpty) return intent
        putBundleToIntent(bundle, intent, keyPrefix)
        return intent
    }

    /** 异步版。 */
    @JvmStatic
    @JvmName("putExtrasFromStringMapAsync")
    fun putExtrasAsync(
        params: Map<String, String>, intent: Intent, keyPrefix: String, onResult: (Intent) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(putExtras(params, intent, keyPrefix)) }
    }

    // ==================================================================
    // 功能二（Map 版）：多值 Map → Intent extras
    // ==================================================================

    /**
     * 将多值参数映射 [Map] 批量写入 [Intent] extras，可选 key 前缀。
     *
     * @param params    参数映射；空 Map 时原样返回 intent
     * @param intent    目标 Intent
     * @param keyPrefix 追加到每个参数 key 前的前缀
     * @return 传入的 Intent（支持链式调用）
     */
    @JvmStatic
    @JvmName("putExtrasFromListMap")
    fun putExtras(params: Map<String, List<String>>, intent: Intent, keyPrefix: String = ""): Intent {
        val bundle = toBundle(params)
        if (bundle.isEmpty) return intent
        putBundleToIntent(bundle, intent, keyPrefix)
        return intent
    }

    /** 异步版。 */
    @JvmStatic
    @JvmName("putExtrasFromListMapAsync")
    fun putExtrasAsync(
        params: Map<String, List<String>>, intent: Intent, keyPrefix: String, onResult: (Intent) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(putExtras(params, intent, keyPrefix)) }
    }

    // ==================================================================
    // 功能二（Map 版）：异构值 Map → Intent extras
    // ==================================================================

    /**
     * 将异构值参数映射 [Map] 批量写入 [Intent] extras，可选 key 前缀。
     *
     * 类型路由规则与 [toBundle]`(Map<String, *>)` 一致。
     * 带前缀时直接按 Map 条目写入以保留精确类型（避免 Bundle 拆包的类型丢失）。
     *
     * @param params    参数映射；空 Map 时原样返回 intent
     * @param intent    目标 Intent
     * @param keyPrefix 追加到每个参数 key 前的前缀
     * @return 传入的 Intent（支持链式调用）
     */
    @JvmStatic
    @JvmName("putExtrasFromWildcardMap")
    fun putExtras(params: Map<String, *>, intent: Intent, keyPrefix: String = ""): Intent {
        if (params.isEmpty()) return intent

        if (keyPrefix.isEmpty()) {
            val bundle = toBundle(params)
            if (bundle.isEmpty) return intent
            intent.putExtras(bundle)
        } else {
            // 带前缀时直接按 Map 条目写入，保留精确类型
            for ((key, value) in params) {
                val prefixedKey = keyPrefix + key
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    null -> { /* skip */ }
                    is String  -> intent.putExtra(prefixedKey, value)
                    is Int     -> intent.putExtra(prefixedKey, value)
                    is Long    -> intent.putExtra(prefixedKey, value)
                    is Boolean -> intent.putExtra(prefixedKey, value)
                    is Float   -> intent.putExtra(prefixedKey, value)
                    is Double  -> intent.putExtra(prefixedKey, value)
                    is List<*> -> {
                        if (value.isEmpty()) {
                            intent.putStringArrayListExtra(prefixedKey, ArrayList())
                        } else {
                            when (value.first()) {
                                is String -> intent.putStringArrayListExtra(
                                    prefixedKey, ArrayList(value.filterIsInstance<String>())
                                )
                                is Int -> intent.putIntegerArrayListExtra(
                                    prefixedKey, ArrayList(value.filterIsInstance<Int>())
                                )
                                else -> intent.putStringArrayListExtra(
                                    prefixedKey, ArrayList(value.map { it.toString() })
                                )
                            }
                        }
                    }
                    else -> intent.putExtra(prefixedKey, value.toString())
                }
            }
        }
        return intent
    }

    /** 异步版。 */
    @JvmStatic
    @JvmName("putExtrasFromWildcardMapAsync")
    fun putExtrasAsync(
        params: Map<String, *>, intent: Intent, keyPrefix: String, onResult: (Intent) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(putExtras(params, intent, keyPrefix)) }
    }

    // ==================================================================
    // 功能三：Bundle 合并
    // ==================================================================

    /**
     * 合并两个 [Bundle] 为一个新的 Bundle，key 冲突时 [second] 覆盖 [first]。
     *
     * 典型场景：URL 解析得到的 Bundle 与业务配置 Map 转换的 Bundle 合并后注入 Intent。
     *
     * @param first  第一个 Bundle
     * @param second 第二个 Bundle（key 冲突时优先）
     * @return 合并后的新 Bundle（独立副本，修改不影响入参）
     *
     * 使用示例：
     * ```kotlin
     * val urlBundle = UrlIntentBuilder.toBundle("https://api.com?source=deeplink")
     * val cfgBundle = UrlIntentBuilder.toBundle(mapOf("userId" to 12345, "isVip" to true))
     * val merged = UrlIntentBuilder.mergeBundles(urlBundle ?: Bundle(), cfgBundle)
     * intent.putExtras(merged)
     * ```
     */
    @JvmStatic
    fun mergeBundles(first: Bundle, second: Bundle): Bundle {
        return Bundle().apply {
            putAll(first)
            putAll(second)
        }
    }

    /**
     * 合并两个 [Bundle]，为 [second] 的所有 key 添加前缀后写入，用于避免 key 命名冲突。
     *
     * 前缀机制与 [putQueryExtras] 的 [keyPrefix] 设计一致。
     * 支持类型保留：String / Int / Long / Boolean / Float / Double / ArrayList<String> / ArrayList<Int>。
     * 未知类型退化兜底为 `putString(key, value.toString())`。
     *
     * @param first     第一个 Bundle（原样写入）
     * @param second    第二个 Bundle（key 加前缀后写入）
     * @param keyPrefix 追加到 [second] 每个 key 前的前缀；空字符串等价于 [mergeBundles]
     * @return 合并后的新 Bundle
     *
     * 使用示例：
     * ```kotlin
     * val urlBundle = UrlIntentBuilder.toBundle("https://api.com?source=deeplink")
     * val cfgBundle = UrlIntentBuilder.toBundle(mapOf("userId" to 12345))
     * val merged = UrlIntentBuilder.mergeBundles(urlBundle ?: Bundle(), cfgBundle, "cfg_")
     * merged.getString("source")        // "deeplink"
     * merged.getInt("cfg_userId")       // 12345
     * ```
     */
    @JvmStatic
    @JvmName("mergeBundlesWithPrefix")
    fun mergeBundles(first: Bundle, second: Bundle, keyPrefix: String): Bundle {
        val merged = Bundle(first)
        if (keyPrefix.isEmpty()) {
            merged.putAll(second)
            return merged
        }
        for (key in second.keySet()) {
            val value = second.get(key)
            @Suppress("UNCHECKED_CAST")
            when (value) {
                null -> { /* skip */ }
                is String  -> merged.putString(keyPrefix + key, value)
                is Int     -> merged.putInt(keyPrefix + key, value)
                is Long    -> merged.putLong(keyPrefix + key, value)
                is Boolean -> merged.putBoolean(keyPrefix + key, value)
                is Float   -> merged.putFloat(keyPrefix + key, value)
                is Double  -> merged.putDouble(keyPrefix + key, value)
                is ArrayList<*> -> {
                    if (value.isEmpty()) {
                        merged.putStringArrayList(keyPrefix + key, ArrayList())
                    } else {
                        when (value.first()) {
                            is String -> merged.putStringArrayList(
                                keyPrefix + key, value as ArrayList<String>
                            )
                            is Int -> merged.putIntegerArrayList(
                                keyPrefix + key, value as ArrayList<Int>
                            )
                            else -> merged.putStringArrayList(
                                keyPrefix + key, ArrayList(value.map { it.toString() })
                            )
                        }
                    }
                }
                else -> merged.putString(keyPrefix + key, value.toString())
            }
        }
        return merged
    }

    /** [mergeBundles] 的异步版本。 */
    @JvmStatic
    fun mergeBundlesAsync(first: Bundle, second: Bundle, onResult: (Bundle) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(mergeBundles(first, second)) }
    }

    /** [mergeBundles] 的异步版本（带前缀）。 */
    @JvmStatic
    @JvmName("mergeBundlesWithPrefixAsync")
    fun mergeBundlesAsync(
        first: Bundle, second: Bundle, keyPrefix: String, onResult: (Bundle) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(mergeBundles(first, second, keyPrefix)) }
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 检测解码后的值是否仍残留 %XX 模式（双重编码的常见信号）。
     *
     * 如果检测到残留的 percent-encoding，说明上游可能对参数进行了两次 encode
     * 或者使用了与本方法不同的字符集编码。此时输出 WARNING 级别日志辅助排查。
     */
    private fun detectDoubleEncoding(key: String, values: List<String>) {
        for (value in values) {
            if (PERCENT_ENCODED_PATTERN.containsMatchIn(value)) {
                Log.w(
                    TAG,
                    "Possible double-encoding detected: key=\"$key\" value=\"$value\" " +
                    "contains residual percent-encoded sequences (%XX). " +
                    "The upstream caller may have double-encoded this value " +
                    "or used a different charset."
                )
            }
        }
    }
}
