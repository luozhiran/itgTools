---
name: scenario-driven-docs
description: Use this skill when writing or rewriting documentation that should help readers choose the right action by scenario, then jump into detailed instructions. Use for API guides, module guides, operational runbooks, migration docs, feature docs, troubleshooting docs, configuration guides, onboarding docs, and any documentation where readers need a readable multi-file structure: README as the entry, a scenario decision table, a table of contents, and detailed docs split into sibling docs/doc markdown files.
---

# Scenario-Driven Documentation

Use this skill to write practical documentation that starts with reader decisions, then expands into detailed tutorials.

Prefer a multi-file structure for medium or large docs:

```text
README.md                # Entry point: scope, scenario table, table of contents, quick start
docs/                    # Or reuse an existing doc/ or docs/ directory
  01-topic.md            # One major tutorial or scenario group per file
  02-topic.md
```

Use a single file only when the full document is short enough to scan comfortably.

## Workflow

1. Read the source of truth first: code, config, existing docs, product behavior, scripts, tests, logs, schemas, or user-provided requirements.
2. Identify reader scenarios, not implementation categories. A good scenario starts from the user's problem or decision.
3. Choose the documentation structure:
   - Existing `docs/` or `doc/` directory: reuse it.
   - No existing docs directory: create `docs/` unless the project convention clearly uses `doc/`.
   - Medium/large docs: keep `README.md` as the entry and split detailed tutorials into `docs/*.md`.
   - Small docs: a single README is acceptable if it remains easy to scan.
4. Build a top overview table in `README.md` that answers: what to use, when to use it, why it works, and what constraints apply.
5. Link each scenario row to the detailed file or anchor that teaches it. Prefer file links such as `docs/04-background-tasks.md` over deep anchors in a giant README.
6. Add a table of contents in `README.md` where each item maps to a detailed docs file.
7. Write detailed docs files with minimal correct examples, constraints, validation, and common mistakes.
8. Remove stale, unsupported, or speculative content unless the user explicitly asks for a proposal.
9. Validate links, examples, names, parameters, claims, and formatting before finishing.

## README Entry Pattern

Use `README.md` as the front door. It should help readers choose where to go, not contain every detail.

```markdown
# Project or Module Name

One short paragraph that defines scope and audience.

## 使用场景总览

| 使用场景 | 推荐做法 | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用 |
| --- | --- | --- | --- | --- |
| [场景名称](./docs/01-topic.md) | 具体 API、命令、配置、流程或方案 | 平台、版本、权限、环境、角色、状态或其他约束 | 读者遇到什么情况时选择它 | 用实现依据、规则依据、流程依据、数据依据或产品行为解释为什么成立 |

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [01. 主题](./docs/01-topic.md) | 这个文件解决什么问题 |
| [02. 主题](./docs/02-topic.md) | 这个文件解决什么问题 |
```

Keep quick-start examples in README only if they are short. Move full explanations to `docs/`.

## Detailed File Pattern

Each linked docs file should be focused and readable.

````markdown
# N. 场景或主题名称

一句话说明这个文件解决什么问题。

## 适用条件

- 条件 1
- 条件 2

## 推荐做法

```text
最小正确示例、命令、配置或流程
```

## 关键说明

- 为什么这样做。
- 需要注意的边界、权限、生命周期、线程、环境、数据或兼容性问题。
- 常见错误或不推荐写法。

## 验证方式

- 如何确认配置、代码、流程或结果是正确的。

[返回 README](../README.md)
````

Delete subsections that do not apply. Do not force every file to have all headings.

## Table Design

Adjust column names to fit the document, but keep the decision-making purpose. Common alternatives:

- `使用场景`: `问题场景`, `用户目标`, `运维场景`, `迁移场景`, `故障现象`
- `推荐做法`: `推荐 API`, `推荐命令`, `推荐配置`, `处理方案`, `排查路径`
- `适用条件/支持范围`: `支持平台`, `前置条件`, `权限要求`, `影响范围`, `兼容性`, `风险等级`
- `什么情况下使用`: `触发条件`, `选择标准`, `适用时机`
- `为什么可以用`: `原理说明`, `依据`, `设计理由`, `验证方式`

Use support values that match the domain. Examples:

- Platform docs: `Android`, `iOS`, `Web`, `Server`, `全平台`
- Version docs: `v1 可用`, `v2 推荐`, `已废弃`, `实验能力`
- Permission docs: `普通用户`, `管理员`, `需要写权限`, `只读可用`
- Runtime docs: `开发环境`, `测试环境`, `生产可用`, `生产慎用`
- Risk docs: `低风险`, `中风险`, `高风险`, `需回滚方案`
- API docs: `同步 API`, `异步 API`, `回调 API`, `批量 API`, `只适合内部调用`
- If no support dimension matters, use `无特殊限制` or remove the column.

## Splitting Guidance

Split docs by how readers look for answers:

- One file per major tutorial, workflow, or scenario group.
- Do not create dozens of tiny files if several rows share one mental model.
- Do not keep a giant README when the detailed content is long enough to require scrolling past unrelated topics.
- Keep filenames stable and numbered when order matters, for example `01-quick-start.md`.
- Preserve existing useful docs when possible; replace or redirect stale umbrella docs.

## Writing Rules

- Prefer concrete instructions over conceptual exposition.
- Do not invent APIs, commands, behavior, permissions, or constraints.
- If something is unknown, inspect the source of truth or explicitly state the uncertainty.
- Keep examples minimal, correct, and directly related to the scenario.
- Match examples to the target audience and technology stack.
- Put selection criteria in the README overview table, not only deep in prose.
- Use precise names from source: API names, config keys, commands, file paths, UI labels, statuses, roles, and versions.
- When replacing existing docs, remove contradicted old content and stale examples.
- When documenting risky operations, include impact, rollback, or validation guidance.
- When documenting async, lifecycle, permissions, state, or deployment behavior, explicitly state who owns cleanup and what can fail.

## Common Document Types

### API or SDK Guide

README table columns:

`使用场景 | 推荐 API | 适用条件/支持范围 | 什么情况下使用 | 为什么可以用`

Detailed files should cover return values, threading or async behavior, lifecycle, error handling, and unsupported uses.

### Operations or Runbook

README table columns:

`故障/任务场景 | 推荐操作 | 权限/环境 | 什么情况下使用 | 为什么可以用`

Detailed files should cover blast radius, required access, validation commands, rollback, and escalation points.

### Configuration Guide

README table columns:

`配置目标 | 推荐配置 | 生效范围 | 什么情况下使用 | 为什么可以用`

Detailed files should cover defaults, precedence, reload/restart requirements, environment differences, and validation.

### Migration Guide

README table columns:

`迁移场景 | 新做法 | 适用版本/范围 | 什么情况下迁移 | 为什么要迁移`

Detailed files should cover old behavior, new behavior, compatibility, rollout, rollback, and test checks.

### Troubleshooting Guide

README table columns:

`故障现象 | 排查路径 | 影响范围 | 什么情况下使用 | 判断依据`

Detailed files should cover symptoms, likely causes, commands/logs to check, fixes, and confirmation signals.

## Validation Checklist

Before finishing, check:

- `README.md` is a useful entry point, not a giant manual.
- The overview table appears near the top of README.
- The first column links to real docs files or headings.
- The README table of contents maps to real docs files.
- Each linked docs file teaches the scenario well enough to act.
- Examples and names match the source of truth.
- Unsupported or deprecated behavior is not presented as recommended.
- The document set explains what to use, when to use it, why it works, constraints, and how to verify.
- Relative links work from README and from docs files.
- Formatting is clean. Run a lightweight check such as `git diff --check` when available.