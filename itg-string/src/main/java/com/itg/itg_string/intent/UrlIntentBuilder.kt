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
 * URL 查询参数 → Android Bundle / Intent extras 转换工具类。
 *
 * 在 [UrlParser] 和 [QueryParams] 之上提供面向 Android 组件的桥接能力：
 * - 将 URL 查询参数构造为 [Bundle]
 * - 将 URL 查询参数批量写入 [Intent] 的 extras
 *
 * 设计目标：
 * - 组合复用 [UrlParser] + [QueryParams]，不重复造轮子
 * - 所有参数值以 [String] 存储（URL 参数本质是字符串，类型推断留给业务层）
 * - 支持自定义字符集解码，兼容 GBK/Shift_JIS 等非 UTF-8 编码的 URL
 * - 内置双重编码检测，辅助排查上游编码问题
 * - 支持 key 前缀，避免 URL 参数覆盖 Intent 已有 extras
 * - 同步 + 异步双模式，异步通过 [TaskExecutor] 驱动
 * - 所有方法无共享可变状态，天然线程安全
 *
 * 使用示例：
 * ```kotlin
 * // 功能一：创建 Bundle
 * val bundle = UrlIntentBuilder.toBundle("https://api.com/v2/users?active=true&role=admin")
 * println(bundle?.getString("active"))  // "true"
 *
 * // 功能二：写入 Intent（带前缀避免冲突）
 * val intent = Intent(context, DetailActivity::class.java)
 * UrlIntentBuilder.putQueryExtras("https://api.com?active=true", intent, keyPrefix = "url_")
 * // intent extras: "url_active" → "true"
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

    // ==================== 功能一：URL 参数 → Bundle ====================

    /**
     * 从 URL 查询参数创建 [Bundle]（默认 UTF-8 解码）。
     *
     * 解析规则：
     * - 单值参数 `?key=value` → [Bundle.putString]
     * - 多值参数 `?tag=a&tag=b` → [Bundle.putStringArrayList]
     * - 无值参数 `?flag` → 值为空字符串 `""`
     * - 所有值均以 [String] 类型存储
     * - 内置双重编码检测：解码结果中若仍含 %XX 模式，输出 WARNING 日志
     *
     * @param url 待解析的 URL 字符串
     * @return 包含所有查询参数的 Bundle；URL 无查询部分时返回空 Bundle；URL 非法或为 null 时返回 null
     *
     * 使用示例：
     * ```kotlin
     * val b = UrlIntentBuilder.toBundle("https://example.com?q=kotlin&page=1&tag=a&tag=b")
     * b?.getString("q")                // "kotlin"
     * b?.getStringArrayList("tag")     // ["a", "b"]
     * ```
     */
    @JvmStatic
    fun toBundle(url: String?): Bundle? {
        return toBundle(url, Charsets.UTF_8)
    }

    /**
     * 从 URL 查询参数创建 [Bundle]，使用指定字符集解码。
     *
     * 适用于非 UTF-8 编码的 URL（如 GBK/GB2312、Shift_JIS、EUC-KR 等）。
     *
     * @param url     待解析的 URL 字符串
     * @param charset 解码字符集，例如 `Charset.forName("GBK")`
     * @return 包含所有查询参数的 Bundle
     *
     * 使用示例：
     * ```kotlin
     * val b = UrlIntentBuilder.toBundle("https://s.com?q=%D6%D0%CE%C4", Charset.forName("GBK"))
     * b?.getString("q")  // "中文"
     * ```
     */
    @JvmStatic
    fun toBundle(url: String?, charset: Charset): Bundle? {
        if (url.isNullOrBlank()) return null
        val components = UrlParser.parse(url) ?: return null
        val queryString = components.query
        if (queryString.isNullOrBlank()) return Bundle()

        val params = QueryParams.parse(queryString, charset)
        if (params.isEmpty()) return Bundle()

        val bundle = Bundle()
        for ((key, values) in params) {
            // 双重编码检测
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
     * 异步从 URL 查询参数创建 [Bundle]（默认 UTF-8）。
     *
     * @param url      待解析的 URL 字符串
     * @param onResult 回调 (result: Bundle?)
     * @return [Future] 可用于取消
     */
    @JvmStatic
    fun toBundleAsync(url: String?, onResult: (Bundle?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toBundle(url)) }
    }

    /**
     * 异步从 URL 查询参数创建 [Bundle]（指定字符集）。
     */
    @JvmStatic
    fun toBundleAsync(url: String?, charset: Charset, onResult: (Bundle?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(toBundle(url, charset)) }
    }

    // ==================== 功能二：URL 参数 → Intent extras ====================

    /**
     * 将 URL 查询参数批量写入 [Intent] 的 extras 中（无前缀）。
     *
     * 内部调用 [toBundle] 获取 Bundle，再通过 [Intent.putExtras] 批量写入。
     * 若 URL 无查询参数或解析失败，原样返回 intent（不做任何写入）。
     *
     * @param url    待解析的 URL 字符串
     * @param intent 目标 Intent，其 extras 将被填充
     * @return 传入的 Intent（支持链式调用）
     */
    @JvmStatic
    fun putQueryExtras(url: String?, intent: Intent): Intent {
        return putQueryExtras(url, intent, "")
    }

    /**
     * 将 URL 查询参数写入 [Intent] extras，可选 key 前缀。
     *
     * 前缀机制避免 URL 参数覆盖 Intent 中已有的同名 extras。
     * 例如传入 `keyPrefix = "url_"` 时，参数 `active=true` 写入为 `url_active=true`。
     *
     * @param url       待解析的 URL 字符串
     * @param intent    目标 Intent
     * @param keyPrefix 追加到每个参数 key 前的前缀，空字符串表示不加前缀
     * @return 传入的 Intent（支持链式调用）
     *
     * 使用示例：
     * ```kotlin
     * val intent = Intent(context, DetailActivity::class.java)
     * intent.putExtra("id", 12345)
     * // 使用前缀避免 URL 中的 "id" 参数覆盖已有的 id extra
     * UrlIntentBuilder.putQueryExtras("https://api.com?id=deep_link", intent, keyPrefix = "url_")
     * // intent extras: "id" → 12345, "url_id" → "deep_link"
     * ```
     */
    @JvmStatic
    fun putQueryExtras(url: String?, intent: Intent, keyPrefix: String): Intent {
        val bundle = toBundle(url) ?: return intent
        if (bundle.isEmpty) return intent

        if (keyPrefix.isEmpty()) {
            intent.putExtras(bundle)
        } else {
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is ArrayList<*> -> {
                        intent.putStringArrayListExtra(keyPrefix + key, value as ArrayList<String>)
                    }
                    else -> {
                        intent.putExtra(keyPrefix + key, value as String?)
                    }
                }
            }
        }
        return intent
    }

    /**
     * 异步将 URL 查询参数批量写入 [Intent] extras。
     */
    @JvmStatic
    fun putQueryExtrasAsync(url: String?, intent: Intent, onResult: (Intent) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(putQueryExtras(url, intent)) }
    }

    /**
     * 异步将 URL 查询参数写入 [Intent] extras（带前缀）。
     */
    @JvmStatic
    fun putQueryExtrasAsync(
        url: String?, intent: Intent, keyPrefix: String, onResult: (Intent) -> Unit
    ): Future<*> {
        return TaskExecutor.io { onResult(putQueryExtras(url, intent, keyPrefix)) }
    }

    // ==================== 内部工具 ====================

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
