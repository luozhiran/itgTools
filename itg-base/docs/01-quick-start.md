# 01. 快速接入

本节说明如何接入 `itg-base`，以及如何写出最小可运行页面。

## 适用条件

- Android 页面模块需要复用基础 Activity/Fragment、ViewModel 状态、权限、消息和 UI 状态能力。
- 页面使用 ViewBinding 或 DataBinding 生成类。
- ViewModel 继承 `ItgModel`。

## 依赖配置

```kotlin
dependencies {
    implementation(project(":itg-base"))
}
```

`itg-base` 自身依赖：

- `androidx.appcompat`
- `androidx.core.ktx`
- `androidx.activity.ktx`
- `material` 为 `compileOnly`，未引入 Material 时 Snackbar 会退化为 Toast。

## 推荐做法

1. 创建布局，确保生成 `ActivityMainBinding`。
2. 创建继承 `ItgModel` 的 ViewModel。
3. Activity 继承 `AutoBindingBaseActivity<ActivityMainBinding, MainModel>`。

## 可复制 Demo

下面示例展示一个最小 Activity。需要替换 `ActivityMainBinding`、布局控件 id 和 `MainModel` 的业务逻辑。

```kotlin
import android.app.Application
import android.os.Bundle
import com.example.itg_base.arch.ItgModel
import com.itg.itg_base.AutoBindingBaseActivity
import com.yourpkg.databinding.ActivityMainBinding

class MainModel(app: Application) : ItgModel(app) {
    fun load() {
        showLoading()
        // TODO: 执行业务加载，完成后调用 hideLoading()
        hideLoading()
    }
}

class MainActivity : AutoBindingBaseActivity<ActivityMainBinding, MainModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.titleText.text = "Hello itg-base"
        binding.loadButton.setOnClickListener {
            viewModel.load()
        }
    }
}
```

## 关键说明

- 必须声明两个具体泛型：`VB` 和 `VM`。泛型不能是类型变量。
- `VM` 必须继承当前源码包名下的 `com.example.itg_base.arch.ItgModel`。
- `binding.root` 最好是 `ViewGroup`，否则 `uiState` 不会自动绑定状态层。
- Activity 基类会自动调用 `setContentView(binding.root)`。

## 验证方式

- IDE 能识别 `binding` 和 `viewModel`。
- Activity 启动后无需手动 inflate 布局。
- 点击按钮能调用 `viewModel.load()`。
- 如果调用 `showLoading()`，页面根布局为 ViewGroup 时会显示 loading overlay。

[返回 README](../README.md)