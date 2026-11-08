# 独立 RN 0.84 AAR 宿主

这个 Android App 不依赖仓库中的业务源码模块；它只声明 `com.itg:itg-rn-bussiness:0.1.0` Maven 坐标，用来验证业务 AAR 的 Bundle、Codegen `.so` 与传递依赖。根目录先执行 `npm ci` 和两个 RN 模块的本地 Maven 发布，再从 `rn-host-sample/android` 运行 `bash gradlew :app:assembleRelease`。

宿主 `package.json` 还直接依赖 `react-native-gesture-handler@2.33.1`，用于验证第三方原生 RN 库的 autolinking。操作说明见 [第三方 RN 库接入教程](../itg-rn-bussiness/THIRD_PARTY_RN_LIBRARY.md)，宿主与业务 `package.json` 的分工见 [根目录说明](rn-host-package-autolinking.md)。

打开 App，选择 **Fabric + TurboModule Demo**。点击原生 TextView 会更新“原生点击”文字；点击计算按钮应显示 `TurboModule 返回：42`；双击蓝色手势区域会增加计数。另一个按钮打开原有订单详情页。

官方 RN Gradle 插件的生成目录在这里：

- `android/build/generated/autolinking/autolinking.json`
- `android/app/build/generated/autolinking/src/main/java/com/facebook/react/PackageList.java`
- `android/app/build/generated/autolinking/src/main/jni/`

`autolinking.json` 中有 `react-native-gesture-handler`，但没有通过 Maven AAR 消费的业务模块；后者由 `SampleApplication` 显式注册。`app/build.gradle` 把 release 加入 `debuggableVariants` 以避免 RN Gradle 插件再生成同名 `index.android.bundle`，release 运行时仍关闭开发支持并加载 AAR 中的 Bundle。构建时使用根目录 `node_modules`，Android 工具链沿用 RN 0.84 官方模板的 Gradle 9.0。
