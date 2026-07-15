# ITG Bitmap

`itg-bitmap` 是 Android Bitmap 处理工具库，提供图片创建、解码、变换、调色、滤镜、压缩、保存和形状裁剪能力。模块 minSdk 21，工具类均为无状态 `object`，耗时图像处理应放到后台线程。

## 使用场景总览

| 使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [创建、复制和检查 Bitmap](./docs/01-basic-decode.md) | `BitmapUtils` | 已有 `Bitmap` 或字节数组 | 需要判断有效性、复制、取尺寸和内存大小 | 工具类封装基础像素与元信息操作 |
| [从文件、资源、Uri 解码](./docs/01-basic-decode.md) | `BitmapDecodeUtils` | Android `Resources`、文件路径、`Uri`、字节数组 | 加载图片到内存 | 支持采样解码和只读尺寸，降低 OOM 风险 |
| [缩放、旋转、裁剪](./docs/02-transform-color-effect.md) | `BitmapTransformUtils` | 输入 Bitmap 有效 | 头像、封面、缩略图需要几何处理 | 返回新 Bitmap，不直接修改源图 |
| [调色和滤镜](./docs/02-transform-color-effect.md) | `BitmapColorUtils`、`BitmapEffectUtils` | 大图建议后台线程 | 灰度、亮度、模糊、素描、马赛克 | 基于 `ColorMatrix`、像素处理和 Canvas 绘制 |
| [压缩上传图](./docs/03-compress-save.md) | `BitmapCompressUtils.compress/smartCompress` | 已有 Bitmap 或文件路径 | 上传前限制尺寸和文件大小 | 组合尺寸压缩、质量压缩、目标大小压缩 |
| [保存到文件、相册、缓存](./docs/03-compress-save.md) | `BitmapSaveUtils` | 需要 Context 或可写路径 | 编辑后落盘、保存相册、生成临时分享文件 | 适配 Android 10+ MediaStore 和私有目录 |
| [裁剪圆形、圆角、水印](./docs/04-shape-performance-api.md) | `BitmapShapeUtils` | UI 头像、卡片缩略图 | 需要形状裁剪或叠加图层 | 使用 Canvas/Path 绘制输出新图 |
| [查看性能与 API 速查](./docs/04-shape-performance-api.md) | API 表 | 所有使用者 | 接入前确认线程、内存、方法名 | 汇总源码中的核心工具类和注意事项 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. 基础与解码](./docs/01-basic-decode.md) | `BitmapUtils`、`BitmapDecodeUtils` |
| [02. 变换、调色与滤镜](./docs/02-transform-color-effect.md) | 几何变换、颜色处理、特效 |
| [03. 压缩与保存](./docs/03-compress-save.md) | 压缩策略、文件/相册/缓存保存 |
| [04. 形状、性能与 API](./docs/04-shape-performance-api.md) | 形状裁剪、性能建议、API 速查 |

## 依赖

```kotlin
dependencies {
    implementation(project(":itg-bitmap"))
}
```