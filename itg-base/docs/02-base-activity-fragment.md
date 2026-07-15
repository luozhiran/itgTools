# 02. Activity 与 Fragment 基类

本节说明 `AutoBindingBaseActivity` 和 `AutoBindingBaseFragment` 的使用方式、自动注入能力和生命周期差异。

## 适用条件

- 页面希望自动获得 `binding` 和 `viewModel`。
- 页面希望复用权限、消息、状态页、Activity Result 等能力。
- Fragment 希望在 `onDestroyView` 自动清空 binding。

## 推荐做法

Activity：

```kotlin
class MainActivity : AutoBindingBaseActivity<ActivityMainBinding, MainModel>()
```

Fragment：

```kotlin
class MainFragment : AutoBindingBaseFragment<FragmentMainBinding, MainModel>()
```

## 可复制 Demo

下面示例展示 Activity + Fragment 共用同一个 `MainModel`。需要替换 binding 类和控件 id。

```kotlin
import android.app.Application
import android.os.Bundle
import android.view.View
import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itg_base.AutoBindingBaseFragment
import com.yourpkg.databinding.ActivityMainBinding
import com.yourpkg.databinding.FragmentMainBinding

class MainModel(app: Application) : ItgModel(app) {
    fun refresh() {
        showLoading()
        hideLoading()
    }
}

class MainActivity : AutoBindingBaseActivity<ActivityMainBinding, MainModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.titleText.text = "Activity ready"
    }
}

class MainFragment : AutoBindingBaseFragment<FragmentMainBinding, MainModel>() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.refreshButton.setOnClickListener {
            viewModel.refresh()
        }
    }
}
```

## Activity 自动流程

`AutoBindingBaseActivity.onCreate` 顺序：

1. `systemBars.inject(this)`
2. `systemBars.useEnableEdgeToEdge()`
3. inflate binding 并 `setContentView(binding.root)`
4. 创建 `viewModel`
5. 注入 `launcher`、`permissions`、`messages`、`uiState`
6. 如果 `binding.root` 是 `ViewGroup`，自动 `uiState.bind(root)`
7. 默认观察 `viewModel.loading` 和 `viewModel.error`
8. `systemBars.updateWindowInsets(this)`

## Fragment 自动流程

- `onCreateView` 中 inflate binding，不调用 `setContentView`。
- `onViewCreated` 中创建 ViewModel 并注入 Ability。
- LiveData 使用 `viewLifecycleOwner` 观察。
- `onDestroyView` 中 `_binding = null`，并允许视图重建时重新注册观察者。

## 关键说明

- Fragment 的 `viewModel` 通过 `ViewModelProvider(ownerActivity)` 创建，因此与宿主 Activity 共享。
- Fragment 中 `binding` 只能在 `onCreateView` 之后、`onDestroyView` 之前使用。
- 子类可以覆写 `observeViewModelStates()`，不调用 `super` 即可禁用默认 loading/error 桥接。
- Activity 的 `permissions/messages/uiState/systemBars` 是 `protected open`，可以覆写传入自定义 Config。

## 验证方式

- Activity 中无需手动调用 `setContentView`。
- Fragment 销毁视图后访问 `binding` 会失败，说明旧视图引用已清理。
- `viewModel.loading` 变化时，默认状态层自动切换。

[返回 README](../README.md)