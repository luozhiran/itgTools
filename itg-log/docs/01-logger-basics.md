# 01. Logger 基础

本节说明如何创建 `TestLogger` 并记录一组带耗时的步骤。

## 适用条件

- 开发或测试阶段需要观察流程耗时。
- 需要每个步骤自动编号。
- 需要把日志输出到 Logcat 或 UI 文本区域。

## 推荐做法

```kotlin
val logger = TestLogger("Demo") { outputToLogcat("Demo") }
```

## 可复制 Demo

```kotlin
import com.itg.log.TestLogger

class DemoModel {
    var onLog: ((String) -> Unit)? = null

    private val logger = TestLogger("Demo") {
        tag = "Demo"
        outputToLogcat("Demo")
        outputToCallback { line -> onLog?.invoke(line) }
    }

    fun runCase() {
        logger.beginTest("文件读取")
        logger.i("开始读取")
        logger.ok("读取完成")
        logger.endTest()
    }
}
```

## 关键说明

- `beginTest(name)` 会重置计时和步骤号。
- `endTest()` 返回本次总耗时毫秒数。
- `ok/fail` 是带成功/失败标记的便捷日志。
- `step()` 返回结构化 `StepRecord`。
- UI 回调中如需操作 View，应切回主线程。

## 验证方式

- 每次 `beginTest` 后第一条日志步骤号应从 1 开始。
- 输出内容应包含 `D+` 增量耗时和 `T` 累计耗时。

[返回 README](../README.md)