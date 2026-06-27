# ITG Bitmap — Android 图片处理工具库

[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat/android-7.0)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](./LICENSE)

ITG Bitmap 是一个功能全面的 Android 图片处理工具库，提供 Bitmap 的创建、解码、变换、颜色调整、特效滤镜、压缩、保存和形状裁剪等一站式解决方案。所有工具类以 `object` 单例形式提供，按功能分包，API 设计统一、简洁、易用。

---

## 目录

- [功能特性](#功能特性)
- [快速开始](#快速开始)
  - [1. 添加依赖](#1-添加依赖)
  - [2. 初始化](#2-初始化)
- [架构概览](#架构概览)
- [详细教程](#详细教程)
  - [1. 基础操作 — BitmapUtils](#1-基础操作--bitmaputils)
  - [2. 图片解码 — BitmapDecodeUtils](#2-图片解码--bitmapdecodeutils)
  - [3. 图片变换 — BitmapTransformUtils](#3-图片变换--bitmaptransformutils)
  - [4. 颜色处理 — BitmapColorUtils](#4-颜色处理--bitmapcolorutils)
  - [5. 特效滤镜 — BitmapEffectUtils](#5-特效滤镜--bitmapeffectutils)
  - [6. 图片压缩 — BitmapCompressUtils](#6-图片压缩--bitmapcompressutils)
  - [7. 图片保存 — BitmapSaveUtils](#7-图片保存--bitmapsaveutils)
  - [8. 形状处理 — BitmapShapeUtils](#8-形状处理--bitmapshapeutils)
- [性能建议](#性能建议)
- [线程安全](#线程安全)
- [常见问题](#常见问题)
- [API 参考](#api-参考)
- [更新日志](#更新日志)
- [许可证](#许可证)

---

## 功能特性

| 分类 | 工具类 | 功能概述 |
|------|--------|----------|
| 🔧 基础 | `BitmapUtils` | 创建、复制、像素操作、信息获取、格式转换 |
| 📥 解码 | `BitmapDecodeUtils` | 从资源/文件/URI/URL/字节数组高效解码，采样率计算 |
| 🔄 变换 | `BitmapTransformUtils` | 缩放、旋转、翻转、裁剪、倾斜、矩阵变换 |
| 🎨 颜色 | `BitmapColorUtils` | 灰度、反色、亮度/对比度/饱和度/色相、复古/冷/暖色调、染色、通道提取 |
| ✨ 特效 | `BitmapEffectUtils` | 高斯模糊、堆栈模糊、锐化、浮雕、边缘检测、素描、像素化、暗角 |
| 📦 压缩 | `BitmapCompressUtils` | 质量压缩、尺寸压缩、采样压缩、目标大小迭代压缩、智能压缩 |
| 💾 保存 | `BitmapSaveUtils` | 保存到文件/相册/缓存/私有目录/临时文件，适配 Android 10+ 分区存储 |
| 🔷 形状 | `BitmapShapeUtils` | 圆形、圆角矩形、椭圆、自定义路径裁剪、带边框、图片叠加 |

---

## 快速开始

### 1. 添加依赖

**方式一：直接引用模块**

在 `settings.gradle.kts` 中确认已包含模块：

```kotlin
include(":itg-bitmap")
```

在 app 模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(project(":itg-bitmap"))
}
```

**方式二：Maven 仓库（待发布）**

```kotlin
dependencies {
    implementation("com.itg:itg-bitmap:1.0.0")
}
```

### 2. 初始化

无需初始化，所有工具类均为 `object` 单例，直接调用静态方法即可：

```kotlin
import com.itg.itg_bitmap.core.BitmapUtils
import com.itg.itg_bitmap.decode.BitmapDecodeUtils
// ... 按需导入

// 直接使用
val isValid = BitmapUtils.isValid(myBitmap)
```

---

## 架构概览

### 📂 按功能快速跳转

> 点击下方链接，直达你需要的工具类教程。

| 你想做什么？ | 工具类 |
|-------------|--------|
| 🏗️ **创建/复制/转换** Bitmap | [BitmapUtils](#1-基础操作--bitmaputils) |
| 📥 **解码图片** (文件/资源/URI/URL，防OOM) | [BitmapDecodeUtils](#2-图片解码--bitmapdecodeutils) |
| 🔄 **缩放/旋转/裁剪/翻转** | [BitmapTransformUtils](#3-图片变换--bitmaptransformutils) |
| 🎨 **调色** (灰度/亮度/对比度/饱和度/复古/冷暖) | [BitmapColorUtils](#4-颜色处理--bitmapcolorutils) |
| ✨ **滤镜特效** (模糊/锐化/浮雕/素描/马赛克/暗角) | [BitmapEffectUtils](#5-特效滤镜--bitmapeffectutils) |
| 📦 **压缩图片** (质量/尺寸/采样/目标大小) | [BitmapCompressUtils](#6-图片压缩--bitmapcompressutils) |
| 💾 **保存图片** (文件/相册/缓存/MediaStore) | [BitmapSaveUtils](#7-图片保存--bitmapsaveutils) |
| 🔷 **形状裁剪** (圆形/圆角/椭圆/自定义路径) | [BitmapShapeUtils](#8-形状处理--bitmapshapeutils) |

### 📦 包结构

```
com.itg.itg_bitmap
├── core/          BitmapUtils            — 基础操作
├── decode/        BitmapDecodeUtils      — 图片解码
├── transform/     BitmapTransformUtils   — 几何变换
├── color/         BitmapColorUtils       — 颜色处理
├── effect/        BitmapEffectUtils      — 特效滤镜
├── compress/      BitmapCompressUtils    — 图片压缩
├── save/          BitmapSaveUtils        — 图片保存
└── shape/         BitmapShapeUtils       — 形状处理
```

---

## 详细教程

### 1. 基础操作 — BitmapUtils

`BitmapUtils` 提供 Bitmap 的创建、复制、信息获取和像素级操作。

#### 创建 Bitmap

```kotlin
// 创建空白 Bitmap
val emptyBitmap = BitmapUtils.createBitmap(800, 600)

// 指定配置
val rgbaBitmap = BitmapUtils.createBitmap(800, 600, Bitmap.Config.ARGB_8888)

// 从字节数组创建
val bytes: ByteArray = ... // 图片字节数据
val bitmap = BitmapUtils.createBitmapFromByteArray(bytes)
```

#### 复制 Bitmap

```kotlin
// 默认复制（保持原配置，mutable）
val copy = BitmapUtils.copy(originalBitmap)

// 指定目标配置
val copy8888 = BitmapUtils.copy(originalBitmap, Bitmap.Config.ARGB_8888)

// 不可变副本
val immutableCopy = BitmapUtils.copy(originalBitmap, isMutable = false)
```

#### 获取 Bitmap 信息

```kotlin
// 检查有效性
if (BitmapUtils.isValid(bitmap)) {
    // bitmap 不为空且未被回收
}

// 获取内存占用
val bytes = BitmapUtils.getByteCount(bitmap)   // 字节数
val kb = BitmapUtils.getSizeKB(bitmap)          // KB
val mb = BitmapUtils.getSizeMB(bitmap)          // MB

// 获取宽高比
val ratio = BitmapUtils.getAspectRatio(bitmap)  // 1.78 (16:9)
val isSquare = BitmapUtils.isSquare(bitmap)      // 是否为正方形
val isWide = BitmapUtils.isLandscape(bitmap)     // 是否横向
val isTall = BitmapUtils.isPortrait(bitmap)      // 是否纵向

// 获取可读信息
println(BitmapUtils.getInfo(bitmap))
// 输出: Bitmap(1080x1920, ARGB_8888, 7.91MB)
```

#### 像素操作

```kotlin
// 获取单个像素
val color = BitmapUtils.getPixel(bitmap, 100, 200)

// 设置单个像素（需要 mutable）
BitmapUtils.setPixel(mutableBitmap, 100, 200, Color.RED)

// 批量获取/设置像素
val pixels = BitmapUtils.getPixels(bitmap)
BitmapUtils.setPixels(mutableBitmap, modifiedPixels)
```

#### 格式转换

```kotlin
// Bitmap → ByteArray (JPEG, 质量 90%)
val jpegBytes = BitmapUtils.toByteArray(bitmap, Bitmap.CompressFormat.JPEG, 90)

// Bitmap → ByteArray (PNG, 无损)
val pngBytes = BitmapUtils.toByteArray(bitmap, Bitmap.CompressFormat.PNG, 100)

// Bitmap → ByteArray (WebP)
val webpBytes = BitmapUtils.toByteArray(bitmap, Bitmap.CompressFormat.WEBP, 80)
```

---

### 2. 图片解码 — BitmapDecodeUtils

`BitmapDecodeUtils` 提供高效的图片解码方法，核心优势是**防 OOM**。

#### 从不同来源解码

```kotlin
// 从资源
val bitmap1 = BitmapDecodeUtils.decodeFromResource(resources, R.drawable.photo)

// 从文件
val bitmap2 = BitmapDecodeUtils.decodeFromFile("/sdcard/DCIM/photo.jpg")

// 从字节数组
val bitmap3 = BitmapDecodeUtils.decodeFromByteArray(imageBytes)

// 从 URI（相册选择后的结果）
val bitmap4 = BitmapDecodeUtils.decodeFromUri(context, imageUri)

// 从网络 URL（注意：需在子线程调用）
thread {
    val bitmap5 = BitmapDecodeUtils.decodeFromUrl("https://example.com/photo.jpg")
    runOnUiThread { imageView.setImageBitmap(bitmap5) }
}
```

#### 采样解码（防止 OOM 的关键）

```kotlin
// 核心方法：自动计算采样率，解码为不超过 800x600 的 Bitmap
val bitmap = BitmapDecodeUtils.decodeSampledBitmap(
    path = "/sdcard/large_photo.jpg",
    reqWidth = 800,
    reqHeight = 600
)

// 带配置
val bitmap2 = BitmapDecodeUtils.decodeSampledBitmap(
    path = "/sdcard/large_photo.jpg",
    reqWidth = 800,
    reqHeight = 600,
    config = Bitmap.Config.RGB_565  // 更省内存
)
```

#### 仅获取图片信息（不加载完整图片）

```kotlin
// 获取图片尺寸
val (width, height) = BitmapDecodeUtils.getImageDimensions("/sdcard/photo.jpg") ?: return
println("尺寸: ${width}x${height}")

// 获取 MIME 类型
val mimeType = BitmapDecodeUtils.getImageMimeType("/sdcard/photo.jpg")
// 输出: "image/jpeg"

// 获取完整信息
val info = BitmapDecodeUtils.getImageInfo("/sdcard/photo.jpg")
info?.forEach { (key, value) -> println("$key: $value") }
// width: 4032
// height: 3024
// mimeType: image/jpeg
// fileSize: 3145728 bytes
```

#### 自定义采样率

```kotlin
val sampleSize = BitmapDecodeUtils.calculateInSampleSize(
    rawWidth = 4032,
    rawHeight = 3024,
    reqWidth = 800,
    reqHeight = 600
)
// sampleSize = 4 (4032/4=1008 > 800, 继续; 4032/8=504 < 800, 停止)
```

---

### 3. 图片变换 — BitmapTransformUtils

`BitmapTransformUtils` 提供几何变换功能，所有方法返回新 Bitmap。

#### 缩放

```kotlin
// 缩放到指定尺寸
val scaled = BitmapTransformUtils.scale(bitmap, 400, 300)

// 按比例缩放
val halfSize = BitmapTransformUtils.scaleByFactor(bitmap, 0.5f)
val doubleSize = BitmapTransformUtils.scaleByFactor(bitmap, 2.0f)

// 缩放到指定宽度（高度等比）
val scaledW = BitmapTransformUtils.scaleToWidth(bitmap, 600)

// 缩放到指定高度（宽度等比）
val scaledH = BitmapTransformUtils.scaleToHeight(bitmap, 800)

// 缩放以适配边界（适合 ImageView fitCenter）
val fitted = BitmapTransformUtils.scaleToFit(bitmap, 800, 600)
```

#### 旋转

```kotlin
// 绕中心旋转 90 度
val rotated = BitmapTransformUtils.rotate(bitmap, 90f)

// 绕自定义中心旋转
val rotated2 = BitmapTransformUtils.rotate(bitmap, 45f, pivotX = 100f, pivotY = 100f)
```

#### 翻转

```kotlin
// 水平镜像翻转
val mirrored = BitmapTransformUtils.flipHorizontal(bitmap)

// 垂直翻转
val flipped = BitmapTransformUtils.flipVertical(bitmap)
```

#### 裁剪

```kotlin
// 裁剪指定区域
val cropped = BitmapTransformUtils.crop(bitmap, x = 100, y = 50, width = 300, height = 200)

// 居中裁剪（常用于头像）
val centered = BitmapTransformUtils.centerCrop(bitmap, 200, 200)

// 填充裁剪（类似 ImageView centerCrop）
val filled = BitmapTransformUtils.fillCrop(bitmap, 200, 200)
```

#### 自定义 Matrix 变换

```kotlin
val matrix = Matrix().apply {
    postScale(0.5f, 0.8f)
    postRotate(30f)
    postTranslate(50f, 0f)
}
val transformed = BitmapTransformUtils.applyMatrix(bitmap, matrix)
```

---

### 4. 颜色处理 — BitmapColorUtils

`BitmapColorUtils` 通过 `ColorMatrix` 实现高效的颜色调整。

#### 灰度与黑白

```kotlin
// 灰度图（标准亮度权重）
val gray = BitmapColorUtils.grayscale(bitmap)

// 黑白二值图（阈值 128）
val bw = BitmapColorUtils.blackWhite(bitmap)

// 自定义阈值
val bwDark = BitmapColorUtils.blackWhite(bitmap, threshold = 200)
```

#### 亮度与对比度

```kotlin
// 调亮 50
val brighter = BitmapColorUtils.adjustBrightness(bitmap, 50f)

// 调暗 30
val darker = BitmapColorUtils.adjustBrightness(bitmap, -30f)

// 增强对比度 50%
val highContrast = BitmapColorUtils.adjustContrast(bitmap, 1.5f)

// 降低对比度
val lowContrast = BitmapColorUtils.adjustContrast(bitmap, 0.5f)
```

#### 饱和度与色相

```kotlin
// 饱和度 x2
val vibrant = BitmapColorUtils.adjustSaturation(bitmap, 2.0f)

// 去色到 30%
val muted = BitmapColorUtils.adjustSaturation(bitmap, 0.3f)

// 色相旋转 180 度（红→青）
val hueShifted = BitmapColorUtils.adjustHue(bitmap, 180f)
```

#### 色调滤镜

```kotlin
// 复古/老照片色调
val sepia = BitmapColorUtils.sepia(bitmap)

// 冷色调（蓝/青偏）
val cool = BitmapColorUtils.coolTone(bitmap, strength = 0.6f)

// 暖色调（黄/橙偏）
val warm = BitmapColorUtils.warmTone(bitmap, strength = 0.5f)
```

#### 色彩覆盖 (Tint)

```kotlin
// 红色 30% 染色
val redTint = BitmapColorUtils.tint(bitmap, Color.RED, alpha = 0.3f)

// 半透明覆盖
val overlay = BitmapColorUtils.tint(bitmap, 0xFF2196F3.toInt(), alpha = 0.4f)
```

#### 通道提取

```kotlin
val redChannel = BitmapColorUtils.extractRed(bitmap)
val greenChannel = BitmapColorUtils.extractGreen(bitmap)
val blueChannel = BitmapColorUtils.extractBlue(bitmap)
```

#### 透明度调整

```kotlin
// 50% 透明
val translucent = BitmapColorUtils.adjustAlpha(bitmap, 0.5f)
```

#### 反色

```kotlin
val inverted = BitmapColorUtils.invert(bitmap)
```

#### 自定义 ColorMatrix

```kotlin
val customMatrix = ColorMatrix().apply {
    setSaturation(0.5f)
    setScale(1.2f, 1.0f, 0.8f, 1.0f) // 增强红色，减弱蓝色
}
val result = BitmapColorUtils.applyColorMatrix(bitmap, customMatrix)
```

---

### 5. 特效滤镜 — BitmapEffectUtils

`BitmapEffectUtils` 提供图像级别的特效处理。

> ⚠️ **注意**: 部分逐像素处理的特效（如 StackBlur、素描、浮雕）对高分辨率图片可能耗时较长，请务必在子线程中调用。

#### 模糊

```kotlin
// 高斯模糊（半径 1-25）
val blurred = BitmapEffectUtils.blur(bitmap, radius = 15)

// Stack Blur（半径 1-200，性能更好）
val stackBlurred = BitmapEffectUtils.stackBlur(bitmap, radius = 30)
```

#### 锐化

```kotlin
// 锐化
val sharp = BitmapEffectUtils.sharpen(bitmap)

// 自定义锐化强度
val slightlySharp = BitmapEffectUtils.sharpen(bitmap, intensity = 0.5f)
val verySharp = BitmapEffectUtils.sharpen(bitmap, intensity = 1.5f)
```

#### 浮雕 / 边缘检测 / 素描

```kotlin
// 浮雕效果
val embossed = BitmapEffectUtils.emboss(bitmap)

// 边缘检测
val edges = BitmapEffectUtils.edgeDetect(bitmap)

// 铅笔素描
val sketch = BitmapEffectUtils.sketch(bitmap)
```

#### 像素化 / 马赛克

```kotlin
// 默认块大小 10px
val mosaic = BitmapEffectUtils.pixelate(bitmap)

// 自定义块大小
val largeMosaic = BitmapEffectUtils.pixelate(bitmap, blockSize = 20)
val fineMosaic = BitmapEffectUtils.pixelate(bitmap, blockSize = 5)
```

#### 暗角

```kotlin
// 默认强度 0.6
val vignette = BitmapEffectUtils.vignette(bitmap)

// 自定义强度
val subtleVignette = BitmapEffectUtils.vignette(bitmap, intensity = 0.3f)
val darkVignette = BitmapEffectUtils.vignette(bitmap, intensity = 0.9f)
```

#### 组合特效示例

```kotlin
// 老照片效果: 复古色调 + 暗角
thread {
    val sepia = BitmapColorUtils.sepia(bitmap) ?: return@thread
    val vignette = BitmapEffectUtils.vignette(sepia, 0.5f)
    runOnUiThread { imageView.setImageBitmap(vignette) }
}

// 毛玻璃背景: 缩小 → 模糊 → 放大
thread {
    val small = BitmapTransformUtils.scaleByFactor(bitmap, 0.25f) ?: return@thread
    val blurred = BitmapEffectUtils.stackBlur(small, 20) ?: return@thread
    val result = BitmapTransformUtils.scaleByFactor(blurred, 4.0f)
    runOnUiThread { backgroundView.setImageBitmap(result) }
}
```

---

### 6. 图片压缩 — BitmapCompressUtils

`BitmapCompressUtils` 提供多维度压缩策略，可根据场景灵活组合。

#### 质量压缩

```kotlin
// JPEG 质量 80%
val bytes = BitmapCompressUtils.compressByQuality(bitmap, quality = 80)

// 估算压缩后大小
val estimatedSize = BitmapCompressUtils.estimateCompressedSize(bitmap, quality = 80)
```

#### 尺寸压缩

```kotlin
// 限制最大尺寸
val resized = BitmapCompressUtils.compressBySize(bitmap, maxWidth = 1280, maxHeight = 720)

// 按比例缩放
val halfSize = BitmapCompressUtils.compressByScale(bitmap, scale = 0.5f)
```

#### 采样压缩（直接从文件解码，最省内存！）

```kotlin
val bitmap = BitmapCompressUtils.compressBySampleSize(
    imagePath = "/sdcard/50MP_photo.jpg",
    maxWidth = 1920,
    maxHeight = 1080,
    config = Bitmap.Config.RGB_565  // 更省内存
)
```

#### 目标大小压缩（迭代二分查找）

```kotlin
// 压缩到 100KB 以内
val bytes = BitmapCompressUtils.compressToTargetSize(
    bitmap,
    targetBytes = 100 * 1024  // 100KB
)

// 获取详细压缩信息
val info = BitmapCompressUtils.compressToTargetSizeWithInfo(
    bitmap,
    targetKB = 100
)
info?.let {
    println("原始: ${it["originalSizeKB"]}KB")
    println("压缩后: ${it["compressedSizeKB"]}KB")
    println("压缩比: ${it["compressionRatio"]}")
}
// 原始: 8294.4KB → 压缩后: 97.5KB
```

#### 组合压缩（最常用）

```kotlin
// 常用流程: 尺寸 + 质量一起压缩
val bytes = BitmapCompressUtils.compress(
    bitmap,
    maxWidth = 1280,
    maxHeight = 720,
    quality = 80
)

// 写入文件
FileOutputStream("/sdcard/compressed.jpg").use { it.write(bytes) }
```

#### 智能压缩

```kotlin
// 自动选择最优压缩策略
val bytes = BitmapCompressUtils.smartCompress(
    bitmap,
    maxWidth = 1920,
    maxHeight = 1080,
    maxSizeKB = 200  // 限制最终文件大小
)
```

#### 压缩策略选择指南

| 场景 | 推荐方法 | 说明 |
|------|----------|------|
| 缩略图列表 | `compressBySampleSize` | 最省内存，直接从文件解码 |
| 网络上传 | `compress` | 组合尺寸+质量压缩 |
| 头像上传 (有大小限制) | `compressToTargetSize` | 迭代压缩至目标大小 |
| 大图预览 | `compressBySize` | 保持质量，仅缩小尺寸 |
| 通用场景 | `smartCompress` | 自动选择策略 |

---

### 7. 图片保存 — BitmapSaveUtils

`BitmapSaveUtils` 提供多种保存方式，自动适配 Android 10+ 分区存储。

#### 保存到文件

```kotlin
// 保存到指定路径
val success = BitmapSaveUtils.saveToFile(
    bitmap,
    filePath = "/sdcard/MyApp/photo.jpg",
    format = Bitmap.CompressFormat.JPEG,
    quality = 95
)

// 保存到目录（自动生成文件名）
val path = BitmapSaveUtils.saveToDirectory(
    bitmap,
    directory = File("/sdcard/MyApp/images"),
    prefix = "Travel",
    format = Bitmap.CompressFormat.PNG
)
// 生成: /sdcard/MyApp/images/Travel_20240101_120000.png
```

#### 保存到相册

```kotlin
// 自动适配 Android 10+ MediaStore
val uri = BitmapSaveUtils.saveToGallery(
    context,
    bitmap,
    fileName = "MyEditedPhoto"
)
if (uri != null) {
    Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
}
```

#### 保存到应用缓存 / 私有目录

```kotlin
// 缓存目录（可能被系统清理）
val cacheFile = BitmapSaveUtils.saveToCache(context, bitmap, "avatar_cache.png")

// 私有目录（外部不可见，不会被清理）
val privateFile = BitmapSaveUtils.saveToPrivate(context, bitmap, "profile.jpg")
```

#### 临时文件

```kotlin
// 创建临时文件（分享、上传等场景）
val tmpFile = BitmapSaveUtils.saveToTempFile(context, bitmap)
if (tmpFile != null) {
    // 使用完毕后删除
    shareImage(tmpFile)
    tmpFile.delete()
}
```

---

### 8. 形状处理 — BitmapShapeUtils

`BitmapShapeUtils` 提供各种形状裁剪功能，常用于头像、缩略图处理。

#### 圆形

```kotlin
// 圆形裁剪
val circle = BitmapShapeUtils.toCircle(bitmap)
imageView.setImageBitmap(circle)

// 带边框的圆形
val borderedCircle = BitmapShapeUtils.toCircleWithBorder(
    bitmap,
    borderWidth = 4,
    borderColor = Color.WHITE
)
```

#### 圆角矩形

```kotlin
// 统一圆角
val rounded = BitmapShapeUtils.roundCorners(bitmap, cornerRadius = 16f)

// 四个角分别设置圆角
val customRounded = BitmapShapeUtils.roundCornersIndividual(
    bitmap,
    topLeftRadius = 24f,
    topRightRadius = 24f,
    bottomLeftRadius = 0f,
    bottomRightRadius = 0f
)

// 带边框的圆角
val borderedRounded = BitmapShapeUtils.roundCornersWithBorder(
    bitmap,
    cornerRadius = 12f,
    borderWidth = 3,
    borderColor = Color.parseColor("#FF5722")
)
```

#### 椭圆

```kotlin
val oval = BitmapShapeUtils.toOval(bitmap)
```

#### 自定义路径裁剪

```kotlin
val starPath = Path().apply {
    // 绘制自定义形状...
    moveTo(100f, 0f)
    // ...
    close()
}
val starBitmap = BitmapShapeUtils.toCustomPath(bitmap, starPath)
```

#### 图片叠加（水印）

```kotlin
// 右下角加水印
val x = backgroundBitmap.width - watermarkBitmap.width - 16
val y = backgroundBitmap.height - watermarkBitmap.height - 16
val watermarked = BitmapShapeUtils.overlay(backgroundBitmap, watermarkBitmap, x, y)
```

---

## 性能建议

### 1. 避免 OOM

```kotlin
// ❌ 不推荐: 直接加载全尺寸图片
val bitmap = BitmapFactory.decodeFile("/sdcard/50MP_photo.jpg")

// ✅ 推荐: 使用采样解码
val bitmap = BitmapDecodeUtils.decodeSampledBitmap("/sdcard/50MP_photo.jpg", 800, 600)

// ✅ 更省内存: 使用 RGB_565 配置
val options = BitmapFactory.Options().apply {
    inSampleSize = 4
    inPreferredConfig = Bitmap.Config.RGB_565
}
val bitmap = BitmapDecodeUtils.decodeFromResource(res, R.drawable.photo, options)
```

### 2. 及时回收

```kotlin
// 对于不再使用的 Bitmap，及时回收
val bitmap = BitmapDecodeUtils.decodeFromFile(path)
// ... 使用 bitmap ...
bitmap.recycle()  // 手动回收（可选，GC 也会处理）
```

### 3. 子线程处理

```kotlin
// 图像处理操作应在子线程进行
thread {
    val processed = BitmapEffectUtils.sketch(largeBitmap)
    runOnUiThread {
        imageView.setImageBitmap(processed)
    }
}
```

### 4. 缓存策略

```kotlin
// 对于频繁使用的处理结果，缓存到磁盘
val cacheFile = BitmapSaveUtils.saveToCache(context, processedBitmap, "effect_sketch.png")
// 下次使用时直接加载缓存文件
```

### 5. 选择合适的 Bitmap.Config

| Config | 每像素字节 | 适用场景 |
|--------|-----------|---------|
| `ARGB_8888` | 4 bytes | 需要透明通道的高质量图片 |
| `RGB_565` | 2 bytes | 不需要透明通道的图片，内存减半 |
| `ALPHA_8` | 1 byte | 仅需透明通道（遮罩等） |

```kotlin
// 列表缩略图场景使用 RGB_565
val options = BitmapFactory.Options().apply {
    inPreferredConfig = Bitmap.Config.RGB_565
}
```

---

## 线程安全

- 所有工具类均为 **无状态** 的 `object` 单例，方法内部不维护共享状态
- 方法参数不可变（接收 Bitmap，返回新 Bitmap）
- 可以在多线程中安全调用，无需额外同步
- 唯一例外: `BitmapSaveUtils.saveToGallery` 涉及 ContentResolver，建议在主线程或使用协程调用

---

## 常见问题

### Q: 处理大图时出现 OOM？

A: 使用 `BitmapDecodeUtils.decodeSampledBitmap()` 进行采样解码，或使用 `BitmapCompressUtils.compressBySampleSize()` 直接从文件低分辨率解码。

### Q: 如何保存图片到相册？

A: 使用 `BitmapSaveUtils.saveToGallery(context, bitmap)`，自动适配 Android 10+ 的分区存储。

### Q: PNG 压缩为什么文件大小不变？

A: PNG 是无损格式，`quality` 参数对 PNG 无效。如需减小文件大小，请使用 JPEG 或 WebP 格式，或先缩小尺寸再保存。

### Q: 如何处理网络图片？

A: 使用 `BitmapDecodeUtils.decodeFromUrl(url)`，注意该方法为**同步操作**，必须在子线程中调用。

### Q: 模糊效果太慢怎么办？

A: 对于大图，建议先缩小再模糊:
```kotlin
val small = BitmapTransformUtils.scaleByFactor(bitmap, 0.25f)
val blurred = BitmapEffectUtils.stackBlur(small, 20)
val result = BitmapTransformUtils.scaleByFactor(blurred, 4.0f)
```

---

## API 参考

完整的 API 文档请参考各工具类的 KDoc 注释。以下是快速索引：

### BitmapUtils (core)
| 方法 | 说明 |
|------|------|
| `createBitmap(w, h, config)` | 创建空白 Bitmap |
| `copy(source, config, isMutable)` | 复制 Bitmap |
| `toByteArray(bitmap, format, quality)` | 转换为字节数组 |
| `isValid(bitmap)` | 检查有效性 |
| `getByteCount(bitmap)` | 获取占用字节 |
| `getSizeKB(bitmap)` | 获取占用 KB |
| `getSizeMB(bitmap)` | 获取占用 MB |
| `getAspectRatio(bitmap)` | 获取宽高比 |
| `isSquare(bitmap)` | 是否为正方形 |
| `isLandscape(bitmap)` | 是否横向 |
| `isPortrait(bitmap)` | 是否纵向 |
| `getInfo(bitmap)` | 获取可读信息 |
| `createBitmapFromByteArray(data, opts)` | 从字节数组创建 |
| `getPixel(bitmap, x, y)` | 获取指定像素 |
| `setPixel(bitmap, x, y, color)` | 设置指定像素 |
| `getPixels(bitmap)` | 批量获取像素 |
| `setPixels(bitmap, pixels)` | 批量设置像素 |

### BitmapDecodeUtils (decode)
| 方法 | 说明 |
|------|------|
| `decodeFromResource(res, id, opts)` | 从资源解码 |
| `decodeFromResource(res, id, maxW, maxH)` | 从资源解码（限制尺寸） |
| `decodeFromFile(path, opts)` | 从文件解码 |
| `decodeSampledBitmap(path, reqW, reqH)` | 采样解码 |
| `decodeFromByteArray(data, opts)` | 从字节数组解码 |
| `decodeFromByteArray(data, maxW, maxH)` | 从字节数组解码（限制尺寸） |
| `decodeFromStream(inputStream, opts)` | 从 InputStream 解码 |
| `decodeFromByteArray(data, opts)` | 从字节数组解码 |
| `decodeFromUri(context, uri, opts)` | 从 URI 解码 |
| `decodeFromUrl(url)` | 从 URL 解码 |
| `getImageDimensions(path)` | 获取图片尺寸 |
| `getImageMimeType(path)` | 获取 MIME 类型 |
| `getImageInfo(path)` | 获取完整信息 |
| `calculateInSampleSize(...)` | 计算采样率 |

### BitmapTransformUtils (transform)
| 方法 | 说明 |
|------|------|
| `scale(bitmap, w, h, filter)` | 缩放到指定尺寸 |
| `scaleByFactor(bitmap, factor)` | 按比例缩放 |
| `scaleToWidth(bitmap, w)` | 按宽度缩放 |
| `scaleToHeight(bitmap, h)` | 按高度缩放 |
| `scaleToFit(bitmap, w, h)` | 适配边界缩放 |
| `rotate(bitmap, deg, px, py)` | 旋转 |
| `flipHorizontal(bitmap)` | 水平翻转 |
| `flipVertical(bitmap)` | 垂直翻转 |
| `crop(bitmap, x, y, w, h)` | 区域裁剪 |
| `centerCrop(bitmap, w, h)` | 居中裁剪 |
| `fillCrop(bitmap, w, h)` | 填充裁剪 |
| `skewX(bitmap, skewX, filter)` | X 轴倾斜 |
| `skewY(bitmap, skewY, filter)` | Y 轴倾斜 |
| `applyMatrix(bitmap, matrix)` | 自定义矩阵变换 |

### BitmapColorUtils (color)
| 方法 | 说明 |
|------|------|
| `grayscale(bitmap)` | 灰度化 |
| `blackWhite(bitmap, threshold)` | 黑白二值化 |
| `invert(bitmap)` | 反色 |
| `adjustBrightness(bitmap, value)` | 亮度调整 |
| `adjustContrast(bitmap, value)` | 对比度调整 |
| `adjustSaturation(bitmap, value)` | 饱和度调整 |
| `adjustHue(bitmap, value)` | 色相旋转 |
| `sepia(bitmap)` | 复古色调 |
| `coolTone(bitmap, strength)` | 冷色调 |
| `warmTone(bitmap, strength)` | 暖色调 |
| `tint(bitmap, color, alpha)` | 染色 |
| `extractRed/Green/Blue(bitmap)` | 通道提取 |
| `adjustAlpha(bitmap, alpha)` | 透明度调整 |
| `applyColorMatrix(bitmap, matrix)` | 自定义颜色矩阵 |

### BitmapEffectUtils (effect)
| 方法 | 说明 |
|------|------|
| `blur(bitmap, radius)` | 高斯模糊 |
| `stackBlur(bitmap, radius)` | 堆栈模糊（快速） |
| `sharpen(bitmap, intensity)` | 锐化 |
| `emboss(bitmap)` | 浮雕效果 |
| `edgeDetect(bitmap)` | 边缘检测 |
| `sketch(bitmap)` | 素描效果 |
| `pixelate(bitmap, blockSize)` | 像素化/马赛克 |
| `vignette(bitmap, intensity)` | 暗角 |

### BitmapCompressUtils (compress)
| 方法 | 说明 |
|------|------|
| `compressByQuality(bitmap, quality, format)` | 质量压缩 |
| `estimateCompressedSize(bitmap, quality, format)` | 预估压缩大小 |
| `compressBySize(bitmap, maxW, maxH)` | 尺寸压缩 |
| `compressByScale(bitmap, scale)` | 按比例缩放 |
| `compressBySampleSize(path, maxW, maxH)` | 采样压缩 |
| `compressToTargetSize(bitmap, targetBytes)` | 目标大小迭代压缩 |
| `compressToTargetSizeWithInfo(bitmap, targetKB)` | 带详情的压缩 |
| `compress(bitmap, maxW, maxH, quality)` | 组合压缩 |
| `smartCompress(bitmap, maxW, maxH, maxSizeKB)` | 智能压缩 |
| `calculateSampleSize(...)` | 采样率计算 |

### BitmapSaveUtils (save)
| 方法 | 说明 |
|------|------|
| `saveToFile(bitmap, path, format, quality)` | 保存到文件 |
| `saveToDirectory(bitmap, dir, prefix, ...)` | 保存到目录 |
| `saveToGallery(context, bitmap, name, ...)` | 保存到相册 |
| `saveToCache(context, bitmap, name, ...)` | 保存到缓存 |
| `saveToPrivate(context, bitmap, name, ...)` | 保存到私有目录 |
| `saveToTempFile(context, bitmap, ...)` | 保存临时文件 |
| `formatFromExtension(ext)` | 扩展名→格式 |
| `formatFromPath(path)` | 路径→格式 |

### BitmapShapeUtils (shape)
| 方法 | 说明 |
|------|------|
| `toCircle(bitmap, recycle)` | 圆形裁剪 |
| `toCircleWithBorder(bitmap, w, color)` | 带边框圆形 |
| `roundCorners(bitmap, radius)` | 圆角裁剪 |
| `roundCornersIndividual(bitmap, tl, tr, bl, br)` | 独立圆角 |
| `roundCornersWithBorder(bitmap, r, w, c)` | 带边框圆角 |
| `toOval(bitmap)` | 椭圆裁剪 |
| `toCustomPath(bitmap, path)` | 自定义路径裁剪 |
| `overlay(bg, fg, x, y)` | 图片叠加 |

---

## 更新日志

### v1.0.0 (2026-06)
- 🎉 初始版本发布
- ✨ 8 大功能模块，60+ 个 API 方法
- 📦 支持从多种来源解码 Bitmap
- 🔄 完整的几何变换能力
- 🎨 15+ 种颜色滤镜和调整
- ✨ 8 种图像特效
- 📐 多维度压缩策略
- 💾 完整的保存方案（适配 Android 10+ 分区存储）
- 🔷 灵活的形状裁剪

---

## 许可证

```
MIT License

Copyright (c) 2026 ITG Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
