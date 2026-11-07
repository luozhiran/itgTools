# ITG RN 容器设计方案

> 状态：首版代码已落地，后续能力仍按本文规划。本文所说的“model”按 Android 工程惯例落为 **module**。

## 当前实现与构建

根目录统一安装 JS 依赖：`package.json`、`package-lock.json`、`metro.config.js`、`babel.config.js` 放在根目录，执行 `npm ci` 后得到根目录 `node_modules`。`itg-rn-bussiness/package.json` 是 npm workspace 包，只声明业务的 `codegenConfig` 与 RN peer 版本；业务 JS 入口在 `itg-rn-bussiness/js/index.js`。以后增加 JS 三方库，统一修改根目录 `package.json` 并更新锁文件；含 Android 原生代码的三方库仍需验证宿主的 autolinking/Codegen。

Gradle 默认从根目录 `node_modules/react-native/ReactAndroid/gradle.properties` 读取 `react-android` 版本和 Maven group，从 `node_modules/react-native/sdks/hermes-engine/version.properties` 读取 Hermes V1 版本；未安装依赖时使用根目录 `package.json` 的固定版本作为同步回退，并校验安装版本一致。`hermes-android` AAR 仍通过 Maven 坐标下载，`node_modules` 提供版本元数据和 `hermesc` 编译器。迁移验证时可用 `-PrnNodeModulesDir=<绝对路径>` 指定已有安装。

```bash
npm ci
npm run codegen:android
bash gradlew :itg-rn-container:assembleRelease :itg-rn-bussiness:assembleRelease
bash gradlew :itg-rn-container:publishReleasePublicationToMavenRepository :itg-rn-bussiness:publishReleasePublicationToMavenRepository
cd rn-host-sample/android && bash gradlew :app:assembleRelease
```

当前已实现 `ItgRnContainerFragment`、复用它的 `ItgRnContainerActivity`、`ItgRnContainer.attach/launch`、`ItgRnInstance.detach`、业务 `openOrderDetail/attachOrderDetail/openNativeDemo`、JS→Native `closeContainer`、业务 Hermes bytecode 打包和两层 Maven 发布。业务模块还包含一个 Codegen Fabric View 与一个 TurboModule，生成的 Java/C++ 放在 `itg-rn-bussiness/codegen/`，release AAR 内有四个 ABI 的 `libitg_rn_business_codegen.so`。独立 `rn-host-sample` 使用业务 Maven 坐标、RN 0.84 官方宿主工具链；在 arm64 模拟器上已验证 Fabric 文本属性、点击事件及 TurboModule `multiply(6, 7) = 42`。同屏多实例生命周期、错误态、Bundle 清单校验、第三方原生库接入及生产混淆仍需验证或实现。

宿主 `Application` 以 RN 0.84 官方模板为基础，在 `PackageList(this).packages` 中加入 `ItgRnBridgePackage()` 和 `ItgRnBusinessPackage()`；调用 `loadReactNative(this)` 后、第一次创建 `ReactHost` 前调用 `ItgRnBusinessNative.install()`。业务页面调用 `ItgRnBusiness.openOrderDetail(activity, orderId)` 或 `ItgRnBusiness.openNativeDemo(activity)`；嵌入场景在自有 `FrameLayout` 上调用对应 `attach...`。通过 `ItgRnInstance.resultKey` 监听 Fragment result，结果 JSON 位于 `ItgRnContainer.EXTRA_RESULT_JSON`；退出时调用 `detach()`。宿主自有 Activity 还需把硬件返回键转发给当前容器 Fragment；内置 Activity 已处理该转发。

```kotlin
// 在 RN 0.84 模板的 Application 中增补：
override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
        context = applicationContext,
        packageList = PackageList(this).packages.apply {
            add(ItgRnBridgePackage())
            add(ItgRnBusinessPackage())
        },
    )
}

override fun onCreate() {
    super.onCreate()
    loadReactNative(this)
    ItgRnBusinessNative.install()
}
```

### Fabric 与 TurboModule 示例如何扩展

1. 在 `itg-rn-bussiness/js/specs/` 增加或修改 `*NativeComponent.ts`、`Native*.ts`。业务 workspace 的 `codegenConfig` 指向此目录，`type = all` 同时生成 Fabric 和 TurboModule。组件名、模块名要与 Android 的 ViewManager、NativeModule 一致。
2. 在根目录执行 `npm run codegen:android`。生成结果放在 `itg-rn-bussiness/codegen/java` 与 `codegen/jni`，并随业务源码提交。`includesGeneratedCode = true` 让消费 AAR 的宿主无需为该业务 Spec 再生成一次代码。[RN 0.84 Codegen CLI](https://reactnative.dev/docs/0.84/the-new-architecture/codegen-cli)
3. 参照 `ItgDemoNativeView`、`ItgDemoNativeViewManager`、`ItgDemoTurboModule` 实现业务行为，在 `ItgRnBusinessPackage` 注册 ViewManager 与 TurboModule。若更改 Codegen 库名，需要同步修改业务 CMake 中的生成目标名与 C++ 安装入口。
4. 发布业务 AAR，再用 `rn-host-sample` 的宿主入口验证。它展示了手动注册 `ItgRnBridgePackage`、`ItgRnBusinessPackage` 与预编译 Codegen `.so`。模拟器验证过 View 属性、点击事件和 `multiply(6, 7)` 的 Promise 结果。[RN 0.84 Fabric](https://reactnative.dev/docs/0.84/fabric-native-components-introduction)、[RN 0.84 TurboModule](https://reactnative.dev/docs/0.84/turbo-native-modules-android)

`rn-host-sample/android/build/generated/autolinking` 与 `rn-host-sample/android/app/build/generated/autolinking` 来自**宿主**的 RN Gradle 插件。当前独立宿主通过 Maven AAR 手动注册业务包，因此 `autolinking.json` 的业务依赖集合为空；AAR 不会自动出现在 npm 自动链接列表。若今后要求业务库自动链接，需要另交付并验证 npm 接入包，而不是仅依靠 AAR。业务 `.so` 的安装代码使用 RN 0.84 内部 C++ 注册钩子，必须锁定 RN 版本；升级 RN 时要重新生成 Codegen、重编四个 ABI 并运行独立宿主验证。

## 1. 目标与边界

容器负责把 React Native（下称 RN）视图嵌入 Android 页面，管理每个嵌入实例的组件、参数、渲染生命周期与通信。`itg-rn-container` 同时提供内置 Activity 和 Fragment 接入 API：业务可直接启动内置 Activity，也可在自己的 Activity/Fragment 中放置 `FrameLayout` 并挂载 RN。`itg-rn-bussiness` 依赖容器，封装业务页面、JS Bundle 和业务原生能力，作为面向业务宿主的交付入口。

首版范围：

- 对外提供 `ItgRnContainerFragment`，其内部承载 RN 0.84 的 `ReactFragment`；容器内置 `ItgRnContainerActivity` 使用同一个容器 Fragment。每个容器实例独立持有组件名、页面参数及销毁状态，可同时嵌入多个占位区域。
- JS Bundle 默认在 `itg-rn-bussiness` 构建阶段生成，并作为业务 AAR 的 asset 随宿主 APK/AAB 合并；业务自己的 Codegen/本地库也由业务模块构建。底层容器 AAR 保持与具体业务无关。
- 支持在占位区域挂载/卸载、传入初始参数、页面结果、基础错误回调和必要的 JS↔Native 能力。
- 发布 `release` AAR 与 Maven 元数据，并提供独立业务接入验证。

暂不纳入首版：远程热更新、运行时下载 Bundle、多 RN 版本共存、每个容器独立 JS 引擎。后续确有场景时再扩展。

**交付目标分两级：**业务方可以只声明一个 Maven 坐标 `com.itg:itg-rn-bussiness:<version>`，由其 POM 传递引入容器及 RN/Hermes 依赖；业务 AAR 可以内置预构建 JS Bundle 和业务 `.so`。如果“只提供 AAR”指**离线拷贝一个裸 `.aar` 文件、没有 POM/仓库/其他依赖，也不改宿主构建配置**，则不适合 RN 0.84 的标准集成链路：裸 AAR 不声明传递依赖，也不会自动完成宿主的 RN 初始化和 App 级 Codegen/autolinking。[Android 库发布说明](https://developer.android.com/build/publish-library/upload-library)、[RN 0.84 Codegen 说明](https://reactnative.dev/docs/0.84/the-new-architecture/using-codegen)

## 2. 与当前工程的关系

仓库目前使用 `settings.gradle.kts` 管理多模块，`gradle/libs.versions.toml` 管理其他 Android 版本，RN/Hermes 版本由根目录 JS 工作区决定。现有 Android 配置为 AGP `9.2.1`、Gradle `9.4.1`、`compileSdk 36.1`、`minSdk 24`、Java 11；`app` 尚未完成 RN 宿主接入。两个 RN 模块已在根工程构建并发布 `release` AAR。

**版本约束：当前使用 RN 0.84.1、React 19.2.3 和 Hermes V1 250829098.0.9。** `react-android` 跟随 RN 版本；Hermes V1 使用 `com.facebook.hermes:hermes-android` 及 RN 包声明的独立版本号。RN 0.84 要求 Node.js **22.11+**，且只运行新架构。[RN 0.84 发布说明](https://reactnative.dev/blog/2026/02/11/react-native-0.84)

**当前工程存在构建工具链冲突风险。**RN 官方在 0.87 才首次加入 AGP 9 支持，而本仓库已使用 AGP 9.2.1 和 Gradle 9.4.1，因此不能承诺 RN 0.84 直接加入当前根工程后可以构建。[RN 0.87 的 AGP 9 支持说明](https://reactnative.dev/blog/2026/08/11/react-native-0.87) 首先用 RN 0.84 的官方模板工具链完成独立宿主验证，再试验现有根工程；若现有组合不通，推荐把 `itg-rn-container` 源码保留在仓库内，用独立 RN 0.84 构建入口和兼容的 AGP/Gradle 版本发布 AAR，而不是为了容器直接改动所有现有模块的构建版本。最终业务 App 的工具链也必须单独验证。

截至本文修订时，RN 官方已将 0.84 标为不受支持系列；选择 0.84 后，应由团队自行维护补丁评估、依赖漏洞跟踪和升级预案。[RN 版本支持状态](https://reactnative.dev/releases/overview)

## 3. 推荐架构

```text
业务 Android App
├─ Application：按 RN 0.84 集成要求提供 ReactHost 与注册入口
├─ 入口 A：直接启动容器内置 ItgRnContainerActivity
├─ 入口 B：在自有 Activity/Fragment 的 FrameLayout 挂载 RN Fragment
└─ implementation("com.itg:itg-rn-bussiness:<version>")
                        │
                        ▼
             :itg-rn-bussiness（面向业务交付的 AAR）
             ├─ 业务组件/pageId/页面 API
             ├─ 预构建 JS Bundle + 资源 assets
             ├─ 业务 ReactPackage / 可选 Codegen + .so
             └─ Maven 元数据依赖 itg-rn-container
                        │
                        ▼
             :itg-rn-container（底层 AAR）
             ├─ ItgRnContainerActivity：开箱即用的全屏宿主
             ├─ ItgRnContainerFragment：公共容器 Fragment，内部持有 ReactFragment
             ├─ ItgRnContainer.attach(...)：挂载容器 Fragment
             ├─ ItgRnInstance：持有实例句柄与页面状态
             ├─ 通用桥接、错误处理与 consumer rules
             └─ Maven 元数据依赖 react-android / hermes-android
```

RN 0.84 官方的 Fragment 集成方式是：宿主放置 `FrameLayout`，用 `ReactFragment.Builder().setComponentName(...).setLaunchOptions(...).build()` 创建 Fragment，再通过 `FragmentManager` 挂载。官方也明确要求先完成现有 App 的 RN 基础接入。[RN 0.84 Fragment 集成](https://reactnative.dev/docs/0.84/integration-with-android-fragment) 容器封装 Fragment 创建与事务；业务模块可以提前构建自己的 JS 与 C++，但仍无法替宿主自动完成 App 级依赖发现和初始化。

### 3.1 实例隔离与运行时边界

- `ItgRnInstance` 是每次 `attach` 创建的对象，不使用全局页面单例；它拥有 `instanceId`、`ItgRnContainerFragment`、组件名、初始参数、状态与回调。一个实例只控制自己的 `containerViewId`，卸载时仅移除自己的容器 Fragment，Fragment tag 使用 `instanceId` 避免互相覆盖。实例索引按宿主 `FragmentManager` 作用域管理，并随宿主销毁清理。
- `ReactFragment` 提供的是**视图/页面实例隔离**。RN 0.84 的官方 Fragment 路径仍基于宿主已有的 RN 基础接入；`ReactApplication` 的 `reactHost` 是 Application 提供的入口。由此不能推断 `ReactFragment.Builder` 会为每个 Fragment 创建独立 JS 引擎或从不同 Host 选路。[RN 0.84 Fragment 集成](https://reactnative.dev/docs/0.84/integration-with-android-fragment)、[RN 0.84 ReactApplication 接口](https://github.com/react/react-native/blob/v0.84.0/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/ReactApplication.kt)
- 默认实现让多个 `ItgRnInstance` 各自渲染自己的组件和参数，同时复用宿主提供的 `ReactHost`。容器不把页面路由表和页面状态塞进 Application；宿主只承担 RN 初始化、原生包注册和运行时入口。
- 如果“独立”明确要求**每个实例有自己的 JS VM、Bundle 或 Native Module 注册表**，应另做多 `ReactHost` 方案验证：由容器管理 Host 与 `ReactSurface` 的绑定、Activity 生命周期分发和资源释放。`ReactHost.createSurface(...)` 提供创建渲染 Surface 的接口，但这条路径不等同于直接使用官方 `ReactFragment.Builder`，不能在首版承诺多运行时路由。[RN 0.84 ReactHost 接口](https://github.com/react/react-native/blob/v0.84.0/packages/react-native/ReactAndroid/src/main/java/com/facebook/react/ReactHost.kt)
- 不继承或强制绑定当前项目的 `itg-base`、TheRouter、KSP 等模块，保持容器可独立用于其他业务工程。业务需要路由时在 App 层做适配。

### 3.2 两种页面入口，共用同一 Fragment 渲染链路

#### 入口 A：使用容器内置 Activity

`itg-rn-container` 提供 `ItgRnContainerActivity`。它只包含一个 `FrameLayout`，在 `onCreate` 中通过容器自身的挂载逻辑放入 `ItgRnContainerFragment`；不再实现第二套 RN 渲染逻辑。容器 Fragment 使用 `childFragmentManager` 在自己的视图内挂载 RN 官方 `ReactFragment`，并管理加载与错误态。Activity 负责窗口/insets、返回键、配置变化恢复和关闭结果。业务模块可以把固定的 `componentName`/`pageId` 封装成类型化入口：

```kotlin
// 宿主只传业务参数，无需编写 Activity 或 Fragment 布局。
ItgRnBusiness.openOrderDetail(this, orderId)
```

底层通用入口设计为 `ItgRnContainer.launch(activity, componentName, pageId, params)` 或返回一个显式 `Intent` 供业务的 `ActivityResultLauncher` 使用。`ItgRnContainerActivity` 在容器 AAR 的 Manifest 中声明为 `exported=false`，使用无 ActionBar 的容器主题；传入参数由工厂校验并放入 Intent。Activity 创建时先查询 `FragmentManager` 中已恢复的容器 Fragment，再决定是否挂载，避免旋转或进程恢复后重复添加。关闭页面时通过 Activity result 返回业务结果。

#### 入口 B：在自有页面挂载 Fragment

业务 Activity 或 Fragment 在布局中提供占位区域，由容器创建 `ItgRnContainerFragment` 并挂载到对应的 `FragmentManager`：

```xml
<FrameLayout
    android:id="@+id/rn_slot"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
// 在 Activity.onCreate 中，setContentView 之后调用；Fragment 宿主则在 onViewCreated 调用。
val rnInstance = ItgRnBusiness.attachOrderDetail(
    fragmentManager = supportFragmentManager,
    containerViewId = R.id.rn_slot,
    orderId = orderId
)
```

业务 API 在内部调用 `ItgRnContainer.attach(componentName, pageId, params, ...)`，返回本次容器实例的句柄，可用于 `detach()` 和订阅状态。容器 Fragment 内部使用 `ReactFragment.Builder().setComponentName(componentName).setLaunchOptions(bundle)`，经 `childFragmentManager` 提交事务；宿主也可通过 `ItgRnContainerFragment.newInstance(...)` 自行添加容器 Fragment。`pageId` 是业务路由标识，`params` 只允许可序列化的 JSON 基本类型、列表和对象；不传 `Activity`、回调、`Parcelable` 或敏感令牌。初始属性包含 `instanceId`、`pageId`、`params`；`componentName` 必须与 JS 的 `AppRegistry.registerComponent` 名称一致。首版参数变化通过重新挂载实例实现，避免把 `setLaunchOptions` 误当作运行时更新 API。[RN 0.84 Fragment 集成](https://reactnative.dev/docs/0.84/integration-with-android-fragment)

实例句柄在配置变化或进程恢复后不能直接复用旧对象；容器需按 Fragment tag 查找已恢复的外层与内层 Fragment，避免重复 `add`。Activity 使用 `supportFragmentManager`，业务 Fragment 使用 `childFragmentManager`；调用方需在 `isStateSaved` 之前挂载，或由容器排队到安全生命周期时机。`detach()` 只移除对应实例并清理回调。外层容器 Fragment 嵌套 RN 官方 Fragment 的方案须在 RN 0.84 真机验证，尤其覆盖销毁/重建、同屏双实例和返回键。

自有页面的宿主 Activity 还需按 RN 0.84 Fragment 指南处理硬件返回键；针对当前项目 `targetSdk 36`，使用 `OnBackPressedDispatcher`，避免依赖已弃用的 `onBackPressed()`。[RN 0.84 Fragment 集成](https://reactnative.dev/docs/0.84/integration-with-android-fragment) 同屏多实例时由宿主指定当前获得焦点的容器；RN 不消费返回键时将事件交还原生导航，并防止重新进入同一个返回键回调。失败时在该占位区域显示原生错误态并通知业务回调；预热运行时作为性能优化选项，首版以实测结果决定。

高级使用方也可直接用 RN 官方 `ReactFragment.Builder` 自行管理 Fragment 事务；这种方式由使用方负责参数约定、返回键与生命周期，容器的实例状态、错误页和 `instanceId` 结果路由只对通过容器 API 创建的实例生效。内置 Activity 与自有页面挂载均复用宿主 `ReactHost`，不会为内置 Activity 再启动独立 JS 运行时。

### 3.3 JS 包与原生包

- **业务 JS 所有权在 `itg-rn-bussiness`。**依赖清单、锁文件与 Metro/Babel 配置统一放在仓库根目录，业务入口放在 `itg-rn-bussiness/js/index.js`；业务模块的 Gradle 任务构建 release Bundle，使用根目录 `node_modules` 中的 `hermesc` 编译为 bytecode，并把产物放入业务 AAR 的 `assets/index.android.bundle`。Android library 的 AAR 能包含 assets，但 RN Gradle 插件默认以 App 构建为中心，业务模块需要显式创建任务。[RN 0.84 Gradle 插件](https://reactnative.dev/docs/0.84/react-native-gradle-plugin)、[Android AAR 结构](https://developer.android.com/studio/projects/android-library)
- 一个共享 `ReactHost` 默认加载一份 JS Bundle。首版把该业务模块需要的所有 RN 组件注册进这一份 Bundle；如果多个独立 `itg-rn-bussiness-*` AAR 各自放一个 `index.android.bundle`，资源名会冲突，Host 也不会自动把多个 Bundle 拼接。多业务模块应在发布时聚合为一个 Bundle，或另行验证多 Host/多 Bundle 方案。
- 容器只预置通用 Native Module，业务 Native Module 放在 `itg-rn-bussiness`。若业务 JS 使用第三方含 Android 原生代码的 RN 包，业务模块构建时处理其 Codegen；最终宿主仍需按交付模式注册所需 `ReactPackage` 和 App 级入口。AAR 本身不会自动获得业务宿主的 `PackageList`。
- RN 0.84 仅运行新架构，Hermes V1 为默认引擎。容器和业务原生三方包都要核对新架构兼容性，且构建产物应验证实际使用的 Hermes 版本。[RN 0.84 发布说明](https://reactnative.dev/blog/2026/02/11/react-native-0.84)

### 3.4 JS ↔ Native 协议

首版桥接只暴露业务必需的少量能力，例如 `closeContainer(instanceId, result)`、`getAppInfo()`、`openNativeRoute(route, params)`。由容器定义白名单和错误码，宿主通过 `ItgRnNativeHandler` 处理业务路由和鉴权相关操作。JS 到原生请求统一携带 `instanceId` 与 `requestId`，由容器把结果路由回对应的活跃 Fragment 实例；已卸载实例直接返回明确错误，不能误投给同屏其他容器。成功与失败都返回结构化结果；所有 UI 操作切主线程，回调不强持有已销毁 Activity。

首版优先评估用容器 AAR 内的 Kotlin/Java `ReactPackage` 实现这几个基础能力，并在 RN 0.84 新架构下验证互操作层能否满足需求；这样容器本身不需要定义 Codegen Spec，也不产生容器专属 C++。如验证不满足或后续需要同步方法、Fabric 组件，再按 RN 0.84 的 Codegen/TurboModule 路径实施。对外只暴露稳定的容器 API，不让业务依赖内部生成类；不要提供可执行任意类名/方法名的通用反射桥。首次落地先实现 `closeContainer` 一条端到端链路。

### 3.5 Bundle 版本与运行校验

推荐在业务 AAR 的 Bundle 旁生成 `rn-bundle.json`，至少包含 `bundleVersion`、`rnVersion`、`componentNames`、`pageIds` 和构建时 SHA-256。容器挂载前校验资产存在、组件名已注册且 RN 主版本/次版本一致；离线打包产物在 CI 比对清单哈希。首版 Bundle 由业务模块预构建、随 AAR 合并进 APK/AAB，不做下载、解压和动态执行，因此更新走正常应用发布流程。

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
   │     ├─ ItgRnContainerActivity.kt
   │     ├─ ItgRnContainerFragment.kt
   │     ├─ fragment/ItgRnFragmentFactory.kt
   │     ├─ internal/ScopedInstanceRegistry.kt
   │     ├─ bridge/ItgRnBridgePackage.kt
   │     └─ internal/BundleVerifier.kt
   │  └─ res/layout/activity_itg_rn_container.xml
   ├─ test/
   └─ androidTest/
app/                         # 本仓库原有示例宿主，尚未接入 RN
package.json / package-lock.json / metro.config.js / babel.config.js
node_modules/                 # 根目录 npm ci 安装，不提交
itg-rn-bussiness/
├─ build.gradle.kts           # 依赖 itg-rn-container；发布业务 AAR
├─ package.json               # npm workspace；业务 Codegen 配置
├─ codegen/java + codegen/jni # 预生成并提交的 Java/C++
├─ js/index.js                # 业务 JS 入口与页面注册
├─ js/specs/                  # Fabric、TurboModule TypeScript Spec
├─ src/main/java/...          # 业务类型化入口
├─ src/main/jni/...           # 预编译 Codegen 安装入口
├─ src/main/assets/...        # 构建时生成 Hermes Bundle，不提交产物
└─ build/generated/...        # Metro 中间产物，不提交
rn-host-sample/android/       # 独立 RN 0.84 宿主；通过 Maven 坐标消费 AAR
```

若当前根工程的 AGP 9 组合验证通过，可用仓库 `app` 通过业务 Maven 坐标做接入验证。若验证不通过，`app` 保持现状，改用独立 RN 0.84 宿主验证；不能只用模块间 `project(...)` 依赖证明发布链路可用。仓库其他模块目前多发布到各自 `build/repo`，两层 AAR 可沿用这一方式，同时在 CI 保存完整仓库目录或推送内部 Maven 仓库。

## 5. 构建与发布设计

1. 先验证构建工具链。当前根工程已经 include 两个空骨架；只有 RN 0.84 与当前组合验证通过，才继续从根工程配置它们的 RN 构建。否则在仓库内设置独立 RN 0.84 `settings.gradle.kts`/Wrapper，引用两模块源码目录并从该构建入口发布。两种路径都集中声明固定的 `rn` 版本，不在各处分别写版本字符串。
2. 容器与业务模块均使用 `com.android.library` 与 `maven-publish`；分别设置 namespace、`minSdk = 24`、consumer rules。业务模块通过 `api(project(":itg-rn-container"))` 或按公开 API 选择 `implementation`，发布后在业务 POM 中保留容器依赖。
3. `react-android` 与 `hermes-android` 以正常 Maven 依赖发布；如公开 API 暴露 RN 类型，用 `api`，否则优先 `implementation`。不可依靠一个手工合并的“fat AAR”隐藏 RN 的原生库、资源与依赖冲突。
4. 两模块均发布 `release` 变体；坐标分别为 `com.itg:itg-rn-container:<version>` 与 `com.itg:itg-rn-bussiness:<version>`。检查业务 AAR 的 JS assets、可选 `.so`，以及 POM/Gradle module metadata 的传递依赖。
5. 业务模块显式执行 JS Bundle/Hermes 编译并将结果纳入自己的 AAR；使用方仍按 RN 0.84 集成要求配置宿主入口及必要的 Gradle 插件/Codegen。RN Gradle 插件默认负责 App 打包，不能假设对 Android library 自动打业务 Bundle。[RN 0.84 Gradle 插件职责](https://reactnative.dev/docs/0.84/react-native-gradle-plugin)

### 5.1 Codegen/C++ 产物如何交付

先区分源码归属。`app/build/generated/source/codegen/jni` 是宿主 App 自己的 Spec 生成物；依赖包的 Spec 会在对应依赖项目的 `android/build/generated/source/codegen/jni` 下生成；App 的 autolinking/入口代码还依赖本次 App 实际安装的 RN 包集合。RN 0.84 的 Codegen 文档明确把这些生成物分别放在 App 和依赖项目下。[RN 0.84 Codegen 说明](https://reactnative.dev/docs/0.84/the-new-architecture/using-codegen)

| 产物 | 交付方式 | 业务侧责任 |
| --- | --- | --- |
| RN 核心与 Hermes 的 C++ 运行库 | `react-android`、`hermes-android` 的 Maven 依赖 | 对齐 RN 0.84 版本与 ABI，不复制源码 |
| 容器自己的 TurboModule/Fabric Spec 生成的 Java/C++ | 在容器发布构建中生成并编译；Java 字节码和编译后的 `.so` 随容器 AAR 发布，原始 C++ 源码不作为业务接入产物 | 按容器提供的注册方式接入；若依赖 autolinking，提供配套 npm 元数据 |
| `itg-rn-bussiness` 自己的 Spec 与业务 JS | 在业务模块发布构建中生成/编译；Java 字节码、`.so`、JS Bundle 和 assets 进入业务 AAR | 使用业务 Maven 坐标；按交付契约完成宿主初始化与注册 |
| App 自己的 Spec、autolinking 与最终入口胶水代码 | 由业务 App 的 RN Gradle 插件按其 JS 依赖图生成 | 保留 RN 构建插件、Node/Codegen 与本地构建步骤；不能从容器 AAR 预先提供通用版本 |

**当前落地路径：**容器只承载 `ReactFragment` 和少量 Kotlin/Java 桥接，不新增容器专属 Spec；`itg-rn-bussiness` 构建整份业务 JS Bundle，并包含 Fabric 与 TurboModule 示例的预生成 Java/C++ 和编译后的 `.so`。宿主仍按 RN 0.84 模板完成 App 级初始化与 autolinking，手动注册当前业务 AAR 的原生包和 Codegen 安装入口。arm64 独立宿主已验证上述示例运行。RN 0.82 起新架构为唯一运行时，官方仍保留互操作层。[RN 新架构与互操作层说明](https://reactnative.dev/blog/2025/10/08/react-native-0.82)

**业务中使用 TurboModule/Fabric：**根目录 `package.json` 的 npm workspace 安装依赖；`itg-rn-bussiness/package.json` 的 `codegenConfig` 指向本模块 Spec，固定 RN 0.84 补丁版。`includesGeneratedCode: true` 使生成 Java/C++ 成为业务模块构建输入，CMake/NDK 将其编成各目标 ABI 的 `.so`，随业务 AAR 的 `jni/<abi>/` 交付。AAR 可以包含 `.so`；对需要提供 C++ 头文件给下游编译的场景，再考虑 Prefab。发布前检查 AAR 内容和独立宿主加载。[RN 0.84 预生成代码指南](https://reactnative.dev/docs/0.84/the-new-architecture/codegen-cli)、[Android AAR 结构](https://developer.android.com/studio/projects/android-library)

`includesGeneratedCode` 只解决**所属库自身**生成代码的交付，不会自动替最终 App 生成 PackageList、CMake autolinking 或注册所有原生包。若只发 Maven AAR、没有配套 npm 包，RN CLI 无法通过 npm 依赖自动发现容器/业务模块；需要在宿主 `Application` 显式注册相应 `ReactPackage`，并验证新架构所需的 native 注册。若希望业务侧自动链接，另提供薄 npm 包的 `package.json`/`react-native.config.js`，映射到已发布的 Android AAR，并在独立宿主验证不会重复编译或重复打包 `.so`。[RN 0.84 预生成代码指南](https://reactnative.dev/docs/0.84/the-new-architecture/codegen-cli)

CI 增加三项检查：对应模块 AAR 中目标 ABI 的 `jni/<abi>/*.so`（仅在其有自定义 C++ 时）、业务 AAR 的 Bundle/资源及最终 APK/AAB 中的 native 库与 Bundle、独立宿主的 JS→Native 调用。不能仅凭 `generateCodegenArtifactsFromSchema` 成功就判定业务 AAR 可用。

当前容器依赖写法：

```kotlin
// itg-rn-container/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "com.itg.rncontainer"
    // compileSdk/minSdk/Java 配置沿用本仓库库模块约定
    defaultConfig { consumerProguardFiles("consumer-rules.keep") }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    api(rootProject.extra["rnReactCoordinate"] as String)
    implementation(rootProject.extra["rnHermesCoordinate"] as String)
}

// 两个坐标由根 build.gradle.kts 从根 node_modules 中的 RN 元数据生成。
```

发布 AAR 时，CI 应检查业务 POM 包含容器依赖、容器 POM 包含 RN/Hermes 依赖；业务工程使用 `dependencyInsight` 检查是否出现重复或被强制升级的 RN/Hermes 版本。两个 ITG 模块与 RN 版本建立映射表，例如 `0.1.x → RN 0.84.<已验证补丁>`，升级 RN 时重新跑完整接入矩阵。

## 6. 业务接入契约

业务方接入步骤应压缩到以下可核验流程：

1. 配置内部 Maven 仓库，只声明 `implementation("com.itg:itg-rn-bussiness:<version>")`；由元数据传递引入容器及 RN/Hermes，核对版本一致。
2. 按 RN 0.84 宿主接入要求配置必要的 RN Gradle 插件/Node 依赖和 `Application` 的 `ReactHost`；使用业务模块预构建 Bundle 时，避免宿主再次输出同名 Bundle。若业务 AAR 含自定义 TurboModule/Fabric，验证宿主 CMake/autolinking/注册链路。
3. 在 `Application` 的 package list 中注册 `ItgRnBridgePackage` 和 `ItgRnBusinessPackage`；调用 RN 0.84 的 `loadReactNative(this)` 后执行 `ItgRnBusinessNative.install()`，再创建 `ReactHost`。示例代码见 `rn-host-sample/android/app`。
4. 全屏业务页调用 `ItgRnBusiness.openXxx(...)` 直接进入容器内置 Activity；需要嵌入现有页面时放置 `FrameLayout`，在安全生命周期调用 `ItgRnBusiness.attachXxx(...)`，退出时卸载对应实例。宿主不维护业务 JS 源码。
5. 在真机上分别验证内置 Activity、自有页面嵌入与直接使用官方 `ReactFragment` 的路径，包括 debug/release 离线启动、返回、重建、混淆；检查 APK/AAB 中只有一份目标 Bundle。

这实现的是**一个 Maven 依赖坐标 + 少量宿主 RN 初始化代码**。若使用方限定只能收到一个裸 `.aar` 文件且不能访问 Maven 仓库、不能配置 RN 构建或初始化宿主，不能直接承诺此方案；需另做固定依赖集合的专用集成包并单独验证，维护成本和升级风险会显著增加。

## 7. 验证矩阵与验收标准

| 场景 | 验收结果 |
| --- | --- |
| 兼容性试验 | 选定 RN 补丁版可在本仓库 AGP/Gradle/Kotlin 组合下 sync、编译、运行；不通过则记录阻断点与可用组合 |
| 本地模块构建 | 两个模块均能 `assembleRelease`；业务 AAR 内含业务 Bundle、资源、必要时各 ABI 的 `.so` |
| Maven 发布 | 两个模块均能发布；独立宿主只声明业务 Maven 坐标即可解析容器和 RN/Hermes 依赖 |
| JS 打包 | 业务 AAR 与 release APK/AAB 均含预构建 Bundle；无业务 JS 源码的宿主可离线打开页面 |
| 内置 Activity | `openXxx` 可启动；Manifest 合并正确；旋转/进程恢复不重复挂载；返回与 Activity result 正确 |
| 自有页面 | 自有 Activity/Fragment 的占位区域可挂载和卸载，原生内容与 RN 内容可共存 |
| 直接 ReactFragment | 高级使用方可按 RN 官方方式自行挂载，并明确自行处理生命周期/返回键 |
| 页面行为 | 单实例挂载、同屏双实例、反复挂载/卸载、返回键、旋转/重建、进程回收恢复无白屏、重复 Fragment 或 Activity 泄漏 |
| 实例隔离 | 两个容器使用不同 `componentName`/`pageId`/参数，各自卸载不影响另一个；状态与错误回调互不串线 |
| 通信 | `instanceId`/参数传递正确；JS `closeContainer` 仅关闭对应实例并返回结果；异常回调可定位错误 |
| 发布质量 | R8 开启后可运行；ABI、包体增长、冷启动耗时有基线；RN 版本冲突能在构建期发现 |
| Codegen/本地库 | 业务 AAR 含四个 ABI 的 Codegen `.so`；宿主完成手动注册和 App 级 autolinking，独立宿主可调用 Fabric/TurboModule |

至少准备一个不依赖仓库其他 ITG 模块的独立宿主，通过 Maven 坐标消费 AAR；当前根工程通过兼容性验证后，再增加本仓库 `app` 的演示接入。独立宿主是判断 AAR 是否真正可被业务消费的关键。

## 8. 实施顺序

1. **兼容性 Spike：**固定 RN 0.84 的具体补丁版与 Node.js 22.11+，先用 0.84 官方模板工具链创建最小 Android 宿主，再验证当前 AGP 9.2.1/Gradle 9.4.1 组合；核对 Hermes V1、新架构和 release Bundle，并记录可用工具链矩阵。
2. **容器实现：**在已有 `:itg-rn-container` 骨架上补发布配置、`ItgRnContainerFragment`、RN Fragment 工厂与实例句柄；再增加复用容器 Fragment 的 `ItgRnContainerActivity`、Manifest 与 Activity result。
3. **业务模块实现：**在已有 `:itg-rn-bussiness` 骨架上增加容器依赖；构建并内置业务 Bundle，提供类型化 `openXxx` 与 `attachXxx` API；Fabric/TurboModule 示例的 Spec、Codegen、CMake 和 `.so` 打包已在本模块完成。
4. **业务契约：**加入 `attach/detach`、`instanceId` 路由、`ItgRnNativeHandler`、Bundle 校验与错误态，完成 JS→Native 回调和同屏双实例验证。
5. **发布验证：**两模块发布到本地 Maven 仓库，用独立宿主只声明业务坐标消费，验证 POM、R8、ABI、预置 Bundle、离线 release。
6. **沉淀文档：**依据真实代码补齐接入 README、版本映射、升级步骤与故障排查；再推广给业务方。

## 9. 需要在 Spike 中确认的决策

- **工具链兼容性：**RN 0.84 对当前 AGP 9.2.1/Gradle 9.4.1 没有官方支持；若验证失败，使用仓库内独立的 RN 0.84 构建入口发布 AAR，并单独确认业务宿主可消费。
- **业务原生包范围：**是否允许业务任意引入 RN 原生三方库；若允许，宿主必须保留 autolink/Codegen 接入，并向容器提供完整 `ReactPackage` 列表。
- **隔离级别：**首版以多个独立 `ReactFragment` 实例为目标；若业务要求每实例独立 JS 引擎/Bundle，应单独验证多 `ReactHost` 与自定义 `ReactSurface` 方案，不能以 `ReactFragment.Builder` 直接替代。
- **Codegen 归属：**先确认容器基础桥接可否使用 Kotlin/Java 互操作层；业务 Spec 放在业务模块并由业务模块预编译。App 级入口和 autolinking 仍由使用方的宿主构建完成。
- **Bundle 数量：**首版一个宿主 `ReactHost` 加载一份由业务模块预构建的 Bundle；多个业务 AAR 各带一份 Bundle 的组合需另行设计聚合或多 Host。
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
