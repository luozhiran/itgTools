# itg-base

组合式 Android MVVM 基座库——通过 Ability 组合模式，将 Activity/Fragment 常用能力拆分为独立、可复用、生命周期安全的组件。

## 要求

| 项目 | 最低版本 |
|------|---------|
| minSdk | 24 (Android 7.0) |
| compileSdk | 34+ |
| Kotlin | 1.9+ |
| Android Gradle Plugin | 8.0+ |

## 依赖

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.itg.itg:itg-base:1.0.0")
}
```

**必须开启 ViewBinding 或 DataBinding**：

```kotlin
android {
    buildFeatures {
        viewBinding = true  // 或 dataBinding = true
    }
}
```

可选（Snackbar 需要）：

```kotlin
implementation("com.google.android.material:material:1.14.0")
```

## 架构

```
AutoBindingBaseActivity<VB, VM>
├── binding: VB                    ← ViewBindingAbility 自动 inflate
├── viewModel: VM                  ← ViewModelAbility 自动创建
│
├── systemBars: SystemBarAbility   ← 边到边 + 系统栏 insets
├── launcher: ActivityResultAbility← 选图 / 拍照 / 跳转回调
├── permissions: PermissionAbility ← 运行时权限
├── messages: MessageAbility       ← Toast / Snackbar（生命周期感知）
└── uiState: UiStateAbility        ← Loading / Empty / Error 状态切换
        └── 自动观察 viewModel.loading → showLoading / showContent
                       viewModel.error   → toast
```

## 快速开始

### 1. 创建 ViewModel

```kotlin
class HomeModel(app: Application) : ItgModel(app) {

    val items = MutableLiveData<List<String>>()

    fun loadData() {
        showLoading()                           // → 自动显示 Loading
        viewModelScope.launch {
            try {
                items.value = api.fetchItems()
                hideLoading()                   // → Loading 自动消失
            } catch (e: Exception) {
                hideLoading()
                postError("加载失败：${e.message}") // → 自动弹 Toast
            }
        }
    }
}
```

### 2. 创建 Activity

```kotlin
class HomeActivity : AutoBindingBaseActivity<
    ActivityHomeBinding,  // ViewBinding 生成类
    HomeModel              // ViewModel 子类
>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // binding 和 viewModel 已经可用
        binding.tvTitle.text = "Home"
        viewModel.loadData()
    }
}
```

### 3. 完成

子类不需要手动 `inflate`、`setContentView`、`ViewModelProvider`、`observe` — 全部自动完成。

## Ability 详解

### LifeAbility（基类）

所有 Ability 的基类，通过 `inject(activity)` 注入生命周期。

| 方法 | 说明 |
|------|------|
| `inject(activity)` | 绑定 Activity 并注册生命周期观察 |
| `isActivityAlive()` | Activity 是否存活（已注入且未 destroy） |
| `onCreate/onStart/.../onDestroy` | 生命周期回调，子类按需覆写 |

**内存安全**：`onDestroy` 时自动 `removeObserver`，防止泄漏。

### SystemBarAbility

边到边显示 + 系统栏 insets 处理。

```kotlin
systemBars.useEnableEdgeToEdge()      // 开启边到边
systemBars.updateWindowInsets(this)   // 设置 insets padding
systemBars.restorePadding(view)       // 恢复原始 padding
```

自动保存原始 padding，insets padding 叠加在原始值之上，不会覆盖 XML 中设置的 padding。

### ViewModelAbility

自动创建 ViewModel（`by lazy` 延迟到首次访问）。

```kotlin
val viewModel = viewModelAbility.viewModel  // 类型为 VM
```

### ViewBindingAbility

反射调用 `XxxBinding.inflate(layoutInflater)` + `setContentView`。

```kotlin
val binding = bindingAbility.binding  // 类型为 VB
```

**R8 注意**：模块内置 keep rules，保护 inflate 方法不被混淆。

### UiStateAbility

Loading / Empty / Error 三态管理，纯代码创建 UI，无需额外资源。

```kotlin
uiState.bind(binding.root)           // 绑定到根布局
uiState.showLoading()                // 显示加载中
uiState.showEmpty("暂无数据")         // 显示空状态
uiState.showError("网络异常") { ... } // 显示错误 + 重试按钮
uiState.showContent()                // 显示正常内容
```

`AutoBindingBaseActivity` 中已自动观察 `viewModel.loading`，无需手动调用 `showLoading/hideLoading`。

### MessageAbility

Toast + Snackbar，生命周期感知。

```kotlin
messages.toast("提示")                    // 短 Toast
messages.toastLong("长提示")               // 长 Toast
messages.snackbar("操作完成")               // 短 Snackbar
messages.snackbar("已删除", "撤销") { }     // Snackbar + 操作
```

Activity 销毁后自动忽略，不会 crash。如果 classpath 上无 Material 库，Snackbar 自动退化为 Toast。

### PermissionAbility

运行时权限请求。

```kotlin
permissions.request(Manifest.permission.CAMERA) { granted ->
    if (granted) openCamera()
}

permissions.requestMultiple(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
) { results -> }

permissions.isGranted(Manifest.permission.CAMERA)     // 检查是否已授权
permissions.shouldShowRationale(permission)            // 是否应展示解释
```

### ActivityResultAbility

启动回调封装。

```kotlin
launcher.startForResult(intent) { resultCode, data -> }
launcher.pickImage { uri -> }                          // "image/*"
launcher.takePhoto(uri) { success -> }
launcher.requestPermission(perm) { granted -> }        // 建议用 PermissionAbility
```

## Fragment

```kotlin
class HomeFragment : AutoBindingBaseFragment<
    FragmentHomeBinding,
    HomeModel
>() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // binding 和 viewModel 已可用
        binding.tvTitle.text = "Home"
        viewModel.loadData()
    }
}
```

**关键区别**：LiveData 使用 `viewLifecycleOwner` 观察，确保 Fragment 视图重建时自动解绑旧观察者。

## ItgModel

ViewModel 基类，内置三个 LiveData：

| LiveData | 类型 | 说明 |
|----------|------|------|
| `loading` | `LiveData<Boolean>` | 加载状态，Activity 自动桥接到 UiStateAbility |
| `event` | `LiveData<Event<Any>>` | 一次性事件，配置变更不重复触发 |
| `error` | `LiveData<Event<String>>` | 错误信息，自动弹出 Toast |

### Event 机制

```kotlin
// ViewModel
postEvent(NavigateEvent.GoToDetail(id))

// Activity/Fragment
viewModel.event.observe(viewLifecycleOwner) { event ->
    event.getContentIfNotHandled()?.let { /* 只执行一次 */ }
}
```

`Event.getContentIfNotHandled()` 保证同一事件只消费一次——旋转屏幕后不会重复执行。

### 覆写自动桥接

如果不需要自动观察 loading/error：

```kotlin
override fun observeViewModelStates() {
    // 不调用 super，自己处理
    viewModel.loading.observe(this) { ... }
}
```

## 定制 UiState 样式

继承 `UiStateAbility` 覆写创建方法：

```kotlin
class BrandUiStateAbility : UiStateAbility() {
    override fun createLoadingView(ctx: Context): View {
        return LottieAnimationView(ctx).apply {
            setAnimation(R.raw.loading)  // 替换为 Lottie 动画
            playAnimation()
        }
    }
}
```

然后在 Activity 中用你的子类替换默认实例。

## 内存泄漏防护

| 机制 | 说明 |
|------|------|
| Lifecycle Observer 自动解绑 | `onDestroy` → `removeObserver(this)` |
| Event 一次性消费 | 配置变更后不重复触发 |
| MessageAbility 存活检查 | `isActivityAlive()` 阻止销毁后的 Toast |
| UiStateAbility 清理 | `onDestroy` 移除 overlay 并置 null |
| AutoClearedValue | 显式清理 Adapter/Dialog/Listener 引用 |
| viewLifecycleOwner | Fragment 视图重建时自动解绑 LiveData |

## 许可

Internal use.
