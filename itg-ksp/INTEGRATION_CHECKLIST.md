# itg-ksp 新模块接入清单

适合直接贴到项目 Wiki 或 PR 描述里。

## 依赖

- [ ] 业务模块已接入 KSP 插件
- [ ] 已添加 `implementation(project(":itg-ksp-annotations"))`
- [ ] 已添加 `implementation(project(":itg-ksp-runtime"))`
- [ ] 已添加 `ksp(project(":itg-ksp-compiler"))`

## Recycler 接入

- [ ] 定义 actions 接口
- [ ] item 实现 `ItgListItem`
- [ ] item 标注 `@ItgViewBindingItem` 或 `@ItgDataBindingItem`
- [ ] 复杂 item 使用 `@ItgBind`
- [ ] 简单字段展示使用 `@ItgAutoTextField`
- [ ] 需要增量更新时补 `@ItgPayload` 或依赖自动 payload
- [ ] 业务侧改为调用 `createXxxRecyclerAdapter(...)`
- [ ] `RecyclerViewAbility.bind(...)` 和 `observeItems(...)` 已接好

## Tab 接入

- [ ] 宿主类标注 `@ItgTabHost`
- [ ] Tab 页面类标注 `@ItgTabItem`
- [ ] 宿主与页面的 `groupName` 完全一致
- [ ] Tab 页面至少间接继承 `Fragment`
- [ ] 宿主类实现 `onCreateTabs()`
- [ ] 宿主类实现 `onCreateTabConfig()`
- [ ] 业务侧确认生成函数可直接编译通过

## 验证

- [ ] 执行 `:app:compileDebugKotlin`
- [ ] 执行 `:app:assembleDebug`
- [ ] 打开示例页验证 Recycler demo
- [ ] 打开示例页验证 Tab demo

## 参考实现

- [`KspRecyclerDemoActivity`](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspRecyclerDemoActivity.kt)
- [`KspTabHostActivity`](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspTabHostActivity.kt)
- [`INTEGRATION_GUIDE.md`](./INTEGRATION_GUIDE.md)

