package com.itg.itg_string.core

import org.junit.Assert.*
import org.junit.Test

/**
 * [UrlParser] 单元测试。
 *
 * 覆盖全量解析、单独获取、边界条件、非法输入等场景。
 */
class UrlParserTest {

    // ==================== parse：全量解析 ====================

    @Test
    fun parse_fullUrlWithAllComponents() {
        val url = "https://user:pass@www.example.com:8080/path/to/resource?query=param#fragment"
        val c = UrlParser.parse(url)

        assertNotNull(c)
        assertEquals("https", c!!.scheme)
        assertEquals("user:pass", c.userInfo)
        assertEquals("www.example.com", c.host)
        assertEquals(8080, c.port)
        assertEquals("user:pass@www.example.com:8080", c.authority)
        assertEquals("/path/to/resource", c.path)
        assertEquals("query=param", c.query)
        assertEquals("fragment", c.fragment)
        assertEquals(url, c.rawUrl)
        assertTrue(c.isAbsolute)
        assertFalse(c.isOpaque)
    }

    @Test
    fun parse_simpleUrl() {
        val c = UrlParser.parse("https://www.example.com/path")
        assertNotNull(c)
        assertEquals("https", c!!.scheme)
        assertEquals("www.example.com", c.host)
        assertEquals("/path", c.path)
        assertEquals(-1, c.port)
        assertNull(c.query)
        assertNull(c.fragment)
        assertNull(c.userInfo)
    }

    @Test
    fun parse_urlWithPort() {
        val c = UrlParser.parse("http://localhost:3000/api")
        assertNotNull(c)
        assertEquals("http", c!!.scheme)
        assertEquals("localhost", c.host)
        assertEquals(3000, c.port)
        assertEquals("/api", c.path)
    }

    @Test
    fun parse_urlWithQueryOnly() {
        val c = UrlParser.parse("https://example.com/search?q=kotlin&page=1&sort=desc")
        assertNotNull(c)
        assertEquals("q=kotlin&page=1&sort=desc", c!!.query)
        assertNull(c.fragment)
    }

    @Test
    fun parse_urlWithFragmentOnly() {
        val c = UrlParser.parse("https://example.com/doc#introduction")
        assertNotNull(c)
        assertEquals("introduction", c!!.fragment)
        assertNull(c.query)
    }

    @Test
    fun parse_urlWithEncodedCharacters() {
        val c = UrlParser.parse("https://example.com/path%20with%20spaces?q=hello+world")
        assertNotNull(c)
        assertEquals("/path%20with%20spaces", c!!.path)
        assertEquals("q=hello+world", c.query)
    }

    @Test
    fun parse_urlWithIPv6() {
        val c = UrlParser.parse("https://[::1]:8080/path")
        assertNotNull(c)
        // java.net.URI 对 IPv6 的处理：host 会去掉方括号
        assertNotNull(c!!.host)
        assertEquals(8080, c.port)
    }

    @Test
    fun parse_ftpUrl() {
        val c = UrlParser.parse("ftp://files.example.com/pub/data.zip")
        assertNotNull(c)
        assertEquals("ftp", c!!.scheme)
        assertEquals("files.example.com", c.host)
        assertEquals("/pub/data.zip", c.path)
    }

    @Test
    fun parse_httpNoPath() {
        val c = UrlParser.parse("https://www.example.com")
        assertNotNull(c)
        assertEquals("https", c!!.scheme)
        assertEquals("www.example.com", c.host)
        assertTrue(c.path.isNullOrEmpty())
    }

    // ==================== parse：边界/异常条件 ====================

    @Test
    fun parse_null_returnsNull() {
        assertNull(UrlParser.parse(null))
    }

    @Test
    fun parse_empty_returnsNull() {
        assertNull(UrlParser.parse(""))
    }

    @Test
    fun parse_blank_returnsNull() {
        assertNull(UrlParser.parse("   "))
    }

    @Test
    fun parse_invalidUrl_returnsNull() {
        assertNull(UrlParser.parse("not a valid url at all"))
    }

    @Test
    fun parse_mailtoScheme() {
        val c = UrlParser.parse("mailto:user@example.com")
        assertNotNull(c)
        assertEquals("mailto", c!!.scheme)
        assertTrue(c.isOpaque)
    }

    // ==================== parseOrThrow ====================

    @Test
    fun parseOrThrow_validUrl_returnsComponents() {
        val c = UrlParser.parseOrThrow("https://example.com")
        assertEquals("example.com", c.host)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseOrThrow_invalidUrl_throws() {
        UrlParser.parseOrThrow(":::invalid:::")
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseOrThrow_null_throws() {
        UrlParser.parseOrThrow(null)
    }

    // ==================== 单独获取：getScheme ====================

    @Test
    fun getScheme_https() {
        assertEquals("https", UrlParser.getScheme("https://www.example.com"))
    }

    @Test
    fun getScheme_ftp() {
        assertEquals("ftp", UrlParser.getScheme("ftp://files.example.com"))
    }

    @Test
    fun getScheme_uppercasedNormalized() {
        assertEquals("https", UrlParser.getScheme("HTTPS://www.example.com"))
    }

    @Test
    fun getScheme_invalid_returnsNull() {
        assertNull(UrlParser.getScheme("not a url"))
    }

    // ==================== 单独获取：getHost ====================

    @Test
    fun getHost_standard() {
        assertEquals("www.example.com", UrlParser.getHost("https://www.example.com/path"))
    }

    @Test
    fun getHost_localhost() {
        assertEquals("localhost", UrlParser.getHost("http://localhost:8080/api"))
    }

    @Test
    fun getHost_ipAddress() {
        assertEquals("192.168.1.1", UrlParser.getHost("https://192.168.1.1/admin"))
    }

    @Test
    fun getHost_invalid_returnsNull() {
        assertNull(UrlParser.getHost("invalid"))
    }

    // ==================== 单独获取：getPort ====================

    @Test
    fun getPort_explicit() {
        assertEquals(8080, UrlParser.getPort("https://example.com:8080/path"))
    }

    @Test
    fun getPort_default_returnsMinusOne() {
        assertEquals(-1, UrlParser.getPort("https://example.com/path"))
    }

    @Test
    fun getPort_httpDefault80() {
        assertEquals(80, UrlParser.getPort("http://example.com:80/path"))
    }

    @Test
    fun getPort_invalid_returnsMinusOne() {
        assertEquals(-1, UrlParser.getPort("not a url"))
    }

    // ==================== 单独获取：getPath ====================

    @Test
    fun getPath_standard() {
        assertEquals("/path/to/resource", UrlParser.getPath("https://example.com/path/to/resource"))
    }

    @Test
    fun getPath_root() {
        assertEquals("/", UrlParser.getPath("https://example.com/"))
    }

    @Test
    fun getPath_empty() {
        val path = UrlParser.getPath("https://example.com")
        assertTrue(path.isNullOrEmpty())
    }

    @Test
    fun getPath_withQueryAndFragment() {
        assertEquals("/search", UrlParser.getPath("https://example.com/search?q=1#top"))
    }

    // ==================== 单独获取：getQuery ====================

    @Test
    fun getQuery_standard() {
        assertEquals("q=kotlin&page=1", UrlParser.getQuery("https://example.com?q=kotlin&page=1"))
    }

    @Test
    fun getQuery_singleParam() {
        assertEquals("query=param", UrlParser.getQuery("https://example.com?query=param"))
    }

    @Test
    fun getQuery_none_returnsNull() {
        assertNull(UrlParser.getQuery("https://example.com/path"))
    }

    @Test
    fun getQuery_emptyValue() {
        assertEquals("key=", UrlParser.getQuery("https://example.com?key="))
    }

    // ==================== 单独获取：getFragment ====================

    @Test
    fun getFragment_standard() {
        assertEquals("fragment", UrlParser.getFragment("https://example.com#fragment"))
    }

    @Test
    fun getFragment_withPath() {
        assertEquals("section1", UrlParser.getFragment("https://example.com/doc#section1"))
    }

    @Test
    fun getFragment_none_returnsNull() {
        assertNull(UrlParser.getFragment("https://example.com/path"))
    }

    @Test
    fun getFragment_empty() {
        assertEquals("", UrlParser.getFragment("https://example.com#"))
    }

    // ==================== 单独获取：getUserInfo ====================

    @Test
    fun getUserInfo_userAndPass() {
        assertEquals("admin:secret", UrlParser.getUserInfo("https://admin:secret@example.com"))
    }

    @Test
    fun getUserInfo_userOnly() {
        assertEquals("user", UrlParser.getUserInfo("https://user@example.com"))
    }

    @Test
    fun getUserInfo_none_returnsNull() {
        assertNull(UrlParser.getUserInfo("https://example.com"))
    }

    // ==================== 单独获取：getAuthority ====================

    @Test
    fun getAuthority_withUserInfo() {
        assertEquals(
            "user:pass@www.example.com:8080",
            UrlParser.getAuthority("https://user:pass@www.example.com:8080/path")
        )
    }

    @Test
    fun getAuthority_hostOnly() {
        assertEquals("www.example.com", UrlParser.getAuthority("https://www.example.com/path"))
    }

    @Test
    fun getAuthority_hostAndPort() {
        assertEquals(
            "www.example.com:8443",
            UrlParser.getAuthority("https://www.example.com:8443/path")
        )
    }

    // ==================== 便捷方法 ====================

    @Test
    fun isValidUrl_valid() {
        assertTrue(UrlParser.isValidUrl("https://example.com"))
    }

    @Test
    fun isValidUrl_invalid() {
        assertFalse(UrlParser.isValidUrl("not a url"))
        assertFalse(UrlParser.isValidUrl(null))
        assertFalse(UrlParser.isValidUrl(""))
    }

    @Test
    fun isHttps_true() {
        assertTrue(UrlParser.isHttps("https://example.com"))
    }

    @Test
    fun isHttps_false() {
        assertFalse(UrlParser.isHttps("http://example.com"))
    }

    @Test
    fun isHttps_invalid() {
        assertFalse(UrlParser.isHttps("invalid"))
    }

    @Test
    fun getBase_standard() {
        assertEquals("https://www.example.com", UrlParser.getBase("https://www.example.com:8080/path?q=1"))
    }

    @Test
    fun getBase_noScheme_returnsNull() {
        assertNull(UrlParser.getBase("//example.com/path"))
    }

    // ==================== UrlComponents 派生属性 ====================

    @Test
    fun components_schemeHostPath() {
        val c = UrlParser.parse("https://www.example.com/path/to/resource?q=1#sec")
        assertEquals("https://www.example.com/path/to/resource", c!!.schemeHostPath)
    }

    @Test
    fun components_hostPort_withPort() {
        val c = UrlParser.parse("https://www.example.com:8080/path")
        assertEquals("www.example.com:8080", c!!.hostPort)
    }

    @Test
    fun components_hostPort_withoutPort() {
        val c = UrlParser.parse("https://www.example.com/path")
        assertEquals("www.example.com", c!!.hostPort)
    }

    @Test
    fun components_isHttps() {
        assertTrue(UrlParser.parse("https://example.com")!!.isHttps)
        assertFalse(UrlParser.parse("http://example.com")!!.isHttps)
    }

    @Test
    fun components_isDefaultPort() {
        assertTrue(UrlParser.parse("https://example.com")!!.isDefaultPort)       // 443 是默认
        assertTrue(UrlParser.parse("http://example.com")!!.isDefaultPort)        // 80 是默认
        assertFalse(UrlParser.parse("http://example.com:8080")!!.isDefaultPort)  // 8080 不是
        assertTrue(UrlParser.parse("https://example.com:443")!!.isDefaultPort)   // 显式 443 = 默认
    }

    @Test
    fun components_toString() {
        val c = UrlParser.parse("https://example.com/path?q=1#sec")
        assertEquals("https://example.com/path?q=1#sec", c.toString())
    }

    // ==================== 非 ASCII 字符 sanitize ====================

    @Test
    fun parse_chineseInQuery() {
        val c = UrlParser.parse("https://example.com/search?q=中文")
        assertNotNull(c)
        // query 被 sanitize 为 percent-encoded 形式，可正常解析
        assertNotNull(c!!.query)
        assertTrue(c.query!!.contains("%"))
    }

    @Test
    fun parse_chineseInPath() {
        val c = UrlParser.parse("https://example.com/路径/资源")
        assertNotNull(c)
        assertEquals("https", c!!.scheme)
        assertEquals("example.com", c.host)
        assertNotNull(c.path)
    }

    @Test
    fun parse_emojiInQuery() {
        val c = UrlParser.parse("https://example.com?emoji=😀")
        assertNotNull(c)
        assertNotNull(c!!.query)
    }

    @Test
    fun parse_mixedAsciiAndNonAscii() {
        val c = UrlParser.parse("https://example.com/api?name=José&city=München")
        assertNotNull(c)
        assertEquals("https", c!!.scheme)
        assertEquals("example.com", c.host)
        assertEquals("/api", c.path)
        assertNotNull(c.query)
    }

    @Test
    fun parse_japaneseInQuery() {
        val c = UrlParser.parse("https://example.com?q=日本語")
        assertNotNull(c)
        assertNotNull(c!!.query)
    }

    @Test
    fun parse_alreadyEncodedUnchanged() {
        // 已经是正确的 percent-encoded 形式，sanitize 不应该二次编码
        val c = UrlParser.parse("https://example.com?q=%E4%B8%AD%E6%96%87")
        assertNotNull(c)
        assertEquals("q=%E4%B8%AD%E6%96%87", c!!.query)
    }

    @Test
    fun parse_pureAsciiUnchanged() {
        val c = UrlParser.parse("https://example.com/path?q=kotlin&page=1#sec")
        assertNotNull(c)
        assertEquals("/path", c!!.path)
        assertEquals("q=kotlin&page=1", c.query)
        assertEquals("sec", c.fragment)
    }
}
