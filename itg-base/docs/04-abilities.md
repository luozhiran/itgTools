# 04. Ability 能力组件

本节说明 `itg-base` 内置 Ability：Activity Result、权限、消息、UI 状态和系统栏。

## 适用条件

- 使用 `AutoBindingBaseActivity/Fragment`，希望直接调用内置能力。
- 需要在 Activity 生命周期内注册 launcher、请求权限、显示状态页或处理系统栏。
- 需要通过 Config 或覆写方式定制 UI 表现。

## 推荐做法

| 能力 | API | 用途 |
| --- | --- | --- |
| `ActivityResultAbility` | `launcher.startForResult/pickImage/takePhoto` | 页面跳转、选图、拍照 |
| `PermissionAbility` | `permissions.request/requestMultiple` | 运行时权限 |
| `MessageAbility` | `messages.toast/snackbarAction` | Toast/Snackbar |
| `UiStateAbility` | `uiState.showLoading/showEmpty/showError` | Loading/Empty/Error |
| `SystemBarAbility` | `systemBars` | edge-to-edge 和 insets |

## 可复制 Demo

下面示例展示常见能力调用。需要替换 binding 类、目标 Activity 和 UI 控件。

```kotlin
import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.AutoBindingBaseActivity
import com.yourpkg.databinding.ActivityDemoBinding

class DemoModel(app: Application) : ItgModel(app) {
    fun reload() {
        // TODO: 重新加载数据
    }
}

class DemoActivity : AutoBindingBaseActivity<ActivityDemoBinding, DemoModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.pickImageButton.setOnClickListener {
            launcher.pickImage { uri: Uri? ->
                messages.toast("选择结果: $uri")
            }
        }

        binding.permissionButton.setOnClickListener {
            permissions.request(Manifest.permission.CAMERA) { granted ->
                if (granted) messages.toast("相机权限已授权") else messages.toast("相机权限被拒绝")
            }
        }

        binding.openButton.setOnClickListener {
            launcher.startForResult(Intent(this, TargetActivity::class.java)) { code, data ->
                messages.snackbar("返回 code=$code")
            }
        }

        binding.loadingButton.setOnClickListener {
            uiState.showLoading()
        }

        binding.errorButton.setOnClickListener {
            uiState.showError("加载失败") { viewModel.reload() }
        }
    }
}
```

## 自定义消息能力

```kotlin
import android.widget.Toast
import com.itg.itg_base.ability.MessageAbility

override val messages = MessageAbility(
    MessageAbility.Config(
        onToast = { ctx, msg, duration ->
            Toast.makeText(ctx, "[Demo] $msg", duration).show()
        }
    )
)
```

## 自定义状态页

```kotlin
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.itg.itg_base.ability.UiStateAbility

override val uiState = UiStateAbility(
    UiStateAbility.Config(
        loadingViewProvider = { ctx -> ProgressBar(ctx) },
        emptyViewProvider = { ctx, msg -> TextView(ctx).apply { text = msg } },
        errorViewProvider = { ctx, msg, retry ->
            Button(ctx).apply {
                text = msg
                setOnClickListener { retry?.invoke() }
            }
        }
    )
)
```

## 自定义系统栏

```kotlin
import androidx.core.view.WindowInsetsCompat
import com.itg.itg_base.ability.SystemBarAbility

override val systemBars = SystemBarAbility(
    SystemBarAbility.Config(
        edgeToEdge = true,
        onApplyInsets = { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
    )
)
```

## 关键说明

- `ActivityResultAbility` 必须在 Activity STARTED 前注册；基类在 `onCreate` 注入，满足要求。
- Fragment 中权限能力如果 Activity 已经 STARTED，建议复用 Activity 的 `PermissionAbility`，避免重复注册 launcher。
- `MessageAbility` Activity 销毁后不会继续弹 Toast/Snackbar。
- `UiStateAbility.bind(container)` 后会向容器添加 overlay，根布局必须是 `ViewGroup`。
- `SystemBarAbility.restorePadding(view)` 只对默认 padding 叠加模式有效。

## 验证方式

- 点击权限按钮能收到授权回调。
- 未引入 Material 时，`snackbar` 自动退化为 Toast。
- 调用 `uiState.showContent()` 后 loading/empty/error 都被隐藏。

[返回 README](../README.md)