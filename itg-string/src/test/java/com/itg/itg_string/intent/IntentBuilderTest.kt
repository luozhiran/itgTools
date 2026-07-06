package com.itg.itg_string.intent

import android.content.Intent
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.Charset

/**
 * [IntentBuilder] 单元测试。
 *
 * 覆盖：数据源接入（URL / Map / Bundle / put）、前缀隔离、错误处理、构建输出、生命周期。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentBuilderTest {

    // ==================================================================
    // 基础链式调用
    // ==================================================================

    @Test
    fun basicChain_fromUrlAndMap() {
        val intent = IntentBuilder()
            .fromUrl("https://api.com?source=deeplink", prefix = "url_")
            .fromMap(mapOf("userId" to 12345, "isVip" to true), prefix = "cfg_")
            .action(Intent.ACTION_VIEW)
            .build()

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("deeplink", intent.getStringExtra("url_source"))
        assertEquals(12345, intent.getIntExtra("cfg_userId", -1))
        assertTrue(intent.getBooleanExtra("cfg_isVip", false))
    }

    @Test
    fun basicChain_singleDataSource() {
        val intent = IntentBuilder()
            .fromUrl("https://api.com?q=kotlin&page=1")
            .action(Intent.ACTION_SEARCH)
            .build()

        assertEquals(Intent.ACTION_SEARCH, intent.action)
        assertEquals("kotlin", intent.getStringExtra("q"))
        assertEquals("1", intent.getStringExtra("page"))
    }

    @Test
    fun basicChain_noDataSource_returnsIntentWithActionOnly() {
        val intent = IntentBuilder()
            .action(Intent.ACTION_MAIN)
            .build()

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertNull(intent.extras)
    }

    @Test
    fun basicChain_noAction_returnsIntentWithoutAction() {
        val intent = IntentBuilder()
            .fromMap(mapOf("key" to "value"))
            .build()

        assertNull(intent.action)
        assertEquals("value", intent.getStringExtra("key"))
    }

    // ==================================================================
    // 数据源：URL
    // ==================================================================

    @Test
    fun fromUrl_standardParams() {
        val intent = IntentBuilder()
            .fromUrl("https://api.com?active=true&role=admin")
            .build()

        assertEquals("true", intent.getStringExtra("active"))
        assertEquals("admin", intent.getStringExtra("role"))
    }

    @Test
    fun fromUrl_multiValueParams() {
        val intent = IntentBuilder()
            .fromUrl("https://api.com?tag=a&tag=b&tag=c&q=kotlin")
            .build()

        assertEquals("kotlin", intent.getStringExtra("q"))
        assertEquals(listOf("a", "b", "c"), intent.getStringArrayListExtra("tag"))
    }

    @Test
    fun fromUrl_null_skippedSilently_noError() {
        val builder = IntentBuilder()
            .fromUrl(null)
            .fromMap(mapOf("fallback" to "used"))

        assertFalse(builder.hasErrors())
        assertEquals(1, builder.sourceCount())

        val intent = builder.build()
        assertEquals("used", intent.getStringExtra("fallback"))
    }

    @Test
    fun fromUrl_empty_skippedSilently() {
        val builder = IntentBuilder()
            .fromUrl("")
            .fromUrl("   ")
            .put("key", "value")

        assertFalse(builder.hasErrors())
        assertEquals(1, builder.sourceCount())
        assertEquals("value", builder.build().getStringExtra("key"))
    }

    @Test
    fun fromUrl_invalid_registersError() {
        val builder = IntentBuilder()
            .fromUrl("not a valid url")
            .fromUrl("https://valid.com?q=ok")

        assertTrue(builder.hasErrors())
        assertEquals(1, builder.errors().size)
        assertEquals("fromUrl", builder.errors()[0].source)
        assertTrue(builder.errors()[0].message.contains("解析"))

        // 有效的 URL 依然正常解析
        val intent = builder.build()
        assertEquals("ok", intent.getStringExtra("q"))
    }

    @Test
    fun fromUrl_multipleUrls_withPrefixes() {
        val intent = IntentBuilder()
            .fromUrl("https://a.com?x=1", prefix = "a_")
            .fromUrl("https://b.com?x=2", prefix = "b_")
            .build()

        assertEquals("1", intent.getStringExtra("a_x"))
        assertEquals("2", intent.getStringExtra("b_x"))
    }

    @Test
    fun fromUrl_withCharset_gbk() {
        val intent = IntentBuilder()
            .fromUrl("https://example.com?q=%D6%D0%CE%C4", Charset.forName("GBK"), prefix = "gbk_")
            .build()

        assertEquals("中文", intent.getStringExtra("gbk_q"))
    }

    @Test
    fun fromUrl_noQuery_parsedButEmpty() {
        val builder = IntentBuilder()
            .fromUrl("https://example.com/path")
            .put("extra", "added")

        // URL 解析成功但 query 为空，返回空 Bundle，不会注册为数据源
        // 空 Bundle 通过 bundle.isEmpty 被跳过
        assertFalse(builder.hasErrors())
        val intent = builder.build()
        assertEquals("added", intent.getStringExtra("extra"))
    }

    // ==================================================================
    // 数据源：Map<String, String>
    // ==================================================================

    @Test
    fun fromMap_stringMap_basic() {
        val intent = IntentBuilder()
            .fromMap(mapOf("env" to "production", "timeout" to "30"), prefix = "srv_")
            .build()

        assertEquals("production", intent.getStringExtra("srv_env"))
        assertEquals("30", intent.getStringExtra("srv_timeout"))
    }

    @Test
    fun fromMap_stringMap_empty_skipped() {
        val builder = IntentBuilder()
            .fromMap(emptyMap<String, String>())
            .put("key", "value")

        assertEquals(1, builder.sourceCount())
    }

    @Test
    fun fromMap_stringMap_noPrefix() {
        val intent = IntentBuilder()
            .fromMap(mapOf("key1" to "val1", "key2" to "val2"))
            .build()

        assertEquals("val1", intent.getStringExtra("key1"))
        assertEquals("val2", intent.getStringExtra("key2"))
    }

    // ==================================================================
    // 数据源：Map<String, List<String>>
    // ==================================================================

    @Test
    fun fromMap_listMap_multiValue() {
        val intent = IntentBuilder()
            .fromMap(linkedMapOf("tags" to listOf("kotlin", "java", "rust")), prefix = "meta_")
            .build()

        assertEquals(listOf("kotlin", "java", "rust"), intent.getStringArrayListExtra("meta_tags"))
    }

    @Test
    fun fromMap_listMap_singleValueBecomesString() {
        val intent = IntentBuilder()
            .fromMap(linkedMapOf("q" to listOf("search_term")))
            .build()

        assertEquals("search_term", intent.getStringExtra("q"))
    }

    @Test
    fun fromMap_listMap_empty_skipped() {
        val builder = IntentBuilder()
            .fromMap(emptyMap<String, List<String>>())
            .put("present", "yes")

        assertEquals(1, builder.sourceCount())
    }

    // ==================================================================
    // 数据源：Map<String, *>（异构）
    // ==================================================================

    @Test
    fun fromMap_wildcardMap_variousTypes() {
        val intent = IntentBuilder()
            .fromMap(
                mapOf<String, Any?>(
                    "id" to 42,
                    "active" to true,
                    "score" to 4.5,
                    "name" to "test",
                    "tags" to listOf("a", "b"),
                    "skip" to null
                ),
                prefix = "cfg_"
            )
            .build()

        assertEquals(42, intent.getIntExtra("cfg_id", -1))
        assertTrue(intent.getBooleanExtra("cfg_active", false))
        assertEquals(4.5, intent.getDoubleExtra("cfg_score", 0.0), 0.0)
        assertEquals("test", intent.getStringExtra("cfg_name"))
        assertEquals(listOf("a", "b"), intent.getStringArrayListExtra("cfg_tags"))
        assertFalse(intent.extras?.containsKey("cfg_skip") ?: true)
    }

    @Test
    fun fromMap_wildcardMap_empty_skipped() {
        val builder = IntentBuilder()
            .fromMap(emptyMap<String, Any>())
            .put("k", "v")

        assertEquals(1, builder.sourceCount())
    }

    // ==================================================================
    // 数据源：Bundle
    // ==================================================================

    @Test
    fun fromBundle_basic() {
        val bundle = Bundle().apply {
            putString("name", "John")
            putInt("age", 30)
            putBoolean("subscribed", true)
        }

        val intent = IntentBuilder()
            .fromBundle(bundle)
            .build()

        assertEquals("John", intent.getStringExtra("name"))
        assertEquals(30, intent.getIntExtra("age", -1))
        assertTrue(intent.getBooleanExtra("subscribed", false))
    }

    @Test
    fun fromBundle_withPrefix() {
        val bundle = Bundle().apply { putString("token", "abc123") }

        val intent = IntentBuilder()
            .fromBundle(bundle, prefix = "auth_")
            .build()

        assertEquals("abc123", intent.getStringExtra("auth_token"))
    }

    @Test
    fun fromBundle_null_skipped() {
        val builder = IntentBuilder()
            .fromBundle(null)
            .put("k", "v")

        assertEquals(1, builder.sourceCount())
        assertFalse(builder.hasErrors())
    }

    @Test
    fun fromBundle_empty_skipped() {
        val builder = IntentBuilder()
            .fromBundle(Bundle())
            .put("k", "v")

        assertEquals(1, builder.sourceCount())
    }

    @Test
    fun fromBundle_defensiveCopy_modifyOriginalDoesNotAffectBuilder() {
        val original = Bundle().apply {
            putString("key", "original_value")
        }

        val builder = IntentBuilder()
            .fromBundle(original)

        // 调用方修改原 Bundle
        original.putString("key", "modified")
        original.putString("new_key", "should_not_appear")

        val bundle = builder.buildBundle()
        assertEquals("original_value", bundle.getString("key"))
        assertFalse(bundle.containsKey("new_key"))
        assertEquals(1, bundle.size())
    }

    // ==================================================================
    // 便捷方法：put()
    // ==================================================================

    @Test
    fun put_string() {
        val intent = IntentBuilder().put("key", "value").build()
        assertEquals("value", intent.getStringExtra("key"))
    }

    @Test
    fun put_int() {
        val intent = IntentBuilder().put("count", 42).build()
        assertEquals(42, intent.getIntExtra("count", -1))
    }

    @Test
    fun put_long() {
        val intent = IntentBuilder().put("ts", 1699000000000L).build()
        assertEquals(1699000000000L, intent.getLongExtra("ts", -1L))
    }

    @Test
    fun put_boolean() {
        val intent = IntentBuilder().put("enabled", true).put("debug", false).build()
        assertTrue(intent.getBooleanExtra("enabled", false))
        assertFalse(intent.getBooleanExtra("debug", true))
    }

    @Test
    fun put_float() {
        val intent = IntentBuilder().put("ratio", 0.75f).build()
        assertEquals(0.75f, intent.getFloatExtra("ratio", 0f))
    }

    @Test
    fun put_double() {
        val intent = IntentBuilder().put("score", 3.14).build()
        assertEquals(3.14, intent.getDoubleExtra("score", 0.0), 0.0)
    }

    @Test
    fun put_chained_multipleTypes() {
        val intent = IntentBuilder()
            .put("name", "Alice")
            .put("age", 25)
            .put("vip", true)
            .build()

        assertEquals("Alice", intent.getStringExtra("name"))
        assertEquals(25, intent.getIntExtra("age", -1))
        assertTrue(intent.getBooleanExtra("vip", false))
    }

    // ==================================================================
    // 前缀隔离
    // ==================================================================

    @Test
    fun prefix_noCrossContamination() {
        val intent = IntentBuilder()
            .fromUrl("https://a.com?id=1&name=Alice", prefix = "a_")
            .fromUrl("https://b.com?id=2&name=Bob", prefix = "b_")
            .build()

        assertEquals("1", intent.getStringExtra("a_id"))
        assertEquals("Alice", intent.getStringExtra("a_name"))
        assertEquals("2", intent.getStringExtra("b_id"))
        assertEquals("Bob", intent.getStringExtra("b_name"))
    }

    @Test
    fun prefix_emptyPrefixOverwrites() {
        val intent = IntentBuilder()
            .put("key", "first")
            .put("key", "second")  // 同 key 无前缀 → 后者覆盖
            .build()

        assertEquals("second", intent.getStringExtra("key"))
    }

    @Test
    fun prefix_sameKeyDifferentPrefixes_noConflict() {
        val intent = IntentBuilder()
            .fromUrl("https://api.com?type=A", prefix = "url_")
            .fromMap(mapOf("type" to "B"), prefix = "cfg_")
            .put("type", "C")
            .build()

        assertEquals("A", intent.getStringExtra("url_type"))
        assertEquals("B", intent.getStringExtra("cfg_type"))
        assertEquals("C", intent.getStringExtra("type"))
    }

    // ==================================================================
    // 错误处理
    // ==================================================================

    @Test
    fun errorHandling_onError_callbackTriggered() {
        val errors = mutableListOf<IntentBuilder.BuildError>()

        val intent = IntentBuilder()
            .onError { e -> errors.add(e) }
            .fromUrl("!!! invalid url")
            .fromUrl("https://valid.com?q=ok")
            .build()

        assertEquals(1, errors.size)
        assertEquals("fromUrl", errors[0].source)
        assertEquals("!!! invalid url", errors[0].input)

        // 有效数据不受影响
        assertEquals("ok", intent.getStringExtra("q"))
    }

    @Test
    fun errorHandling_errors_listAccessible() {
        val builder = IntentBuilder()
            .fromUrl("!!! bad url 1")
            .fromUrl("!!! bad url 2")
            .fromUrl("https://good.com?a=1")

        val errs = builder.errors()
        assertEquals(2, errs.size)
        assertEquals("!!! bad url 1", errs[0].input)
        assertEquals("!!! bad url 2", errs[1].input)
    }

    @Test
    fun errorHandling_hasErrors() {
        val builder = IntentBuilder()
        assertFalse(builder.hasErrors())

        builder.fromUrl("!!! invalid")
        assertTrue(builder.hasErrors())
    }

    @Test
    fun errorHandling_multipleErrorTypes() {
        val builder = IntentBuilder()
            .fromUrl("!!! bad 1")
            .fromUrl("!!! bad 2")
            .fromUrl("!!! bad 3")

        assertEquals(3, builder.errors().size)
    }

    @Test
    fun errorHandling_onErrorCanBeRemoved() {
        var callCount = 0
        val builder = IntentBuilder()
            .onError { callCount++ }
            .fromUrl("!!! bad url")

        assertEquals(1, callCount)

        // reset 会同时清除 errorHandler
        builder.reset()
        builder.fromUrl("!!! bad again")
        assertEquals(1, callCount)  // handler 已清除，不再触发
    }

    @Test
    fun errorHandling_callbackException_doesNotCrashBuilder() {
        val builder = IntentBuilder()
            .onError { throw RuntimeException("test crash in callback") }
            .fromUrl("!!! bad url")
            .fromMap(mapOf("safe" to "value"))

        assertTrue(builder.hasErrors())
        val intent = builder.build()
        assertEquals("value", intent.getStringExtra("safe"))
    }

    @Test
    fun errorHandling_errors_returnsCopy() {
        val builder = IntentBuilder().fromUrl("!!! bad url")
        val copy = builder.errors()
        builder.reset()

        // reset 清空了 builder 内部列表，但 copy 不受影响
        assertEquals(1, copy.size)
        assertFalse(builder.hasErrors())
    }

    @Test
    fun errorHandling_buildError_toString() {
        val err = IntentBuilder.BuildError("fromUrl", "bad_input", "解析失败")
        val str = err.toString()
        assertTrue(str.contains("IntentBuilder"))
        assertTrue(str.contains("fromUrl"))
        assertTrue(str.contains("bad_input"))
        assertTrue(str.contains("解析失败"))
    }

    // ==================================================================
    // 构建输出：build() / into()
    // ==================================================================

    @Test
    fun build_actionIsSet() {
        val intent = IntentBuilder()
            .action(Intent.ACTION_SEND)
            .put("text", "hello")
            .build()

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("hello", intent.getStringExtra("text"))
    }

    @Test
    fun build_noSources_actionOnly() {
        val intent = IntentBuilder()
            .action(Intent.ACTION_MAIN)
            .build()

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertNull(intent.extras)
    }

    @Test
    fun into_injectsToExistingIntent() {
        val existing = Intent(Intent.ACTION_VIEW)
        existing.putExtra("original", "keep_me")

        val result = IntentBuilder()
            .fromMap(mapOf("added" to "new_value"))
            .into(existing)

        assertSame(existing, result)
        assertEquals("keep_me", existing.getStringExtra("original"))
        assertEquals("new_value", existing.getStringExtra("added"))
    }

    @Test
    fun into_overwritesExistingKeys() {
        val existing = Intent(Intent.ACTION_VIEW)
        existing.putExtra("key", "old_value")

        IntentBuilder()
            .put("key", "new_value")
            .into(existing)

        assertEquals("new_value", existing.getStringExtra("key"))
    }

    @Test
    fun into_noSources_returnsSameIntent() {
        val existing = Intent(Intent.ACTION_VIEW)
        existing.putExtra("keep", "me")

        val result = IntentBuilder().into(existing)

        assertSame(existing, result)
        assertEquals("me", existing.getStringExtra("keep"))
    }

    // ==================================================================
    // 构建输出：buildBundle()
    // ==================================================================

    @Test
    fun buildBundle_basic() {
        val bundle = IntentBuilder()
            .put("name", "Alice")
            .put("age", 25)
            .buildBundle()

        assertEquals("Alice", bundle.getString("name"))
        assertEquals(25, bundle.getInt("age"))
    }

    @Test
    fun buildBundle_multiSourceMerged() {
        val bundle = IntentBuilder()
            .fromUrl("https://api.com?source=deeplink", prefix = "url_")
            .fromMap(mapOf("userId" to 999), prefix = "cfg_")
            .put("extra", "bonus")
            .buildBundle()

        assertEquals("deeplink", bundle.getString("url_source"))
        assertEquals(999, bundle.getInt("cfg_userId"))
        assertEquals("bonus", bundle.getString("extra"))
    }

    @Test
    fun buildBundle_secondOverwritesFirst() {
        val bundle = IntentBuilder()
            .put("key", "first")
            .put("key", "second")
            .buildBundle()

        assertEquals("second", bundle.getString("key"))
    }

    @Test
    fun buildBundle_noSources_returnsEmptyBundle() {
        val bundle = IntentBuilder().buildBundle()

        assertTrue(bundle.isEmpty)
    }

    @Test
    fun buildBundle_resultIndependentOfBuilder() {
        val builder = IntentBuilder().put("k", "v")
        val bundle = builder.buildBundle()

        // 修改 bundle 不影响 builder
        bundle.putString("k", "modified")

        val bundle2 = builder.buildBundle()
        assertEquals("v", bundle2.getString("k"))
    }

    @Test
    fun buildBundle_furtherProcessing_thenIntoIntent() {
        // 先获取合并后的 Bundle，二次加工后注入 Intent
        val bundle = IntentBuilder()
            .fromUrl("https://api.com?q=kotlin", prefix = "url_")
            .put("version", 2)
            .buildBundle()

        bundle.putLong("timestamp", 1700000000000L)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtras(bundle)

        assertEquals("kotlin", intent.getStringExtra("url_q"))
        assertEquals(2, intent.getIntExtra("version", -1))
        assertEquals(1700000000000L, intent.getLongExtra("timestamp", -1L))
    }

    // ==================================================================
    // 生命周期
    // ==================================================================

    @Test
    fun reset_clearsAllState() {
        val builder = IntentBuilder()
            .action(Intent.ACTION_VIEW)
            .fromUrl("https://api.com?q=test")
            .fromUrl("!!! bad url")  // 产生一个错误

        assertTrue(builder.hasErrors())
        assertEquals(1, builder.sourceCount())

        builder.reset()

        assertFalse(builder.hasErrors())
        assertEquals(0, builder.sourceCount())
        assertNull(builder.build().action)
    }

    @Test
    fun reset_returnsSelf_forChaining() {
        val builder = IntentBuilder()
            .fromUrl("https://api.com?q=test")

        val result = builder.reset()
        assertSame(builder, result)
        assertEquals(0, builder.sourceCount())
    }

    @Test
    fun reset_thenReuse() {
        val builder = IntentBuilder()
            .put("first", "A")
        assertEquals("A", builder.build().getStringExtra("first"))

        builder.reset()
            .put("second", "B")

        val intent = builder.build()
        assertNull(intent.getStringExtra("first"))
        assertEquals("B", intent.getStringExtra("second"))
    }

    @Test
    fun sourceCount_tracksDataSources() {
        val builder = IntentBuilder()
        assertEquals(0, builder.sourceCount())

        builder.fromUrl("https://api.com?a=1")
        assertEquals(1, builder.sourceCount())

        builder.fromMap(mapOf("b" to "2"), prefix = "m_")
        assertEquals(2, builder.sourceCount())

        builder.fromBundle(Bundle().apply { putString("c", "3") })
        assertEquals(3, builder.sourceCount())
    }

    // ==================================================================
    // 集成场景
    // ==================================================================

    @Test
    fun integration_fullPipeline_urlAndMapAndBundle() {
        val existingData = Bundle().apply {
            putString("session", "sess_12345")
            putBoolean("authenticated", true)
        }

        val intent = IntentBuilder()
            .fromUrl("https://tracker.com?campaign=spring_sale", prefix = "track_")
            .fromMap(mapOf("userId" to 999, "env" to "production"), prefix = "cfg_")
            .fromBundle(existingData)
            .action(Intent.ACTION_VIEW)
            .build()

        // URL with prefix
        assertEquals("spring_sale", intent.getStringExtra("track_campaign"))
        // Map with prefix
        assertEquals(999, intent.getIntExtra("cfg_userId", -1))
        assertEquals("production", intent.getStringExtra("cfg_env"))
        // Bundle without prefix
        assertEquals("sess_12345", intent.getStringExtra("session"))
        assertTrue(intent.getBooleanExtra("authenticated", false))
        // Action
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test
    fun integration_errorRecovery_gracefulDegradation() {
        // 即使部分数据源失败，成功的部分仍能正常使用
        var errorCount = 0

        val intent = IntentBuilder()
            .onError { errorCount++ }
            .fromUrl("!!! invalid url 1")
            .fromUrl("https://api.com?usable=true")
            .fromUrl("!!! invalid url 2")
            .fromMap(mapOf("fallback" to "works"), prefix = "fb_")
            .action(Intent.ACTION_VIEW)
            .build()

        assertEquals(2, errorCount)
        assertEquals("true", intent.getStringExtra("usable"))
        assertEquals("works", intent.getStringExtra("fb_fallback"))
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test
    fun integration_complexPrefixMerge() {
        val intent = IntentBuilder()
            .fromUrl("https://a.com?shared=from_a", prefix = "a_")
            .fromUrl("https://b.com?shared=from_b", prefix = "b_")
            .put("shared", "from_put")
            .build()

        assertEquals("from_a", intent.getStringExtra("a_shared"))
        assertEquals("from_b", intent.getStringExtra("b_shared"))
        assertEquals("from_put", intent.getStringExtra("shared"))
    }

    @Test
    fun integration_deepLinkRealWorld() {
        // 模拟真实深链接场景：运营下发的 URL + 本地配置 + 用户状态
        val campaignUrl = "https://app.com/open?page=product_detail&id=SKU123&ref=push"
        val localConfig = mapOf<String, Any?>(
            "ab_test_group" to "B",
            "feature_flags" to listOf("new_ui", "fast_checkout")
        )
        val userState = Bundle().apply {
            putString("login_token", "tok_abc")
            putBoolean("is_premium", true)
        }

        val intent = IntentBuilder()
            .fromUrl(campaignUrl, prefix = "camp_")
            .fromMap(localConfig, prefix = "cfg_")
            .fromBundle(userState, prefix = "user_")
            .action(Intent.ACTION_VIEW)
            .into(Intent(Intent.ACTION_VIEW))

        // 运营参数
        assertEquals("product_detail", intent.getStringExtra("camp_page"))
        assertEquals("SKU123", intent.getStringExtra("camp_id"))
        assertEquals("push", intent.getStringExtra("camp_ref"))
        // 本地配置
        assertEquals("B", intent.getStringExtra("cfg_ab_test_group"))
        assertEquals(listOf("new_ui", "fast_checkout"), intent.getStringArrayListExtra("cfg_feature_flags"))
        // 用户状态
        assertEquals("tok_abc", intent.getStringExtra("user_login_token"))
        assertTrue(intent.getBooleanExtra("user_is_premium", false))
    }

    // ==================================================================
    // 边界情况
    // ==================================================================

    @Test
    fun edge_emptyEverything() {
        val builder = IntentBuilder()
        assertFalse(builder.hasErrors())
        assertEquals(0, builder.sourceCount())

        val intent = builder.build()
        assertNull(intent.action)
        assertNull(intent.extras)
    }

    @Test
    fun edge_onlyFailingSources_stillBuilds() {
        val builder = IntentBuilder()
            .fromUrl("!!! bad url 1")
            .fromUrl("!!! bad url 2")

        assertTrue(builder.hasErrors())

        val intent = builder.build()
        assertNotNull(intent)
        assertNull(intent.extras)
    }

    @Test
    fun edge_manySources() {
        val builder = IntentBuilder()
        for (i in 1..50) {
            builder.put("key_$i", "value_$i")
        }

        assertEquals(50, builder.sourceCount())
        val intent = builder.build()
        assertEquals(50, intent.extras?.size())
        assertEquals("value_25", intent.getStringExtra("key_25"))
    }

    // ==================================================================
    // 配置：目标组件
    // ==================================================================

    @Test
    fun component_byComponentName() {
        val cn = android.content.ComponentName("com.example", "com.example.DetailActivity")
        val intent = IntentBuilder()
            .component(cn)
            .build()

        assertEquals(cn, intent.component)
    }

    @Test
    fun component_byPackageAndClass() {
        val intent = IntentBuilder()
            .component("com.example", "com.example.DetailActivity")
            .build()

        assertEquals("com.example", intent.component?.packageName)
        assertEquals("com.example.DetailActivity", intent.component?.className)
    }

    @Test
    fun component_nullClearsIt() {
        val builder = IntentBuilder()
            .component("com.example", "com.example.Activity")
        builder.component(null)

        assertNull(builder.build().component)
    }

    // ==================================================================
    // 配置：data / type / dataAndType
    // ==================================================================

    @Test
    fun data_setsUri() {
        val uri = android.net.Uri.parse("https://example.com/product/123")
        val intent = IntentBuilder()
            .data(uri)
            .build()

        assertEquals(uri, intent.data)
    }

    @Test
    fun type_setsMimeType() {
        val intent = IntentBuilder()
            .type("image/png")
            .build()

        assertEquals("image/png", intent.type)
    }

    @Test
    fun dataAndType_setsBoth() {
        val uri = android.net.Uri.parse("content://media/123")
        val intent = IntentBuilder()
            .dataAndType(uri, "image/jpeg")
            .build()

        assertEquals(uri, intent.data)
        assertEquals("image/jpeg", intent.type)
    }

    @Test
    fun dataAndType_separate_alsoWorks() {
        val uri = android.net.Uri.parse("https://example.com")
        val intent = IntentBuilder()
            .data(uri)
            .type("text/html")
            .action(Intent.ACTION_VIEW)
            .build()

        assertEquals(uri, intent.data)
        assertEquals("text/html", intent.type)
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    // ==================================================================
    // 配置：flags / addFlags
    // ==================================================================

    @Test
    fun flags_setMode() {
        val intent = IntentBuilder()
            .flags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .build()

        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
    }

    @Test
    fun addFlags_accumulate() {
        val intent = IntentBuilder()
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .build()

        val expected = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(expected, intent.flags and expected)
    }

    @Test
    fun flags_overwritesPrevious() {
        val intent = IntentBuilder()
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .flags(Intent.FLAG_ACTIVITY_CLEAR_TOP)  // setFlags 覆盖模式
            .build()

        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags)
    }

    // ==================================================================
    // 配置：category
    // ==================================================================

    @Test
    fun addCategory_single() {
        val intent = IntentBuilder()
            .addCategory(Intent.CATEGORY_DEFAULT)
            .build()

        assertTrue(intent.categories.contains(Intent.CATEGORY_DEFAULT))
    }

    @Test
    fun addCategory_multiple() {
        val intent = IntentBuilder()
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .build()

        assertEquals(2, intent.categories.size)
        assertTrue(intent.categories.contains(Intent.CATEGORY_DEFAULT))
        assertTrue(intent.categories.contains(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun addCategory_duplicate_deduplicated() {
        val intent = IntentBuilder()
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .build()

        assertEquals(1, intent.categories.size)
    }

    // ==================================================================
    // 配置：reset 清除新字段
    // ==================================================================

    @Test
    fun reset_clearsComponentFlagsDataCategories() {
        val builder = IntentBuilder()
            .component("com.example", "com.example.Activity")
            .flags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .data(android.net.Uri.parse("https://example.com"))
            .type("text/html")
            .addCategory(Intent.CATEGORY_DEFAULT)

        builder.reset()

        val intent = builder.build()
        assertNull(intent.component)
        assertEquals(0, intent.flags)
        assertNull(intent.data)
        assertNull(intent.type)
        assertTrue(intent.categories.isNullOrEmpty())
    }

    // ==================================================================
    // 集成：显式 Intent 启动 Activity
    // ==================================================================

    @Test
    fun integration_explicitActivity_fullConfig() {
        val uri = android.net.Uri.parse("myapp://product/12345")
        val intent = IntentBuilder()
            .component("com.example", "com.example.ProductActivity")
            .action(Intent.ACTION_VIEW)
            .data(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .fromUrl("https://tracker.com?campaign=summer", prefix = "track_")
            .put("source", "push")
            .build()

        assertEquals("com.example", intent.component?.packageName)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(uri, intent.data)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.categories.contains(Intent.CATEGORY_DEFAULT))
        assertEquals("summer", intent.getStringExtra("track_campaign"))
        assertEquals("push", intent.getStringExtra("source"))
    }

    @Test
    fun integration_shareIntent_pattern() {
        val imageUri = android.net.Uri.parse("content://media/external/images/123")
        val intent = IntentBuilder()
            .action(Intent.ACTION_SEND)
            .dataAndType(imageUri, "image/png")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .put("title", "分享图片")
            .build()

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(imageUri, intent.data)
        assertEquals("image/png", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("分享图片", intent.getStringExtra("title"))
    }
}
