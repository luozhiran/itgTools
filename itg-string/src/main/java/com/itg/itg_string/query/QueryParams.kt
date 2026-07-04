package com.itg.itg_string.query

import com.itg.itg_string.core.UrlParser
import com.itg.itg_thread_pools.executor.TaskExecutor
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.Future

/**
 * URL 查询参数解析/构建工具类。
 *
 * 独立处理 `?key1=value1&key2=value2` 子语言，支持：
 * - 多值参数（如 `?tag=a&tag=b`）
 * - 无值参数（如 `?flag`）
 * - 编码/解码
 * - 参数增删改
 *
 * 设计遵循项目既有 [object] + [@JvmStatic] + 同步/异步双模模式。
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object QueryParams {

    /**
     * 解析查询字符串为有序参数映射（默认 UTF-8 解码）。
     *
     * 保留参数插入顺序和重复 key。无值的 key（如 `?flag`）其 value 列表包含空字符串。
     *
     * @param queryString 查询字符串（可带或不带前导 `?`）
     * @return 参数名 → 值列表 的映射；空字符串输入返回空 Map
     *
     * 使用示例：
     * ```kotlin
     * val params = QueryParams.parse("q=kotlin&tag=android&tag=jvm&flag")
     * // { "q" → ["kotlin"], "tag" → ["android", "jvm"], "flag" → [""] }
     * ```
     */
    @JvmStatic
    fun parse(queryString: String?): Map<String, List<String>> {
        return parse(queryString, Charsets.UTF_8)
    }

    /**
     * 解析查询字符串为有序参数映射，使用指定字符集解码。
     *
     * 适用于非 UTF-8 编码的 URL（如 GBK/GB2312、Shift_JIS、EUC-KR 等）。
     *
     * @param queryString 查询字符串（可带或不带前导 `?`）
     * @param charset     解码字符集，例如 [Charsets.UTF_8]、`Charset.forName("GBK")`
     * @return 参数名 → 值列表 的映射
     *
     * 使用示例：
     * ```kotlin
     * // GBK 编码的 URL 参数
     * val params = QueryParams.parse("%D6%D0%CE%C4", Charset.forName("GBK"))
     * // { "q" → ["中文"] }
     * ```
     */
    @JvmStatic
    fun parse(queryString: String?, charset: Charset): Map<String, List<String>> {
        if (queryString.isNullOrBlank()) return emptyMap()
        val query = queryString.trimStart('?').trim()
        if (query.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, MutableList<String>>()
        val pairs = query.split("&")
        for (pair in pairs) {
            if (pair.isEmpty()) continue
            val eqIndex = pair.indexOf('=')
            val key: String
            val value: String
            if (eqIndex < 0) {
                key = decode(pair, charset)
                value = ""
            } else {
                key = decode(pair.substring(0, eqIndex), charset)
                value = decode(pair.substring(eqIndex + 1), charset)
            }
            result.getOrPut(key) { mutableListOf() }.add(value)
        }
        return result
    }

    /**
     * 获取查询字符串中指定 key 的第一个值。
     *
     * @param queryString 查询字符串（可带或不带前导 `?`）
     * @param key 参数名
     * @return 参数值，不存在返回 null
     *
     * 使用示例：
     * ```kotlin
     * QueryParams.getFirst("q=kotlin&page=2", "q")     // "kotlin"
     * QueryParams.getFirst("q=kotlin&page=2", "size")   // null
     * ```
     */
    @JvmStatic
    fun getFirst(queryString: String?, key: String): String? {
        return parse(queryString)[key]?.firstOrNull()
    }

    /**
     * 获取查询字符串中指定 key 的所有值。
     *
     * @param queryString 查询字符串
     * @param key 参数名
     * @return 值列表，不存在返回空列表
     */
    @JvmStatic
    fun getAll(queryString: String?, key: String): List<String> {
        return parse(queryString)[key] ?: emptyList()
    }

    /**
     * 从完整 URL 中提取指定查询参数的值。
     *
     * 这是 [UrlParser.getQuery] + [getFirst] 的组合便捷方法。
     *
     * @param url 完整 URL 字符串
     * @param key 参数名
     * @return 参数值，不存在返回 null
     *
     * 使用示例：
     * ```kotlin
     * QueryParams.getParam("https://example.com/search?q=kotlin&page=1", "q")  // "kotlin"
     * ```
     */
    @JvmStatic
    fun getParam(url: String?, key: String): String? {
        val query = UrlParser.getQuery(url) ?: return null
        return getFirst(query, key)
    }

    /**
     * 从完整 URL 中提取指定查询参数的所有值。
     */
    @JvmStatic
    fun getParams(url: String?, key: String): List<String> {
        val query = UrlParser.getQuery(url) ?: return emptyList()
        return getAll(query, key)
    }

    /**
     * 将参数映射构建为查询字符串（不含前导 `?`）。
     *
     * @param params 参数名 → 值 的映射（单值场景）
     * @return 查询字符串，如 "q=kotlin&page=1"；空 Map 返回空字符串
     *
     * 使用示例：
     * ```kotlin
     * QueryParams.build(mapOf("q" to "kotlin", "page" to "1"))  // "q=kotlin&page=1"
     * ```
     */
    @JvmStatic
    fun build(params: Map<String, String>): String {
        if (params.isEmpty()) return ""
        return params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    /**
     * 将多值参数映射构建为查询字符串（不含前导 `?`）。
     *
     * @param params 参数名 → 值列表 的映射
     * @return 查询字符串，如 "tag=a&tag=b&q=kotlin"
     */
    @JvmStatic
    fun buildMulti(params: Map<String, List<String>>): String {
        if (params.isEmpty()) return ""
        return params.entries.flatMap { (key, values) ->
            if (values.isEmpty()) {
                listOf(encode(key))
            } else {
                values.map { value -> "${encode(key)}=${encode(value)}" }
            }
        }.joinToString("&")
    }

    /**
     * 判断查询字符串中是否包含指定 key。
     */
    @JvmStatic
    fun containsKey(queryString: String?, key: String): Boolean {
        return parse(queryString).containsKey(key)
    }

    /**
     * 获取查询字符串中的参数个数（去重 key 数）。
     */
    @JvmStatic
    fun count(queryString: String?): Int {
        return parse(queryString).size
    }

    // ==================== 编码/解码 ====================

    /**
     * URL 编码（UTF-8）。
     *
     * 使用示例：
     * ```kotlin
     * QueryParams.encode("hello world")  // "hello+world"
     * QueryParams.encode("a&b=c")         // "a%26b%3Dc"
     * ```
     */
    @JvmStatic
    fun encode(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }

    /**
     * URL 解码（默认 UTF-8）。
     *
     * 使用示例：
     * ```kotlin
     * QueryParams.decode("hello+world")    // "hello world"
     * QueryParams.decode("a%26b%3Dc")      // "a&b=c"
     * ```
     */
    @JvmStatic
    fun decode(value: String): String {
        return decode(value, Charsets.UTF_8)
    }

    /**
     * URL 解码，使用指定字符集。
     *
     * 适用于非 UTF-8 编码的 percent-encoded 字符串（如 GBK/Shift_JIS）。
     * 解码失败时静默返回原始字符串。
     *
     * @param value   待解码的 percent-encoded 字符串
     * @param charset 解码字符集
     * @return 解码后的字符串
     */
    @JvmStatic
    fun decode(value: String, charset: Charset): String {
        return try {
            URLDecoder.decode(value, charset.name())
        } catch (_: Exception) {
            value
        }
    }

    // ==================== 异步方法 ====================

    @JvmStatic
    fun parseAsync(queryString: String?, onResult: (Map<String, List<String>>) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(parse(queryString)) }
    }

    @JvmStatic
    fun getFirstAsync(queryString: String?, key: String, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getFirst(queryString, key)) }
    }

    @JvmStatic
    fun getParamAsync(url: String?, key: String, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getParam(url, key)) }
    }
}
