# 01. 基础与解码

本节说明基础 Bitmap 操作和安全解码图片。

## 适用条件

- 需要创建、复制、检查 Bitmap。
- 需要从资源、文件、Uri、URL 或字节数组解码。
- 大图需要控制目标尺寸，避免 OOM。

## 推荐做法

```kotlin
val bitmap = BitmapDecodeUtils.decodeSampledBitmap(path, 800, 600)
```

## 可复制 Demo

```kotlin
import android.graphics.Bitmap
import com.itg.itg_bitmap.core.BitmapUtils
import com.itg.itg_bitmap.decode.BitmapDecodeUtils

val bitmap = BitmapDecodeUtils.decodeSampledBitmap(
    path = photoPath,
    reqWidth = 1080,
    reqHeight = 1080,
    config = Bitmap.Config.RGB_565
) ?: return

if (BitmapUtils.isValid(bitmap)) {
    val info = BitmapUtils.getInfo(bitmap)
    val bytes = BitmapUtils.getByteCount(bitmap)
}
```

## 关键说明

- `decodeSampledBitmap` 适合本地大图预览。
- `decodeFromUrl` 是同步网络读取，必须放后台线程。
- `getImageDimensions` 可只读尺寸，不加载完整图片。
- `BitmapUtils.copy` 返回新实例，原图生命周期由调用方管理。
- 返回 `Bitmap?` 的 API 失败时返回 `null`。

## 验证方式

- 对 4K 或更大图片采样后，输出尺寸应不超过预期数量级。
- `BitmapUtils.getSizeMB(bitmap)` 应显著小于原始全尺寸解码。

[返回 README](../README.md)