# 04. 形状、性能与 API

本节说明形状裁剪、叠加和常用 API 速查。

## 适用条件

- 头像、卡片封面、角标或水印需要形状处理。
- 接入前需要确认内存和线程边界。
- 已知道场景，只想查工具类。

## 推荐做法

```kotlin
val avatar = BitmapShapeUtils.toCircleWithBorder(bitmap, 4, Color.WHITE)
```

## 可复制 Demo

```kotlin
import android.graphics.Color
import com.itg.itg_bitmap.shape.BitmapShapeUtils

val circle = BitmapShapeUtils.toCircleWithBorder(
    bitmap = sourceBitmap,
    borderWidth = 6,
    borderColor = Color.WHITE
)

imageView.setImageBitmap(circle)
```

## 性能建议

- 大图先采样解码，再做特效和形状处理。
- 图像处理不要阻塞主线程。
- 列表缩略图可考虑 `Bitmap.Config.RGB_565` 降低内存。
- 长链路处理要及时释放不再使用的中间 Bitmap。

## API 速查

| 分类 | 工具类 | 常用 API |
| --- | --- | --- |
| 基础 | `BitmapUtils` | `createBitmap`、`copy`、`isValid`、`getInfo`、`toByteArray` |
| 解码 | `BitmapDecodeUtils` | `decodeFromFile`、`decodeSampledBitmap`、`decodeFromUri`、`getImageDimensions` |
| 变换 | `BitmapTransformUtils` | `scale`、`rotate`、`crop`、`centerCrop`、`applyMatrix` |
| 颜色 | `BitmapColorUtils` | `grayscale`、`adjustBrightness`、`sepia`、`tint` |
| 特效 | `BitmapEffectUtils` | `blur`、`stackBlur`、`sharpen`、`pixelate`、`vignette` |
| 压缩 | `BitmapCompressUtils` | `compress`、`smartCompress`、`compressToTargetSize` |
| 保存 | `BitmapSaveUtils` | `saveToFile`、`saveToGallery`、`saveToCache`、`saveToTempFile` |
| 形状 | `BitmapShapeUtils` | `toCircle`、`roundCorners`、`toOval`、`overlay` |

## 验证方式

- 圆形和圆角输出应保留透明背景。
- API 返回 `null` 时业务应有降级路径。

[返回 README](../README.md)