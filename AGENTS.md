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

## UI 设计核对标准

需求中出现"与设计一致 / UI / 界面 / 样式 / 布局 / 图标"等要求时，必须按本节执行；
纯功能改动不触发。**目标是与设计图一模一样**，不是"大致相符"。

### 1. 权威来源

- 唯一视觉依据是设计页的**渲染图**（moonvy snapshot，下载到临时目录，不入库）。
- 图层树只能用来取坐标、色值、层级，**不能**作为"是否一致"的证据：它表达不出填充还是描边、
  图层是否可见、圆角、描边与色块之间的缝隙、图标字形等。
- 先确定对应关系：设计页名 ↔ 界面状态（例：`draw`=阅读器绘制态，`sign`/`sign2`=签名页，
  `sign-color`/`sign-color2`=颜色弹窗，`sign-down`=签名贴回态，`sign-add`=保存后）。

### 2. 强制流程（缺任一步 = 未核对，不得给结论）

1. 取设计页渲染图（`moonvy_download_asset`，type=snapshot）。
2. 截实现同状态截图（同一设备、同一状态）。
3. 列**元素表**：设计元素 | 设计值 | 实现值 | 结论 | 证据。
4. 只改表中标红的项。
5. 改完重测并更新表，交付前后截图。

### 3. "已核对"的判定

- 每个元素必须有**实测值**（像素位置、尺寸、色值、文本、显隐、所用图标资源名）。
- 只凭代码或只凭图层树得出的判断只能标注"推断"，不得写成"一致"。
- 实现里的既有写法**不能**当作设计一致的证据：历史代码可能本就偏离设计。
- 结论只用三种表述：实测一致 / 实测不一致 / 有差异（注明原因）。
- 禁止使用"已对齐""完全一致"这类无证据的笼统表述。

### 4. 素材

- 优先使用仓库现有素材；仓库没有的用代码绘制占位并汇总清单，不得从设计稿下载入库。
- 用近似素材时必须在报告里写明形态/颜色差异，不得当作一致。
- 颜色条（绘制工具条、签名页颜色弹窗）必须支持横向滑动：颜色数量会增加，固定排布在小屏上放不下。

### 5. 交付物

- 逐项元素表 + 前后截图 + 未一致项及原因清单。

### 6. 范围

- 核对 UI 只改视觉。发现功能缺失、设计稿与功能冲突时，列出交项目所有者决定，不自行增删功能。

