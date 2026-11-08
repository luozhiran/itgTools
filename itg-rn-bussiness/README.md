# 业务 Fabric / TurboModule 示例（RN 0.84.1）

本模块同时发布业务 JS Bundle、一个 Fabric 原生 View 和一个 TurboModule。`ItgRnBusiness.openNativeDemo(activity)` 打开验证页：Android `TextView` 接收 JS 的 `text` 属性并发出 `onPress`；TurboModule 的 `multiply(6, 7)` 返回 `42`。

新增组件或模块请按 [Codegen 开发教程](CODEGEN_GUIDE.md) 操作。
引入带 Android 原生代码的 RN 第三方库请按 [Gesture Handler 接入教程](THIRD_PARTY_RN_LIBRARY.md) 操作。

| 位置 | 用途 |
| --- | --- |
| `package.json` | npm workspace 的 `codegenConfig`；`type: all`，`includesGeneratedCode: true` |
| `js/specs/ItgDemoNativeComponent.ts` | Fabric 属性与事件契约 |
| `js/specs/NativeItgDemo.ts` | TurboModule 方法契约 |
| `codegen/java`、`codegen/jni` | RN 0.84.1 Codegen 生成物，随源码提交 |
| `src/main/java/.../nativecode` | Android View、ViewManager、TurboModule、ReactPackage 与安装入口 |
| `src/main/jni` | 把生成的 C++ 编为 `.so`，接入 RN 的组件与模块注册 |

扩展时先改 TypeScript Spec，再在仓库根目录运行：

```bash
npm ci
npm run codegen:android
bash gradlew :itg-rn-bussiness:assembleRelease
bash gradlew :itg-rn-bussiness:publishReleasePublicationToMavenRepository
cd rn-host-sample/android && bash gradlew :app:assembleRelease
```

然后实现生成接口、在 `ItgRnBusinessPackage` 注册，并在业务 JS 页面调用。更改 Codegen 库名时，还需同步 CMake 的 `react_codegen_...` 目标与 `ItgRnBusinessOnLoad.cpp` 中的生成函数名。升级 RN 时重新生成全部 Codegen、重编 AAR 并运行独立宿主；原生注册使用了 RN 0.84 的内部 C++ 钩子，不能仅靠 Java/Kotlin 编译成功判断兼容。

宿主依照 [`rn-host-sample/android/app/src/main/java/com/itg/rnhostsample/SampleApplication.kt`](../rn-host-sample/android/app/src/main/java/com/itg/rnhostsample/SampleApplication.kt) 注册 `ItgRnBridgePackage` 和 `ItgRnBusinessPackage`，调用 `loadReactNative(this)` 后、创建首个 `ReactHost` 前调用 `ItgRnBusinessNative.install()`。业务 AAR 的 Codegen 已预编译，宿主不需要为它再生成一次；宿主自己的 RN Gradle 插件仍会生成 App 级 `autolinking` 目录。业务 AAR 不会自动进入 npm autolinking 的依赖列表。
