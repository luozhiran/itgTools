# 独立 RN 0.84 AAR 宿主

这个 Android App 不依赖仓库中的业务源码模块；它只声明 `com.itg:itg-rn-bussiness:0.1.0` Maven 坐标，用来验证业务 AAR 的 Bundle、Codegen `.so` 与传递依赖。根目录先执行 `npm ci` 和两个 RN 模块的本地 Maven 发布，再从 `rn-host-sample/android` 运行 `bash gradlew :app:assembleRelease`。

打开 App，选择 **Fabric + TurboModule Demo**。点击原生 TextView 会更新“原生点击”文字；点击计算按钮应显示 `TurboModule 返回：42`。另一个按钮打开原有订单详情页。

官方 RN Gradle 插件的生成目录在这里：

- `android/build/generated/autolinking/autolinking.json`
- `android/app/build/generated/autolinking/src/main/java/com/facebook/react/PackageList.java`
- `android/app/build/generated/autolinking/src/main/jni/`

`autolinking.json` 中业务依赖为空是预期结果：此宿主通过 Maven AAR 消费业务库，业务包由 `SampleApplication` 显式注册；RN 的自动链接发现范围是宿主的 npm 依赖。`app/build.gradle` 把 release 加入 `debuggableVariants` 以避免 RN Gradle 插件再生成同名 `index.android.bundle`，release 运行时仍关闭开发支持并加载 AAR 中的 Bundle。构建时使用根目录 `node_modules`，Android 工具链沿用 RN 0.84 官方模板的 Gradle 9.0。
