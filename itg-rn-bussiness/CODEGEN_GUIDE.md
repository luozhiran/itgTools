# Codegen 开发教程（React Native 0.84.1 / Android）

本文面向 `itg-rn-bussiness` 的业务开发者。目标是从 TypeScript Spec 增加 Fabric 原生组件或 TurboModule，生成桥接代码，完成 Android 实现并发布业务 AAR。可以先运行现有 `ItgDemoNativeView` 和 `ItgDemoTurboModule`，再按下文扩展。

## 1. 先理解哪些代码由谁提供

| 内容 | 来源 | 本工程位置 |
| --- | --- | --- |
| 属性、事件、方法的声明 | 开发者编写 TypeScript Spec | `js/specs/` |
| Fabric 的 ViewManager 接口、Delegate、C++ 描述符 | Codegen 自动生成 | `codegen/java/`、`codegen/jni/` |
| TurboModule 的抽象 Spec、C++ 桥接 | Codegen 自动生成 | `codegen/java/`、`codegen/jni/` |
| Android View、ViewManager 的属性处理和事件发送 | 开发者编写 | `src/main/java/com/itg/rnbussiness/nativecode/` |
| TurboModule 的实际方法、ReactPackage 注册 | 开发者编写 | 同上 |
| JNI 编译与业务库注册入口 | 本工程已有；通常新增 Spec 无须逐个修改 | `src/main/jni/` |

**不要手改 `codegen/` 中的文件。**重新运行 Codegen 会覆盖生成内容；原生业务逻辑应写在 `src/main/java/`。Codegen 只知道 Spec 声明，无法推断 Android View 如何显示或方法如何执行。

## 2. 本工程的配置与生成命令

根目录 `package.json` 把 `itg-rn-bussiness` 设为 npm workspace，并提供：

```bash
# 在仓库根目录执行；首次或锁文件变化后先运行 npm ci
npm ci
npm run codegen:android
```

`codegen:android` 实际调用：

```bash
react-native codegen \
  --path itg-rn-bussiness \
  --platform android \
  --outputPath itg-rn-bussiness/codegen
```

业务模块的 `package.json` 包含以下配置：

```json
{
  "codegenConfig": {
    "name": "ItgRnBusinessSpec",
    "type": "all",
    "jsSrcsDir": "js/specs",
    "android": { "javaPackageName": "com.facebook.fbreact.specs" },
    "includesGeneratedCode": true
  }
}
```

`jsSrcsDir` 相对于业务模块；`type: all` 同时处理 Fabric 和 TurboModule；`name` 是**整个业务 Codegen 库名**，新增组件时无需改成组件名。`includesGeneratedCode: true` 表示由业务模块预生成、编译并交付代码。这里选择库内 `codegen/` 作为 `--outputPath`，所以不会得到官方 App 示例中的 `android/app/build/generated/source/codegen/` 路径。App 的那一目录属于 App 构建；本工程生成物属于业务库。

运行后可检查这些现有产物：

```text
codegen/java/com/facebook/react/viewmanagers/ItgDemoNativeViewManagerInterface.java
codegen/java/com/facebook/react/viewmanagers/ItgDemoNativeViewManagerDelegate.java
codegen/java/com/facebook/fbreact/specs/NativeItgDemoSpec.java
codegen/jni/react/renderer/components/ItgRnBusinessSpec/
codegen/jni/ItgRnBusinessSpec-generated.cpp
```

CLI 输出 `Generated artifacts: itg-rn-bussiness/codegen` 表示生成完成。仅提示找不到库内 `autolinking.json` 或 `react-native.config.js`、但命令以退出码 0 结束时，不代表 Codegen 失败。当前 Gradle 构建**不会自动运行**此 npm 脚本；每次新增或修改 Spec，都要先执行 `npm run codegen:android`，再构建 AAR，并把 Spec 与 `codegen/` 的变更一起提交。

## 3. 新增 Fabric 组件

### 第一步：编写 Spec

在 `js/specs/` 下新建以 `NativeComponent.ts` 结尾的文件，例如 `ItgBadgeNativeComponent.ts`：

```ts
import type {HostComponent, ViewProps} from 'react-native';
import {codegenNativeComponent} from 'react-native';

export interface NativeProps extends ViewProps {
  label?: string;
}

export default codegenNativeComponent<NativeProps>(
  'ItgBadgeView',
) as HostComponent<NativeProps>;
```

`ItgBadgeView` 是原生组件名，必须与 Android ViewManager 的 `getName()` 完全一致。若要定义事件，可参考现有 `ItgDemoNativeComponent.ts` 的 `CodegenTypes.BubblingEventHandler` 和 `onPress`。

### 第二步：运行 Codegen 并查看接口

在仓库根目录运行 `npm run codegen:android`。它应新增 `ItgBadgeViewManagerInterface.java`、`ItgBadgeViewManagerDelegate.java` 和同一 `ItgRnBusinessSpec` 库下的 C++ 内容。先打开生成的 Interface，确认属性对应的 `setLabel(...)` 签名。无需手写这些 Java/C++ 文件。

### 第三步：实现 Android View 和 ViewManager

在 `src/main/java/com/itg/rnbussiness/nativecode/` 新建实际 View 和 ViewManager。下面是上述 `label` 属性的最小实现；文件各自放在同名 `.kt` 中：

```kotlin
// ItgBadgeView.kt
package com.itg.rnbussiness.nativecode

import android.widget.TextView
import com.facebook.react.bridge.ReactContext

class ItgBadgeView(context: ReactContext) : TextView(context)
```

```kotlin
// ItgBadgeViewManager.kt
package com.itg.rnbussiness.nativecode

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.ItgBadgeViewManagerDelegate
import com.facebook.react.viewmanagers.ItgBadgeViewManagerInterface

@ReactModule(name = ItgBadgeViewManager.NAME)
class ItgBadgeViewManager : SimpleViewManager<ItgBadgeView>(),
    ItgBadgeViewManagerInterface<ItgBadgeView> {
    private val generatedDelegate = ItgBadgeViewManagerDelegate(this)

    override fun getDelegate(): ViewManagerDelegate<ItgBadgeView> = generatedDelegate
    override fun getName() = NAME
    override fun createViewInstance(context: ThemedReactContext) = ItgBadgeView(context)

    @ReactProp(name = "label")
    override fun setLabel(view: ItgBadgeView, value: String?) {
        view.text = value ?: ""
    }

    companion object { const val NAME = "ItgBadgeView" }
}
```

可以复制现有 `ItgDemoNativeView.kt`、`ItgDemoNativeViewManager.kt` 来扩展样式或事件，重点核对以下位置：

1. View 的 Android 行为，例如 `TextView` 的文字、样式、点击响应。
2. ViewManager 实现新生成的 `ItgBadgeViewManagerInterface<ItgBadgeView>`，并使用新生成的 `ItgBadgeViewManagerDelegate(this)`。
3. `getName()` 返回 `ItgBadgeView`；`@ReactProp(name = "label")` 的 `setLabel(...)` 把 JS 属性写到 Android View。
4. 若 Spec 定义事件，由 View 调用 `UIManagerHelper.getEventDispatcherForReactTag(...)` 发送事件，并在 ViewManager 导出事件名称；参照现有 `onPress` 实现。

最后在 `ItgRnBusinessPackage.createViewManagers()` 的列表中加入新的 ViewManager，例如 `listOf(ItgDemoNativeViewManager(), ItgBadgeViewManager())`。Codegen 不会替你注册这个 Kotlin 类。

### 第四步：在 JS 页面使用

在 `js/index.js` 导入并渲染组件：

```jsx
import ItgBadgeNativeView from './specs/ItgBadgeNativeComponent';

// 页面 JSX 内
<ItgBadgeNativeView label="新组件" style={{height: 48}} />
```

这里的导入变量名可自定；`codegenNativeComponent('ItgBadgeView')` 与 ViewManager 名称必须一致。

## 4. 新增 TurboModule

### 第一步：编写 Spec

在 `js/specs/` 下新建以 `Native` 开头的 TypeScript 文件，例如 `NativeItgCalculator.ts`：

```ts
import type {TurboModule} from 'react-native';
import {TurboModuleRegistry} from 'react-native';

export interface Spec extends TurboModule {
  add(a: number, b: number): Promise<number>;
}

export default TurboModuleRegistry.get<Spec>('ItgCalculator');
```

### 第二步：生成并实现

运行 `npm run codegen:android`，检查新生成的 `codegen/java/com/facebook/fbreact/specs/NativeItgCalculatorSpec.java`，按其中的真实方法签名编写 Kotlin 子类。对于上面的 `add` Spec，可在 `nativecode/ItgCalculatorModule.kt` 写：

```kotlin
package com.itg.rnbussiness.nativecode

import com.facebook.fbreact.specs.NativeItgCalculatorSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = NativeItgCalculatorSpec.NAME)
class ItgCalculatorModule(context: ReactApplicationContext) :
    NativeItgCalculatorSpec(context) {
    override fun add(a: Double, b: Double, promise: Promise) {
        promise.resolve(a + b)
    }
}
```

出错时可通过 `promise.reject(...)` 报错。实际方法签名以新生成的 Spec 为准。

在 `ItgRnBusinessPackage` 中同时更新 `getModule(...)` 和 `getReactModuleInfoProvider()`，为新模块提供实例及 `isTurboModule = true` 的元数据。`TurboModuleRegistry.get<Spec>('ItgCalculator')` 的字符串必须与生成类的 `NAME`、包注册名称一致。然后在 `js/index.js` 中导入并调用。

## 5. 构建、交付和验证

修改 Spec 后，按以下顺序在仓库根目录执行：

```bash
npm run codegen:android
bash gradlew :itg-rn-bussiness:assembleRelease
bash gradlew :itg-rn-container:publishReleasePublicationToMavenRepository \
  :itg-rn-bussiness:publishReleasePublicationToMavenRepository
cd rn-host-sample/android
bash gradlew :app:assembleRelease
```

业务模块的 Gradle 配置会编译 `codegen/java`，CMake 会编译 `codegen/jni`，并把生成的原生库与 JS Bundle 打入业务 AAR。可用 `unzip -Z1 itg-rn-bussiness/build/outputs/aar/itg-rn-bussiness-release.aar` 检查 `assets/index.android.bundle` 与各 ABI 的 `jni/<abi>/libitg_rn_business_codegen.so`；此命令需回到仓库根目录执行。

`rn-host-sample` 通过 Maven AAR 消费业务模块。宿主的 `SampleApplication` 注册 `ItgRnBusinessPackage`，并在 `loadReactNative(this)` 之后、首个 ReactHost 创建之前调用 `ItgRnBusinessNative.install()`。业务 AAR 不会因 `includesGeneratedCode` 自动出现在宿主的 npm autolinking 列表；宿主自己的 `android/build/generated/autolinking` 与 `android/app/build/generated/autolinking` 仍由宿主 RN Gradle 插件生成。现有示例页可以验证 Fabric 的文字属性与点击事件，以及 TurboModule 的 `multiply(6, 7) = 42`。

## 6. 常见问题

| 现象 | 先检查 |
| --- | --- |
| 新 Spec 没生成 | 文件是否放在 `js/specs/`；Fabric 文件名是否以 `NativeComponent.ts` 结尾、Turbo 文件名是否以 `Native` 开头；是否在**仓库根目录**执行脚本 |
| 生成了接口但 Kotlin 编译失败 | 是否按最新生成签名实现；是否先运行 Codegen；ViewManager 是否实现新 Interface 并使用新 Delegate |
| JS 找不到原生组件 | `codegenNativeComponent(...)` 名称、ViewManager `getName()` 和 `ItgRnBusinessPackage.createViewManagers()` 是否一致 |
| JS 找不到 TurboModule | `TurboModuleRegistry` 名称、生成 Spec 的 `NAME`、`getModule()` 和模块元数据是否一致 |
| AAR 能构建但宿主运行失败 | 检查宿主注册与 `ItgRnBusinessNative.install()` 的调用时机；检查 AAR 是否含目标 ABI 的 `.so`；用独立宿主实际运行，而不只看编译结果 |
| 修改 Spec 后运行表现仍旧 | 重新 Codegen、重建并重新发布 AAR，确保宿主拿到新版本；预构建 JS Bundle 也须重建 |

当前 `src/main/jni/ItgRnBusinessOnLoad.cpp` 使用 RN 0.84 的内部 C++ 注册入口。**仅新增 Spec 且保持 `codegenConfig.name = ItgRnBusinessSpec` 时通常不必改这个入口**：它调用该库生成的统一组件注册函数和模块 Provider。若修改 Codegen 库名或升级 RN，需要同步检查 CMake 目标名、C++ 函数名与 ABI 兼容，并在独立宿主上重新验证。

参考：[RN 0.84 Codegen CLI](https://reactnative.dev/docs/0.84/the-new-architecture/codegen-cli)、[Fabric 原生组件](https://reactnative.dev/docs/0.84/fabric-native-components-introduction)、[Android TurboModule](https://reactnative.dev/docs/0.84/turbo-native-modules-android)。
