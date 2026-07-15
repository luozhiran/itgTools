# 02. 归档、比较与完整性

本节说明下载之外的文件验证能力。

## 适用条件

- 需要在解压前检查归档文件。
- 需要比较两个文件是否一致。
- 需要组合多种校验规则判断完整性。

## 推荐做法

```kotlin
val result = FileComparator.compareByHash(path1, path2)
if (!result.areEqual) return
```

## 可复制 Demo

```kotlin
import com.itg.itg_erification.compare.FileComparator

val result = FileComparator.compareByHash(
    path1 = source.absolutePath,
    path2 = target.absolutePath,
    algorithm = "SHA-256"
)

if (!result.areEqual) {
    // TODO: 重新复制或重新下载
    println(result.reason)
}
```

## 关键说明

- `compareByHash` 返回 `CompareResult`，用 `areEqual` 判断结果。
- 比较大文件时 hash 比较通常比逐字节比较更适合业务校验。
- 归档校验只能说明归档结构/条目可检查，不等于业务内容完全合法。
- 完整性检查应明确失败后的动作：重试、重下、回滚或提示用户。
- 文件比较和归档检查都可能耗时，建议后台线程执行。

## 验证方式

- 对两个相同文件比较应返回一致。
- 破坏归档文件尾部后归档校验应失败。

[返回 README](../README.md)