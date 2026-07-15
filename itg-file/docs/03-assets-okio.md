# 03. Assets 与 Okio

本节说明 assets 读取/复制和 Okio 版本工具。

## 适用条件

- 需要读取 `src/main/assets` 中的配置、模板或离线资源。
- 需要复制 assets 到 app 文件目录。
- 需要使用 Okio 的 `ByteString`、`Buffer`、source/sink。

## 推荐做法

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

## 验证方式

- assets 文件名拼错时 API 应返回失败结果而不是崩溃。
- 复制成功后目标文件大小应大于 0。

[返回 README](../README.md)