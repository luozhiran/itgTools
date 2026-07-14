# itg-log

轻量级步骤计时日志库 — 可插拔 Formatter / Output，线程安全。

## 特性

- **步骤序号** — 每次 `log()` 自动递增，`beginTest()` 时重置
- **增量计时** — 显示距上一步的耗时 (`D+`)
- **累计计时** — 显示距测试开始的耗时 (`T`)
- **线程安全** — `@Synchronized` + `AtomicInteger`，多线程并发调用不乱序
- **可插拔 Formatter** — 内置紧凑型 / 时间戳 / ANSI 着色三种格式，可实现自定义
- **多目标 Output** — 同时输出到 logcat + UI 回调 + 文件等，组合自由
- **DSL 配置** — Builder 模式，一行代码完成配置
- **零依赖** — 仅依赖 Android SDK，无第三方库

## 输出示例

```
===== 1.1 IO 任务提交 =====
[#01 D+   0ms T    0ms] [IO线程] 执行中... thread=itg-io-1
[#02 D+  52ms T   52ms] IO任务完成, flag=42, result=42: 符合预期 ✅
----- 1.1 IO 任务提交 结束, 总耗时: 55ms -----

===== 1.2 Compute 任务提交 =====
[#01 D+   0ms T    0ms] [Compute线程] 执行计算... thread=itg-compute-2
[#02 D+ 120ms T  120ms] Compute任务完成: sum=500000500000 ✅
----- 1.2 Compute 任务提交 结束, 总耗时: 122ms -----
```

每条日志的格式为：`[#步骤号 D+增量ms T累计ms] 消息内容`

- `#01` — 步骤序号（`beginTest` 时从 1 重新计数）
- `D+ 52ms` — 距离上一条日志的增量耗时
- `T  52ms` — 从 `beginTest` 开始的累计耗时

## 快速开始

### 1. 添加依赖

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":itg-log"))
}
```

### 2. 基础使用

```kotlin
import com.itg.log.TestLogger
import com.itg.log.config.configureLogger
import com.itg.log.output.CallbackOutput

class MyTestModel {
    var onLog: ((String) -> Unit)? = null

    private val logger = TestLogger("MyTest") {
        tag = "MyTest"
        outputToLogcat()
        outputToCallback { msg -> onLog?.invoke(msg) }
    }

    fun testSomething() {
        logger.beginTest("基本操作测试")

        logger.i("开始执行操作")
        // ... 执行操作 ...
        logger.d("操作完成, 结果: 42")

        logger.ok("测试通过")
        logger.endTest()
    }
}
```

### 3. Activity 中显示日志

```kotlin
class TestActivity : AppCompatActivity() {
    private val model = MyTestModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        // ...
        model.onLog = { text ->
            runOnUiThread {
                logTextView.append("\n$text")
            }
        }
        // 点击按钮触发测试
        button.setOnClickListener {
            Thread { model.testSomething() }.start()
        }
    }
}
```

## 架构

```
TestLogger (门面)
├── StepTimer      (计时引擎 — @Synchronized 线程安全)
├── LogConfig      (配置 — Builder DSL)
├── LogFormatter   (格式化策略 — 可替换)
└── List<LogOutput> (输出目标 — 可多个)
      ├── LogcatOutput       → android.util.Log
      ├── CallbackOutput     → (String) -> Unit 回调
      └── CompositeOutput    → 组合多个 Output
```

## API 参考

### TestLogger — 门面

| 方法 | 说明 |
|------|------|
| `beginTest(name?)` | 开始测试，输出标题，重置计时器 |
| `endTest(): Long` | 结束测试，输出总耗时 |
| `d(msg)` / `i(msg)` / `w(msg)` / `e(msg)` / `v(msg)` | 按级别输出日志 |
| `ok(msg)` | 输出 `✅` 成功标记 |
| `fail(msg)` | 输出 `❌` 失败标记 |
| `step(msg, level?): StepRecord` | 记录步骤，返回结构化数据 |
| `mark(): Long` | 标记时间点（不增步骤），返回 ms |
| `currentStep` | 当前步骤序号 |
| `elapsedMs` | 测试已运行时长 |
| `title` | 当前测试标题 |

### StepTimer — 计时引擎

| 方法 | 说明 |
|------|------|
| `begin()` | 开始计时，重置计数器 |
| `step(msg, level?, tag?): LogEntry` | 记录步骤（线程安全） |
| `mark(): Long` | 标记时间点 |
| `reset()` | 重置计时器 |
| `elapsedMs` | 已运行时长 |
| `currentStep` | 当前步骤号 |

### LogConfig — 配置

```kotlin
val config = configureLogger {
    tag = "MyTest"                          // 默认标签
    minLevel = LogLevel.DEBUG               // 最低输出级别
    formatter = CompactFormatter()          // 自定义格式
    headerFormatter = { "═══ $it ═══" }      // 标题格式
    outputToLogcat("MyTag")                 // 输出到 logcat
    outputToCallback { msg -> /* ... */ }   // 输出到回调
    addOutput(MyCustomOutput())             // 自定义输出
}
```

### LogFormatter — 格式化器

| 实现 | 输出示例 |
|------|---------|
| `CompactFormatter` (默认) | `[#01 D+   0ms T    0ms] 消息` |
| `CompactFormatter(stepWidth=3, deltaWidth=6)` | `[#001 D+     0ms T    0ms] 消息` |
| `TimestampFormatter` | `12:30:45.123 [#01 D+   0ms T    0ms] 消息` |
| `ColoredFormatter` | 带 ANSI 颜色代码（终端用） |

自定义 Formatter：

```kotlin
class MyFormatter : LogFormatter {
    override fun format(entry: LogEntry): String {
        return "[${entry.step}] ${entry.message} (${entry.deltaMs}ms)"
    }
}
```

### LogOutput — 输出目标

| 实现 | 说明 |
|------|------|
| `LogcatOutput(tag?)` | 写入 Android Logcat |
| `CallbackOutput { msg -> }` | 通过回调输出（适合 UI 显示） |
| `CompositeOutput(o1, o2, ...)` | 同时输出到多个目标 |

自定义 Output：

```kotlin
// 写入文件
class FileOutput(private val file: File) : LogOutput {
    override fun write(entry: LogEntry, formatted: String) {
        file.appendText("$formatted\n")
    }
}
```

### LogEntry — 日志数据

| 字段 | 类型 | 说明 |
|------|------|------|
| `step` | `Int` | 步骤序号 |
| `deltaMs` | `Long` | 距上一步耗时 |
| `totalMs` | `Long` | 距测试开始耗时 |
| `level` | `LogLevel` | 日志级别 |
| `tag` | `String` | 标签 |
| `message` | `String` | 消息正文 |
| `timestampMs` | `Long` | 系统时间戳 |

## 高级用法

### 自定义 Formatter

```kotlin
// JSON 格式输出（适合日志分析）
class JsonFormatter : LogFormatter {
    override fun format(entry: LogEntry): String {
        return """{"step":${entry.step},"delta":${entry.deltaMs},"total":${entry.totalMs},"msg":"${entry.message}"}"""
    }
}

val logger = TestLogger("ApiTest", configureLogger {
    formatter = JsonFormatter()
    outputToLogcat()
})
```

### 多目标输出

```kotlin
val logger = TestLogger("MyTest", configureLogger {
    addOutput(CompositeOutput(
        LogcatOutput("MyTag"),                    // Android Studio 可见
        CallbackOutput { uiTextView.append(it) }, // 界面实时显示
        FileOutput(File(cacheDir, "test.log"))     // 持久化到文件
    ))
})
```

### 与协程配合

```kotlin
// 协程中跨线程调用，线程安全
class MyTest {
    val logger = TestLogger("CoroutineTest") { outputToLogcat() }

    fun test() = runBlocking {
        logger.beginTest("协程并发测试")

        val jobs = (1..5).map { i ->
            launch(Dispatchers.IO) {
                logger.d("协程 $i 开始")       // ← IO线程写入
                delay(100)
                logger.ok("协程 $i 完成")       // ← 步骤序号严格递增
            }
        }
        jobs.joinAll()
        logger.endTest()
    }
}
```

输出：
```
===== 协程并发测试 =====
[#01 D+   0ms T    0ms] 协程 1 开始
[#02 D+   2ms T    2ms] 协程 3 开始
[#03 D+   1ms T    3ms] 协程 2 开始
[#04 D+   0ms T    3ms] 协程 5 开始
[#05 D+   0ms T    3ms] 协程 4 开始
[#06 D+  98ms T  101ms] 协程 1 完成 ✅
[#07 D+   1ms T  102ms] 协程 3 完成 ✅
[#08 D+   0ms T  102ms] 协程 5 完成 ✅
[#09 D+   0ms T  102ms] 协程 2 完成 ✅
[#10 D+   1ms T  103ms] 协程 4 完成 ✅
----- 协程并发测试 结束, 总耗时: 105ms -----
```

## 线程安全

### 设计

| 组件 | 机制 | 说明 |
|------|------|------|
| `StepTimer.step()` | `@Synchronized` | 步骤递增 + 计时在同一个临界区内，多线程串行执行 |
| `TestLogger.beginTest()` | `@Synchronized` | 生命周期操作互斥 |
| `stepCounter` | `AtomicInteger` | 读取步骤号无需加锁 |

### 并发行为

```
Thread-A (IO)         Thread-B (Compute)
    │                      │
    │ logger.d("A")        │ logger.d("B")
    ├─ timer.step() 获得锁  │
    │  step=1, delta=0      │
    │  释放锁               │
    │                 ├─ timer.step() 获得锁
    │                 │  step=2, delta=正确(基于A)
    │                 │  释放锁
```

- 步骤号严格递增，无跳号 / 重复
- 增量耗时基于前一步的 `lastStepTime`，不会出现为 0 的错误增量
- 读取操作（`currentStep`、`elapsedMs`）无锁，性能无影响

## 模块信息

| 项 | 值 |
|----|-----|
| artifactId | `itg-log` |
| groupId | `com.itg` |
| minSdk | 21 |
| 依赖 | 仅 Android SDK |
| 语言 | Kotlin |
