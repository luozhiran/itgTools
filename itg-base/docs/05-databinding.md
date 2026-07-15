# 05. DataBinding 扩展

本节说明 `ViewDataBinding` 的两个扩展函数：`bindLifecycle` 和 `bindVariable`。

## 适用条件

- 使用 DataBinding 布局，而不是纯 ViewBinding。
- XML 中声明了 `<variable>`。
- XML 表达式中使用了 LiveData，例如 `@{vm.loading}`。

## 推荐做法

```kotlin
binding.bindLifecycle(this)
binding.bindVariable(BR.viewModel, viewModel)
```

## 可复制 Demo

布局示例：

```xml
<layout xmlns:android="http://schemas.android.com/apk/res/android">
    <data>
        <variable
            name="vm"
            type="com.yourpkg.MainModel" />
    </data>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text='@{vm.loading ? "加载中" : "已完成"}' />
</layout>
```

Activity 中绑定：

```kotlin
import com.example.itg_base.binding.bindLifecycle
import com.example.itg_base.binding.bindVariable
import com.itg.itg_base.AutoBindingBaseActivity
import com.yourpkg.BR
import com.yourpkg.databinding.ActivityDataBindingDemoBinding

class DataBindingDemoActivity : AutoBindingBaseActivity<ActivityDataBindingDemoBinding, MainModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.bindLifecycle(this)
        binding.bindVariable(BR.vm, viewModel)
    }
}
```

## 关键说明

- `bindLifecycle(owner)` 实际设置 `ViewDataBinding.lifecycleOwner`。
- `bindVariable(variableId, value)` 会调用 `setVariable` 并立即 `executePendingBindings()`。
- 只有 DataBinding 生成类才继承 `ViewDataBinding`；纯 ViewBinding 不能调用这两个扩展。

## 验证方式

- XML 中 LiveData 表达式会随生命周期自动刷新。
- `BR.vm` 名称要和 XML `<variable name="vm">` 一致。
- 如果 IDE 找不到扩展函数，检查 import 是否是 `com.example.itg_base.binding.*`。

[返回 README](../README.md)