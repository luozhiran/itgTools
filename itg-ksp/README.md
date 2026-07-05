# itg-ksp 文档索引

`itg-ksp` 是 `itg-ui` 的编译期增强层，由三个模块组成：

- `itg-ksp-annotations`：业务代码使用的 SOURCE 注解。
- `itg-ksp-compiler`：KSP 处理器，生成 Recycler registry、adapter 工厂和 Tab 声明。
- `itg-ksp-runtime`：生成代码与 `itg-ui` 之间的运行时桥接。

## 从哪里开始

| 目标 | 文档 |
|---|---|
| 新模块首次接入 | [01-接入与构建配置](./docs/01-接入与构建配置.md) |
| 理解 runtime，或不使用注解手写 registry | [02-runtime完整教程](./docs/02-runtime完整教程.md) |
| ViewBinding Recycler：简单、复杂、自动字段绑定 | [03-Recycler-ViewBinding](./docs/03-Recycler-ViewBinding.md) |
| DataBinding Recycler | [04-Recycler-DataBinding](./docs/04-Recycler-DataBinding.md) |
| Diff、payload、混合列表、多列表 | [05-Recycler-Diff与组合场景](./docs/05-Recycler-Diff与组合场景.md) |
| Activity/Fragment Tab、标题、图标、参数、角标、样式 | [06-Tab完整教程](./docs/06-Tab完整教程.md) |
| 查询生成文件、类名和函数名 | [07-生成规则与API速查](./docs/07-生成规则与API速查.md) |
| 生成失败、引用标红、编译或运行异常 | [08-排错手册](./docs/08-排错手册.md) |
| 按能力确认是否支持 | [09-场景覆盖矩阵](./docs/09-场景覆盖矩阵.md) |

## 建议阅读路径

首次接入：`01 → 03/04 → 05 → 07 → 08`。

只使用 runtime：`02 → itg-ui Recycler 文档`。

接入 Tab：`01 → 06 → 07 → 08`。

## 仓库中的可运行示例

- [KspRecyclerDemoActivity](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspRecyclerDemoActivity.kt)
- [KspRecyclerItems](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspRecyclerItems.kt)
- [KspTabHostActivity](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspTabHostActivity.kt)
- [KspTabPages](../app/src/main/java/com/itg/itgtools/pages/itgui/ksp/KspTabPages.kt)

## 其他入口

- [接入手册（简版）](./INTEGRATION_GUIDE.md)
- [新模块接入清单](./INTEGRATION_CHECKLIST.md)

