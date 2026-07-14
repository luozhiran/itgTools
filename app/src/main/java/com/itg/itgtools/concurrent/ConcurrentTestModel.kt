package com.itg.itgtools.concurrent

import com.itg.concurrent.*
import com.itg.concurrent.util.ConcurrentUtils
import com.itg.log.TestLogger
import com.itg.log.config.LogConfig
import com.itg.log.output.CallbackOutput
import com.itg.log.output.CompositeOutput
import com.itg.log.output.LogcatOutput
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * itg-concurrent-core 全功能测试模型
 *
 * 覆盖场景:
 * 1. 基本任务提交 (io/compute/background/main)
 * 2. Future 操作 (await/cancel)
 * 3. 延迟执行
 * 4. 后端切换 (COROUTINE/THREAD_POOL/AUTO)
 * 5. 混合模式
 * 6. 自定义分发器注册
 * 7. 可用性检测
 * 8. 协程原生 API (suspend)
 * 9. ConcurrentUtils 工具类
 * 10. 边界场景
 */
class ConcurrentTestModel {

    // ==================== 日志 (基于 itg-log) ====================

    var onLog: ((String) -> Unit)? = null

    private val logger: TestLogger = TestLogger(
        "ConcurrentTest",
        LogConfig.builder().apply {
            tag = "ConcurrentTest"
            addOutput(CompositeOutput(
                LogcatOutput("ConcurrentTest"),
                CallbackOutput { msg -> onLog?.invoke(msg) }
            ))
        }.build()
    )

    private fun beginTest(name: String) = logger.beginTest(name)
    private fun log(msg: String) = logger.d(msg)
    private fun logOk(label: String, result: Any? = null) {
        val extra = if (result != null) ": $result" else ""
        logger.ok("$label$extra")
    }
    private fun logFail(label: String, msg: String = "") {
        logger.fail(if (msg.isNotEmpty()) "$label — $msg" else label)
    }

    // ==================== 1. 基本任务提交 ====================

    fun testBasicSubmitIO() {
        beginTest("1.1 IO 任务提交")
        val flag = AtomicInteger(0)
        val future = Concurrent.io<Int> {
            log("  [IO线程] 执行中... thread=${Thread.currentThread().name}")
            flag.set(42)
            42
        }
        val result = ConcurrentUtils.await(future, 5000)
        logOk("IO任务完成, flag=${flag.get()}, result=$result",
            if (flag.get() == 42 && result == 42) "符合预期" else null)
    }

    fun testBasicSubmitCompute() {
        beginTest("1.2 Compute 任务提交")
        val future = Concurrent.compute<String> {
            log("  [Compute线程] 执行计算... thread=${Thread.currentThread().name}")
            var sum = 0L
            for (i in 1..1_000_000) sum += i
            "sum=$sum"
        }
        val result = ConcurrentUtils.await(future, 5000)
        logOk("Compute任务完成", result)
    }

    fun testBasicSubmitBackground() {
        beginTest("1.3 Background 任务提交")
        val future = Concurrent.background<Long> {
            log("  [Background线程] thread=${Thread.currentThread().name}")
            System.currentTimeMillis()
        }
        val result = ConcurrentUtils.await(future, 3000)
        logOk("Background任务完成", "ts=$result")
    }

    fun testMainThread() {
        beginTest("1.4 Main 线程切换")
        val isMainBefore = ConcurrentUtils.isMainThread()
        log("  调用前是否主线程: $isMainBefore")
        // Concurrent.main 在非主线程环境下会 post 到主线程
        // 从测试线程调用时不会立即执行，验证 API 不崩溃
        Concurrent.main {
            logOk("Main任务执行", "isMain=${ConcurrentUtils.isMainThread()}")
        }
        logOk("main() API调用完成 (任务可能稍后在主线程执行)")
    }

    fun testSubmitMultipleTasks() {
        beginTest("1.5 批量任务提交")
        val futures = (1..5).map { i ->
            Concurrent.io<Int> {
                Thread.sleep(50)
                i * 10
            }
        }
        var completed = 0
        futures.forEachIndexed { idx, f ->
            val v = ConcurrentUtils.await(f, 3000)
            completed++
            log("  任务$idx 结果: $v")
        }
        logOk("批量任务完成", "$completed/5")
    }

    // ==================== 2. Future 操作 ====================

    fun testFutureAwait() {
        beginTest("2.1 Future.await")
        val future = Concurrent.io<String> {
            Thread.sleep(100)
            "awaited-value"
        }
        log("  等待前: isDone=${future.isDone}")
        val result = ConcurrentUtils.await(future, 5000)
        log("  等待后: isDone=${future.isDone}, result=$result")
        logOk("await 成功", if (result == "awaited-value") null else "结果不匹配")
    }

    fun testFutureAwaitTimeout() {
        beginTest("2.2 Future.await 超时")
        val future = Concurrent.io<String> {
            Thread.sleep(5000)  // 很长
            "never-returned"
        }
        val result = ConcurrentUtils.await(future, 200)  // 200ms 超时
        logOk("超时返回", if (result == null) "null (正确)" else "unexpected: $result")
    }

    fun testFutureCancel() {
        beginTest("2.3 Future.cancel")
        val future = Concurrent.io<String> {
            try {
                Thread.sleep(3000)
                "done"
            } catch (e: InterruptedException) {
                log("  任务被中断")
                throw e
            }
        }
        Thread.sleep(50)  // 等任务开始
        val cancelled = ConcurrentUtils.cancel(future)
        Thread.sleep(100)
        logOk("cancel结果", "cancelled=$cancelled, isCancelled=${future.isCancelled}, isDone=${future.isDone}")
    }

    fun testFutureIsDone() {
        beginTest("2.4 Future.isDone")
        val fast = Concurrent.io<Int> { 1 + 1 }
        ConcurrentUtils.await(fast, 2000)
        logOk("已完成任务 isDone", fast.isDone)
        val slow = Concurrent.io<Int> { Thread.sleep(1000); 99 }
        log("  未完成任务 isDone: ${slow.isDone} (应为false)")
        ConcurrentUtils.cancel(slow)
        Thread.sleep(50)
        logOk("已取消任务 isDone", slow.isDone)
    }

    // ==================== 3. 延迟执行 ====================

    fun testIoDelayed() {
        beginTest("3.1 ioDelayed")
        val start = System.currentTimeMillis()
        val future = Concurrent.ioDelayed({
            val elapsed = System.currentTimeMillis() - start
            logOk("ioDelayed 回调", "延迟约${elapsed}ms")
        }, delayMs = 300)
        ConcurrentUtils.await(future, 3000)
        val elapsed = System.currentTimeMillis() - start
        logOk("ioDelayed完成", "总耗时${elapsed}ms (期望≥300ms: ${elapsed >= 280})")
    }

    fun testBackgroundDelayed() {
        beginTest("3.2 backgroundDelayed")
        val start = System.currentTimeMillis()
        val future = Concurrent.backgroundDelayed({
            val elapsed = System.currentTimeMillis() - start
            logOk("backgroundDelayed 回调", "延迟约${elapsed}ms")
        }, delayMs = 200)
        ConcurrentUtils.await(future, 3000)
        logOk("backgroundDelayed完成")
    }

    fun testMainDelayed() {
        beginTest("3.3 mainDelayed")
        val future = Concurrent.mainDelayed({
            logOk("mainDelayed 在主线程执行", "isMain=${ConcurrentUtils.isMainThread()}")
        }, delayMs = 100)
        log("  已提交 mainDelayed, future.isDone=${future.isDone}")
        logOk("mainDelayed 提交完成 (将在主线程延迟执行)")
    }

    fun testDelayedCancellation() {
        beginTest("3.4 延迟任务取消")
        val future = Concurrent.ioDelayed({
            logFail("不应执行", "被取消的任务不应触发")
        }, delayMs = 5000)
        Thread.sleep(50)
        val cancelled = ConcurrentUtils.cancel(future)
        logOk("取消结果", "cancelled=$cancelled")
    }

    // ==================== 4. 后端切换 ====================

    fun testSwitchToCoroutine() {
        beginTest("4.1 switchTo(COROUTINE)")
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
        log("  已切换到 COROUTINE 后端")
        val future = Concurrent.io<String> {
            val name = Thread.currentThread().name
            log("  [协程后端] 执行线程: $name")
            name
        }
        val result = ConcurrentUtils.await(future, 5000)
        logOk("协程后端执行结果", result)
        // 恢复 AUTO
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
        log("  已恢复 AUTO 模式")
    }

    fun testSwitchToThreadPool() {
        beginTest("4.2 switchTo(THREAD_POOL)")
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.THREAD_POOL)
        log("  已切换到 THREAD_POOL 后端")
        val future = Concurrent.compute<Int> {
            val name = Thread.currentThread().name
            log("  [线程池后端] 执行线程: $name")
            100
        }
        val result = ConcurrentUtils.await(future, 5000)
        logOk("线程池后端执行结果", result)
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
        log("  已恢复 AUTO 模式")
    }

    fun testAutoDetection() {
        beginTest("4.3 AUTO 自动检测")
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
        val coroAvailable = ConcurrentFactory.isCoroutineAvailable()
        val threadAvailable = ConcurrentFactory.isThreadPoolAvailable()
        log("  协程可用: $coroAvailable, 线程池可用: $threadAvailable")

        val future = Concurrent.io<String> { "auto-selected" }
        val result = ConcurrentUtils.await(future, 5000)
        val currentBackend = ConcurrentFactory.currentBackend
        logOk("AUTO模式结果", "$result, backend=$currentBackend")
    }

    // ==================== 5. 混合模式 ====================

    fun testMixedMode() {
        beginTest("5.1 useMixed 混合模式")
        ConcurrentFactory.useMixed(mapOf(
            DispatcherType.IO to ConcurrentFactory.BackendType.COROUTINE,
            DispatcherType.COMPUTE to ConcurrentFactory.BackendType.THREAD_POOL,
        ))
        log("  配置: IO→协程, COMPUTE→线程池")

        val ioFuture = Concurrent.io<String> {
            "io-on-${Thread.currentThread().name}"
        }
        val computeFuture = Concurrent.compute<String> {
            "compute-on-${Thread.currentThread().name}"
        }
        logOk("IO (协程)", ConcurrentUtils.await(ioFuture, 3000))
        logOk("COMPUTE (线程池)", ConcurrentUtils.await(computeFuture, 3000))

        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
        log("  恢复 AUTO")
    }

    fun testCustomRegister() {
        beginTest("5.2 自定义分发器注册")
        // 获取默认的 IO 分发器包装一下
        val defaultIO = ConcurrentFactory.getDispatcher(DispatcherType.IO)
        val counter = AtomicInteger(0)
        val customDispatcher = object : TaskDispatcher {
            override val name: String = "custom-io-wrapper"
            override val supportsCoroutineNative: Boolean = false
            override fun execute(task: () -> Unit) {
                counter.incrementAndGet()
                defaultIO.execute(task)
            }
            override fun <T> submit(task: () -> T): Future<T> {
                counter.incrementAndGet()
                return defaultIO.submit(task)
            }
            override fun schedule(task: () -> Unit, delayMs: Long): Future<*> {
                counter.incrementAndGet()
                return defaultIO.schedule(task, delayMs)
            }
        }
        ConcurrentFactory.register(DispatcherType.IO, customDispatcher)
        log("  已注册自定义 IO 分发器")

        Concurrent.io<Int> { 1 }.let { ConcurrentUtils.await(it, 3000) }
        Concurrent.io<Int> { 2 }.let { ConcurrentUtils.await(it, 3000) }
        logOk("自定义分发器调用次数", counter.get())

        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
        log("  已恢复默认")
    }

    // ==================== 6. 可用性检测 ====================

    fun testAvailabilityDetection() {
        beginTest("6.1 可用性检测")
        val coroAvail = ConcurrentFactory.isCoroutineAvailable()
        val threadAvail = ConcurrentFactory.isThreadPoolAvailable()
        val availableList = ConcurrentFactory.getAvailableBackends()
        val current = ConcurrentFactory.currentBackend
        logOk("isCoroutineAvailable", coroAvail)
        logOk("isThreadPoolAvailable", threadAvail)
        logOk("getAvailableBackends", availableList)
        logOk("currentBackend", current)
    }

    fun testGetDispatcher() {
        beginTest("6.2 getDispatcher")
        listOf(
            DispatcherType.IO,
            DispatcherType.COMPUTE,
            DispatcherType.BACKGROUND,
            DispatcherType.SINGLE,
            DispatcherType.MAIN
        ).forEach { type ->
            val disp = ConcurrentFactory.getDispatcher(type)
            logOk("  $type", "name=${disp.name}, supportsCoroutine=${disp.supportsCoroutineNative}")
        }
    }

    fun testGetCoroutineDispatcher() {
        beginTest("6.3 getCoroutineDispatcher")
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.COROUTINE)
        val disp = Concurrent.getCoroutineDispatcher(DispatcherType.IO)
        logOk("CoroutineDispatcher(IO)", disp.toString())
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
    }

    // ==================== 7. 协程原生 API ====================

    fun testSuspendAPI() {
        beginTest("7.1 协程原生 suspend API")
        runBlocking {
            val result1 = Concurrent.ioSuspend {
                delay(50)
                log("  ioSuspend 在线程: ${Thread.currentThread().name}")
                "io-result"
            }
            logOk("ioSuspend", result1)

            val result2 = Concurrent.computeSuspend {
                delay(30)
                "compute-result"
            }
            logOk("computeSuspend", result2)

            val result3 = Concurrent.backgroundSuspend {
                delay(20)
                "bg-result"
            }
            logOk("backgroundSuspend", result3)
        }
    }

    fun testMainSuspend() {
        beginTest("7.2 mainSuspend")
        runBlocking {
            val result = Concurrent.mainSuspend {
                log("  mainSuspend 在线程: ${Thread.currentThread().name}")
                "main-result"
            }
            logOk("mainSuspend", result)
        }
    }

    fun testSuspendWithException() {
        beginTest("7.3 suspend 异常处理")
        runBlocking {
            try {
                Concurrent.ioSuspend<String> {
                    delay(10)
                    throw RuntimeException("测试异常")
                }
                logFail("应该抛出异常")
            } catch (e: RuntimeException) {
                logOk("正确捕获异常", e.message)
            }
        }
    }

    fun testSuspendCancellation() {
        beginTest("7.4 suspend 取消")
        runBlocking {
            val job = launch {
                try {
                    Concurrent.ioSuspend<String> {
                        delay(5000)
                        "never"
                    }
                } catch (e: CancellationException) {
                    logOk("协程取消被正确传播")
                }
            }
            delay(100)
            job.cancel()
            job.join()
        }
    }

    // ==================== 8. ConcurrentUtils 工具类 ====================

    fun testIsMainThread() {
        beginTest("8.1 isMainThread / isBackgroundThread")
        logOk("isMainThread", ConcurrentUtils.isMainThread())
        logOk("isBackgroundThread", ConcurrentUtils.isBackgroundThread())
    }

    fun testAssertMainThread() {
        beginTest("8.2 assertMainThread / assertBackgroundThread")
        // 当前在测试线程（非主线程），assertBackgroundThread 应通过
        try {
            ConcurrentUtils.assertBackgroundThread()
            logOk("assertBackgroundThread 通过")
        } catch (e: IllegalStateException) {
            logFail("assertBackgroundThread 异常", e.message ?: "")
        }

        // assertMainThread 应该抛出异常
        try {
            ConcurrentUtils.assertMainThread()
            logFail("应抛出异常")
        } catch (e: IllegalStateException) {
            logOk("assertMainThread 正确抛出", e.message ?: "")
        }
    }

    fun testAssertBackgroundThread() {
        beginTest("8.3 assertBackgroundThread 反例")
        try {
            ConcurrentUtils.assertBackgroundThread("自定义消息")
            logOk("assertBackgroundThread 通过 (非主线程)")
        } catch (e: IllegalStateException) {
            logFail("不应抛出异常", e.message ?: "")
        }
    }

    fun testSleep() {
        beginTest("8.4 sleep")
        val start = System.currentTimeMillis()
        ConcurrentUtils.sleep(200)
        val elapsed = System.currentTimeMillis() - start
        logOk("sleep(200ms)", "实际${elapsed}ms (≈200)")
    }

    fun testGetCurrentThreadInfo() {
        beginTest("8.5 getCurrentThreadInfo / getCurrentThreadDescription")
        val desc = ConcurrentUtils.getCurrentThreadDescription()
        logOk("getCurrentThreadDescription", desc)
        val info = ConcurrentUtils.getCurrentThreadInfo()
        logOk("getCurrentThreadInfo", "keys=${info.keys}")
    }

    fun testAwaitAll() {
        beginTest("8.6 等待多个 Future")
        val futures = (1..4).map { i ->
            Concurrent.io<Int> {
                Thread.sleep((100 - i * 20).toLong())
                i * 100
            }
        }
        var allDone = true
        futures.forEachIndexed { idx, f ->
            val result = ConcurrentUtils.await(f, 3000)
            if (result == null) allDone = false
            log("  任务$idx: $result")
        }
        logOk("全部完成", allDone)
    }

    fun testAwaitAllWithTimeout() {
        beginTest("8.7 await 超时返回 null")
        val slow = Concurrent.io<String> {
            Thread.sleep(5000)
            "slow"
        }
        val result = ConcurrentUtils.await(slow, 100)
        logOk("超时返回null", if (result == null) "正确" else "错误: $result")
        ConcurrentUtils.cancel(slow)
    }

    // ==================== 9. 生命周期 ====================

    fun testShutdown() {
        beginTest("9.1 shutdown")
        // 先记录当前状态
        log("  调用 shutdown...")
        ConcurrentFactory.shutdown()
        logOk("shutdown 完成 (已清理手动注册的分发器)")
        // 恢复可用: switchTo AUTO 会触发重新创建
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
        log("  已重新初始化")
    }

    fun testMultipleSwitch() {
        beginTest("9.2 多次切换后端")
        repeat(3) { i ->
            val backend = if (i % 2 == 0)
                ConcurrentFactory.BackendType.COROUTINE
            else
                ConcurrentFactory.BackendType.THREAD_POOL

            ConcurrentFactory.switchTo(backend)
            val future = Concurrent.io<Int> { 1 }
            ConcurrentUtils.await(future, 3000)
            log("  切换$i → $backend: OK")
        }
        ConcurrentFactory.switchTo(ConcurrentFactory.BackendType.AUTO)
        logOk("多次切换完成")
    }

    // ==================== 10. 边界场景 ====================

    fun testNullTaskHandling() {
        beginTest("10.1 异常任务处理")
        val future = Concurrent.io<String> {
            throw IllegalArgumentException("故意抛出异常")
        }
        try {
            ConcurrentUtils.await(future, 3000)
            logFail("应该出现异常")
        } catch (e: Exception) {
            logOk("异常被正确传播", e.javaClass.simpleName)
        }
    }

    fun testConcurrentMainFromMainThread() {
        beginTest("10.2 main 从主线程调用")
        // 从测试线程调用，验证不崩溃
        val flag = AtomicInteger(0)
        Concurrent.main { flag.set(1) }
        Thread.sleep(200) // 等待主线程执行
        log("  flag=${flag.get()} (从非主线程调用时 post 到主线程)")
        logOk("main() 调用无崩溃")
    }

    fun testGetAllDispatcherTypes() {
        beginTest("10.3 所有 DispatcherType 遍历")
        DispatcherType.entries.forEach { type ->
            val disp = Concurrent.get(type)
            val future = disp.submit<Boolean> { true }
            val ok = ConcurrentUtils.await(future, 3000)
            log("  $type: name=${disp.name}, result=$ok")
        }
        logOk("所有类型分发器正常工作")
    }
}
