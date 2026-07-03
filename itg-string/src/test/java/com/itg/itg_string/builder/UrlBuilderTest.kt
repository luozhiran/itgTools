package com.itg.itg_string.builder

import com.itg.itg_string.core.UrlParser
import org.junit.Assert.*
import org.junit.Test

/**
 * [UrlBuilder] 单元测试。
 *
 * 覆盖从已有 URL 修改、从空白构造、查询参数操作等场景。
 */
class UrlBuilderTest {

    // ==================== 从已有 URL 构造 ====================

    @Test
    fun fromExistingUrl_modifyScheme() {
        val result = UrlBuilder("https://www.example.com/path")
            .scheme("http")
            .buildString()
        assertEquals("http://www.example.com/path", result)
    }

    @Test
    fun fromExistingUrl_modifyHost() {
        val result = UrlBuilder("https://www.example.com/path")
            .host("api.example.com")
            .buildString()
        assertEquals("https://api.example.com/path", result)
    }

    @Test
    fun fromExistingUrl_addPort() {
        val result = UrlBuilder("https://www.example.com/path")
            .port(8080)
            .buildString()
        assertEquals("https://www.example.com:8080/path", result)
    }

    @Test
    fun fromExistingUrl_removePort() {
        val result = UrlBuilder("https://www.example.com:8080/path")
            .port(-1)
            .buildString()
        assertEquals("https://www.example.com/path", result)
    }

    @Test
    fun fromExistingUrl_modifyPath() {
        val result = UrlBuilder("https://www.example.com/old/path")
            .path("/new/path")
            .buildString()
        assertEquals("https://www.example.com/new/path", result)
    }

    @Test
    fun fromExistingUrl_modifyFragment() {
        val result = UrlBuilder("https://www.example.com/doc#old")
            .fragment("new")
            .buildString()
        assertEquals("https://www.example.com/doc#new", result)
    }

    @Test
    fun fromExistingUrl_removeFragment() {
        val result = UrlBuilder("https://www.example.com/doc#section")
            .fragment(null)
            .buildString()
        assertEquals("https://www.example.com/doc", result)
    }

    @Test
    fun fromExistingUrl_addUserInfo() {
        val result = UrlBuilder("https://www.example.com/path")
            .userInfo("admin:secret")
            .buildString()
        assertEquals("https://admin:secret@www.example.com/path", result)
    }

    @Test
    fun fromExistingUrl_chainMultipleChanges() {
        val result = UrlBuilder("https://www.example.com:8080/path/to?q=old#oldsec")
            .scheme("http")
            .host("api.example.com")
            .port(3000)
            .path("/v2/data")
            .fragment("newsec")
            .buildString()
        assertEquals("http://api.example.com:3000/v2/data?q=old#newsec", result)
    }

    // ==================== 从空白构造 ====================

    @Test
    fun fromScratch_minimal() {
        val result = UrlBuilder()
            .scheme("https")
            .host("example.com")
            .buildString()
        assertEquals("https://example.com", result)
    }

    @Test
    fun fromScratch_full() {
        val result = UrlBuilder()
            .scheme("https")
            .userInfo("user:pass")
            .host("api.example.com")
            .port(8443)
            .path("/v1/users")
            .fragment("summary")
            .buildString()
        assertEquals("https://user:pass@api.example.com:8443/v1/users#summary", result)
    }

    @Test
    fun fromScratch_empty_returnsNull() {
        assertNull(UrlBuilder().buildString())
    }

    // ==================== appendPath ====================

    @Test
    fun appendPath_single() {
        val result = UrlBuilder("https://example.com/api")
            .appendPath("v1")
            .buildString()
        assertEquals("https://example.com/api/v1", result)
    }

    @Test
    fun appendPath_multiple() {
        val result = UrlBuilder("https://example.com/api")
            .appendPath("v1")
            .appendPath("users")
            .appendPath("profile")
            .buildString()
        assertEquals("https://example.com/api/v1/users/profile", result)
    }

    @Test
    fun appendPath_toRoot() {
        val result = UrlBuilder("https://example.com/")
            .appendPath("api")
            .appendPath("v1")
            .buildString()
        assertEquals("https://example.com/api/v1", result)
    }

    @Test
    fun appendPath_withLeadingSlashInSegment() {
        val result = UrlBuilder("https://example.com/api")
            .appendPath("/v1")
            .buildString()
        assertEquals("https://example.com/api/v1", result)
    }

    @Test
    fun appendPath_trailingSlashInBase() {
        val result = UrlBuilder("https://example.com/api/")
            .appendPath("v1")
            .buildString()
        assertEquals("https://example.com/api/v1", result)
    }

    // ==================== 查询参数操作 ====================

    @Test
    fun setQueryParam_single() {
        val result = UrlBuilder("https://example.com/search")
            .setQueryParam("q", "kotlin")
            .setQueryParam("page", "1")
            .buildString()
        assertEquals("https://example.com/search?q=kotlin&page=1", result)
    }

    @Test
    fun addQueryParam_multiValue() {
        val result = UrlBuilder("https://example.com/search")
            .addQueryParam("tag", "android")
            .addQueryParam("tag", "kotlin")
            .buildString()
        assertEquals("https://example.com/search?tag=android&tag=kotlin", result)
    }

    @Test
    fun setQueryParam_overwrites() {
        val result = UrlBuilder("https://example.com?q=old")
            .setQueryParam("q", "new")
            .buildString()
        assertEquals("https://example.com?q=new", result)
    }

    @Test
    fun removeQueryParam() {
        val result = UrlBuilder("https://example.com?q=kotlin&page=1&sort=desc")
            .removeQueryParam("sort")
            .buildString()
        assertEquals("https://example.com?q=kotlin&page=1", result)
    }

    @Test
    fun removeAllQueryParams() {
        val result = UrlBuilder("https://example.com?q=kotlin&page=1")
            .clearQueryParams()
            .buildString()
        assertEquals("https://example.com", result)
    }

    @Test
    fun setQueryParams_batch() {
        val result = UrlBuilder("https://example.com/search")
            .setQueryParams(mapOf("q" to "kotlin", "page" to "2", "size" to "20"))
            .buildString()
        // 包含所有参数
        assertTrue(result!!.contains("q=kotlin"))
        assertTrue(result.contains("page=2"))
        assertTrue(result.contains("size=20"))
    }

    @Test
    fun query_rawString_overwrites() {
        val result = UrlBuilder("https://example.com?q=old&p=1")
            .query("q=new&sort=asc")
            .buildString()
        assertEquals("https://example.com?q=new&sort=asc", result)
    }

    @Test
    fun getQueryParams_returnsCurrentState() {
        val builder = UrlBuilder("https://example.com?q=kotlin&page=1")
        val params = builder.getQueryParams()
        assertEquals(2, params.size)
        assertEquals(listOf("kotlin"), params["q"])
    }

    // ==================== 编码场景 ====================

    @Test
    fun setQueryParam_withSpecialChars() {
        val result = UrlBuilder("https://example.com/search")
            .setQueryParam("q", "hello world")
            .setQueryParam("filter", "a&b=c")
            .buildString()
        assertNotNull(result)
        // 查询参数中的特殊字符应被编码
        val c = UrlParser.parse(result!!)
        assertNotNull(c?.query)
    }

    // ==================== 以 UrlComponents 构造 ====================

    @Test
    fun fromUrlComponents() {
        val original = UrlParser.parse("https://www.example.com:8080/path?q=1#sec")
        val result = UrlBuilder(original)
            .scheme("http")
            .port(-1)
            .buildString()
        assertEquals("http://www.example.com/path?q=1#sec", result)
    }

    // ==================== 空/无效基础 URL ====================

    @Test
    fun fromInvalidUrl_startsEmpty() {
        val builder = UrlBuilder(":::invalid:::")
        // 应等价于空白构造
        assertNull(builder.buildString())
    }

    @Test
    fun fromNullUrl_startsEmpty() {
        val builder = UrlBuilder(null as String?)
        assertNull(builder.buildString())
    }

    // ==================== build() 返回 UrlComponents ====================

    @Test
    fun build_returnsComponents() {
        val components = UrlBuilder()
            .scheme("https")
            .host("example.com")
            .path("/path")
            .setQueryParam("q", "1")
            .fragment("sec")
            .build()
        assertNotNull(components)
        assertEquals("https", components!!.scheme)
        assertEquals("example.com", components.host)
        assertEquals("/path", components.path)
        assertEquals("q=1", components.query)
        assertEquals("sec", components.fragment)
    }

    // ==================== buildUri() ====================

    @Test
    fun buildUri_standard() {
        val uri = UrlBuilder()
            .scheme("https")
            .host("example.com")
            .path("/path")
            .buildUri()
        assertNotNull(uri)
        assertEquals("https", uri!!.scheme)
        assertEquals("example.com", uri.host)
    }

    @Test
    fun buildUri_invalid_returnsNull() {
        assertNull(UrlBuilder().buildUri())
    }

    // ==================== reset ====================

    @Test
    fun reset_clearsAllFields() {
        val builder = UrlBuilder("https://example.com/path?q=1#sec")
        builder.reset()
        assertNull(builder.buildString())
        assertTrue(builder.getQueryParams().isEmpty())
    }

    // ==================== 复杂场景 ====================

    @Test
    fun rebuildComplexUrl() {
        // 解析 → 修改 → 重建 → 再解析，验证往返一致性
        val original = "https://user:pass@www.example.com:8080/path/to/resource?query=param#fragment"
        val rebuilt = UrlBuilder(original).buildString()
        assertEquals(original, rebuilt)
    }

    @Test
    fun modifyQueryAndFragment() {
        val result = UrlBuilder("https://example.com/path?old=value#oldsec")
            .removeQueryParam("old")
            .setQueryParam("new", "data")
            .fragment("newsec")
            .buildString()
        assertEquals("https://example.com/path?new=data#newsec", result)
    }
}
