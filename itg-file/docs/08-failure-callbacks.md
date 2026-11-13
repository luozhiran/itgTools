# 08. 文件操作失败通知

实际读写、复制、移动、删除、资源读取和哈希操作提供末位可空的 `onFailure` 参数。需要展示失败原因时传入回调。

## 适用条件

- 调用方需要在操作失败时得到一次明确通知。
- `exists()` 返回 `false`、目录列表为空等查询结果按正常结果处理。
- 调用方负责把回调中的原因转换为界面文案；文件工具模块不直接弹出提示。

## 推荐做法

```kotlin
import com.itg.itg_file.core.FileUtils

var failureMessage: String? = null
val copied = FileUtils.copy(
    srcPath = sourceFile.absolutePath,
    destPath = destinationFile.absolutePath,
    onFailure = { error -> failureMessage = error.message }
)
if (!copied) {
    // 在页面层显示 failureMessage ?: "文件复制失败"
}
```

`onFailure` 默认为 `null`；传入 `null` 时只得到 `false`、`null` 或 `-1` 等失败返回值。传入回调时，失败会先通知回调，再返回原有失败值；成功时不调用回调。原有尾随 lambda 调用方式仍可使用兼容入口。

## 在协程中调用

```kotlin
import com.itg.itg_file.core.FileCoroutines
import com.itg.itg_file.read.FileReadUtils

var failureMessage: String? = null
val content = FileCoroutines.withIo {
    FileReadUtils.readText(
        path = sourceFile.absolutePath,
        onFailure = { failureMessage = it.message }
    )
}
if (content == null) {
    // 在页面层显示 failureMessage ?: "文件读取失败"
}
```

失败回调在执行操作的线程上运行。协程示例在 `withIo` 返回后再处理界面更新。`File.copyFromAsset`、`File.copyFromRaw`、`ZipUtils.extract` 和 `CleanupExecutor.execute` 也将可空失败回调放在末位；清理管理器仍通过 `onResult` 返回结果。

`createFileSuspend` 等只返回成功状态的协程方法也提供末位可空的 `onFailure`：

```kotlin
FileUtils.createFileSuspend(
    path = target.absolutePath,
    onResult = { created -> /* 处理创建结果 */ },
    onFailure = { error -> /* 处理具体失败步骤 */ }
)
```

原本通过 `onResult(result, error)` 返回错误的协程读取和哈希方法会传递实际失败原因。协程取消仍会传播给调用方。
协程方法把 `onFailure` 放在最后；同时传入结果回调和失败回调时，请显式写 `onResult = { ... }`。

## 关键说明

- 校验失败时，回调会说明未通过的具体检查；底层读写抛出异常时，回调收到原异常及其 `cause`。部分文件系统 API 只返回 `false`，此时只能指出失败的操作步骤，无法取得系统错误码。
- 实现中在失败发生处传递异常或标明失败步骤，`FileFailureCallbacks` 负责统一捕获和通知。返回值检查仅用于发现遗漏的失败分支；出现“未报告具体失败步骤”表示内部实现需要补充失败原因。
- 私有操作实现显式接收失败报告器并传给下层调用；不再通过线程状态传递失败信息。未提供 `onFailure` 时，报告器保留原有失败返回值。
- `readLinesStreaming` 和 `readChunks` 的负数结果表示失败；传入回调时只在负数结果下通知，正常提前停止仍返回已处理数量。
- `copyAndVerify` 的校验不一致也会通知失败。`verify()` 和 `compareFiles()` 的 `false` 可表示正常的不匹配，不纳入失败回调。
- 回调抛出的异常会被捕获并打印，操作仍返回原有失败值。

## 验证方式

- 用不存在的源文件调用并传入失败回调，确认收到一次通知且返回失败值。
- 用有效文件调用并传入失败回调，确认操作成功且不触发回调。

[返回 README](../README.md)
