# 宿主 package.json 与 RN autolinking（0.84.1）

`rn-host-sample` 是消费业务 AAR 的 Android 宿主。它需要自己的 [`package.json`](rn-host-sample/package.json)，但不需要复制 [`itg-rn-bussiness/package.json`](itg-rn-bussiness/package.json) 中的 `codegenConfig`。

| 文件 | 职责 |
| --- | --- |
| 根目录 `package.json`、`package-lock.json` | npm workspace 的统一安装和版本锁定；执行 `npm ci` 后依赖位于根目录 `node_modules` |
| `rn-host-sample/package.json` | 声明宿主直接使用的 RN、React 及原生 RN 库；RN CLI 据此发现可 autolink 的 npm 包 |
| `itg-rn-bussiness/package.json` | 声明业务 JS 入口、Fabric/TurboModule 的 `codegenConfig`，以及业务使用的 peer dependency |

真正触发宿主 autolinking 的是 [`rn-host-sample/android/settings.gradle`](rn-host-sample/android/settings.gradle) 中的 `com.facebook.react.settings` 和 `autolinkLibrariesFromCommand()`。Gradle 项目同步会评估 settings，因此可能生成或复用 `rn-host-sample/android/build/generated/autolinking/autolinking.json`；构建也会执行这一阶段。RN 插件根据 `package.json`/锁文件的哈希决定是否更新缓存。**只创建 `package.json` 而不运行 Gradle，同步产物不会自行出现。**

宿主 [`app/build.gradle`](rn-host-sample/android/app/build.gradle) 中的 `autolinkLibrariesWithApp()` 配合 App 构建任务生成 `android/app/build/generated/autolinking/` 下的 `PackageList.java` 和 JNI/CMake 文件；仅做一次同步不保证所有 App 级文件都已经出现。当前宿主没有自己的 Spec，因此不用配置 App 级 `codegenConfig`。业务 Spec 的预生成文件在 `itg-rn-bussiness/codegen/`，由业务模块编入 AAR。

运行与检查：

```bash
# 仓库根目录
npm ci
cd rn-host-sample/android
bash gradlew :app:assembleRelease
```

```text
rn-host-sample/android/build/generated/autolinking/autolinking.json
rn-host-sample/android/app/build/generated/autolinking/src/main/java/com/facebook/react/PackageList.java
rn-host-sample/android/app/build/generated/autolinking/src/main/jni/Android-autolinking.cmake
```

本示例通过 Maven 坐标消费 `itg-rn-bussiness` AAR，因此业务 AAR 不在宿主的 npm autolinking 列表中，`SampleApplication` 显式注册 `ItgRnBusinessPackage`。第三方 `react-native-gesture-handler` 则是宿主 `package.json` 的直接依赖，会出现在 autolinking 配置与生成的 `PackageList.java` 中。新增原生 RN 库的操作见 [第三方 RN 库接入教程](itg-rn-bussiness/THIRD_PARTY_RN_LIBRARY.md)。

`rn-host-sample/build/` 是此前一次以外层目录评估 Gradle 时留下的缓存；当前有效的宿主 Gradle 根目录是 `rn-host-sample/android`，应查看其 `android/build/`。这些 `build/` 目录都不需要提交。

参考：[React Native 0.84 接入现有 Android App](https://reactnative.dev/docs/0.84/integration-with-existing-apps)。
