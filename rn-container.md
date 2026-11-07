# ITG RN 容器设计方案

> 状态：设计稿；目标模块为 `:itg-rn-container`，最终产物为供 Android 业务工程依赖的 AAR。本文所说的“model”按 Android 工程惯例落为 **module**。

## 1. 目标与边界

容器负责把 React Native（下称 RN）视图嵌入业务已有的 Activity/Fragment，管理每个嵌入实例的组件、参数、渲染生命周期与通信，并以 `com.itg:itg-rn-container:<version>` 发布。业务方只需提供 `FrameLayout` 占位视图并调用容器 API，不直接操作 `ReactFragment` 的事务细节。

首版范围：

- 以 `ReactFragment` 为默认页面载体；每个容器实例独立持有一个 RN Fragment、组件名、页面参数及销毁状态，可同时嵌入多个占位区域。
- JS Bundle 随宿主 APK/AAB 发布；容器 AAR 交付容器代码、清单、资源、必要时容器自己的预编译 `.so`，并通过 Maven 元数据声明依赖。
- 支持在占位区域挂载/卸载、传入初始参数、页面结果、基础错误回调和必要的 JS↔Native 能力。
- 发布 `release` AAR 与 Maven 元数据，并提供独立业务接入验证。

暂不纳入首版：远程热更新、运行时下载 Bundle、多 RN 版本共存、每个容器独立 JS 引擎。后续确有场景时再扩展。

**关键交付事实：**单独拷贝一个 `.aar` 文件不能自动携带 RN/Hermes 的全部依赖，也不能凭空提供业务 JS 页面。对业务的标准交付物应是 **Maven 坐标所指的 AAR + POM/Gradle 元数据 + 业务 JS Bundle 的构建接入说明**。Android 官方同样建议以 Maven 仓库发布 AAR，使传递依赖可被解析。[Android 库发布说明](https://developer.android.com/build/publish-library/upload-library)

## 2. 与当前工程的关系

仓库目前使用 `settings.gradle.kts` 管理多模块，`gradle/libs.versions.toml` 管理版本，Android 库模块普遍采用 `maven-publish`。现有 Android 配置为 AGP `9.2.1`、Gradle `9.4.1`、`compileSdk 36.1`、`minSdk 24`、Java 11；`app` 是示例宿主。新模块沿用 Kotlin DSL、`singleVariant("release")`、`consumerProguardFiles` 和当前发布坐标风格。

**版本约束：RN 固定在 0.84.x 系列。**实施时再选定具体补丁号；npm `react-native`、`react-android`、`hermes-android` 与 RN Gradle 插件必须使用同一版本线，React 版本遵循 0.84 模板及其 peer dependency。RN 0.84 要求 Node.js **22.11+**，默认使用 **Hermes V1**，且只运行新架构；不要把旧架构回退当作兼容方案。[RN 0.84 发布说明](https://reactnative.dev/blog/2026/02/11/react-native-0.84)

**当前工程存在构建工具链冲突风险。**RN 官方在 0.87 才首次加入 AGP 9 支持，而本仓库已使用 AGP 9.2.1 和 Gradle 9.4.1，因此不能承诺 RN 0.84 直接加入当前根工程后可以构建。[RN 0.87 的 AGP 9 支持说明](https://reactnative.dev/blog/2026/08/11/react-native-0.87) 首先用 RN 0.84 的官方模板工具链完成独立宿主验证，再试验现有根工程；若现有组合不通，推荐把 `itg-rn-container` 源码保留在仓库内，用独立 RN 0.84 构建入口和兼容的 AGP/Gradle 版本发布 AAR，而不是为了容器直接改动所有现有模块的构建版本。最终业务 App 的工具链也必须单独验证。

截至本文修订时，RN 官方已将 0.84 标为不受支持系列；选择 0.84 后，应由团队自行维护补丁评估、依赖漏洞跟踪和升级预案。[RN 版本支持状态](https://reactnative.dev/releases/overview)

## 3. 推荐架构

```text
业务 Android App
├─ Application：按 RN 0.84 集成要求提供 ReactHost
├─ Activity/Fragment 布局：FrameLayout 占位
├─ app/build.gradle.kts：RN Gradle 插件、JS 入口与 Bundle 构建
├─ assets/index.android.bundle：由业务 JS 工程构建，进入 APK/AAB
└─ implementation("com.itg:itg-rn-container:<version>")
                        │
                        ▼
             :itg-rn-container（发布 AAR）
             ├─ ItgRnContainer.attach(...)：创建容器实例
             ├─ ItgRnInstance：持有自己的 ReactFragment 与页面状态
             ├─ ItgRnBridgePackage：受控 Native 能力
             ├─ Bundle/页面校验与错误回调
             └─ 资源、consumer rules
                        │
                        ▼
             react-android + hermes-android（Maven 传递依赖）
```

RN 0.84 官方的 Fragment 集成方式是：宿主放置 `FrameLayout`，用 `ReactFragment.Builder().setComponentName(...).setLaunchOptions(...).build()` 创建 Fragment，再通过 `FragmentManager` 挂载。官方也明确要求先完成现有 App 的 RN 基础接入。[RN 0.84 Fragment 集成](https://reactnative.dev/docs/0.84/integration-with-android-fragment) 容器封装 Fragment 创建与事务，但无法替宿主完成 JS 工程构建，也无法在 AAR 发布后自动发现业务工程新增的原生 RN 包。

### 3.1 实例隔离与运行时边界

- `ItgRnInstance` 是每次 `attach` 创建的对象，不使用全局页面单例；它拥有 `instanceId`、`ReactFragment`、组件名、初始参数、状态与回调。一个实例只控制自己的 `containerViewId`，卸载时仅移除自己的 Fragment，Fragment tag 使用 `instanceId` 避免互相覆盖。实例索引按宿主 `FragmentManager` 作用域管理，并随宿主销毁清理。
- `ReactFragment` 提供的是**视图/页面实例隔离**。RN 0.84 的官方 Fragment 路径仍基于宿主已有的 RN 基础接入；`ReactApplication` 的 `reactHost` 是 Application 提供的入口。由此不能推断 `ReactFragment.Builder` 会为每个 Fragment 创建独立 JS 引擎或从不同 Host 选路。[RN 0.84 Fragment 集成](https://reactnative.dev/docs/0.84/integration-with-android-fragment)、[RN 0.84 ReactApplication 接口](https://github.com/react/react-native/blob/v0.84.0/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/ReactApplication.kt)
- 默认实现让多个 `ItgRnInstance` 各自渲染自己的组件和参数，同时复用宿主提供的 `ReactHost`。容器不把页面路由表和页面状态塞进 Application；宿主只承担 RN 初始化、原生包注册和运行时入口。
- 如果“独立”明确要求**每个实例有自己的 JS VM、Bundle 或 Native Module 注册表**，应另做多 `ReactHost` 方案验证：由容器管理 Host 与 `ReactSurface` 的绑定、Activity 生命周期分发和资源释放。`ReactHost.createSurface(...)` 提供创建渲染 Surface 的接口，但这条路径不等同于直接使用官方 `ReactFragment.Builder`，不能在首版承诺多运行时路由。[RN 0.84 ReactHost 接口](https://github.com/react/react-native/blob/v0.84.0/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/ReactHost.kt)
- 不继承或强制绑定当前项目的 `itg-base`、TheRouter、KSP 等模块，保持容器可独立用于其他业务工程。业务需要路由时在 App 层做适配。

### 3.2 Fragment 页面承载与接入 API

业务 Activity 或 Fragment 在布局中提供占位区域，由容器创建 `ReactFragment` 并挂载到对应的 `FragmentManager`：

```xml
<FrameLayout
    android:id="@+id/rn_slot"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
// 在 Activity.onCreate 中，setContentView 之后调用；Fragment 宿主则在 onViewCreated 调用。
val rnInstance = ItgRnContainer.attach(
    fragmentManager = supportFragmentManager,
    containerViewId = R.id.rn_slot,
    componentName = "ItgRnRoot",
    pageId = "order/detail",
    params = mapOf("orderId" to orderId)
)
```

`attach` 返回本次容器实例的句柄，可用于 `detach()` 和订阅状态。内部使用 `ReactFragment.Builder().setComponentName(componentName).setLaunchOptions(bundle)`，再提交 Fragment 事务；业务不需要自己管理 `ReactFragment`。`pageId` 是业务路由标识，`params` 只允许可序列化的 JSON 基本类型、列表和对象；不传 `Activity`、回调、`Parcelable` 或敏感令牌。初始属性包含 `instanceId`、`pageId`、`params`；`componentName` 必须与 JS 的 `AppRegistry.registerComponent` 名称一致。首版参数变化通过重新挂载实例实现，避免把 `setLaunchOptions` 误当作运行时更新 API。[RN 0.84 Fragment 集成](https://reactnative.dev/docs/0.84/integration-with-android-fragment)

实例句柄在配置变化或进程恢复后不能直接复用旧对象；容器需按 Fragment tag 查找已恢复 Fragment，避免重复 `add`。Activity 使用 `supportFragmentManager`，业务 Fragment 使用 `childFragmentManager`；调用方需在 `isStateSaved` 之前挂载，或由容器排队到安全生命周期时机。`detach()` 只移除对应实例并清理回调。多个占位区域、同屏两个 RN Fragment、反复挂载/卸载都应测试。

宿主 Activity 还需按 RN 0.84 Fragment 指南处理硬件返回键；针对当前项目 `targetSdk 36`，使用 `OnBackPressedDispatcher`，避免依赖已弃用的 `onBackPressed()`。[RN 0.84 Fragment 集成](https://reactnative.dev/docs/0.84/integration-with-android-fragment) 同屏多实例时由宿主指定当前获得焦点的容器；RN 不消费返回键时将事件交还原生导航，并防止重新进入同一个返回键回调。失败时在该占位区域显示原生错误态并通知业务回调；预热运行时作为性能优化选项，首版以实测结果决定。

### 3.3 JS 包与原生包

- **业务 JS 所有权在业务工程。**推荐在仓库新增 `rn-demo/` 作为示例 JS 工程；正式业务工程使用自己的 `package.json`、锁文件、Metro 配置和 `index.js`。可统一注册 `ItgRnRoot`，按 `pageId` 分发；也可为不同业务注册多个 `componentName`，由各容器实例分别指定。
- 标准模式由宿主 `app` 使用 RN Gradle 插件生成 release Bundle。插件默认产物名为 `index.android.bundle`；`debuggableVariants` 不打入 Bundle，开发环境需 Metro。生产变体必须包含 Bundle。[RN 0.84 Gradle 插件](https://reactnative.dev/docs/0.84/react-native-gradle-plugin)
- 容器只预置明确列入清单的 Native Module。若业务 JS 使用第三方含 Android 原生代码的 RN 包，由业务 App 安装、autolink/Codegen 并把相应 `ReactPackage` 注册到宿主 `ReactHost`；AAR 不会自动获得业务的 `PackageList`。首版应有一个纯 JS 页面和一个含原生包页面作为接入验证。
- RN 0.84 仅运行新架构，Hermes V1 为默认引擎。容器和业务原生三方包都要核对新架构兼容性，且构建产物应验证实际使用的 Hermes 版本。[RN 0.84 发布说明](https://reactnative.dev/blog/2026/02/11/react-native-0.84)

### 3.4 JS ↔ Native 协议

首版桥接只暴露业务必需的少量能力，例如 `closeContainer(instanceId, result)`、`getAppInfo()`、`openNativeRoute(route, params)`。由容器定义白名单和错误码，宿主通过 `ItgRnNativeHandler` 处理业务路由和鉴权相关操作。JS 到原生请求统一携带 `instanceId` 与 `requestId`，由容器把结果路由回对应的活跃 Fragment 实例；已卸载实例直接返回明确错误，不能误投给同屏其他容器。成功与失败都返回结构化结果；所有 UI 操作切主线程，回调不强持有已销毁 Activity。

首版优先评估用容器 AAR 内的 Kotlin/Java `ReactPackage` 实现这几个基础能力，并在 RN 0.84 新架构下验证互操作层能否满足需求；这样容器本身不需要定义 Codegen Spec，也不产生容器专属 C++。如验证不满足或后续需要同步方法、Fabric 组件，再按 RN 0.84 的 Codegen/TurboModule 路径实施。对外只暴露稳定的容器 API，不让业务依赖内部生成类；不要提供可执行任意类名/方法名的通用反射桥。首次落地先实现 `closeContainer` 一条端到端链路。

### 3.5 Bundle 版本与运行校验

推荐在业务 JS 包旁生成 `rn-bundle.json`，至少包含 `bundleVersion`、`rnVersion`、`componentNames`、`pageIds` 和构建时 SHA-256。容器挂载前校验资产存在、组件名已注册且 RN 主版本/次版本一致；离线打包产物在 CI 比对清单哈希。首版 Bundle 随 APK/AAB 发布，不做下载、解压和动态执行，因此更新走正常应用发布流程。

## 4. 模块目录建议

```text
itg-rn-container/
├─ build.gradle.kts
├─ consumer-rules.pro
└─ src/
   ├─ main/
   │  ├─ AndroidManifest.xml
   │  └─ java/com/itg/rncontainer/
   │     ├─ ItgRnContainer.kt
   │     ├─ ItgRnConfig.kt
   │     ├─ ItgRnInstance.kt
   │     ├─ fragment/ItgRnFragmentFactory.kt
   │     ├─ internal/ScopedInstanceRegistry.kt
   │     ├─ bridge/ItgRnBridgePackage.kt
   │     └─ internal/BundleVerifier.kt
   ├─ test/
   └─ androidTest/
app/                         # 本仓库示例宿主，验证真实接入
rn-demo/                     # 示例 JS 工程；不作为容器 AAR 的隐式内容
```

若当前根工程的 AGP 9 组合验证通过，可用仓库 `app` 通过 Maven 本地仓库坐标做接入验证。若验证不通过，`app` 保持现状，改用独立 RN 0.84 宿主验证；不能只用 `implementation(project(":itg-rn-container"))` 证明发布链路可用。仓库其他模块目前多发布到各自 `build/repo`，容器可沿用这一方式，同时在 CI 保存仓库目录或推送内部 Maven 仓库。

## 5. 构建与发布设计

1. 先验证构建工具链。只有当前根工程组合可用时，才在根 `settings.gradle.kts` 加入 `include(":itg-rn-container")`；否则在仓库内设置独立 RN 0.84 `settings.gradle.kts`/Wrapper，引用 `itg-rn-container` 源码目录并从该构建入口发布。两种路径都集中声明固定的 `rn` 版本，不在各处分别写版本字符串。
2. 模块使用 `com.android.library` 与 `maven-publish`，`namespace = "com.itg.rncontainer"`，`minSdk = 24`，`compileSdk` 沿用当前工程。`consumerProguardFiles("consumer-rules.pro")` 只放容器 API 与确需保留的反射/桥接规则。
3. `react-android` 与 `hermes-android` 以正常 Maven 依赖发布；如公开 API 暴露 RN 类型，用 `api`，否则优先 `implementation`。不可依靠一个手工合并的“fat AAR”隐藏 RN 的原生库、资源与依赖冲突。
4. `singleVariant("release") { withSourcesJar() }`，发布坐标建议 `com.itg:itg-rn-container:0.1.0`，发布内容检查 AAR、POM、Gradle module metadata、sources JAR。
5. 业务 App 的 `com.facebook.react` 插件按 RN 0.84 集成指南设置；JS Bundle 打包任务属于 App release 构建，不应假设 `:itg-rn-container:assembleRelease` 会产出业务页面。[RN 0.84 Gradle 插件职责](https://reactnative.dev/docs/0.84/react-native-gradle-plugin)

### 5.1 Codegen/C++ 产物如何交付

先区分源码归属。`app/build/generated/source/codegen/jni` 是业务 App 自己的 Spec 生成物；依赖包的 Spec 会在对应依赖项目的 `android/build/generated/source/codegen/jni` 下生成；App 的 autolinking/入口代码还依赖本次 App 实际安装的 RN 包集合。RN 0.84 的 Codegen 文档明确把这些生成物分别放在 App 和依赖项目下。[RN 0.84 Codegen 说明](https://reactnative.dev/docs/0.84/the-new-architecture/using-codegen)

| 产物 | 交付方式 | 业务侧责任 |
| --- | --- | --- |
| RN 核心与 Hermes 的 C++ 运行库 | `react-android`、`hermes-android` 的 Maven 依赖 | 对齐 RN 0.84 版本与 ABI，不复制源码 |
| 容器自己的 TurboModule/Fabric Spec 生成的 Java/C++ | 在容器发布构建中生成并编译；Java 字节码和编译后的 `.so` 随容器 AAR 发布，原始 C++ 源码不作为业务接入产物 | 按容器提供的注册方式接入；若依赖 autolinking，提供配套 npm 元数据 |
| App 自己的 Spec、autolinking 与最终入口胶水代码 | 由业务 App 的 RN Gradle 插件按其 JS 依赖图生成 | 保留 RN 构建插件、Node/Codegen 与本地构建步骤；不能从容器 AAR 预先提供通用版本 |

**推荐的首版路径：**容器只承载 `ReactFragment` 和少量 Kotlin/Java 桥接，先验证 RN 0.84 互操作层的运行结果，不新增容器专属 Spec；此时容器没有需要“提供给业务”的自定义 C++ 源码。业务 App 仍要按 RN 0.84 模板执行自身的 Codegen/autolinking，因为它负责最终依赖集合和 JS Bundle。RN 0.82 起新架构为唯一运行时，但官方仍保留互操作层；具体桥接类在 0.84 下能否运行，以独立宿主真机验证为准。[RN 新架构与互操作层说明](https://reactnative.dev/blog/2025/10/08/react-native-0.82)

**若容器必须使用 TurboModule/Fabric：**在容器源码旁维护 `package.json`、TypeScript/Flow Spec 和 `codegenConfig`，固定 RN 0.84 补丁版；采用官方 `includesGeneratedCode: true` 路径，把生成 Java/C++ 作为库的构建输入，再通过 Android library 的 CMake/NDK 构建为各目标 ABI 的 `.so`，随 AAR 的 `jni/<abi>/` 交付。AAR 可以包含 `.so`；对需要提供 C++ 头文件给下游编译的场景，再考虑 Prefab。发布前检查 AAR 内容、符号链接和独立宿主加载。[RN 0.84 预生成代码指南](https://reactnative.dev/docs/0.84/the-new-architecture/codegen-cli)、[Android AAR 结构](https://developer.android.com/studio/projects/android-library)

`includesGeneratedCode` 只解决**容器自身**生成代码的交付，不会自动替业务 App 生成 PackageList、CMake autolinking 或注册所有业务原生包。若只发 Maven AAR、没有配套 npm 包，RN CLI 无法通过 npm 依赖自动发现该容器；需要在业务 `Application` 显式注册容器的 `ReactPackage`，并验证新架构所需的 native 注册。若希望业务侧自动链接，另提供薄 npm 包的 `package.json`/`react-native.config.js`，映射到已发布的 Android AAR，并在独立宿主验证不会重复编译或重复打包 `.so`。[RN 0.84 预生成代码指南](https://reactnative.dev/docs/0.84/the-new-architecture/codegen-cli)

CI 增加三项检查：AAR 中目标 ABI 的 `jni/<abi>/*.so`（仅在容器确有自定义 C++ 时）、业务 APK/AAB 中最终 native 库与 Bundle、独立宿主的 JS→Native 调用。不能仅凭 `generateCodegenArtifactsFromSchema` 成功就判定 AAR 可用。

示意配置（最终语法需按 RN 补丁版和构建兼容性验证结果落地）：

```kotlin
// itg-rn-container/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.itg.rncontainer"
    // compileSdk/minSdk/Java 配置沿用本仓库库模块约定
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    api("com.facebook.react:react-android:<固定 RN 版本>")
    implementation("com.facebook.react:hermes-android:<同一 RN 版本>")
}

// afterEvaluate 中从 components["release"] 创建 MavenPublication；
// groupId = "com.itg", artifactId = "itg-rn-container"。
```

发布 AAR 时，CI 应检查 `pom.xml` 是否含上述 RN 依赖；业务工程使用 `dependencyInsight` 检查是否出现重复或被强制升级的 RN/Hermes 版本。容器版本与 RN 版本建立映射表，例如 `0.1.x → RN 0.84.<已验证补丁>`，升级 RN 时重新跑完整接入矩阵。

## 6. 业务接入契约

业务方接入步骤应压缩到以下可核验流程：

1. 配置内部 Maven 仓库，依赖 `com.itg:itg-rn-container:<version>`；对齐容器声明的 RN 版本。
2. 在宿主 App 配置 RN Gradle 插件及 JS 工程路径；为所有生产变体生成 Bundle，确保 APK/AAB 内存在 `assets/index.android.bundle`。
3. 在 `Application` 中按 RN 0.84 要求初始化并暴露 `ReactHost`，注册业务 `ReactPackage` 与容器桥接包；设置业务的 `ItgRnNativeHandler`。RN 运行时初始化一次，容器页面实例可以创建多个。
4. 业务 JS 注册 `ItgRnRoot` 或多个组件名；业务页面放置 `FrameLayout`，在 Activity/Fragment 的安全生命周期调用 `ItgRnContainer.attach(...)`，退出时卸载对应实例。
5. 在真机上验证 debug/Metro 和 release/离线启动、返回、重建、混淆后的行为。

这意味着“业务只加一行 AAR 依赖”并非现实的完整接入承诺。若未来业务方确实需要**不维护 JS 构建环境**，可另做固定业务 Bundle 的配套发布物（独立 assets AAR 或预构建 App 资源包），但仍须提供 RN 运行时的 Maven 依赖和版本约束。

## 7. 验证矩阵与验收标准

| 场景 | 验收结果 |
| --- | --- |
| 兼容性试验 | 选定 RN 补丁版可在本仓库 AGP/Gradle/Kotlin 组合下 sync、编译、运行；不通过则记录阻断点与可用组合 |
| 本地模块构建 | `:itg-rn-container:assembleRelease` 成功；AAR 内含清单、代码、必要资源和 consumer rules |
| Maven 发布 | `publishReleasePublicationTo...Repository` 成功；独立宿主仅用 Maven 坐标可解析 RN/Hermes 依赖 |
| JS 打包 | release APK/AAB 内有 Bundle；debug 可从 Metro 加载；生产变体离线可打开页面 |
| 页面行为 | 单实例挂载、同屏双实例、反复挂载/卸载、返回键、旋转/重建、进程回收恢复无白屏、重复 Fragment 或 Activity 泄漏 |
| 实例隔离 | 两个容器使用不同 `componentName`/`pageId`/参数，各自卸载不影响另一个；状态与错误回调互不串线 |
| 通信 | `instanceId`/参数传递正确；JS `closeContainer` 仅关闭对应实例并返回结果；异常回调可定位错误 |
| 发布质量 | R8 开启后可运行；ABI、包体增长、冷启动耗时有基线；RN 版本冲突能在构建期发现 |
| Codegen/本地库 | 无自定义 Spec 时确认容器无额外 C++ 交付；有自定义 Spec 时确认 AAR 含对应 ABI 的 `.so`、App autolinking/注册完成、独立宿主可调用 |

至少准备一个不依赖仓库其他 ITG 模块的独立宿主，通过 Maven 坐标消费 AAR；当前根工程通过兼容性验证后，再增加本仓库 `app` 的演示接入。独立宿主是判断 AAR 是否真正可被业务消费的关键。

## 8. 实施顺序

1. **兼容性 Spike：**固定 RN 0.84 的具体补丁版与 Node.js 22.11+，先用 0.84 官方模板工具链创建最小 Android 宿主，再验证当前 AGP 9.2.1/Gradle 9.4.1 组合；核对 Hermes V1、新架构和 release Bundle，并记录可用工具链矩阵。
2. **模块骨架：**新增 `:itg-rn-container` 与发布配置，完成 `ReactFragment` 工厂、实例句柄和 `FrameLayout` 挂载；先跑通一个纯 JS 页面。
3. **业务契约：**加入 `attach/detach`、`instanceId` 路由、`ItgRnNativeHandler`、Bundle 校验与错误态，再完成一条 JS→Native 回调链路和同屏双实例验证。
4. **发布验证：**发布到本地 Maven 仓库，用独立宿主通过坐标消费，验证依赖元数据、R8、ABI、离线 release。
5. **沉淀文档：**依据真实代码补齐接入 README、版本映射、升级步骤与故障排查；再推广给业务方。

## 9. 需要在 Spike 中确认的决策

- **工具链兼容性：**RN 0.84 对当前 AGP 9.2.1/Gradle 9.4.1 没有官方支持；若验证失败，使用仓库内独立的 RN 0.84 构建入口发布 AAR，并单独确认业务宿主可消费。
- **业务原生包范围：**是否允许业务任意引入 RN 原生三方库；若允许，宿主必须保留 autolink/Codegen 接入，并向容器提供完整 `ReactPackage` 列表。
- **隔离级别：**首版以多个独立 `ReactFragment` 实例为目标；若业务要求每实例独立 JS 引擎/Bundle，应单独验证多 `ReactHost` 与自定义 `ReactSurface` 方案，不能以 `ReactFragment.Builder` 直接替代。
- **Codegen 归属：**先确认容器基础桥接可否使用 Kotlin/Java 互操作层；只有确需 TurboModule/Fabric 时再引入容器自己的 Spec、预生成代码和 native AAR 构建。
- **包体预算：**RN/Hermes 引入后的 APK/AAB 增量、ABI 策略与冷启动指标，依据测量决定是否做预热。

## 参考资料

- [React Native 0.84：集成到现有应用](https://reactnative.dev/docs/0.84/integration-with-existing-apps)
- [React Native 0.84：集成到 Android Fragment](https://reactnative.dev/docs/0.84/integration-with-android-fragment)
- [React Native 0.84 Gradle 插件](https://reactnative.dev/docs/0.84/react-native-gradle-plugin)
- [React Native 0.84 Codegen](https://reactnative.dev/docs/0.84/the-new-architecture/using-codegen)
- [React Native 0.84：库内预生成代码](https://reactnative.dev/docs/0.84/the-new-architecture/codegen-cli)
- [React Native 0.84：原生与 RN 通信](https://reactnative.dev/docs/0.84/communication-android)
- [React Native 0.84 发布说明](https://reactnative.dev/blog/2026/02/11/react-native-0.84)
- [React Native 0.87：首次支持 AGP 9](https://reactnative.dev/blog/2026/08/11/react-native-0.87)
- [React Native 版本支持状态](https://reactnative.dev/releases/overview)
- [Android：发布 AAR 库](https://developer.android.com/build/publish-library/upload-library)
