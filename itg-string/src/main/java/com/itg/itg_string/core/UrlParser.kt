package com.itg.itg_string.core

import com.itg.itg_thread_pools.executor.TaskExecutor
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.Charset
import java.util.concurrent.Future

/**
 * URL 解析工具类。
 *
 * 基于 [java.net.URI] 实现，严格遵循 RFC 3986，无需 Android Context，
 * 可在 JVM 单测中直接运行。
 *
 * 设计目标：
 * - 提供全量解析 [parse] 和单独获取 [getScheme]/[getHost]/[getPath] 等两种调用方式
 * - 解析失败返回 null，不抛异常（与项目其他模块一致）
 * - 同步 + 异步双模式，异步通过 [TaskExecutor] 驱动
 * - 所有方法无共享可变状态，天然线程安全
 *
 * 使用示例：
 * ```kotlin
 * // 全量解析
 * val components = UrlParser.parse("https://www.example.com:8080/path?q=1#sec")
 * println(components?.host)   // "www.example.com"
 * println(components?.port)   // 8080
 *
 * // 单独获取
 * val host = UrlParser.getHost("https://www.example.com/path")
 * val scheme = UrlParser.getScheme("https://www.example.com/path")
 * ```
 *
 * @author ITG Team
 * @since 1.0.0
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object UrlParser {

    // ==================== 全量解析 ====================

    /**
     * 解析 URL 字符串，返回 [UrlComponents]。
     *
     * 解析失败（格式非法、空字符串等）返回 null。
     *
     * @param url 待解析的 URL 字符串
     * @return 解析结果，失败返回 null
     *
     * 使用示例：
     * ```kotlin
     * val c = UrlParser.parse("https://user:pass@www.example.com:8080/path?query=param#fragment")
     * println(c?.scheme)     // "https"
     * println(c?.userInfo)   // "user:pass"
     * println(c?.host)       // "www.example.com"
     * println(c?.port)       // 8080
     * println(c?.path)       // "/path"
     * println(c?.query)      // "query=param"
     * println(c?.fragment)   // "fragment"
     * ```
     */
    @JvmStatic
    fun parse(url: String?): UrlComponents? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = URI(sanitizeNonAscii(url))
            UrlComponents(
                rawUrl = url,
                scheme = uri.scheme?.lowercase(),
                userInfo = uri.rawUserInfo,
                host = uri.host?.lowercase(),
                port = uri.port,
                authority = uri.rawAuthority,
                path = uri.rawPath,
                query = uri.rawQuery,
                fragment = uri.rawFragment,
                isAbsolute = uri.isAbsolute,
                isOpaque = uri.isOpaque
            )
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * 解析 URL 字符串，失败时抛出 [IllegalArgumentException]。
     *
     * @param url 待解析的 URL 字符串
     * @return 解析结果
     * @throws IllegalArgumentException URL 格式非法时抛出
     */
    @JvmStatic
    fun parseOrThrow(url: String?): UrlComponents {
        return parse(url) ?: throw IllegalArgumentException("Invalid URL: $url")
    }

    // ==================== 全量解析（异步） ====================

    /**
     * 异步解析 URL。
     *
     * @param url      待解析的 URL 字符串
     * @param onResult 回调 (result: UrlComponents?)
     * @return [Future] 可用于取消
     */
    @JvmStatic
    fun parseAsync(url: String?, onResult: (UrlComponents?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(parse(url)) }
    }

    // ==================== 单独获取：scheme ====================

    /**
     * 获取 URL 的协议（scheme）。
     *
     * @param url 待解析的 URL 字符串
     * @return 协议字符串（小写），如 "https"；失败返回 null
     *
     * 使用示例：
     * ```kotlin
     * UrlParser.getScheme("https://www.example.com")  // "https"
     * UrlParser.getScheme("ftp://files.example.com")   // "ftp"
     * UrlParser.getScheme("invalid")                    // null
     * ```
     */
    @JvmStatic
    fun getScheme(url: String?): String? = parse(url)?.scheme

    /**
     * 异步获取 scheme。
     */
    @JvmStatic
    fun getSchemeAsync(url: String?, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getScheme(url)) }
    }

    // ==================== 单独获取：host ====================

    /**
     * 获取 URL 的主机名（host）。
     *
     * @param url 待解析的 URL 字符串
     * @return 主机名（小写），如 "www.example.com"；失败返回 null
     *
     * 使用示例：
     * ```kotlin
     * UrlParser.getHost("https://www.example.com/path")     // "www.example.com"
     * UrlParser.getHost("https://192.168.1.1:8080/admin")   // "192.168.1.1"
     * ```
     */
    @JvmStatic
    fun getHost(url: String?): String? = parse(url)?.host

    /**
     * 异步获取 host。
     */
    @JvmStatic
    fun getHostAsync(url: String?, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getHost(url)) }
    }

    // ==================== 单独获取：port ====================

    /**
     * 获取 URL 的端口号。
     *
     * @param url 待解析的 URL 字符串
     * @return 端口号，未显式指定时返回 -1；失败返回 -1
     *
     * 使用示例：
     * ```kotlin
     * UrlParser.getPort("https://www.example.com:8080/path")  // 8080
     * UrlParser.getPort("https://www.example.com/path")       // -1
     * ```
     */
    @JvmStatic
    fun getPort(url: String?): Int = parse(url)?.port ?: -1

    /**
     * 异步获取 port。
     */
    @JvmStatic
    fun getPortAsync(url: String?, onResult: (Int) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getPort(url)) }
    }

    // ==================== 单独获取：path ====================

    /**
     * 获取 URL 的路径部分。
     *
     * @param url 待解析的 URL 字符串
     * @return 路径字符串（原始未解码），如 "/path/to/resource"；失败返回 null
     *
     * 使用示例：
     * ```kotlin
     * UrlParser.getPath("https://www.example.com/path/to/resource?q=1")  // "/path/to/resource"
     * UrlParser.getPath("https://www.example.com")                        // ""（空路径）
     * ```
     */
    @JvmStatic
    fun getPath(url: String?): String? = parse(url)?.path

    /**
     * 异步获取 path。
     */
    @JvmStatic
    fun getPathAsync(url: String?, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getPath(url)) }
    }

    // ==================== 单独获取：query ====================

    /**
     * 获取 URL 的查询字符串（? 之后、# 之前的部分，原始未解码）。
     *
     * @param url 待解析的 URL 字符串
     * @return 查询字符串，如 "query=param&foo=bar"；无查询部分返回 null
     *
     * 使用示例：
     * ```kotlin
     * UrlParser.getQuery("https://www.example.com/search?q=kotlin&page=1")  // "q=kotlin&page=1"
     * UrlParser.getQuery("https://www.example.com/about")                    // null
     * ```
     */
    @JvmStatic
    fun getQuery(url: String?): String? = parse(url)?.query

    /**
     * 异步获取 query。
     */
    @JvmStatic
    fun getQueryAsync(url: String?, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getQuery(url)) }
    }

    // ==================== 单独获取：fragment ====================

    /**
     * 获取 URL 的片段标识符（# 之后的部分）。
     *
     * @param url 待解析的 URL 字符串
     * @return 片段字符串，如 "section1"；无片段返回 null
     *
     * 使用示例：
     * ```kotlin
     * UrlParser.getFragment("https://www.example.com/doc#introduction")  // "introduction"
     * UrlParser.getFragment("https://www.example.com/doc")               // null
     * ```
     */
    @JvmStatic
    fun getFragment(url: String?): String? = parse(url)?.fragment

    /**
     * 异步获取 fragment。
     */
    @JvmStatic
    fun getFragmentAsync(url: String?, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getFragment(url)) }
    }

    // ==================== 单独获取：userInfo ====================

    /**
     * 获取 URL 的用户信息部分（"user:pass" 格式）。
     *
     * @param url 待解析的 URL 字符串
     * @return 用户信息字符串，如 "user:pass"；不存在返回 null
     *
     * 使用示例：
     * ```kotlin
     * UrlParser.getUserInfo("https://admin:secret@www.example.com")  // "admin:secret"
     * UrlParser.getUserInfo("https://www.example.com")               // null
     * ```
     */
    @JvmStatic
    fun getUserInfo(url: String?): String? = parse(url)?.userInfo

    /**
     * 异步获取 userInfo。
     */
    @JvmStatic
    fun getUserInfoAsync(url: String?, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getUserInfo(url)) }
    }

    // ==================== 单独获取：authority ====================

    /**
     * 获取 URL 的授权部分（userInfo@host:port）。
     *
     * @param url 待解析的 URL 字符串
     * @return 授权部分字符串，如 "user:pass@www.example.com:8080"；不存在返回 null
     *
     * 使用示例：
     * ```kotlin
     * UrlParser.getAuthority("https://user:pass@www.example.com:8080/path")
     * // "user:pass@www.example.com:8080"
     * ```
     */
    @JvmStatic
    fun getAuthority(url: String?): String? = parse(url)?.authority

    /**
     * 异步获取 authority。
     */
    @JvmStatic
    fun getAuthorityAsync(url: String?, onResult: (String?) -> Unit): Future<*> {
        return TaskExecutor.io { onResult(getAuthority(url)) }
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断字符串是否为合法 URL。
     *
     * @param url 待判断的字符串
     * @return true 表示可被成功解析
     */
    @JvmStatic
    fun isValidUrl(url: String?): Boolean = parse(url) != null

    /**
     * 判断是否为 HTTPS URL。
     */
    @JvmStatic
    fun isHttps(url: String?): Boolean = parse(url)?.isHttps == true

    /**
     * 获取 URL 的基础部分（scheme + "://" + host）。
     *
     * 例如对 "https://www.example.com:8080/path?q=1" 返回 "https://www.example.com"。
     */
    @JvmStatic
    fun getBase(url: String?): String? {
        val c = parse(url) ?: return null
        if (c.scheme == null || c.host == null) return null
        return "${c.scheme}://${c.host}"
    }

    // ==================== 内部工具 ====================

    /**
     * 对 URL 中非 ASCII 字符（U+0080 及以上）做 UTF-8 percent-encoding，
     * 确保传入 [java.net.URI] 的字符串严格符合 RFC 3986。
     *
     * 已正确编码的序列（如 %20）不受影响，因为 '%'、'2'、'0' 均为 ASCII 字符。
     * 正确处理 BMP 外字符（如 emoji），通过 [String.codePointAt] 按 Unicode
     * code point 粒度编码，避免错误拆分 surrogate pair。
     *
     * 示例：
     * - `"https://a.com?q=中文"` → `"https://a.com?q=%E4%B8%AD%E6%96%87"`
     * - `"https://a.com/path/café"` → `"https://a.com/path/caf%C3%A9"`
     * - `"https://a.com?emoji=😀"` → `"https://a.com?emoji=%F0%9F%98%80"`
     */
    private fun sanitizeNonAscii(url: String): String {
        var hasNonAscii = false
        var i = 0
        while (i < url.length) {
            if (url.codePointAt(i) > 127) {
                hasNonAscii = true
                break
            }
            i += Character.charCount(url.codePointAt(i))
        }
        if (!hasNonAscii) return url

        val sb = StringBuilder(url.length + 16)
        i = 0
        while (i < url.length) {
            val codePoint = url.codePointAt(i)
            if (codePoint > 127) {
                val chars = Character.toChars(codePoint)
                for (byte in String(chars).toByteArray(Charsets.UTF_8)) {
                    sb.append('%')
                    sb.append(byte.toUByte().toString(16).uppercase().padStart(2, '0'))
                }
            } else {
                sb.append(url[i])
            }
            i += Character.charCount(codePoint)
        }
        return sb.toString()
    }
}
