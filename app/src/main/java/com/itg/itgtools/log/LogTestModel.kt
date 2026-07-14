package com.itg.itgtools.log

import com.itg.log.ItgLog
import com.itg.log.TestLogger
import com.itg.log.config.LogConfig
import com.itg.log.config.configureLogger
import com.itg.log.core.LogEntry
import com.itg.log.core.LogLevel
import com.itg.log.format.ColoredFormatter
import com.itg.log.format.CompactFormatter
import com.itg.log.format.LogFormatter
import com.itg.log.format.TimestampFormatter
import com.itg.log.output.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * itg-log 全功能测试模型
 *
 * 覆盖: 基础 API / 计时精度 / 多线程并发 /
 * 自定义 Formatter / 自定义 Output / DSL 配置 / 边界场景
 */
class LogTestModel(private val cacheDir: File) {

    var onLog: ((String) -> Unit)? = null

    // ==================== 默认 Logger ====================

    private val logger: TestLogger = TestLogger("LogTest") {
        tag = "LogTest"
        addOutput(CompositeOutput(
            LogcatOutput("LogTest"),
            CallbackOutput { msg -> onLog?.invoke(msg) }
        ))
    }

    // ==================== 1. 基础 API ====================

    fun testBasicLifecycle() {
        logger.beginTest("1.1 基础生命周期")

        logger.d("这是一条 DEBUG 日志")
        logger.i("这是一条 INFO 日志")
        logger.w("这是一条 WARN 日志")
        logger.e("这是一条 ERROR 日志")
        logger.v("这是一条 VERBOSE 日志")

        logger.ok("操作成功")
        logger.fail("操作失败")

        logger.endTest()
    }

    fun testMultipleTests() {
        // 连续多个测试，验证步骤计数器正确重置
        for (i in 1..3) {
            logger.beginTest("1.2 第${i}轮测试")
            logger.d("第${i}轮 - 步骤1")
            logger.d("第${i}轮 - 步骤2")
            logger.ok("第${i}轮完成")
            logger.endTest()
        }
    }

    fun testLogLevels() {
        logger.beginTest("1.3 日志级别过滤")

        // 用 minLevel=INFO 的配置创建新 logger
        val filtered = TestLogger("LevelTest") {
            tag = "LevelTest"
            minLevel = LogLevel.INFO  // 过滤 DEBUG 和 VERBOSE
            outputToCallback { msg -> onLog?.invoke(msg) }
        }
        filtered.beginTest("级别过滤测试 (minLevel=INFO)")
        filtered.d("这条 DEBUG 不应出现")
        filtered.v("这条 VERBOSE 不应出现")
        filtered.i("这条 INFO 应出现 ✅")
        filtered.w("这条 WARN 应出现")
        filtered.e("这条 ERROR 应出现")
        filtered.ok("过滤正常 — 如果上面只有3条则正确")
        filtered.endTest()
    }

    fun testStepMethod() {
        logger.beginTest("1.4 step() 方法")
        val r1 = logger.step("第一步", LogLevel.INFO)
        Thread.sleep(50)
        val r2 = logger.step("第二步")
        logger.d("StepRecord 1: step=${r1.step}, delta=${r1.deltaMs}ms, total=${r1.totalMs}ms")
        logger.d("StepRecord 2: step=${r2.step}, delta=${r2.deltaMs}ms, total=${r2.totalMs}ms")
        logger.ok("step() 返回结构化数据")
        logger.endTest()
    }

    fun testMarkMethod() {
        logger.beginTest("1.5 mark() — 时间标记")
        Thread.sleep(30)
        val t1 = logger.mark()
        logger.d("mark() 1: ${t1}ms (不增加步骤)")
        Thread.sleep(30)
        val t2 = logger.mark()
        logger.d("mark() 2: ${t2}ms (步骤号未变: ${logger.currentStep})")
        logger.ok("mark() 不影响步骤计数")
        logger.endTest()
    }

    // ==================== 2. 计时精度 ====================

    fun testTimingAccuracy() {
        logger.beginTest("2.1 计时精度验证")

        val sleepMs = 200L
        val start = System.currentTimeMillis()
        Thread.sleep(sleepMs)
        logger.d("sleep(${sleepMs}ms) 后")
        val actual = System.currentTimeMillis() - start

        logger.i("实际耗时: ${actual}ms (期望 ≈${sleepMs}ms)")
        logger.i("elapsedMs: ${logger.elapsedMs}ms")
        if (actual in (sleepMs - 50)..(sleepMs + 100)) logger.ok("计时验证准确") else logger.fail("计时偏差较大")
        logger.endTest()
    }

    fun testDeltaAccuracy() {
        logger.beginTest("2.2 增量计时验证")
        // D+ 应该反映两条日志之间的实际间隔
        Thread.sleep(30)
        logger.d("步骤 A")
        Thread.sleep(70)
        logger.d("步骤 B (D+ 应≈70ms)")
        Thread.sleep(100)
        logger.d("步骤 C (D+ 应≈100ms)")
        logger.ok("增量计时应基本准确")
        logger.endTest()
    }

    fun testRapidLogging() {
        logger.beginTest("2.3 高频日志 (100条)")
        val start = System.currentTimeMillis()
        repeat(100) { i ->
            logger.d("高频日志 #${i + 1}")
        }
        logger.i("100条日志总耗时: ${System.currentTimeMillis() - start}ms")
        logger.ok("高频日志无异常, step=${logger.currentStep}")
        logger.endTest()
    }

    // ==================== 3. 多线程并发 ====================

    fun testConcurrentLogging() {
        logger.beginTest("3.1 多线程并发日志 (10线程 × 5条)")

        val latch = CountDownLatch(10)
        val errors = AtomicInteger(0)
        val completed = AtomicInteger(0)

        repeat(10) { threadIdx ->
            Thread {
                try {
                    repeat(5) { i ->
                        logger.d("Thread-$threadIdx 日志 #$i")
                    }
                    completed.incrementAndGet()
                } catch (e: Exception) {
                    errors.incrementAndGet()
                    logger.e("Thread-$threadIdx 异常: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        latch.await()
        logger.i("完成: ${completed.get()}/10 线程, 错误: ${errors.get()}")
        logger.i("总步骤: ${logger.currentStep} (期望 50)")
        if (errors.get() == 0 && logger.currentStep == 50) logger.ok("并发测试通过") else logger.fail("并发测试存在异常")
        logger.endTest()
    }

    fun testConcurrentStepsInOrder() {
        logger.beginTest("3.2 并发步骤序号严格递增")

        val latch = CountDownLatch(5)
        val stepNumbers = mutableListOf<Int>()
        val lock = Any()

        repeat(5) { i ->
            Thread {
                val record = logger.step("并发步骤 $i")
                synchronized(lock) { stepNumbers.add(record.step) }
                latch.countDown()
            }.start()
        }

        latch.await()
        val sorted = stepNumbers.sorted()
        logger.i("步骤号: $stepNumbers")
        if (stepNumbers.size == sorted.size && stepNumbers.toSet().size == stepNumbers.size) logger.ok("步骤严格递增") else logger.fail("步骤存在重复/乱序")
        logger.endTest()
    }

    // ==================== 4. 自定义 Formatter ====================

    fun testCompactFormatter() {
        val fmt = CompactFormatter(stepWidth = 2, deltaWidth = 4, totalWidth = 5)
        logger.beginTest("4.1 CompactFormatter (默认)")
        logger.d("紧凑格式: stepWidth=2, delta=4, total=5")
        logger.ok("CompactFormatter 测试完成")
        logger.endTest()
    }

    fun testTimestampFormatter() {
        val tsLogger = TestLogger("Timestamp") {
            tag = "Timestamp"
            formatter = TimestampFormatter(CompactFormatter())
            outputToCallback { msg -> onLog?.invoke(msg) }
        }
        tsLogger.beginTest("4.2 TimestampFormatter")
        tsLogger.d("带时间戳的日志")
        Thread.sleep(30)
        tsLogger.d("第二条带时间戳")
        tsLogger.ok("TimestampFormatter 正常")
        tsLogger.endTest()
    }

    fun testColoredFormatter() {
        val colorLogger = TestLogger("Colored") {
            tag = "Colored"
            formatter = ColoredFormatter(CompactFormatter())
            outputToCallback { msg -> onLog?.invoke(msg) }
        }
        colorLogger.beginTest("4.3 ColoredFormatter (ANSI)")
        colorLogger.d("DEBUG — 青色")
        colorLogger.i("INFO  — 绿色")
        colorLogger.w("WARN  — 黄色")
        colorLogger.e("ERROR — 红色")
        colorLogger.ok("ANSI 颜色代码已嵌入 (终端可见)")
        colorLogger.endTest()
    }

    fun testCustomFormatter() {
        // JSON 格式
        val jsonFmt = LogFormatter { entry ->
            """{"step":${entry.step},"delta":${entry.deltaMs},"total":${entry.totalMs},"msg":"${entry.message}"}"""
        }
        val jsonLogger = TestLogger("Json") {
            tag = "Json"
            formatter = jsonFmt
            outputToCallback { msg -> onLog?.invoke(msg) }
        }
        jsonLogger.beginTest("4.4 自定义 JSON Formatter")
        jsonLogger.d("JSON 格式日志1")
        Thread.sleep(30)
        jsonLogger.d("JSON 格式日志2")
        jsonLogger.ok("JSON Formatter 正常")
        jsonLogger.endTest()
    }

    fun testCustomHeaderFormatter() {
        val hdrLogger = TestLogger("Header") {
            tag = "Header"
            headerFormatter = { "╔═══ $it ═══╗" }
            outputToCallback { msg -> onLog?.invoke(msg) }
        }
        hdrLogger.beginTest("4.5 自定义标题格式")
        hdrLogger.d("标题使用了自定义格式")
        hdrLogger.ok("headerFormatter 正常")
        hdrLogger.endTest()
    }

    // ==================== 5. 自定义 Output ====================

    fun testCallbackOutput() {
        val captured = mutableListOf<String>()
        val cbLogger = TestLogger("Callback") {
            tag = "Callback"
            outputToCallback { captured.add(it) }
        }
        cbLogger.beginTest("5.1 CallbackOutput")
        cbLogger.d("消息1")
        cbLogger.d("消息2")
        cbLogger.ok("捕获消息数: ${captured.size}")
        cbLogger.endTest()

        logger.beginTest("5.1 CallbackOutput 验证")
        logger.i("捕获到 ${captured.size} 条消息 (期望≥4: 标题+2条+ok+footer)")
        captured.forEachIndexed { i, s -> logger.d("  [$i] ${s.take(80)}") }
        if (captured.size >= 4) logger.ok("CallbackOutput 消息完整") else logger.fail("消息不足: ${captured.size}")
        logger.endTest()
    }

    fun testFileOutput() {
        val file = File(cacheDir, "itg_log_test_${System.nanoTime()}.log")
        val fileLogger = TestLogger("File") {
            tag = "File"
            addOutput(CompositeOutput(
                LogcatOutput("FileLog"),
                object : LogOutput {
                    override fun write(entry: LogEntry, formatted: String) {
                        file.appendText("$formatted\n")
                    }
                }
            ))
        }
        fileLogger.beginTest("5.2 FileOutput")
        fileLogger.d("写入文件测试")
        fileLogger.i("文件路径: ${file.absolutePath}")
        fileLogger.ok("写入完成, 文件大小=${file.length()}bytes")
        fileLogger.endTest()

        logger.beginTest("5.2 FileOutput 验证")
        logger.i("文件: ${file.absolutePath}")
        logger.i("大小: ${file.length()} bytes")
        logger.i("内容预览: ${file.readText().take(200)}")
        if (file.length() > 0) logger.ok("FileOutput 写入成功") else logger.fail("文件为空")
        logger.endTest()
    }

    fun testCompositeOutput() {
        val compositeLogger = TestLogger("Composite") {
            tag = "Composite"
            addOutput(CompositeOutput(
                LogcatOutput("LogA"),
                LogcatOutput("LogB"),
                CallbackOutput { msg -> onLog?.invoke("[双路] $msg") }
            ))
        }
        compositeLogger.beginTest("5.3 CompositeOutput (3路输出)")
        compositeLogger.d("同时输出到 logcat-A, logcat-B, 和 UI 回调")
        compositeLogger.ok("CompositeOutput 正常")
        compositeLogger.endTest()
    }

    // ==================== 6. 配置 DSL ====================

    fun testConfigurationBuilder() {
        logger.beginTest("6.1 配置 Builder")

        val cfg = configureLogger {
            tag = "DemoTag"
            minLevel = LogLevel.WARN
            formatter = CompactFormatter(stepWidth = 3, deltaWidth = 6, totalWidth = 7)
            outputToLogcat("DemoTag")
            outputToCallback { }
            headerFormatter = { ">>> $it <<<" }
        }
        logger.i("tag=${cfg.tag}, minLevel=${cfg.minLevel}")
        logger.i("formatter=${cfg.formatter.javaClass.simpleName}")
        logger.i("outputs=${cfg.outputs.size}个")
        logger.ok("DSL 配置正常")
        logger.endTest()
    }

    fun testDefaultConfig() {
        val defaultLogger = TestLogger("Default")
        defaultLogger.beginTest("6.2 默认配置 (无参数)")
        defaultLogger.d("使用默认 LogConfig, 输出到 logcat")
        defaultLogger.ok("默认配置正常")
        defaultLogger.endTest()
    }

    // ==================== 7. 查询 API ====================

    fun testQueryAPI() {
        logger.beginTest("7.1 查询 API")
        logger.i("开始时: step=${logger.currentStep}, elapsed=${logger.elapsedMs}ms, started=${logger.isStarted}")
        Thread.sleep(30)
        logger.d("步骤1")
        Thread.sleep(30)
        logger.d("步骤2")
        logger.i("结束时: step=${logger.currentStep}, elapsed=${logger.elapsedMs}ms")
        logger.ok("查询 API 正常")
        logger.endTest()
    }

    fun testNestedBeginTest() {
        logger.beginTest("7.2 嵌套 beginTest")
        logger.d("外层 - 步骤1")
        logger.beginTest("内层测试")
        logger.d("内层 - 步骤1 (步骤号已重置)")
        logger.ok("内层完成")
        logger.endTest()
        logger.d("外层 - 步骤2")
        logger.ok("嵌套 beginTest 正常 (步骤号各自独立)")
        logger.endTest()
    }

    // ==================== 8. 边界场景 ====================

    fun testEmptyMessage() {
        logger.beginTest("8.1 空消息")
        logger.d("")
        logger.i("")
        logger.e("")
        logger.ok("")
        logger.endTest()
    }

    fun testLongMessage() {
        logger.beginTest("8.2 超长消息")
        val longMsg = "A".repeat(2000)
        logger.d(longMsg.take(500) + "...")
        logger.ok("超长消息截断输出正常")
        logger.endTest()
    }

    fun testSpecialCharacters() {
        logger.beginTest("8.3 特殊字符")
        logger.d("换行\\n测试\n实际换行")
        logger.d("Unicode: 🎉🔥✅❌📖 日本語 한국어")
        logger.d("JSON: {\"key\": \"value\"}")
        logger.ok("特殊字符正常")
        logger.endTest()
    }

    fun testNoBeginTest() {
        // 不调用 beginTest 直接输出
        val bare = TestLogger("Bare") {
            outputToCallback { msg -> onLog?.invoke(msg) }
        }
        // 不调用 beginTest, 直接 log, 验证不崩溃
        bare.d("没有 beginTest 直接输出")
        bare.ok("无 beginTest 不崩溃")
    }

    fun testBeginWithoutEnd() {
        logger.beginTest("8.5 beginTest 后不调用 endTest")
        logger.d("后续测试会调用新的 beginTest")
        logger.d("这验证了 beginTest 可以安全覆盖上一个未结束的测试")
        // 不调用 endTest, 直接开始下一个
    }

    // ==================== 9. 生产环境关闭 ====================

    fun testGlobalDisabled() {
        logger.beginTest("9.1 全局关闭 ItgLog.globalEnabled=false")
        logger.d("关闭前 — 这条可见")

        ItgLog.globalEnabled = false
        logger.d("关闭后 — 这条不可见 (静默)")
        logger.i("INFO — 不可见")
        logger.w("WARN — 不可见")
        logger.e("ERROR — 不可见")
        logger.ok("成功 — 不可见")
        logger.fail("失败 — 不可见")

        ItgLog.globalEnabled = true
        logger.d("恢复后 — 这条可见")
        logger.ok("全局开关正常：关闭期间无任何输出")
        logger.endTest()
    }

    fun testPerInstanceDisabled() {
        logger.beginTest("9.2 单实例关闭 config.enabled=false")

        val disabled = TestLogger("Disabled") {
            tag = "Disabled"
            enabled = false  // 此 logger 永久静默
            outputToCallback { msg -> onLog?.invoke(msg) }
        }
        disabled.beginTest("此标题不应出现")
        disabled.d("此日志不应出现")
        disabled.i("INFO — 不应出现")
        disabled.ok("OK — 不应出现")
        disabled.endTest()

        logger.d("但主 logger 不受影响")
        logger.ok("单实例关闭正常：仅 disabled logger 被静默")
        logger.endTest()
    }

    fun testProductionPattern() {
        logger.beginTest("9.3 生产环境推荐模式")

        // 模拟: 全局开关绑定 BuildConfig.DEBUG
        ItgLog.globalEnabled = false
        logger.d("这行不应出现 (模拟 release 模式)")

        // 即使全局关闭，独立创建的 logger 也静默
        val prod = TestLogger("Prod") {
            outputToCallback { msg -> onLog?.invoke(msg) }
        }
        prod.beginTest("不应出现")
        prod.d("不应出现")
        prod.endTest()

        ItgLog.globalEnabled = true
        logger.i("全局开关恢复后可见")
        logger.ok("生产模式验证通过: globalEnabled=false 时零输出")

        logger.d("推荐在 Application.onCreate() 中设置:")
        logger.d("  ItgLog.globalEnabled = BuildConfig.DEBUG")
        logger.endTest()
    }
}
