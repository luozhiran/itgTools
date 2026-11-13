# 03. Assets 与 Okio

本节说明 assets 读取/复制和 Okio 版本工具。

## 适用条件

- 需要读取 `src/main/assets` 中的配置、模板或离线资源。
- 需要复制 assets 到 app 文件目录。
- 需要把 `res/raw` 中的资源复制到本地 `File`。
- 需要使用 Okio 的 `ByteString`、`Buffer`、source/sink。

## 推荐做法

```kotlin
import com.itg.itg_file.copyFromAssetSuspend
import com.itg.itg_file.copyFromRawSuspend

val assetFile = context.filesDir.resolve("templates/report.html")
val assetCopied = assetFile.copyFromAssetSuspend(context, "templates/report.html")

val rawFile = context.filesDir.resolve("defaults/avatar.png")
val rawCopied = rawFile.copyFromRawSuspend(context, R.raw.default_avatar)
```

以上挂起函数可在 `viewModelScope.launch` 等协程中调用，复制在 IO 调度器执行。

## 同步调用与进度回调

```kotlin
import com.itg.itg_file.copyFromAsset
import com.itg.itg_file.copyFromRaw

val target = context.filesDir.resolve("data/model.bin")
val copied = target.copyFromAsset(
    context = context,
    assetPath = "models/model.bin",
    overwrite = false,
    onProgress = { bytes, total ->
        // 在当前调用线程处理进度；total 可能为 -1。
    },
    onFailure = { error -> /* 在页面层提示 error.message */ }
)

val license = context.filesDir.resolve("license.txt")
val rawCopied = license.copyFromRaw(context, R.raw.license)
```

同步扩展方法会阻塞调用线程，适合在已有工作线程中使用。扩展方法复用 `AssetUtils` 的写入流程，返回 `Boolean` 表示是否复制成功。

## 原有工具类调用

```kotlin
val text = AssetUtils.readAssetText(context, "config/default.json")
OkioAssetUtils.copyAssetToFile(context, "db/base.db", destPath)
```

## 可复制 Demo

```kotlin
import com.itg.itg_file.resource.AssetUtils
import com.itg.itg_file.resource.OkioAssetUtils

val config = AssetUtils.readAssetText(context, "config/default.json")

val copied = OkioAssetUtils.copyAssetToFile(
    context = context,
    assetPath = "templates/report.html",
    destPath = context.filesDir.resolve("report.html").absolutePath,
    overwrite = true
)
```

## 关键说明

- assets 路径是相对 `assets/` 的路径，不要以 `/` 开头。
- 读取大 assets 时优先使用 streaming 或复制到文件。
- `OkioAssetUtils` 适合二进制、Buffer 和进度复制场景。
- 使用 `context.applicationContext` 可避免异步任务持有 Activity。
- `File.copyFromAssetSuspend` 和 `File.copyFromRawSuspend` 的进度回调在 IO 线程执行，更新界面时请切换到主线程。

## 验证方式

- assets 文件名拼错时 API 应返回失败结果而不是崩溃。
- 复制成功后目标文件大小应大于 0。

[返回 README](../README.md)
