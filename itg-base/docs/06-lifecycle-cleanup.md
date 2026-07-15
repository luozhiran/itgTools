# 06. 生命周期清理工具

本节说明 `AutoClearedValue` 和 `autoCleared()`，用于在生命周期结束时断开引用。

## 适用条件

- Fragment 持有 Adapter、callback、binding 相关对象，需要在 `onDestroyView` 清理。
- Activity 或普通 owner 持有临时大对象，需要在 `onDestroy` 或 `release` 中置空。
- 希望访问已清理值时能明确报错。

## 推荐做法

包装器：

```kotlin
private val adapterHolder = AutoClearedValue<MyAdapter>()
adapterHolder.set(MyAdapter())
adapterHolder.clear()
```

属性委托：

```kotlin
private var adapter: MyAdapter? by autoCleared()
adapter = null
```

## 可复制 Demo

下面示例在 Fragment 视图销毁时清理 RecyclerView Adapter。需要替换 `DemoAdapter` 和 binding 类。

```kotlin
import android.app.Application
import android.os.Bundle
import android.view.View
import com.example.itg_base.arch.ItgModel
import com.example.itg_base.util.autoCleared
import com.itg.itg_base.AutoBindingBaseFragment
import com.yourpkg.databinding.FragmentListBinding

class ListModel(app: Application) : ItgModel(app)

class ListFragment : AutoBindingBaseFragment<FragmentListBinding, ListModel>() {
    private var adapter: DemoAdapter? by autoCleared()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = DemoAdapter()
        binding.recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        adapter = null
        super.onDestroyView()
    }
}
```

使用 `AutoClearedValue.require()`：

```kotlin
val holder = AutoClearedValue<DemoAdapter>()
holder.set(DemoAdapter())
val adapter = holder.require()
holder.clear()
```

## 关键说明

- `autoCleared()` 不会自动监听生命周期，需要你在合适的生命周期方法里赋
ull`。
- `AutoClearedValue.require()` 在未设置或已清理时抛出 `IllegalStateException`。
- Fragment 中清理 Adapter 时，建议先断开 `RecyclerView.adapter`，再清理本地引用。

## 验证方式

- `onDestroyView` 后 adapter 引用为 null。
- 调用 `holder.require()` 前必须先 `set`。
- LeakCanary 或内存工具不应再看到旧 View 被 Adapter 间接持有。

[返回 README](../README.md)