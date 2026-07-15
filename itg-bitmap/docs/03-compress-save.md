# 03. 压缩与保存

本节说明图片上传前压缩和保存到不同位置。

## 适用条件

- 上传、分享或缓存前需要限制图片尺寸或字节大小。
- 需要保存到 app 私有目录、缓存目录、临时文件或相册。
- 需要适配 Android 10+ 分区存储。

## 推荐做法

```kotlin
val bytes = BitmapCompressUtils.smartCompress(bitmap, 1280, 1280, 300)
val file = BitmapSaveUtils.saveToCache(context, bitmap, "avatar.jpg")
```

## 可复制 Demo

```kotlin
import android.graphics.Bitmap
import com.itg.itg_bitmap.compress.BitmapCompressUtils
import com.itg.itg_bitmap.save.BitmapSaveUtils

val uploadBytes = BitmapCompressUtils.compress(
    bitmap = sourceBitmap,
    maxWidth = 1280,
    maxHeight = 1280,
    quality = 82,
    format = Bitmap.CompressFormat.JPEG
) ?: return

val cacheFile = BitmapSaveUtils.saveToCache(
    context = context,
    bitmap = sourceBitmap,
    name = "preview.jpg",
    format = Bitmap.CompressFormat.JPEG,
)
```

## 关键说明

- 缩略图优先从文件采样解码，避免先全量加载再压缩。
- JPEG/WebP 的 quality 对体积影响明显；PNG 是无损格式，quality 不一定减小体积。
- `saveToGallery` 需要 `Context`，Android 10+ 走 MediaStore。
- `saveToTempFile` 适合分享或上传临时文件，用完应删除。
- 保存到公共目录仍要考虑系统存储策略和权限。

## 验证方式

- 压缩输出字节数组大小应满足上传限制。
- 保存成功后返回的 `File` 或 `Uri` 不应为空。

[返回 README](../README.md)