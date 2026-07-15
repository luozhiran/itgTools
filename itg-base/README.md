# ITG Base

`itg-base` 是 Android 页面层基础库，提供组合式 `Activity/Fragment` 基类、`ItgModel` 状态模型、常用 UI Ability、DataBinding 辅助和可清理引用工具。

本文档是入口页：先用场景表选择要看的能力，再跳转到 `docs/` 下的详细教程和可复制 Demo。

## 使用场景总览

| 使用场景 | 推荐做法 | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [快速接入 itg-base](./docs/01-quick-start.md) | `implementation(project(":itg-base"))` | Android Library，minSdk 21 | 新模块要复用基础页面能力 | 模块已开启 DataBinding，并依赖 AppCompat、core-ktx、activity-ktx |
| [创建自动绑定 Activity](./docs/02-base-activity-fragment.md) | `AutoBindingBaseActivity<VB, VM>` | `VB : ViewBinding`，`VM : ItgModel` | 页面需要自动 inflate、自动创建 ViewModel、内置权限/消息/状态能力 | 基类通过泛型反射解析 Binding 和 ViewModel，并在 `onCreate` 中注入 Ability |
| [创建自动绑定 Fragment](./docs/02-base-activity-fragment.md) | `AutoBindingBaseFragment<VB, VM>` | Fragment 视图生命周期内使用 | Fragment 需要自动 binding、共享宿主 Activity ViewModel、自动状态桥接 | Fragment 使用 `viewLifecycleOwner` 观察 LiveData，并在 `onDestroyView` 清空 binding |
| [定义页面 ViewModel](./docs/03-model-event.md) | 继承 `ItgModel` | 构造函数接收 `Application` | 页面需要统一 loading、error、event 状态 | `ItgModel` 内置 `loading`、`event`、`error` LiveData |
| [发送一次性事件](./docs/03-model-event.md) | `Event<T>` / `postEvent(value)` | LiveData 事件场景 | 导航、Toast、一次性动作不希望旋转屏幕后重复消费 | `Event.getContentIfNotHandled()` 只在首次消费时返回内容 |
| [自动处理 Loading/Error](./docs/03-model-event.md) | `showLoading()` / `hideLoading()` / `postError()` | 使用 `AutoBindingBaseActivity/Fragment` | ViewModel 发状态，页面自动显示 loading 或 toast error | 基类默认观察 `viewModel.loading` 和 `viewModel.error` 并桥接到 `uiState/messages` |
| [请求 Activity Result](./docs/04-abilities.md) | `launcher.startForResult(...)` | Activity 基类中直接使用 | 跳转页面并接收 resultCode/data | `ActivityResultAbility` 在 onCreate 阶段注册 launcher，满足 STARTED 前注册要求 |
| [选择图片或拍照](./docs/04-abilities.md) | `launcher.pickImage(...)` / `launcher.takePhoto(...)` | 需要系统选择器或相机保存 Uri | 头像、图片上传、拍照取证 | Ability 封装 `GetContent` 和 `TakePicture` contracts |
| [请求运行时权限](./docs/04-abilities.md) | `permissions.request(...)` / `requestMultiple(...)` | Activity onCreate 注入后 | 相机、录音、存储等运行时权限 | `PermissionAbility` 封装 Activity Result 权限 contract，并提供授权状态检查 |
| [显示 Toast/Snackbar](./docs/04-abilities.md) | `messages.toast(...)` / `snackbarAction(...)` | Activity 存活时 | 操作成功、失败、撤销提示 | `MessageAbility` 生命周期感知，Activity 销毁后不再弹；Snackbar 缺失时退化 Toast |
| [显示 Loading/Empty/Error](./docs/04-abilities.md) | `uiState.showLoading/showEmpty/showError` | 根布局是 `ViewGroup` | 页面需要统一状态页覆盖内容 | `UiStateAbility.bind(container)` 后创建 overlay 管理三种状态 View |
| [处理沉浸式和系统栏 Insets](./docs/04-abilities.md) | `systemBars` / `SystemBarAbility.Config` | Activity 页面 | 需要 edge-to-edge 和系统栏 padding 处理 | `SystemBarAbility` 调用 `enableEdgeToEdge` 并为根 View 应用 WindowInsets |
| [在 XML DataBinding 中绑定 LiveData](./docs/05-databinding.md) | `binding.bindLifecycle(owner)` | `ViewDataBinding` | XML 使用 `@{vm.xxx}` 观察 LiveData | 扩展函数设置 `lifecycleOwner`，LiveData 表达式跟随生命周期刷新 |
| [设置 DataBinding 变量](./docs/05-databinding.md) | `binding.bindVariable(BR.xxx, value)` | `ViewDataBinding` | 需要用代码设置 XML `<variable>` | 扩展函数调用 `setVariable` 后立即 `executePendingBindings()` |
| [清理 Adapter/临时引用](./docs/06-lifecycle-cleanup.md) | `AutoClearedValue` / `autoCleared()` | Fragment/Activity 销毁或视图销毁时 | Adapter、callback、view 引用需要手动断开避免泄漏 | 工具类将引用置空，`require()` 可在未设置或已清理时给出明确错误 |
| [查看完整 API](./docs/07-api-reference.md) | API 速查表 | 所有使用者 | 已知道场景，只想查方法名、返回值、注意点 | 汇总当前源码中真实存在的类和方法 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. 快速接入](./docs/01-quick-start.md) | 依赖、模块配置、最小页面示例 |
| [02. Activity 与 Fragment 基类](./docs/02-base-activity-fragment.md) | `AutoBindingBaseActivity`、`AutoBindingBaseFragment` 的使用和限制 |
| [03. ItgModel 与 Event](./docs/03-model-event.md) | loading、error、event、一次性事件消费 |
| [04. Ability 能力组件](./docs/04-abilities.md) | ActivityResult、Permission、Message、UiState、SystemBar |
| [05. DataBinding 扩展](./docs/05-databinding.md) | `bindLifecycle`、`bindVariable` |
| [06. 生命周期清理工具](./docs/06-lifecycle-cleanup.md) | `AutoClearedValue`、`autoCleared()` |
| [07. API 速查表](./docs/07-api-reference.md) | 当前真实 API 汇总 |

## 包名提示

当前源码中包名并不完全统一：

- 基类：`com.itg.itg_base.*`
- Ability：`com.itg.itg_base.ability.*`
- `ItgModel`、`Event`：`com.example.itg_base.arch.*`
- DataBinding 扩展：`com.example.itg_base.binding.*`
- 清理工具：`com.example.itg_base.util.*`

复制 Demo 时请按当前源码包名 import。