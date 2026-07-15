# 07. API 速查表

本文件汇总当前源码中真实存在的类和方法。

## 基类

| API | 说明 |
| --- | --- |
| `AutoBindingBaseActivity<VB, VM>` | 自动 binding、ViewModel、Ability 注入的 Activity 基类 |
| `AutoBindingBaseFragment<VB, VM>` | 自动 binding、共享 Activity ViewModel、视图销毁清理的 Fragment 基类 |
| `observeViewModelStates()` | 可覆写，默认桥接 loading/error 到 UI |

## Arch

| API | 说明 |
| --- | --- |
| `ItgModel.loading` | `LiveData<Boolean>` loading 状态 |
| `ItgModel.event` | `LiveData<Event<Any>>` 一次性事件 |
| `ItgModel.error` | `LiveData<Event<String>>` 错误事件 |
| `showLoading()` / `hideLoading()` | 子类中修改 loading 状态 |
| `postEvent(value)` | 子类中发送一次性事件 |
| `postError(message)` | 子类中发送错误事件 |
| `Event.getContentIfNotHandled()` | 首次消费返回内容，之后返回 null |
| `Event.peekContent()` | 查看内容但不标记消费 |
| `Event.isHandled` | 当前事件是否已消费 |

## ActivityResultAbility

| API | 说明 |
| --- | --- |
| `startForResult(intent, callback)` | 启动 Activity 并返回 resultCode/data |
| `pickImage(mimeType, callback)` | 打开系统选择器选择内容，默认 `image/*` |
| `requestPermission(permission, callback)` | 请求单个权限 |
| `takePhoto(uri, callback)` | 调用系统相机保存到指定 Uri |

## PermissionAbility

| API | 说明 |
| --- | --- |
| `request(permission, callback)` | 请求单个权限 |
| `requestMultiple(vararg permissions, callback)` | 请求多个权限 |
| `isGranted(permission)` | 检查权限是否已授权 |
| `shouldShowRationale(permission)` | 是否应展示权限说明 |
| `PermissionAbility.Config(onPermanentlyDenied)` | 权限永久拒绝回调 |

## MessageAbility

| API | 说明 |
| --- | --- |
| `toast(message)` | 短 Toast |
| `toastLong(message)` | 长 Toast |
| `snackbar(message)` | Snackbar，无 anchor 或无 Material 时退化 Toast |
| `snackbarAction(message, actionText, action)` | 带操作的 Snackbar |
| `MessageAbility.Config(onToast, onSnackbar)` | 自定义消息展示 |

## UiStateAbility

| API | 说明 |
| --- | --- |
| `bind(container)` | 绑定状态层容器 |
| `showLoading()` | 显示 loading |
| `showEmpty(message)` | 显示 empty |
| `showError(message, retryAction)` | 显示 error，可带重试 |
| `showContent()` | 隐藏所有状态 View |
| `UiStateAbility.Config(...)` | 自定义 loading/empty/error View |

## SystemBarAbility

| API | 说明 |
| --- | --- |
| `useEnableEdgeToEdge()` | 开启 edge-to-edge，内部防重复 |
| `updateWindowInsets(activity)` | 给 Activity 根 View 应用 insets |
| `updateWindowInsets(rootView)` | 给指定 View 应用 insets |
| `restorePadding(view)` | 恢复默认模式保存的原始 padding |
| `findActivityRootView(activity)` | 查找 Activity content 的第一个子 View |

## DataBinding 扩展

| API | 说明 |
| --- | --- |
| `ViewDataBinding.bindLifecycle(owner)` | 设置 lifecycleOwner |
| `ViewDataBinding.bindVariable(variableId, value)` | 设置 variable 并 executePendingBindings |

## 清理工具

| API | 说明 |
| --- | --- |
| `AutoClearedValue<T>.get()` | 返回当前值或 null |
| `AutoClearedValue<T>.set(value)` | 设置引用 |
| `AutoClearedValue<T>.clear()` | 清理引用 |
| `AutoClearedValue<T>.require()` | 获取值，未设置或已清理时抛异常 |
| `autoCleared<T>()` | 可赋 null 清理的属性委托 |

## 可复制 Demo

下面示例展示多个 API 的组合。需要替换 binding 类、Adapter 和业务逻辑。

```kotlin
import android.app.Application
import android.os.Bundle
import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.AutoBindingBaseActivity
import com.yourpkg.databinding.ActivityDemoBinding

class DemoModel(app: Application) : ItgModel(app)

class DemoActivity : AutoBindingBaseActivity<ActivityDemoBinding, DemoModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.button.setOnClickListener {
            permissions.request(android.Manifest.permission.CAMERA) { granted ->
                if (granted) messages.toast("granted") else messages.toast("denied")
            }
        }
        uiState.showContent()
    }
}
```

## 包名速查

| 能力 | 当前包名 |
| --- | --- |
| 基类 | `com.itg.itg_base.*` |
| Ability | `com.itg.itg_base.ability.*` |
| `ItgModel` / `Event` | `com.example.itg_base.arch.*` |
| DataBinding 扩展 | `com.example.itg_base.binding.*` |
| 清理工具 | `com.example.itg_base.util.*` |

[返回 README](../README.md)