# 02. 格式化与输出

本节说明如何替换日志格式和输出目标。

## 适用条件

- 默认紧凑格式不满足业务展示。
- 日志需要写入文件、上传分析或进入自定义 UI。
- 同一条日志需要输出到多个目标。

## 推荐做法

```kotlin
val logger = TestLogger("Api") {
    formatter = TimestampFormatter()
    outputToLogcat("Api")
}
```

## 可复制 Demo

```kotlin
import com.itg.log.TestLogger
import com.itg.log.core.LogEntry
import com.itg.log.format.LogFormatter
import com.itg.log.output.LogOutput
import java.io.File

class JsonFormatter : LogFormatter {
    override fun format(entry: LogEntry): String {
        return "{\"step\":${entry.step},\"total\":${entry.totalMs},\"msg\":\"${entry.message}\"}"
    }
}

class FileOutput(private val file: File) : LogOutput {
    override fun write(entry: LogEntry, formatted: String) {
        file.appendText(formatted + "\n")
    }
}

val logger = TestLogger("Api") {
    formatter = JsonFormatter()
    addOutput(FileOutput(File(context.cacheDir, "api-test.log")))
    outputToLogcat("Api")
}
```

## 关键说明

- `LogFormatter.format(entry)` 只负责把结构化日志变成字符串。
- `LogOutput.write(entry, formatted)` 负责输出目标。
- 未配置输出时，`LogConfig` 会使用默认输出。
- 文件输出要注意 I/O 线程和文件权限。

## 验证方式

- 自定义 Formatter 的输出应同时出现在所有 Output 中。
- 文件输出失败不要影响主流程，必要时在自定义 Output 内部 try-catch。

[返回 README](../README.md)