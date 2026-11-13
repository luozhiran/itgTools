# 07. ZIP 解压

将本地 ZIP 压缩包解压到一个新目录，并在全部文件完成校验后交付该目录。

## 适用条件

- 输入是本地 `.zip` 文件路径，目标是普通文件系统目录。
- 目标目录尚不存在；方法不会覆盖已有目录。
- 默认最多 10,000 个条目、解压后总大小最多 1 GiB。可通过参数调整上限。

## 推荐做法

```kotlin
import com.itg.itg_file.archive.ZipUtils

val zipPath = context.filesDir.resolve("download/package.zip").absolutePath
val outputDir = context.filesDir.resolve("packages/package-v1")

var failureReason: String? = null
val success = ZipUtils.extractSuspend(zipPath, outputDir.absolutePath, onFailure = { error ->
    failureReason = error.message
})
if (success) {
    // 此时 outputDir 中的全部 ZIP 条目已经写入并通过大小、CRC 校验。
} else {
    // 根据 failureReason 提示用户；不要读取 outputDir 作为本次结果。
}
```

在 `viewModelScope.launch` 或 `lifecycleScope.launch` 内调用 `extractSuspend`。该方法在 `Dispatchers.IO` 执行解压，调用方协程挂起等待结果。取消协程会中断正在执行的解压；如果解压已开始，中断失败会通过 `onFailure` 回调传递，协程取消仍按协程规则传播。回调在 IO 线程上运行；如需更新界面，请在调用返回主线程后处理。提交已经发生后，取消不会撤销结果。

## 关键说明

- `extract` 是同步接口，会阻塞调用线程；`extractSuspend` 是协程异步接口，在 IO 线程解压。两者都返回 `Boolean`，可选的 `onFailure` 回调在失败时收到原因。
- 解压过程中写入目标目录旁边的随机临时目录。正常失败会删除临时目录；进程被杀死时可能留下临时目录，但目标目录不会出现半成品。
- 对每个条目检查路径、声明大小和 CRC，并限制条目数量和解压后的总字节数。路径中的 `..`、绝对路径和反斜杠会被拒绝。
- 符号链接条目会按普通文件写入，不会创建文件系统符号链接；大小写或 Unicode 归一化后重名的路径会被拒绝。
- 临时目录和目标目录位于同一父目录，但 `renameTo` 的原子性取决于平台；若其他进程同时创建同名目标目录，调用方不能依赖这一步解决竞争。
- 每个文件写入后都会同步到磁盘，文件很多时可能较慢；目录元数据没有单独同步，断电后的持久性不由此方法保证。
- ZIP 之外的 `7z`、`RAR`、`tar` 格式不受支持。现有 `OkioReadUtils.readGzip` 只把 `.gz` 内容读取到内存，不会创建目录。
- 如果需要更新已存在的资源目录，先解压到新的版本目录，再由业务切换所使用的目录。旧目录可在确认不再使用后清理。
- 应用启动时可清理确认已无解压任务使用的遗留临时目录。不要在解压任务仍运行时清理它们。

## 验证方式

- 成功后检查 `ZipUtils.extractSuspend(...)` 返回 `true`，并读取预期文件。
- 损坏、越界或超限的 ZIP 应返回 `false`，且目标目录不应出现。

[返回 README](../README.md)
