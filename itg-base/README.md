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
    implementation("com.example.itg:itg-base:1.0.0")
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

边到边显示 + 系统栏 insets 处理，通过 `Config` 支持三级定制。

**零配置（默认行为）：**

```kotlin
systemBars.useEnableEdgeToEdge()      // 开启边到边
systemBars.updateWindowInsets(this)   // 设置 insets padding
systemBars.restorePadding(view)       // 恢复原始 padding
```

**Config 参数：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `edgeToEdge` | `true` | 是否开启边到边 |
| `insetsTypes` | `systemBars()` | 处理哪些栏：`statusBars()` / `navigationBars()` / `systemBars()` / `ime()` |
| `onApplyInsets` | `null` | null=默认叠加 padding；非 null=完全接管 insets→View 映射 |

**场景一：只处理状态栏，不管导航栏：**

```kotlin
class MyActivity : AutoBindingBaseActivity<MyBinding, MyModel>() {
    override val systemBars by lazy {
        SystemBarAbility(
            SystemBarAbility.Config(
                insetsTypes = WindowInsetsCompat.Type.statusBars()
            )
        )
    }
}
```

**场景二：insets 用 margin 而非 padding：**

```kotlin
override val systemBars by lazy {
    SystemBarAbility(
        SystemBarAbility.Config(
            onApplyInsets = { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                (view.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                    topMargin = bars.top
                    bottomMargin = bars.bottom
                }
                insets
            }
        )
    )
}
```

**场景三：完全禁用边到边：**

```kotlin
override val systemBars by lazy {
    SystemBarAbility(SystemBarAbility.Config(edgeToEdge = false))
}
```

不覆写 `systemBars` 则自动使用默认配置，与之前行为完全一致。

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

Loading / Empty / Error 三态管理，支持 Config 一键替换或子类深度定制。

**默认使用：**

```kotlin
uiState.bind(binding.root)           // 绑定到根布局
uiState.showLoading()                // 显示加载中
uiState.showEmpty("暂无数据")         // 显示空状态
uiState.showError("网络异常") { ... } // 显示错误 + 重试按钮
uiState.showContent()                // 显示正常内容
```

`AutoBindingBaseActivity` 中已自动观察 `viewModel.loading`，无需手动调用 `showLoading/hideLoading`。

**Config 参数：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `loadingViewProvider` | `null` | 替换 Loading 视图，`(Context) -> View` |
| `emptyViewProvider` | `null` | 替换空状态视图，`(Context, String) -> View` |
| `errorViewProvider` | `null` | 替换错误视图，`(Context, String, retryAction?) -> View` |

**方式一：Config（一行换 Loading 样式）：**

```kotlin
override val uiState by lazy {
    UiStateAbility(
        UiStateAbility.Config(
            loadingViewProvider = { ctx ->
                LottieAnimationView(ctx).apply {
                    setAnimation(R.raw.loading)
                    playAnimation()
                }
            }
        )
    )
}
```

**方式二：子类覆写（换多个 + 加逻辑）：**

```kotlin
override val uiState by lazy {
    object : UiStateAbility() {
        override fun createLoadingView(ctx: Context) =
            LottieAnimationView(ctx).apply { setAnimation(R.raw.loading); playAnimation() }

        override fun createEmptyView(ctx: Context, msg: String) =
            MyEmptyView(ctx).apply { setMessage(msg) }

        override fun createErrorView(ctx: Context, msg: String, retry: (() -> Unit)?) =
            MyErrorView(ctx).apply { setMessage(msg); setOnRetry { retry?.invoke() } }
    }
}
```

### MessageAbility

Toast + Snackbar，生命周期感知，支持 Config 替换或子类覆写。

**默认使用：**

```kotlin
messages.toast("提示")                    // 短 Toast
messages.toastLong("长提示")               // 长 Toast
messages.snackbar("操作完成")               // 短 Snackbar
messages.snackbar("已删除", "撤销") { }     // Snackbar + 操作
```

Activity 销毁后自动忽略，不会 crash。如果 classpath 上无 Material 库，Snackbar 自动退化为 Toast。

**Config 参数：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `onToast` | `null` | 替换 Toast，`(Context, String, duration) -> Unit` |
| `onSnackbar` | `null` | 替换 Snackbar，`(View, String, actionText?, action?) -> Unit` |

**方式一：Config（替换为自定义 Toast 库 / 顶部 Banner）：**

```kotlin
override val messages by lazy {
    MessageAbility(
        MessageAbility.Config(
            onToast = { ctx, msg, _ -> Toasty.custom(ctx, msg, R.drawable.ic_info).show() },
            onSnackbar = { anchor, msg, actionText, action ->
                showTopBanner(msg, actionText, action)
            }
        )
    )
}
```

**方式二：子类覆写：**

```kotlin
override val messages by lazy {
    object : MessageAbility() {
        override fun showToast(context: Context, message: String, duration: Int) {
            MyToastUtils.show(context, message)
        }
    }
}
```

### PermissionAbility

运行时权限请求，支持 Config 处理永久拒绝场景。

**默认使用：**

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

**Config 参数：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `onPermanentlyDenied` | `null` | 权限被永久拒绝时回调，参数为被拒绝的权限名 |

**自定义：权限被永久拒绝时引导用户去设置页：**

```kotlin
override val permissions by lazy {
    PermissionAbility(
        PermissionAbility.Config(
            onPermanentlyDenied = { perm ->
                AlertDialog.Builder(this@MyActivity)
                    .setMessage("$perm 权限已被禁用，请前往设置页开启")
                    .setPositiveButton("去设置") { _, _ -> openAppSettings() }
                    .show()
            }
        )
    )
}
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

## 自定义 Ability

所有需要定制的 Ability 遵循统一的三层模式：

```
Level 1            Level 2               Level 3
Config 数据类       覆写 protected open    整体替换
──────────         ──────────────────     ────────
一行搞定额色/样式    按需改几个方法          完全控制

SystemBarAbility.Config                     override val systemBars
MessageAbility.Config                           by lazy { MySystemBarAbility() }
UiStateAbility.Config
PermissionAbility.Config
```

| Ability | Config 关键参数 |
|---------|---------------|
| `SystemBarAbility` | `insetsTypes` / `onApplyInsets` / `edgeToEdge` |
| `MessageAbility` | `onToast` / `onSnackbar` |
| `UiStateAbility` | `loadingViewProvider` / `emptyViewProvider` / `errorViewProvider` |
| `PermissionAbility` | `onPermanentlyDenied` |

不覆写的默认行为不受影响，零行额外代码走默认。

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
