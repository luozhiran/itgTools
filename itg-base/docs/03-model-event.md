# 03. ItgModel 与 Event

本节说明 `ItgModel` 内置状态和 `Event` 一次性事件模型。

## 适用条件

- 页面 ViewModel 需要统一 loading、错误提示或一次性事件。
- 使用 `AutoBindingBaseActivity/Fragment` 自动观察 loading/error。
- LiveData 事件不希望在旋转屏幕后重复消费。

## 推荐做法

ViewModel 继承 `ItgModel`：

```kotlin
class MainModel(app: Application) : ItgModel(app)
```

发送状态：

```kotlin
showLoading()
hideLoading()
postError("加载失败")
postEvent(Navigation.GoHome)
```

## 可复制 Demo

下面示例展示 loading、error 和导航事件。需要在页面中观察 `viewModel.event`。

```kotlin
import android.app.Application
import android.os.Bundle
import com.example.itg_base.arch.Event
import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.AutoBindingBaseActivity
import com.yourpkg.databinding.ActivityMainBinding

sealed class MainEvent {
    data object GoDetail : MainEvent()
}

class MainModel(app: Application) : ItgModel(app) {
    fun load(success: Boolean) {
        showLoading()

        if (success) {
            hideLoading()
            postEvent(MainEvent.GoDetail)
        } else {
            hideLoading()
            postError("加载失败")
        }
    }
}

class MainActivity : AutoBindingBaseActivity<ActivityMainBinding, MainModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.event.observe(this) { event: Event<Any> ->
            when (event.getContentIfNotHandled()) {
                MainEvent.GoDetail -> openDetailPage()
            }
        }

        binding.loadButton.setOnClickListener {
            viewModel.load(success = true)
        }
    }

    private fun openDetailPage() {
        // TODO: 打开详情页
    }
}
```

## 关键说明

- `showLoading()`、`hideLoading()`、`postEvent()`、`postError()` 是 `protected`，只能在子类中调用。
- `AutoBindingBaseActivity/Fragment` 默认只自动观察 `loading` 和 `error`。
- `event` 需要页面按业务类型自行观察和消费。
- `Event.peekContent()` 不会标记已消费，适合调试或只读检查。

## 自动桥接行为

基类默认行为：

| ViewModel 状态 | UI 行为 |
| --- | --- |
| `loading = true` | `uiState.showLoading()` |
| `loading = false` | `uiState.showContent()` |
| `error` 事件 | `messages.toast(message)` |

## 验证方式

- 连续调用 `event.getContentIfNotHandled()`，只有第一次返回内容。
- 旋转屏幕后已消费事件不会再次触发导航。
- `postError("xxx")` 后页面默认显示 Toast。

[返回 README](../README.md)