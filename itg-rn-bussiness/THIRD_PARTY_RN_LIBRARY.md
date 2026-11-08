# 引入第三方 RN 原生库：Gesture Handler 示例

本教程以 `react-native-gesture-handler@2.33.1` 为例，适用于当前 React Native 0.84.1 的 Android 容器工程。业务 AAR 预打包 JS Bundle；宿主通过 RN Gradle 插件构建和自动链接第三方原生代码。示例已经落在 `js/index.js`：打开宿主的 **Fabric + TurboModule Demo**，双击蓝色区域，“双击次数”会增加。手势库 2.32.0 起支持 RN 0.84；这里固定 2.33.1，避免不同机器解析到不同版本。[Gesture Handler 兼容表](https://github.com/software-mansion/react-native-gesture-handler#react-native-support)

## 1. 依赖分别放在哪里

| 位置 | 当前配置 | 原因 |
| --- | --- | --- |
| 根目录 `package.json` | `dependencies.react-native-gesture-handler = 2.33.1` | 根目录 `node_modules` 供 Metro 打业务 Bundle，也供宿主 Gradle 读取源码 |
| `itg-rn-bussiness/package.json` | `peerDependencies.react-native-gesture-handler = 2.33.1` | 业务 JS 使用这个包，声明宿主必须提供同版本；避免业务模块安装第二份 |
| `rn-host-sample/package.json` | `dependencies.react-native-gesture-handler = 2.33.1` | 宿主必须把原生 RN 库列为**直接依赖**，RN CLI 才能为它做 autolinking 和 Codegen |

根目录 `workspaces` 包含业务和示例宿主，版本由一份 `package-lock.json` 锁定。首次引入这个库时，在仓库根目录执行以下命令，并把业务模块 `package.json` 的 `peerDependencies` 同步设为 `2.33.1`：

```bash
npm install react-native-gesture-handler@2.33.1 --save-exact
npm install react-native-gesture-handler@2.33.1 --workspace rn-host-sample --save-exact
```

上述配置和锁文件已写入仓库；新检出后在**仓库根目录**运行 `npm ci` 即可。以后添加另一款原生 RN 库，也要同时检查业务 JS 的依赖声明、宿主直接依赖和根目录安装/锁文件。仅在业务 AAR 的 POM 中声明 Maven 依赖，不能替宿主 npm autolinking 发现 JS 包。[Gesture Handler 排错说明](https://docs.swmansion.com/react-native-gesture-handler/docs/2.x/guides/troubleshooting/)

## 2. 业务 JS 如何使用

`js/index.js` 从手势库导入 `Gesture`、`GestureDetector`、`GestureHandlerRootView`。业务入口的 `BusinessRoot` 用 `GestureHandlerRootView` 包住整个 RN 页面，并设置 `flex: 1`。这对嵌入 `ReactFragment` 的业务页同样适用。`NativeDemoPage` 定义双击手势：

```jsx
const doubleTap = Gesture.Tap()
  .numberOfTaps(2)
  .runOnJS(true)
  .onEnd((_event, success) => {
    if (success) setGestureCount(count => count + 1);
  });

<GestureDetector gesture={doubleTap}>
  <View collapsable={false} style={styles.gestureBox}>
    <Text>双击这里体验 Gesture Handler</Text>
  </View>
</GestureDetector>
```

`runOnJS(true)` 让这个更新 React state 的回调在 JS 线程运行；此示例不需要 Reanimated。`GestureDetector` 需要找到实际原生 View，故示例直接使用 `View`，并显式设置 `collapsable={false}`。根视图尽量贴近业务 RN 根节点；如在 RN `Modal` 中使用手势，还需给 Modal 内容单独加 `GestureHandlerRootView`。[安装与根视图要求](https://docs.swmansion.com/react-native-gesture-handler/docs/2.x/fundamentals/installation/)、[GestureDetector 说明](https://docs.swmansion.com/react-native-gesture-handler/docs/2.x/gestures/gesture-detector/)

## 3. 宿主如何自动链接原生部分

宿主 `android/settings.gradle` 已启用 `com.facebook.react.settings` 和 `autolinkLibrariesFromCommand()`；`android/app/build.gradle` 已启用 `com.facebook.react` 和 `autolinkLibrariesWithApp()`。由于宿主 `package.json` 直接依赖手势库，Gradle 同步/构建后应能看到：

```text
rn-host-sample/android/build/generated/autolinking/autolinking.json
  dependencies.react-native-gesture-handler

rn-host-sample/android/app/build/generated/autolinking/src/main/java/com/facebook/react/PackageList.java
  new com.swmansion.gesturehandler.RNGestureHandlerPackage()

rn-host-sample/android/app/build/generated/autolinking/src/main/jni/Android-autolinking.cmake
  rngesturehandler_codegen
```

这三个文件都是**宿主构建生成物**，不要手改。`SampleApplication` 通过 `PackageList(this).packages` 获取自动链接的手势包；其中只需手动加入当前业务 AAR 的 `ItgRnBridgePackage`、`ItgRnBusinessPackage`。手势库自己的 Codegen 由宿主构建在 `node_modules/react-native-gesture-handler/android/build/generated/source/codegen/` 生成；无需把它复制到 `itg-rn-bussiness/codegen/`。后者只存放业务自己定义的 Fabric/TurboModule Spec 产物。

## 4. 构建和验证

在仓库根目录运行：

```bash
npm ci
bash gradlew :itg-rn-container:publishReleasePublicationToMavenRepository \
  :itg-rn-bussiness:publishReleasePublicationToMavenRepository
cd rn-host-sample/android
bash gradlew :app:assembleRelease
```

检查自动链接：

```bash
# 以下命令在仓库根目录执行
rg 'react-native-gesture-handler' rn-host-sample/android/build/generated/autolinking/autolinking.json
rg 'RNGestureHandlerPackage' rn-host-sample/android/app/build/generated/autolinking/src/main/java/com/facebook/react/PackageList.java
rg 'rngesturehandler_codegen' rn-host-sample/android/app/build/generated/autolinking/src/main/jni/Android-autolinking.cmake
```

在模拟器安装 `rn-host-sample/android/app/build/outputs/apk/release/app-release.apk`，打开第一项 **Fabric + TurboModule Demo**，双击蓝色区域，确认计数从 0 变成 1。当前示例已在 arm64 模拟器验证。

修改业务 JS 后需要重建并重新发布业务 AAR，再让宿主获取新的 AAR。仅新增此第三方库、未修改业务 Spec 时，`npm run codegen:android` 不需要额外运行；手势库的 Codegen 由宿主的 RN Gradle 插件处理。开发期间若反复发布相同 Maven 版本 `0.1.0`，可在宿主构建时加 `--refresh-dependencies`；正式发布应提升版本号。

## 5. 业务 AAR 的交付边界

业务 AAR 已含手势示例所需的 JS Bundle，**不包含手势库在最终宿主中自动链接的 Android 工程与 Codegen 入口**。实际业务 App 必须把 `react-native-gesture-handler` 列为自身 `package.json` 的直接依赖，安装相同版本，并保留 RN 0.84 的 settings/app Gradle 插件配置，然后重新构建 App。只复制业务 AAR、没有宿主 npm/Gradle 接入，手势库原生模块不会自动出现。

常见故障：

| 现象 | 检查 |
| --- | --- |
| `RNGestureHandlerModule` 找不到 | 宿主 `package.json` 是否直接声明该包；`autolinking.json`、`PackageList.java` 是否包含它；是否重新构建并安装 APK |
| 手势区显示但不响应 | `GestureHandlerRootView` 是否包住该 RN 子树，样式是否保持 `flex: 1`；`GestureDetector` 下方是否有实际原生 View |
| 出现多个 Gesture Handler 实例 | 运行 `npm ls react-native-gesture-handler`，核对业务 peer 与宿主直接依赖的版本是否一致 |
| 构建缺少第三方 Codegen 文件 | 先确认宿主自动链接能发现该包，再从宿主 Android 项目重新构建；不要手改 `build/generated` |

参考：[Gesture Handler 2.x 安装](https://docs.swmansion.com/react-native-gesture-handler/docs/2.x/fundamentals/installation/)、[RN 0.84 Android 接入](https://reactnative.dev/docs/0.84/integration-with-existing-apps)。
