# 02. 变换、调色与滤镜

本节说明几何变换、颜色调整和特效滤镜。

## 适用条件

- 图片已经解码为有效 Bitmap。
- 需要缩放、旋转、裁剪、翻转。
- 需要灰度、反色、亮度、对比度、模糊、锐化、素描等效果。

## 推荐做法

```kotlin
val cropped = BitmapTransformUtils.centerCrop(bitmap, 300, 300)
val gray = BitmapColorUtils.grayscale(cropped!!)
```

## 可复制 Demo

```kotlin
import com.itg.itg_bitmap.color.BitmapColorUtils
import com.itg.itg_bitmap.effect.BitmapEffectUtils
import com.itg.itg_bitmap.transform.BitmapTransformUtils

Thread {
    val avatar = BitmapTransformUtils.centerCrop(sourceBitmap, 512, 512) ?: return@Thread
    val warm = BitmapColorUtils.warmTone(avatar, strength = 0.3f) ?: return@Thread
    val result = BitmapEffectUtils.vignette(warm, intensity = 0.4f)

    activity.runOnUiThread {
        imageView.setImageBitmap(result)
    }
}.start()
```

## 关键说明

- 大部分 API 返回新 Bitmap，不会直接修改源图。
- 特效类逐像素处理较多，大图必须后台线程执行。
- `scaleToFit` 保持图片完整，`centerCrop/fillCrop` 会裁剪边缘。
- 亮度、对比度、色调参数内部会做范围约束。
- 链式处理时注意中间 Bitmap 的内存占用。

## 验证方式

- UI 线程不应直接执行大图模糊、素描、马赛克。
- 输出 Bitmap 宽高应符合目标裁剪或缩放尺寸。

[返回 README](../README.md)