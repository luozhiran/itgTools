# 04. API 速查

本节汇总 `itg-log` 常用类型和方法。

## API 速查

| 类型 | API | 说明 |
| --- | --- | --- |
| `ItgLog` | `globalEnabled` | 全局开关 |
| `TestLogger` | `beginTest`、`endTest` | 开始/结束一组步骤日志 |
| `TestLogger` | `v/d/i/w/e` | 按级别输出日志 |
| `TestLogger` | `ok/fail` | 成功/失败便捷标记 |
| `TestLogger` | `step`、`mark` | 记录结构化步骤或时间点 |
| `TestLogger` | `currentStep`、`elapsedMs`、`isStarted` | 当前状态查询 |
| `LogConfig.Builder` | `tag`、`minLevel`、`enabled`、`formatter` | 配置标签、级别、开关、格式 |
| `LogConfig.Builder` | `outputToLogcat`、`outputToCallback`、`addOutput` | 配置输出目标 |
| `LogFormatter` | `format(entry)` | 自定义格式化 |
| `LogOutput` | `write(entry, formatted)` | 自定义输出 |
| `LogEntry` | `step`、`deltaMs`、`totalMs`、`level`、`tag`、`message` | 结构化日志字段 |

## 可复制 Demo

```kotlin
import com.itg.log.TestLogger
import com.itg.log.core.LogLevel

val logger = TestLogger("Api") {
    tag = "Api"
    minLevel = LogLevel.INFO
    outputToLogcat("Api")
}

logger.beginTest("接口测试")
logger.i("请求开始")
val mark = logger.mark()
logger.ok("请求结束, mark=$mark")
logger.endTest()
```

## 关键说明

- `minLevel` 会过滤低于阈值的输出。
- `step()` 即使在输出关闭时仍返回 `StepRecord`，但不产生格式化输出。
- 自定义输出目标要自行处理阻塞和异常。

## 验证方式

- 设置 `minLevel = LogLevel.INFO` 后 DEBUG 日志不应输出。
- `endTest()` 返回值应接近本次测试总耗时。

[返回 README](../README.md)