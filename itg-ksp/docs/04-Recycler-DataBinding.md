# 04 Recycler DataBinding

## 1. 布局声明

```xml
<layout xmlns:android="http://schemas.android.com/apk/res/android">
    <data>
        <variable
            name="item"
            type="com.example.feed.ArticleRow" />
        <variable
            name="actions"
            type="com.example.feed.FeedActions" />
    </data>

    <!-- 实际 View 布局 -->
</layout>
```

## 2. Item 注解

```kotlin
@ItgDataBindingItem(
    layoutExpression = "com.example.R.layout.item_article",
    itemVariableExpression = "com.example.BR.item",
    actionsVariableExpression = "com.example.BR.actions",
    actionsClassName = "com.example.feed.FeedActions",
    itemKeyProperty = "articleId",
)
data class ArticleRow(
    val articleId: String,
    val title: String,
)
```

四个参数都是生成进 Kotlin 源码的表达式或类型名：

| 参数 | 含义 |
|---|---|
| `layoutExpression` | layout 资源表达式 |
| `itemVariableExpression` | Item 的 BR id |
| `actionsVariableExpression` | actions 的 BR id |
| `actionsClassName` | actions 完整限定类名，也是 registry 分组键 |
| `itemKeyProperty` | 可选；不实现 ItgListItem 时指定已有非空唯一属性 |

## 3. BR 类位置

DataBinding 可能生成模块级 `BR`，也可能项目使用自定义常量。以实际可编译表达式为准，例如：

```kotlin
itemVariableExpression = "com.example.BR.item"
```

或：

```kotlin
itemVariableExpression = "com.example.RecyclerBindingVariables.ITEM"
```

## 4. 无点击回调的 DataBinding

`itg-ui` runtime 的 `dataBinding` DSL 支持不设置 actions variable，但当前 KSP 注解生成器始终把 `actionsVariableExpression` 传给 runtime；默认值 `"0"` 会触发 runtime 的参数校验。

因此当前注解方式必须提供布局中真实存在、非 0 的 actions variable。没有业务回调时可使用空对象：

```kotlin
object NoArticleActions
```

并在布局和注解中声明该对象类型。若必须完全省略 actions variable，请改用手写 `GeneratedRecyclerRegistry`，调用 runtime DSL 时不传 `actionsVariableId`。

## 5. DataBinding 与生命周期

`RecyclerViewAbility.bind` 会把 LifecycleOwner 传给 adapter；DataBinding renderer 会设置 `binding.lifecycleOwner`。Fragment 场景必须传 `viewLifecycleOwner`，否则 LiveData 表达式可能绑定到错误生命周期。

## 6. Payload 和内容比较

DataBinding Item 同样支持：

```kotlin
@ItgPayload
fun payload(oldItem: ArticleRow): Any? =
    if (oldItem.title != title) "title" else null

@ItgContentsSame
fun contentsSame(oldItem: ArticleRow): Boolean =
    oldItem.title == title
```

DataBinding 的 payload 用于 DiffUtil 通知；最终 `setVariable` 仍会执行。需要精细的局部 View 更新时，ViewBinding 的三参数 `@ItgBind` 更直接。

## 7. 常见运行错误

- `layoutId 不能为 0`：layout 表达式错误。
- `itemVariableId 不能为 0`：布局未声明对应 variable 或 BR 表达式错误。
- `actionsVariableId 不能为 0`：使用了默认 `"0"`；当前生成器要求真实 actions variable。
- `布局 ... 不包含 itemVariableId`：注解指向的 BR id 与该布局不匹配。
- `布局 ... 不包含 actionsVariableId`：布局没有对应 actions variable。
