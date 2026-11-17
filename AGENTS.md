# AGENTS.md

本文件是本仓库的 Agent 执行约定，Kilo / 其它编码 Agent 在此项目工作时必须遵守。

## Git 操作：全面禁止

**禁止执行任何 git 操作**，包括但不限于：

- `git commit` / `git push` / `git pull` / `git fetch`
- `git add` / `git rm` / `git mv` / `git stash`
- `git checkout` / `git switch` / `git restore` / `git reset` / `git revert` / `git clean`
- `git branch` / `git tag` / `git merge` / `git rebase` / `git cherry-pick`
- `git config`（含 local / global）
- 任何改写 `.git/` 目录内容的操作

允许的只读操作仅限：`git status` / `git diff` / `git log` / `git show`（用于理解当前代码状态）。

**所有改动一律留在工作区**，由项目所有者自行审阅、暂存与提交。Agent 不代替所有者做版本管理决策。

## 技术栈约定

- Kotlin + Android View 体系（XML 布局 + ViewBinding + RecyclerView）。**不使用 Jetpack Compose**。
- 只有一个 `:app` 模块。预览内核（PDF 引擎、OOXML 解析、预览自定义 View、通用弹窗基类）在 `com.peng.pdfreader.core` 包内，业务层在 `com.peng.pdfreader.ui` / `feature` 等包内。
- 所有自定义 View 统一放在 `com.peng.pdfreader.core.widget` 一个包内。
- 代码中不得出现全路径类名，一律使用 `import`。
- 依赖必须来自海外仓库（`google()` / `mavenCentral()` / `gradlePluginPortal()`），不得引入国内镜像。

## 国际化约定

- App 名称与文件类型名（PDF / Word / Excel / PPT / TXT）标记 `translatable="false"`，且不得出现在任何 `values-XX` 语言包中。
- 业务文案一律走 `res/values*/strings*.xml`，禁止把面向用户的文案写死在 Kotlin 或 XML 布局中。
- PDF 引擎只抛稳定错误码（如 `PDF_ENCRYPTED`），由 app 层映射为本地化文案。

## 日志约定

- 统一走 `com.peng.pdfreader.core.util.Logger`，内部以 `BuildConfig.DEBUG` 门控。
- release 构建由 `app/proguard-rules.pro` 的 `-assumenosideeffects class android.util.Log` 剥离所有日志。
- debug 包允许中文日志；release 包内不得残留任何日志调用。

## 资源约定

- 不从设计图下载任何资源。设计稿中存在但仓库缺失的素材，一律用现有资源或代码绘制占位，并汇总成清单交给项目所有者补齐。
- 设计稿地址：https://moonvy.com/project/1e7e53d3-a442-4a29-8efd-ccbf689af91a/f96b1cb3-41ae-44d3-9376-7dcbe5f51f35
  （可通过 moonvy MCP 工具读取，`workspaceDir` 为本仓库根目录）
