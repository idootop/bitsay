# bitsay · 开发进度与交接文档（PROGERSS.md）

> 本文件是**唯一权威交接文档**。任何 agent / 开发者接手前请先完整读一遍，
> 完成阶段性工作后**必须**回来更新「§10 任务列表」「§11 实机验证记录」「§12 下一步」。

- 最后更新：2026-10-01
- 当前阶段：**核心功能 + 桌面小组件已全部跑通并在真机验证；UI 视觉处于「可用的第一版」，等待按 HTML 稿细化**
- 一句话状态：可安装、可日常使用的可用版本已成型（release APK ≈ **2.1 MB**，63 个单元测试全绿）

---

## 1. 这是什么

一个极简的**纯文字笔记 + 待办** Android 应用，核心卖点是**桌面小组件**：
不用打开 App，在桌面就能看列表、勾待办、记一条新的。

- 笔记和待办都只是「一段纯文字」，没有标题 / 富文本 / 图片 / 音频 / 视频 / 附件
- 完整 CRUD + 创建时间 / 修改时间
- JSON 备份导出 / 导入，换手机一键迁移
- 桌面小组件：切换笔记↔待办、新建、点击查看、勾选待办、随宫格尺寸自适应
- **零权限**（连存储权限都不要，备份走 SAF 让用户自己选文件）

包名：`com.del.bitsay`；debug 变体为 `com.del.bitsay.debug`（可共存，方便对比）。

---

## 2. 快速开始（换一台电脑也能跑）

> ### 🔴 第一条硬约定：本机安装测试**只用 debug 包**
>
> - 需要装到手机上验证时，**一律用 `com.del.bitsay.debug`**：
>   `./gradlew :app:installDebug` 或 `adb install -r -t app/build/outputs/apk/debug/app-debug.apk`
> - **release 包（`com.del.bitsay`）由用户本人手动打包、安装、测试。**
>   agent 可以跑 `:app:assembleRelease` 确认能出包（CI 性质的验证），
>   **但不要把 release APK 装到设备上**，也不要 `adb uninstall com.del.bitsay`。
> - 两个包的 applicationId 不同，**可以共存**，所以 debug 的反复安装永远不会影响
>   用户手机上的 release 版本和数据。

```bash
cd /Users/del/X/App/Android/bitsay

# 本机 JAVA_HOME 默认指向一个不存在的 zulu-17，必须显式指定 Temurin 25：
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home

./gradlew :app:assembleDebug          # 调试包
./gradlew :app:testDebugUnitTest      # 63 个单元测试
./gradlew :app:assembleRelease        # 正式签名包（2.1 MB）—— 只验证能出包，不安装
./gradlew :app:installDebug           # 装到已连接的设备（推荐用这个）

# 真机（本机已通过 WiFi adb 连了一台 Android 16 / API 36 的 vivo V2309A）：
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.del.bitsay.debug/com.del.bitsay.MainActivity
```

**坑位提醒**

- `JAVA_HOME` 必须先导出，否则 Gradle 起不来（见上）。
- `adb install` 需要手机**亮屏解锁**，否则 vivo 会直接回 `INSTALL_FAILED_ABORTED: User rejected permissions`。
- 直接用 sqlite3 改设备数据库后，App 需要重新进入前台才会刷新（`ON_START` 会 reload）。
- 设备上两个包各自有独立的数据目录：debug 是 `/data/data/com.del.bitsay.debug/`，
  release 是 `/data/data/com.del.bitsay/`。用 `run-as` 直接看库时注意别搞混
  （`run-as` 只对 debuggable 的 debug 包有效）。

---

## 3. 技术选型与理由

| 项 | 选择 | 为什么 |
|---|---|---|
| 构建 | Gradle **9.6.0** + AGP **9.4.1** | 本机缓存里已有的稳定版；AGP 9 起**内置 Kotlin 支持** |
| Kotlin | **2.4.20**（由 AGP 内置，**不要**再 apply `org.jetbrains.kotlin.android`） | AGP 9 会直接报错拒绝 |
| Compose | Compose BOM **2026.09.00** + Material3 1.4.0 | 最新稳定 |
| compileSdk / targetSdk | **37（Android 17）** | 已装 `platforms/android-37.0` |
| minSdk | **31（Android 12）** | 见 §3.1 |
| DB | **手写 SQLite**（`SQLiteOpenHelper`），**不用 Room** | 见 §3.2 |
| 备份格式 | **手写 JSON codec**，零依赖 | 见 §3.3 |
| 小组件 | **RemoteViews + `RemoteCollectionItems`**，不用 Glance、不用 `RemoteViewsService` | 见 §3.4 |
| 偏好存储 | `SharedPreferences`（小组件配置） | 只有几个 int，DataStore 的异步模型反而碍事 |
| 导航 | 手写 sealed class（3 个页面） | 不值得引入 navigation-compose |
| DI | 手写 `AppContainer` | 4 个对象不值得引入 Hilt |
| 图标 | 全部自绘 vector（`res/drawable/*.xml`） | 不依赖 material-icons，风格统一 |
| 应用图标 | 仅 `mipmap-anydpi-v26` 自适应图标（纯 vector） | minSdk 31 ⇒ 不需要任何 PNG，省体积 |

### 3.1 为什么 minSdk = 31 而不是更低

用户最初要求「暂定安卓 12，最后如实选择其兼容的最低版本」。**实测 31 就是真实下界**：

1. 小组件用的 `RemoteViews.RemoteCollectionItems` / `setRemoteAdapter(int, RemoteCollectionItems)`
   是 **API 31 新增**；
2. 在 Android 17 上，老的 `setRemoteAdapter(int, Intent)` + `RemoteViewsService` 路径**已被废弃**
   （android-37 源码里 `notifyAppWidgetViewDataChanged` 的 `@deprecated` 明确指向 `RemoteCollectionItems`）。
   用新 API 就必须 31+。
3. `targetCellWidth/Height`、`previewLayout`、`widgetFeatures="reconfigurable"`、`description`
   也都是 API 31，正好一起用上。

> 若要下探到 Android 8~11：需要补一条 `RemoteViewsService` + `RemoteViewsFactory` 老路径
> （`refs/EverythingDone` 里有现成参考），并做 `Build.VERSION.SDK_INT` 分支。**没有做，是刻意的取舍。**

### 3.2 为什么不用 Room

- 业务数据就是**一张表两个字段**，Room 的收益（关系映射、类型安全 SQL）几乎为零；
- 省掉 KSP 注解处理器 ⇒ 编译更快、构建链更短（顺带躲开 KSP 与 Kotlin 2.4 的版本配对问题）；
- 省掉 `room-runtime` + `sqlite-framework` 约 1 MB；
- DAO 被抽象成 `ItemStore` 接口，**测试性反而更好**（单测用纯内存 Fake，不需要 Robolectric）。

### 3.3 为什么手写 JSON

- `org.json` 是 Android framework 类，在 JVM 单测里是 stub（一调用就抛异常），
  会让「备份格式」这块核心逻辑无法测试；
- Gson / Moshi / kotlinx.serialization 都要引依赖或加编译器插件；
- 我们的 payload 形状固定且扁平，`MiniJson`（约 200 行，含完整转义/解析）**100% 单测覆盖**，
  且 APK 零增量。

### 3.4 为什么小组件不用 Glance

Glance 会额外引入 `glance-appwidget` + 一层 Compose runtime 包装；而小组件的布局**本来就必须是
XML**（RemoteViews 不支持 Compose 直接渲染）。用 `RemoteCollectionItems` 后：

- 不需要在 manifest 注册 `RemoteViewsService`；
- 少一次 binder 往返（列表行在主进程直接构建好）；
- 尺寸/类型切换只需重新 `updateAppWidget`，没有工厂生命周期要管。

代价：所有行是**一次性构建**的，所以有 `WidgetItems.MAX_ROWS = 50` 的上限（桌面小组件本来也不会翻 300 条）。

### 3.5 minSdk 31 的另一个好处

`targetSdk 37` + `minSdk 31` ⇒ 全程无需任何 `Build.VERSION.SDK_INT` 分支、无需 desugaring、
`java.time` 直接可用（`TimeText` 就是这么写的）。

---

## 4. 架构：数据层 / 业务层 / UI 状态层（+ 小组件层）

```
app/src/main/java/com/del/bitsay/
├── BitSayApp.kt / AppContainer.kt     ← 手写 DI 容器 + Application（含小组件自动刷新）
├── MainActivity.kt                    ← 单 Activity，处理桌面来的 ACTION_NEW / ACTION_OPEN
│
├── core/                              ← ① 数据层 + ② 业务逻辑层（无 Android UI 依赖）
│   ├── model/Item.kt                  ← 领域模型：Item(kind, text, done, createdAt, updatedAt, doneAt)
│   ├── db/BitSayDb.kt                 ← SQLiteOpenHelper：建表 / 索引 / 版本历史
│   ├── db/SqliteItemStore.kt          ← ItemStore 的 SQLite 实现（全部阻塞式，调用方负责切线程）
│   ├── repo/ItemStore.kt              ← 存储抽象接口（单测用 FakeItemStore 实现它）
│   ├── repo/ItemRepository.kt         ← ★业务规则的唯一归属地（见 §4.1）
│   ├── backup/MiniJson.kt             ← 零依赖 JSON 读写
│   ├── backup/BackupCodec.kt          ← 备份 payload ↔ JSON（纯函数）
│   ├── backup/BackupManager.kt        ← 导出/导入用例 + SAF 文件读写（BackupFiles）
│   └── util/TimeText.kt               ← 时间文案（纯函数，注入 now/zone 便于测试）
│
├── ui/                                ← ③ UI 状态层 + Compose
│   ├── AppViewModel.kt                ← 全部 UI 状态（StateFlow<AppUiState>）+ 自动保存调度
│   ├── BitSayRoot.kt                  ← 根组合：屏幕切换 / 返回键 / 文件选择器 / Snackbar
│   ├── theme/{Color,Theme}.kt         ← 可爱风格调色板 + 不规则圆角
│   ├── components/Common.kt           ← 纸纹背景、CuteCard、分段切换、设置行…
│   └── screen/{ListScreen,EditorScreen,SettingsScreen}.kt
│
└── widget/                            ← 桌面小组件（只读 core，core 完全不知道它存在）
    ├── BitSayWidgetProvider.kt        ← AppWidgetProvider + WidgetUpdater
    ├── WidgetRenderer.kt              ← 组装 RemoteViews / PendingIntent
    ├── WidgetItems.kt                 ← Item → 行 RemoteViews（含 MAX_ROWS 上限）
    ├── WidgetPrefs.kt                 ← 每个 widget 的 kind 配置 + WidgetSize（尺寸档位）
    └── WidgetConfigActivity.kt        ← 放置小组件时的「显示笔记还是待办」配置页
```

**依赖方向严格单向**：`ui → core ← widget`。core 里没有一行 `android.widget` / `androidx.compose`。

### 4.1 业务规则住在哪（重要）

全部在 `ItemRepository`，**UI 和小组件都不允许自己实现这些规则**：

- 文本 `trim()`，空白拒绝，超长截断（`MAX_TEXT_LENGTH = 20_000`）
- `createdAt` 只在创建时写一次；`updatedAt` 每次真实修改才更新
- 勾选待办写 `doneAt`，取消勾选清空 `doneAt`
- 笔记和待办共用一张表但永不混排
- 排序：`done ASC, updated_at DESC, id DESC`（未完成在上，最近改动的在上）
- `saveDraft()`：**幂等的「边打字边存」**——见 §5
- `restore()`：合并时按 id 比对 `updatedAt`，**本地更新的记录不会被旧备份覆盖**
- `dataVersion`：每次真实写入 +1，小组件层订阅它自动刷新（业务层不知道小组件的存在）

### 4.2 命名说明

根目录同时存在 `AGENTS.md` 与 `PROGERSS.md`（用户指定的文件名拼写如此，**不要"纠正"成 PROGRESS**）。
`AGENTS.md` 是原始需求文本，`PROGERSS.md`（本文件）是活的交接文档。

---

## 5. 自动保存（用户明确追加的需求）

> 需求原文：「笔记在输入的时候，应该随输入自动保存，防止丢失灵感和重要信息」

实现位置：`core/util/WriteThrottle` + `AppViewModel.setDraft / flushDraft / persistDraft`
+ `ItemRepository.saveDraft`。

节流策略是**前缘 + 后缘**（`WriteThrottle`，窗口 `AUTO_SAVE_THROTTLE_MS = 400ms`）：

1. **第一个字立刻写库**（前缘）——新想法不会先在内存里等；
2. 之后**每个窗口最多写一次**，连续打字时定时落盘；
3. 每次按键都会重新排程，所以**最后一个字必定有自己的那次写**（后缘），最终内容不会漏；
4. `ON_PAUSE` 时立即 flush（`LifecycleEventEffect`，在 `BitSayRoot` 里）；
5. 文本没变则**完全不碰数据库**（不写、不 bump 版本、不刷新小组件）；
6. 编辑器底部显示状态：`正在输入…` / `✓ 已自动保存 · 刚刚`；
7. 底部按钮语义从「保存」改为「**完成**」——保存已经不需要用户操心了。

> 为什么不是纯 debounce：纯 debounce 下「第一个字」要等一整个窗口才落盘，而且连续打字期间
> **一次都不写**。前缘把最坏情况从「窗口 + 一段输入」压到「窗口」，最好情况（第一个字）是 0。
> 时序策略是纯函数，`WriteThrottleTest` 用事件序列做了确定性回放（如 10 次按键 → 写入
> `[0, 400, 800]`：前缘一次 + 每窗口一次 + 收尾一次）。

边界情况（已实现且有测试）：

- 新建的笔记，打完又全部删光再退出 ⇒ 自动删掉这条空记录；
- 已有笔记被清空后退出 ⇒ **保留最后一版非空内容**，并提示「内容为空」；
- 空白文本 + `id = 0` ⇒ 什么都不建。

**真机验证**：输入 "IdeaautosaveXYZ" 后直接 `am force-stop`（模拟划掉/崩溃），
重启 App 该条笔记完整存在（见 §11）。

### 5.1 输入法（软键盘）唤起策略

规则一句话：**键盘跟着「要写字」的意图走，不跟着「打开编辑器」走。**

| 入口 | 意图 | 自动唤起键盘 |
|---|---|---|
| App 内 FAB `+` | 创建 | ✅ |
| 桌面小组件 `+`（快捷记录） | 创建 | ✅ |
| 点搜索图标 / 小组件搜索按钮 | 搜索 | ✅（进入独立搜索页） |
| App 列表点条目 | 查看 | ❌ |
| 桌面小组件点条目 | 查看 | ❌ |

实现：

- `AppUiState.autoFocusEditor`（只有 `startNew()` 置 true）+ `editorSession`（每次进入编辑器自增）；
- `EditorScreen` 用 `LaunchedEffect(state.editorSession)` 做一次性 `focusRequester.requestFocus()`
  + `keyboard?.show()`；
- **为什么用 `editorSession` 当 key 而不是 `LaunchedEffect(Unit)`**：如果编辑器已经开着，
  再从桌面点 `+`（`singleTask` → `onNewIntent`），Compose 不会重建 `EditorScreen`，
  `Unit` 版本的副作用不会重跑，键盘就弹不出来；
- 搜索页：`SearchScreen` 自己拥有输入框，`LaunchedEffect(Unit)` 请求焦点并 show 即可
  （每次进入搜索页都是新组合）。

> 注意一个符合 Android 惯例的细节：键盘弹起时，**第一次按返回是收起键盘**，第二次才退出编辑器。
> 这是系统行为，不要用 `BackHandler` 去抢。

#### 5.1.1 「输入法卡死」的根因（微信输入法 · 已定位到可复现判据）

用户反馈：输入框聚焦后**偶现**键盘不弹出，一旦发生就**怎么点都不弹**；
**重装 App 也无效**，只有「切到别的 App 点几下输入框」才能恢复。

##### 判据：看 IME insets 源的高度

```bash
adb shell dumpsys window | grep -oE 'type=ime frame=\[[^]]*\]\[[^]]*\]' | head -1
```

| 状态 | InsetsSource(type=ime) | 键盘高度 | 截图 |
|---|---|---|---|
| **显示** | `frame=[0,1824][1260,2800]` | **976 px** | 键盘完整可见 |
| **隐藏** | `frame=[0,0][0,0]` | 0 px（`visibleFrame` 仍是上次的 1824） | 无键盘，正常 |
| **卡死** | `frame=[0,2799][1260,2800]` | **1 px** | 什么都没有 |

卡死态是**第三种**几何：既不是显示态的 976px，也不是隐藏态的 0，而是一条 1px 的退化底边。

脚本判定：`height = 2800 - top`，`<= 1` 且字段已聚焦、系统说 IME 已显示 → 卡死。

##### 机制

IME 的 insets 源被卡在**"隐藏态"的几何尺寸（1px）**上，但 `visible=true`、`mImeShowing=true`、
IMMS 的 `mInputShown=true`。于是：

- App 收到的是 1px 的 IME inset → 不 resize、`imePadding()` 也几乎为 0 → 布局不动；
- 系统认为"IME 已经显示了" → 后续任何 `showSoftInput` 都是空操作 → 只能靠别的窗口接管输入
  才会重算 insets。

**这个陈旧状态不在我们进程里**（重装 App = 杀进程，依然卡死），而在系统的 insets 控制器里。

##### 我踩过的坑（都是错的，别再重复）

以下字段在**正常状态和卡死状态下一模一样**，不能拿来判卡死：

- `InputMethodService` 的 `visibleTopInsets=2673` / `touchableRegion=SkRegion()`（这就是 WeType 的正常值）
- WM 的 `mCapturedLeash=… animation-leash of insets_animation`（正常时也挂在这个 leash 下）
- `mRequestedShowExplicitly=true` / `mInputShown=true` / `mImeWindowVis=3`
- `dumpsys input` 里 IME 窗口的 `inputConfig` 少了 `NOT_VISIBLE`（两者都有过）

另外两条**错误推断**，一并记下：

1. "WeType 给键盘窗口加了 FLAG_SECURE，所以截图看不到键盘" —— **错的**。
   `screencap` 能完整截到微信输入法（Chrome 截图里键盘、候选条、前往键全在）。
   **截图是可信的**：我们 App 截图里没键盘 = 真的没画出来。
2. "把 `keyboard?.show()` 去掉就好了" —— **错的**。去掉后 ImeTracker 里
   `SHOW_SOFT_INPUT_BY_INSETS_API` 依然出现（那是 Compose 自己在字段获焦时发的），
   请求模式没有任何变化。这次改动只是去掉一次**重复**请求，**不是修复**。

> 教训：判断"键盘在不在"必须**截图 + insets 高度**一起看；只看 `mInputShown` 一定会误判
> （卡死时它也是 `true`）。同样，不要拿"看起来可疑"的字段下结论 —— 先和正常态对比。

##### 仍未确定的部分

**触发条件还没找到**（偶现）。已知的相关性：

- 大多发生在**用 adb 自动化操作**的时候（`input keyevent BACK` 收键盘、快速切页、
  键盘动画期间 `am force-stop` / `adb install`），人手动操作时少见；
- 现象上像是一次 **show/hide 动画被打断**，insets 停在了隐藏态的几何尺寸上。

**下一步（按用户要求：先复现、再改，不许先打补丁）**：

**已证伪的触发假设（别再重复试）**：

| 假设 | 实验 | 结果 |
|---|---|---|
| 快速 show/hide 竞争 | 编辑器内「点输入框 → BACK」× 8 轮 | ❌ 全部正常 |
| 键盘开着时进程被突然杀掉 | 键盘升起后 `am force-stop` | ❌ 干净隐藏，重开正常 |
| `noHistory` 独立任务窗销毁 | 小组件悬浮窗（搜索页）开→BACK×2 关窗 × 3 轮 | ❌ 干净隐藏 |
| 跨页面快速切换 | 列表⇄搜索页 × 20 轮（各 0.25s） | ❌ 全部 `[0,1824]` |
| 悬浮窗关不掉 / HOME 无效 | 观测到残留窗口 | ❌ 误判（是用户手动打开的） |

**下一步**：ImeTracker 的历史缓冲**只有约 7 秒**（实测两次 dump 只覆盖 19:42:50–19:42:57），
所以必须**在卡死发生后的几秒内**抓 `dumpsys input_method`，否则回放不了。卡死时立刻抓，
重点看 `TYPE_SHOW/HIDE … STATUS_FAIL/TIMEOUT` 那几条的前后顺序。

**排查纪律（这轮踩过的坑）**：

1. **`mInputShown` 不能用来判断键盘在不在** —— 卡死时它也是 `true`。必须用上面的 insets 判据 + 截图。
2. **别拿"看起来可疑"的字段下结论** —— 先和确认过的正常态对比。这轮我把
   `visibleTopInsets=2673`、`touchableRegion=SkRegion()`、`animation-leash`、
   `mRequestedShowExplicitly` 全当成了"铁证"，结果**全都是正常值**。
3. **一次测量说明不了问题** —— 我至少三次因为 `sleep` 太短、launcher 还在切换动画里，
   就得出了错误结论（"HOME 关不掉悬浮窗""小组件点击无效"）。测量前先确认状态稳定。
4. **用户可能同时在手动操作手机** —— 观测到的状态未必是我的操作造成的。先问一句。
2. 复现成功后，再决定是「App 侧自愈」（检测到 `ime` inset ≤1px 而字段已聚焦时，
   主动 `hide(ime())` + `show(ime())` 触发重算）还是别的做法。

#### 5.1.2 现场原始存档

- 卡死态：`/tmp/ime2/im_wedge.txt`、`win_wedge.txt`、`input_wedge.txt`、`wedge.png`（无键盘）
- 正常态（同机同 App，恢复后）：`/tmp/ime2/after.png`（键盘完整）
- 正常态对照（Chrome）：`/tmp/ime2/chrome.png`
- 早期两次误判为"卡死"的存档：`/tmp/ime/{im,win,log}_stuck.txt`、`/tmp/ime/win_ok.txt`
  （后者其实**也是卡死态**，所以当时 diff 不出差异）

---

## 6. 数据库

`bitsay.db`，version 1，**WAL 模式**（读的小组件不会阻塞写的 App）。

```sql
CREATE TABLE items (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kind        INTEGER NOT NULL,          -- 0 = 笔记, 1 = 待办
    text        TEXT    NOT NULL,
    done        INTEGER NOT NULL DEFAULT 0,-- 仅对待办有意义
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    done_at     INTEGER
);
CREATE INDEX idx_items_kind ON items (kind, done, updated_at DESC);
```

**版本历史**：1 = 初版（单表 + kind 判别式）。以后加字段请写 `onUpgrade` 并在本节追加一行。

> 设计取舍：笔记和待办**共用一张表**（single-table inheritance）。
> 形状完全相同（一段文字 + 时间戳），共用后 DAO / 仓储 / 备份 / 小组件查询全部只有一份实现。
> 未来若要加「标签」「提醒时间」，再评估是否拆表。

---

## 7. 备份格式（schema 1，二进制 + gzip）

**文件 = gzip(自定义二进制 payload)**，默认名 `bitsay-yyyyMMdd-HHmm.bitsay`。

### 7.1 为什么是二进制，而不是 JSON

这是**实测**出来的结论，不是偏好。同一份数据（10 000 条真实感中文笔记）：

| 容器 | 原始 | gzip-6 | gzip-9 |
|---|---|---|---|
| 理论下界（只压正文文本） | 835 KB | 100.6 KB | **98.5 KB** |
| pretty JSON | 2613 KB | 241.5 KB | 233.7 KB |
| 紧凑 JSON + 省略默认值 | 1517 KB | 182.6 KB | 178.7 KB |
| TSV 纯文本 | 1238 KB | 189.4 KB | 187.1 KB |
| **打包 SQLite（`VACUUM INTO`）** | 1120 KB | 228.0 KB | 226.5 KB |
| ★ **本项目采用：varint + delta 二进制** | **868 KB** | **123.6 KB** | **121.2 KB** |

- 二进制**原始体积是 JSON 的 57%**，压缩后仍小 **32%**，离理论下界只差 23%；
- **「打包 db」是这几条路里最差的**（压缩后 226.5 KB > JSON 的 178.7 KB）——
  SQLite 的页头与二进制结构比纯文本更难被 deflate 吃掉。所以"直接打包 db"这个直觉是错的；
- 压缩仍然是最大的单一杠杆（868 KB → 121 KB，7×），但容器浪费掉的部分熵编码**变不回来**：
  JSON 每条记录都要重新拼一遍 `"createdAt":` 和一个 13 位时间戳，
  而二进制只花一个 varint 存**时间戳增量**、一个 bit 存 `done`。

**真机端到端**：同样 10 006 条数据，旧格式导出 183 601 B，新格式 **124 414 B（−32%）**，
与预测的 121.2 KB 吻合。

### 7.2 编码布局

```
"BSB1"                     magic（4 字节）
uvarint                    schema
uvarint                    exportedAt（毫秒时间戳）
uvarint                    记录数
每条记录：
  uvarint                  id 相对上一条的增量
  uvarint                  flags: bit0 done, bit1 有 updatedAt, bit2 有 doneAt, bit3 是待办
  svarint                  createdAt 相对上一条的增量（zigzag）
  svarint                  updatedAt - createdAt   （仅 bit1）
  svarint                  doneAt - createdAt      （仅 bit2）
  uvarint 长度 + 字节         UTF-8 正文
```

用的都是最老实的成熟技术（varint / zigzag / delta，即 protobuf 的做法），
但不引入代码生成器和运行时——为两列扁平数据不值得。

**解码是防御性的**：每个长度都要和「剩余字节数」对账，截断文件、外来文件、
荒谬长度都会抛 `BackupFormatException`，**绝不会半途导入一个残缺的库**。
（`BackupCodecTest` 覆盖了截断、伪造记录数、未来 schema、外来文件等情形。）

### 7.3 文件名、MIME 与选择器过滤

文件叫 **`bitsay-yyyyMMdd-HHmm.bitsay.gz`**，MIME **`application/gzip`**。

为什么不是光秃秃的 `.bitsay`：Android 的 `MimeTypeMap` 认不出自定义扩展名，
未知扩展名一律上报为 `application/octet-stream` —— 用它过滤等于**不过滤**，
导入选择器会把手机里所有不相干的文件都列出来（实测确实如此：浏览器备份、`.podownloading`
之类全都混在里面）。加一层 `.gz` 就落进了真实存在的 MIME 类型：

| | 导出文件名 | 导入选择器 |
|---|---|---|
| 之前 | `bitsay-….bitsay` | `application/octet-stream` + `*/*` → **列出所有文件** |
| 现在 | `bitsay-….bitsay.gz` | `application/gzip` → **只列出备份**（选择器标题也变成「压缩包」） |

代价：多三个字符。收益：迁移时不用在几百个文件里找自己的备份。

### 7.4 其余约定

- 正文全空白的条目在编码时丢弃
- `schema` 比当前大 ⇒ 明确报错「备份来自更新的版本」，不猜
- 导入两种模式：**合并**（默认，按 id 比较 `updatedAt` 取新）/ **覆盖**（清空重灌）
- **没有历史包袱**：App 未发布，不需要兼容旧的 JSON 备份（若将来要加，靠 magic 分流即可）

加字段时：`SCHEMA` 常量 +1，用掉一个空闲的 flags bit 或在记录尾部追加字段，并更新本节。

---

## 8. 桌面小组件

一个 provider：`BitSayWidgetProvider`（`res/xml/widget_info_bitsay.xml`，`xml-v31/` 里有增强版）。

| 能力 | 实现 |
|---|---|
| 显示笔记 / 待办 | 顶部做成 **笔记 / 待办 两个 tab**（替代原来的 ⇄ 单按钮）→ 发 `ACTION_SET_KIND`（带明确的 kind，不做 toggle）→ 写 `WidgetPrefs` → 重绘并滚回顶部 |
| 进 App 首页 | 顶栏右上角**应用图标**（便签+对勾，与桌面图标同一套视觉）→ `PendingIntent.getActivity(MainActivity, ACTION_SHOW_LIST)`，**强制落在列表页**，不是上次停留的页面 |
| 新建 | **右下角悬浮 +**（真的浮在列表之上，列表底部**不预留空白**，最后一行可以滑到按钮下面）→ 打开 `WidgetEntryActivity` |
| 点击查看 | 列表 template（**必须 `FLAG_MUTABLE`**）+ 行 fill-in intent → 广播 → 打开 `WidgetEntryActivity` |
| 勾选待办 | 行内圆圈自己一个 fill-in intent → `ACTION_ITEM_CLICK` + `ITEM_ACTION_TOGGLE_DONE` → `goAsync()` 写库并刷新 |
| 宫格缩放 | `onAppWidgetOptionsChanged` → `WidgetLayout.showHeader(minHeightDp, headerDp, rowDp)`：**装不下「header + 3 行」就收起顶栏**（阈值由 `dimens.xml` 真实尺寸算出，≈196dp）。最小尺寸 **2×2**（`minResize* = 110dp`），默认放置 3×2 |
| 数据联动 | `BitSayApp` 订阅 `repository.change`，防抖 120 ms 后 `WidgetUpdater.refreshAll(scrollToTop = 本次是否为新增)` |
| 行样式 | **单行、无日期、固定 48dp 行高**（`core/util/TextPreview` 把多行内容压成一行，换行变空格）—— App 内列表用同一套规则 |
| 触摸目标 | 行高、顶栏按钮、标题全部 **48dp**（Android/Material 最小触摸目标）；勾选圆圈的 glyph 只有 18dp，但它的**可点区域是 44×48dp** |
| 滚动条 | 3dp 圆角 thumb，**滚动时出现、停 1.2s 后 0.5s 淡出**（不常驻、不挡内容）；`outsideOverlay` + 3dp 尾部内边距保证显示时也不压住卡片 |

**踩过的坑（改小组件前必读）**

1. `setPendingIntentTemplate` 的 PendingIntent **必须带 `FLAG_MUTABLE`**，否则 launcher 无法把
   `setOnClickFillInIntent` 合并进去，点击完全没反应。
2. 行内子 View 的 `setOnClickFillInIntent`（勾选圆圈）**会生效**，但前提是这个 View 真的被点到——
   实测 `18dp` 的圆圈在桌面上触摸区域较小，用 adb 点偏一点就变成「打开 App」。
   如果后续觉得难点，把 `widget_item.xml` 里 icon 的尺寸或外边距调大即可。
3. 子 View 的点击最终靠 `RemoteResponse.handleViewInteraction()` → 要求祖先链上有 `AdapterView`
   且其 `tag` 是 template PendingIntent。所以**不要**把 ListView 换成非 AdapterView 的容器。
4. `RemoteCollectionItems.Builder.addItem(id, view)` —— **id 在前**，写反了编译期就报错。
5. 别再用 `notifyAppWidgetViewDataChanged`（Android 17 已废弃）；数据变了直接重新 `updateAppWidget`。
6. **RemoteViews 只允许调用带 `@RemotableViewMethod` 的方法**（`RemoteViews.getMethod()` 会直接
   抛 `ActionException`）。所以 `ListView.setSelection(0)` **不能用**；
   `ListView.smoothScrollToPosition(int)` 有该注解，是唯一可用的滚动手段。

### 8.1 「新建后看不到新条目」的根因（务必先读，别再打补丁）

现象：从桌面 `+` 记一条，回到桌面后小组件里看不到它。**小组件其实刷新成功了**——
数据一直在，只是**被顶到视口上方**。

根因链条（全部有 AOSP 源码依据，android-37）：

1. 更新时框架**复用** `RemoteCollectionItemsAdapter`，只调 `notifyDataSetChanged()`
   —— `RemoteViews.java` `SetRemoteAdapterItem.apply()`；
2. 数据变更回调 → `AdapterView.AdapterDataSetObserver.onChanged()` → `rememberSyncState()`；
3. `rememberSyncState()` 记录 **`mSyncRowId = adapter.getItemId(mFirstPosition)`**，
   `mSyncMode = SYNC_FIRST_POSITION` —— 即「把旧的第一可见行」当作滚动锚点，
   而且**没有 `hasStableIds` 守卫**；
4. `RemoteCollectionItems.getItemId()` 无条件返回我们传入的 Item id —— 锚点是稳定的。

⇒ 新条目插到 index 0 后，ListView 会把「原来那一行」重新钉在顶部，新条目留在视口上方。

修复：**只在「新增」时**回顶部，其它写入保持用户位置。

- `ItemRepository` 把原来单纯的 `dataVersion: StateFlow<Long>` 换成
  `change: StateFlow<DataChange>`，其中 `DataChange.insertedAt` 记录最近一次**插入**发生在哪个版本；
- `BitSayApp` 比较 `insertedAt` 是否比上次处理过的更新 → 只有新增才传 `scrollToTop = true`；
- `WidgetRenderer.render(..., scrollToTop)` 里发 `setInt(R.id.widget_list, "smoothScrollToPosition", 0)`。

**为什么不做成「任何变更都回顶部」**：勾选待办时用户在列表中部，跳回顶部会丢失上下文。
真机对比验证过：新增 → 回顶部；勾选 → 保持原位（见 §11）。

> ⚠️ 排查这类问题时不要靠猜。这次我先误判成「进程被杀导致刷新丢失 / 协程被异常打死」，
> 加了 `runCatching` 兜底和「退出前同步推送」——**全是错的方向，已全部移除**。
> 正确做法是先读 AOSP 源码确认框架行为，再动手。

### 8.2 为什么桌面入口用独立的窗口（`WidgetEntryActivity`）

**旧行为**：桌面 `+` / 点条目直接 `startActivity(MainActivity)`，两个后果：

1. `MainActivity` 是 `singleTask`。App 的任务若已在后台，会被**整个拉到前台**，先把它上一次的
   页面（列表）画出来，再走 `onNewIntent` 切到编辑器 —— 这就是「首页一闪而过」；
2. 退出后 App 的主任务仍留在后台 / 最近任务里。

**现行为**：`WidgetEntryActivity` —— **渲染的是和应用完全一样的界面**（直接复用
`BitSayRoot` → 同一个 `EditorScreen`、同一个纸纹背景，**没有遮罩、没有圆角卡片**）。
理由很实际：第二个"长得差不多但不完全一样"的编辑器，就是第二套 bug。

它只做两件不同的事，且只有这两件：

```xml
android:taskAffinity="com.del.bitsay.widgetentry"   <!-- 自己的任务，永远不拉 App 主任务 -->
android:excludeFromRecents="true"                   <!-- 不进最近任务 -->
android:noHistory="true"                            <!-- 离开即销毁 -->
android:theme="@style/Theme.BitSay"                 <!-- 和 App 同一个主题 -->
```

- `AppViewModel.leaveEditor(fromWidget = true)` **不再先切到列表页**——切了就会在 `finish()`
  落地前把列表画一帧，正是要消除的闪烁；现在直接发 `exit` 事件让窗口 `finish()`；
- `MainActivity` 不再处理 `ACTION_NEW / ACTION_OPEN`，只保留启动页 + 小组件的 `ACTION_SHOW_LIST`。

**真机验证**：退出后 `topResumedActivity` = launcher，`dumpsys activity activities` 里
**`WidgetEntryActivity` 出现次数为 0**（整个 widgetentry 任务消失，无残留窗口）。

---

## 8.5 性能与容量（真机压测数据）

测试机：vivo V2309A / Android 16；数据集：**13 005 条**（8 005 笔记 + 5 000 待办），
文本总量 **3 030 万字符**，含 3 000 条 10 000 字 + 5 条 20 000 字长文（DB ≈ 93 MB）。

| 指标 | 实测 |
|---|---|
| 冷启动到列表渲染 | **481 ms** |
| Dalvik Heap | **15.7 MB** |
| 滚动（8 轮上下滑，596 帧） | 卡顿 **0.34%**，p50 **11 ms** / p90 20 ms / p95 27 ms |
| 列表查询 8 005 行（带 `substr` 预览） | 33.6 ms |
| 列表查询 8 005 行（取全文，作对比） | 55.8 ms |
| 全文 `LIKE` 搜索（扫描 3 000 万字） | 378–391 ms |
| 按 id 取单条详情（2 万字长文） | ≈ 4 ms |
| 批量删除 8 005 条（分块 500/语句，一次事务） | 成功 |

### 8.5.1 三个关键设计

1. **列表只取预览**（`substr(text, 1, 200)`，`ItemStore.PREVIEW_CHARS`）。
   在此之前，同样的数据把 Dalvik Heap 顶到 **137 MB 且进程被系统杀掉**；
   现在稳定在 **15.7 MB 且与文本长度无关**（换 10 000 条短笔记时同样是 15.2 MB）。
   详情/编辑/备份仍然读完整文本（`findById` / `listAll`）。
2. **搜索走 SQL，不筛内存**。内存里只有 200 字预览，如果过滤内存，
   长文深处的内容会**静默搜不到**。`LIKE` 直接打在完整 `text` 列上，并转义 `%` `_`。
3. **编辑器自动保存不再全量 reload**。原来每次防抖写库都重查两张全表；
   现在 `saveDraft(reload = false)` 只 bump 变更信号（小组件照常刷新），
   关编辑器时 reload 一次。

### 8.5.2 已知特征（不是 bug，是取舍）

- **搜索是 `LIKE '%…%'` 全表扫描**，代价与「总文本量」成正比，任何索引都救不了中缀匹配。
  10 000 条普通短笔记时约 **3 ms**，3 000 万字极端数据时约 **380 ms**（叠加 180 ms 防抖）。
  真到不可接受时，下一步是 FTS5 + trigram 分词器（需 schema v2 + 迁移），当前不做。
- **不做 LIMIT/OFFSET 分页**：`LazyColumn` 本身只渲染可见项，内存已由预览封顶；
  再加一层分页只会增加状态而没有收益。这一点是刻意的，别再"补"上。

---

## 9. 签名（长期正式分发）

- keystore：`keystore/bitsay-release.jks`（PKCS12）
- 口令等：`keystore.properties`（**已在 .gitignore 里，不要提交到公开仓库**）
- 别名：`bitsay`；算法 RSA 4096 / SHA256withRSA
- 有效期：**36500 天（到 2126-09-07）**，足够长期分发
- 证书 SHA-256：`1F:64:48:51:0F:EE:FC:8D:CA:FD:FA:D9:86:20:17:65:AD:58:DE:8C:4F:28:85:93:D1:82:0B:8A:9B:56:73:2F`
- 签名方案：AGP 默认（minSdk 31 ⇒ **v2**，apksigner 已验证 `Verified using v2 scheme: true`）

> ⚠️ **`keystore/` 目录和 `keystore.properties` 必须单独备份**（网盘/U盘/密码管理器）。
> 丢了就永远无法给同一个包名发更新。轮换密钥要靠 v3 rotation，现在没配。
>
> 密钥**刻意不进 Git**（`.gitignore` 里的 `keystore/*` + `*.jks`）。
> 为了方便交接，`keystore/README.md`（入库）记录了别名、指纹、恢复步骤与备份清单；
> `keystore.properties.example`（入库）是口令模板。

### 9.1 仓库卫生（`.gitignore` / `.gitattributes`）

`.gitignore` 按八节组织：Gradle / Android 构建产物 / Kotlin / IDE / 签名与密钥 /
本机专有 / 参考资料 / 系统垃圾。几条容易漏的已经补上：

- **`.kotlin/`** —— Kotlin 2.x 会在项目根写会话与增量编译状态，之前漏了会脏化仓库；
- **`keystore/*`（而非裸 `keystore`）** —— 用 `keystore/*` + `!keystore/README.md`
  的写法才能既挡住密钥、又让说明文件入库（裸目录名会让 git 根本不进去）；
- **`/refs/`（锚定根目录）** —— 只忽略参考资料，不影响将来出现同名源码包；
- **刻意不写 `*.jar` / `*.aar`** —— 那会连 `gradle/wrapper/gradle-wrapper.jar` 一起干掉，
  clone 下来直接无法构建。

`.gitattributes` 锁死换行符：`gradlew` 强制 LF（CRLF 会让 macOS/Linux 上
`./gradlew` 报 `'sh\r': No such file or directory`），`*.bat` 强制 CRLF，
`.jks` / `.apk` 等标记为 binary。

**自查命令**（改完忽略规则后跑一遍）：

```bash
git check-ignore -v path/to/file   # 某文件为什么被忽略
git status --short -uall           # 有没有漏网之鱼
git add -A -n                      # 提交前预演，确认没有产物/密钥混进去
```

当前预演结果：**87 个文件全部是源码与文档，无任何构建产物、密钥或 `refs/`**。

---

## 10. 任务列表与完成情况

### ✅ 已完成

- [x] 技术选型 + 项目骨架（AGP 9 内置 Kotlin 的正确写法、Gradle wrapper、version catalog）
- [x] 长期发布签名（keystore + Gradle 签名配置 + apksigner 验证）
- [x] core/model + core/db：SQLite 建表、索引、WAL、`ItemStore` 抽象 + SQLite 实现
- [x] core/repo：`ItemRepository` 全部业务规则 + `saveDraft` 自动保存
- [x] core/backup：`MiniJson` + `BackupCodec` + `BackupManager` + SAF 读写
- [x] core/util：`TimeText` 相对时间文案
- [x] **101 个单元测试全绿**（repo 41 / codec 15 / archive 8 / manager 5 / time 10 / throttle 9 / preview 5 / widget 6）
- [x] ui 状态层：`AppViewModel`（StateFlow 单一状态源）+ 自动保存调度 + 生命周期 flush
- [x] Compose 四个页面：列表（笔记/待办分段 + FAB）、编辑器、设置、搜索
- [x] 桌面小组件全部交互（切换/新建/查看/勾选/缩放）+ 放置配置页
- [x] 应用图标（纯 vector 自适应图标，含 monochrome 主题图标）
- [x] 真机（Android 16 / API 36）全流程验证 —— 见 §11
- [x] release 包体积 **2.11 MB**，R8 + resource shrinking，签名并实机跑通
- [x] **i18n**：中英双语，默认跟随系统，找不到回落到英文（默认资源集）—— 见 §14
- [x] **亮暗色模式**：跟随系统 / 浅色 / 深色；小组件同样跟随 App 设置并在切换时刷新 —— 见 §15
- [x] 仓库卫生：完整 `.gitignore` + `.gitattributes` + `keystore/README.md` + 口令模板 —— 见 §9.1
- [x] 自动保存改为**前缘+后缘节流**（400ms），第一个字立刻落盘 —— 见 §5
- [x] 列表默认按**创建时间**排序（编辑/勾选不再让列表跳动）
- [x] 小组件行样式：单行、无日期、固定 48dp 行高
- [x] 小组件触摸目标全部提到 **48dp**（行高 / 顶栏按钮 / 标题）
- [x] 小组件滚动条：滚动时出现、停 1.2s 后淡出（不常驻遮挡）
- [x] 从桌面看详情 → 返回直接回桌面，不再落到 App 首页
- [x] 从桌面 `+` → 快捷记录：无完成/保存按钮，返回即存并刷新小组件
- [x] 设置页内「添加到桌面」（`requestPinAppWidget`）
- [x] **修复「新建后小组件看不到新条目」**（滚动锚点根因）—— 见 §8.1
- [x] **输入焦点策略**：新建 → 自动唤起输入法；查看已有条目 → 不唤起；搜索 → 展开即唤起 —— 见 §5.1
- [x] **桌面入口改为独立悬浮窗**：不再先闪一下 App 首页，退出后不残留任何窗口 —— 见 §8.2
- [x] 编辑器去掉「完成」按钮，完全依赖自动保存
- [x] 列表预览统一为**单行 + 省略号**（App 内列表与小组件共用 `TextPreview`）
- [x] 小组件改版：**笔记/待办 tab** + 右上角**进 App**（应用图标） + 右下角**悬浮 +**（浮在列表上，不占空白）
- [x] 桌面入口窗口改为**全屏、与应用内完全一致**（复用 `BitSayRoot`，无遮罩无圆角）—— 见 §8.2
- [x] 小组件最小尺寸 **2×2**；装不下「header + 3 行」时自动收起顶栏
- [x] 备份改 **二进制编码 + gzip-9**：实测比紧凑 JSON 再小 **32%**，比打包 db 小 46% —— 见 §7
- [x] **列表只取 200 字预览**，内存与文本长度解耦（137 MB → 15.7 MB）—— 见 §8.5
- [x] 搜索改走 SQL 全文，长文深处也能搜到
- [x] 编辑器自动保存不再全量 reload
- [x] **多选批量删除**（单事务，500 条一批）。多选顶栏只有「退出 / 已选 N 项 / 删除」：
      无全选、无批量 toggle 完成；多选时隐藏待办的完成状态圈，避免两个圆圈并排
- [x] **搜索独立成页**：`Screen.Search`，顶栏「返回 + 输入框」，小组件顶栏右侧搜索按钮对称入口
- [x] **搜索结果与首页共用同一套 list / 行样式**：`ui/components/ItemList.kt`，
      `ListScreen` 与 `SearchScreen` 都调它，不存在第二套「轻量行」—— 见 §16
- [x] **搜索页切分类不清空关键词**：同一个词换到另一个分类里重查 —— 见 §16

### ⚠️ 已知限制（不是 bug，是外部行为）

- **`requestPinAppWidget` 在 OriginOS（vivo）上会「假装成功」**：API 返回 `true`，
  但桌面既不弹确认框也不真的添加（实测小组件实例数不变）。
  该 API 在原生/Pixel 桌面上正常。因此设置页那句话保留手动兜底文案：
  「已请求添加到桌面；若桌面没有反应，请长按桌面空白处手动添加」。
  代码本身是对的，**不要为了这个再改逻辑**。
- 小组件被宿主（OriginOS）以约 **0.91 倍密度**渲染：声明 48dp 实测约 43.7 物理 dp。
  这是桌面自己的缩放，换桌面就不同，所以**保持声明的 48dp 不要补偿**。

### ⏳ 待办（按建议优先级）

- [ ] **UI/UX 细化（当前正在做）**：打开 `design/index.html`（可交互、单一样式，就是当前 App 的样子），
      在 `design/css/tokens.css` 上直接调整；定稿后回填 `ui/theme/*` 与 `res/drawable/*`。见 §13
- [ ] 备份：可选的「自动定期备份到 SAF 目录」（现在只有手动导出）
- [ ] 列表：单条分享为文本（长按多选 + 批量删除已完成）
- [ ] 待办：拖拽排序（真·手动顺序，现在固定按 updatedAt）
- [ ] 小组件：`android:configure` 已支持重配置，但没做「选择显示条数 / 字号」的细化
- [ ] 字体：目前用系统字体。若要更强的可爱风，可考虑打包中文手写字体子集（注意体积）
- [ ] CI / 版本号自动化（现在 `versionCode = 1`，手改）
- [ ] 深色模式人工走查（主题已写好，未在真机夜间模式逐屏检查）

---

## 11. 实机验证记录（Android 16 / vivo V2309A / API 36）

| 场景 | 结果 |
|---|---|
| `assembleDebug` / `assembleRelease` | ✅ |
| 真机安装（debug 与 release 双包共存） | ✅ |
| 冷启动、空列表、空状态文案 | ✅ |
| 新建笔记 / 待办、保存、列表排序 | ✅ |
| 中文、emoji 🌿、多行文本渲染 | ✅ |
| 待办勾选 / 取消勾选、删除线、置灰、沉底 | ✅ |
| **自动保存：输入后不按任何按钮，直接 force-stop，重启内容仍在** | ✅ |
| 编辑器状态文案（正在输入… / ✓ 已自动保存 · 刚刚） | ✅ |
| 搜索、设置页、统计数字 | ✅ |
| **导出备份 → Downloads/bitsay-YYYYMMDD-HHmm.json，内容正确** | ✅ |
| **清空数据库 → 导入备份（合并）→ 6 条记录 + id/kind/done/多行文本全部还原** | ✅ |
| 添加小组件到桌面（OriginOS：原子组件 → 应用插件 → 比特记） | ✅ |
| 小组件切换笔记↔待办 | ✅ |
| 小组件点击条目 → 打开对应编辑器 | ✅ |
| 小组件 `+` → 打开对应 kind 的空白编辑器 | ✅ |
| 小组件点圆圈勾掉待办 → 数据库 + 界面同步 | ✅ |
| 小组件随尺寸变化显示时间戳（EXPANDED 档） | ✅ |
| release 包（R8）启动 + 小组件 receiver 响应 | ✅ |
| 自动保存：前缘节流，第一个字立刻入库 | ✅ |
| 列表按创建时间排序；编辑旧条目不会把它顶到前面 | ✅ |
| 小组件行：单行、无日期、48dp 行高（实测触摸目标 ~44×48 物理 dp） | ✅ |
| 小组件滚动条：滚动时出现，3 秒后已淡出（前后截图对比） | ✅ |
| **新增后小组件自动回到顶部并显示新条目**（创建前列表停在列表中段） | ✅ |
| **勾选待办不会跳回顶部**，保持用户当前位置（前后截图对比） | ✅ |
| 从桌面看详情 → 返回落在桌面（`topResumedActivity=com.bbk.launcher2/.Launcher`） | ✅ |
| 从桌面 `+` 快捷记录：无完成按钮 → 返回桌面 → 条目已入库 → 小组件已刷新 | ✅ |
| 设置页「添加到桌面」入口出现且可点（API 返回已受理；见 §10 已知限制） | ⚠️ |
| **新建（App FAB）自动弹键盘**（`mInputShown=false → true`） | ✅ |
| **查看已有条目不弹键盘**（编辑器全屏可见，`mInputShown=false`） | ✅ |
| **点搜索图标 / 小组件搜索按钮：进入独立搜索页 + 键盘弹出** | ✅ |
| 搜索输入过滤生效（输入 zz → 「没有找到相关内容」） | ✅ |
| 小组件 tab 切换笔记/待办（写入 `kind_29`，列表随之切换） | ✅ |
| 小组件右下角悬浮 + → 打开 `WidgetEntryActivity`（顶层即悬浮窗，无首页闪烁） | ✅ |
| 小组件点条目 → 悬浮窗查看（无键盘、无完成按钮） | ✅ |
| **退出悬浮窗后顶层为 launcher，且无任何 bitsay ActivityRecord 残留** | ✅ |
| 小组件右上角 ↗ → 进入 App 列表页（首页） | ✅ |
| 悬浮窗内新建 → 返回桌面 → 条目已入库且小组件已自动刷新 | ✅ |
| App 列表预览为单行省略（「会议纪要1. 确定 Q4 目标 2. 排期评审 …」） | ✅ |
| 桌面入口窗口全屏、无遮罩无圆角，与应用内编辑器完全一致 | ✅ |
| 退出入口窗口后 `WidgetEntryActivity` 残留数 = 0 | ✅ |
| 小组件 + 悬浮在列表之上，底部无预留空白（最后一行可滑到按钮下） | ✅ |
| 小组件顶栏应用图标（便签+对勾）渲染正常 | ✅ |
| **小组件缩到 145dp：`widget_header` 从视图树中消失**；放大到 235dp：顶栏回来 | ✅ |
| 13 005 条 / 3 030 万字下冷启动 481 ms、Dalvik 15.7 MB、进程存活 | ✅ |
| 该数据集下滚动 596 帧仅 0.34% 卡顿 | ✅ |
| 长按卡片进入多选；逐条勾选计数正确（1 → 3 项） | ✅ |
| 多选顶栏只含「退出多选 / 已选 N 项 / 删除」三项 | ✅ |
| 批量删除 8 005 条（分块 500/语句，一次事务） | ✅ |
| 导出 `.bitsay`（二进制+gzip）：10 006 条 → 124 414 B（旧格式 183 601 B） | ✅ |
| 覆盖导入还原 10 006 条，`SUM(LENGTH(text))` 与导出前完全一致 | ✅ |
| 导出文件名 `.bitsay.gz`；导入选择器只显示备份文件（无关文件被过滤掉） | ✅ |
| 用新扩展名/mime 走完整往返：导出 → 选择器选中 → 合并导入 6 条 | ✅ |
| 语言默认跟随系统（设备 zh-CN → 中文界面） | ✅ |
| 切到 English → 全界面英文；force-stop 重启后仍是英文（已持久化） | ✅ |
| 切回「跟随系统」→ 恢复中文，偏好键被移除 | ✅ |
| **系统中文 + App 设为 English → 小组件立即显示 Notes / Todos**（无需其它操作触发） | ✅ |
| 切回「跟随系统」→ 小组件立即恢复 笔记 / 待办 | ✅ |
| 主题默认跟随系统（系统浅色 → 浅色界面） | ✅ |
| 切「深色」→ App 全部页面深色；设置页概览卡对比度正常 | ✅ |
| **系统浅色 + App 设为深色 → 小组件立即变深色**（无需其它操作触发） | ✅ |
| 切回「跟随系统」→ App 与小组件同时恢复浅色，偏好键被移除 | ✅ |
| 小组件顶栏搜索按钮位置与「进 App」左右对称，点击进搜索页 | ✅ |
| **搜索结果行 = 首页行**（同色系卡片 + 创建时间，`ui/components/ItemList.kt` 单一实现） | ✅ |
| **搜索「Q4」→ 笔记分类 1 条命中；切「待办」关键词仍在、0 条命中（「没有找到相关内容」）；切回笔记命中恢复** | ✅ |
| 搜索页空关键词 → 显示「输入关键词搜索笔记和待办」，切分类不崩 | ✅ |
| 导入后 done / doneAt 一一对应（5000 待办中 1281 完成、1281 个 doneAt） | ✅ |

未验证 / 待补：小组件**手动拖拽缩放**的中间档位逐级走查（只验证了当前档位的渲染结果与 `WidgetSize` 单测）、
深色模式逐屏、Android 12~15 真机（本机只有 Android 16，只能靠 minSdk 与实际 API 使用面推断）。

---

## 12. 下一步建议（给接手的 agent）

1. **先跑一遍 §2 的构建命令**，确认 63 个测试全绿、release 能出包。环境问题优先解决 `JAVA_HOME`。
2. **UI 方向**：打开 `design/index.html`（浏览器直接打开，无需构建、无需起服务器；`?dark=1` 进深色）。
   只有一份样式，旋钮集中在 `design/css/tokens.css`；改样式先看 `design/README.md` 的分工表。
   用户明确要求「先在 HTML 上预览调整验收，确定之后再迁移到 Android」。
   定稿后按 §13 的映射表回填。
3. **没做完的功能**在 §10「待办」里，按用户的实际反馈排序，不要自己加需求。
4. 改任何业务规则，**只改 `ItemRepository`**，然后补 `ItemRepositoryTest` 的用例。
   不要为了图快把规则写进 ViewModel 或小组件。
5. 改小组件前，务必先读 §8 的「踩过的坑」。
6. 每次阶段性完成，回来更新 §10 / §11 / §12 和顶部的「最后更新」。

---

## 13. 设计台 → Android 映射表

设计源不再是单张 HTML，而是一个**可交互的设计台工程** `design/`（详见 `design/README.md`）。
用浏览器直接打开 `design/index.html` 即可，**不需要起服务器**（普通 `<script>`，`file://` 能跑）。

### 13.1 设计台里有什么

一个页面看完整 App，**所有视图共用同一份数据**（在任一处勾掉一条待办，其它视图立刻跟着变）：

| 区块 | 内容 |
|---|---|
| ① 可交互真机演示 | 列表 ⇄ 编辑器 ⇄ 搜索 ⇄ 设置 真的能走通 |
| ② 全部页面总览 | 7 台手机：笔记 / 待办 / 多选 / 编辑器（查看）/ 编辑器（快捷记录）/ 搜索 / 设置 |
| ③ 桌面小组件 | 真实桌面（壁纸 + 图标列 + Dock）+ 小组件，2×5 / 2×3 / 2×2 三种尺寸 |
| ④ 空状态 | 列表空 / 搜索空 / 小组件空 —— 都是文字 + 一株线描嫩芽，**没有吉祥物** |
| ⑤ 应用图标 | 墨底两个方向共 7 个方案，每个都给 108 母版 / 三种裁切 / 48px / 24px |
| ⑥ 组件库 | 卡片、分段 tab、图标按钮、勾选圈、空状态 |
| ⑦ 设计令牌 | 与 `Color.kt` 一一对应的色板（切亮暗会跟着变） |

**只有一份样式**（= 当前 App 的样子），没有多套主题。调观感优先改 `design/css/tokens.css`；
单页微调去 `design/css/pages/<页面>.css` 顶部的「本页可调参数」；深色只在 `design/css/dark.css` 里替换 token。

### 13.2 文件分工（改东西先看这张表）

| 想改什么 | 改哪里 |
|---|---|
| 颜色 / 圆角 / 间距节奏 / 字号 | `design/css/tokens.css` |
| **某一个页面**的样式 | `design/css/pages/<页面>.css` 顶部的「本页可调参数」 |
| 跨页面组件（卡片 / tab / FAB） | `design/css/components.css` |
| 深色模式 | `design/css/dark.css`（只替换 token，页面样式不动） |
| 界面图标（14 个线性图标） | `design/js/icons.js` |
| **应用图标**方案 | `design/js/appicon.js`（+ `design/css/appicon.css`） |
| 空状态那株嫩芽 | `design/js/sprout.js` |
| 数据规则（排序 / 搜索 / 增删） | `design/js/store.js` |

### 13.3 应用图标的底色：一律墨黑

这一条是被用户否掉两轮之后定下来的，别再回去试：

- **纸色底（`SAND #F2EDE4`）全部作废** —— 用户原话：「纸色背景不好看」；
- **绿底作废** —— 绿色成了品牌色，和 App 内「绿只给植物」的规则打架。

由此推出一条硬规则：**任何新图标方案，先过"放在墨底上还看不看得见"这一关。**
石缝探芽原来用墨色石头配纸底，换墨底后石头直接消失 —— 必须改成石灰色
（`STONE #55534C`）、裂缝改用更暗的墨线压出来，才读得出是"裂开的石"而不是"一丘土"。

两个方向并存：**C 族**（`C` 基准 / `C1` 亮芽 / `C2` 描边 / `C3` 圆土 / `C4` 特写 / `C5` 破土）
是"土上发芽"的六个身位，**`G`** 石缝探芽换叙事。几何坑（闭合的土、V 形缝、
倾斜椭圆叶、石头要有棱角）记在 `design/README.md` 的「这一族踩过的坑」。

### 13.4 图标已定稿：`C5` 破土（2026-10）

用户敲定，**已落地**：

| 文件 | 内容 |
|---|---|
| `drawable/ic_launcher_background.xml` | 纯墨底 `#17140F`（旧的黄色底 + 两道手绘波浪线已删） |
| `drawable/ic_launcher_foreground.xml` | 两瓣土 `#2C4A35` + 茎/叶 `#7FC98F`，几何与 `design/js/appicon.js` 的 C5 逐字一致 |
| `drawable/ic_launcher_monochrome.xml` | 主题图标层（Android 13+），**土合并成一整丘** |
| `drawable/ic_widget_open_app.xml` | widget 顶栏「进入 App」，同一几何缩 0.38 到 24dp，单色 `#3D3A38` |

**`ic_widget_open_app.xml` 是最容易漏的一个** —— 它原来画的是便签+对勾（旧黄色图标那套视觉），
品牌换了它不换，就会出现"桌面是芽、点进 App 的按钮是便签"。设计台 `js/icons.js` 的 `openApp`
已同步成同一份几何。

**实机已验证**：debug 包装机后，系统「应用信息」页渲染的自适应图标正确（墨底 + V 缝两瓣土 + 亮绿芽，圆角方遮罩正常）。

**单色场景为什么合并土丘**：定稿的 V 形缝宽 5 单位，缩到 24dp 只剩 1.9，读起来不像"缝"而像毛刺。
彩色前景保留缝，`monochrome` 和 widget 两个单色场景合并。

### 13.5 顺手修掉的既有缺陷：widget 图标在暗色下不可见

改 widget 品牌图标时发现的，**不是这次改出来的**：

- `WidgetRenderer.kt:60` 会按**应用主题**把背景切成 `widget_bg_dark`（`#2C2A27`）；
- 但三个顶栏/悬浮图标（`ic_widget_open_app` / `ic_widget_search` / `ic_widget_add`）都是写死
  `#3D3A38` 的静态 vector，**从不染色** —— 对比度 **1.26:1**，等于隐形。

**根因**：RemoteViews 布局由宿主用**它自己的配置** inflate，所以资源驱动的颜色跟不了应用主题；
而这些图标偏偏是资源驱动的。

**修法**：`views.setColorStateList(id, "setImageTintList", …)`，颜色取现成的 `palette.ink`
（亮 `#3D3A38` / 暗 `#F2EDE4`）。这个 API 的可行性是**查 AOSP 源码核实过的**，不是凭记忆：
`ImageView.setImageTintList` 带 `@RemotableViewMethod`（`android/widget/ImageView.java`），
而 `RemoteViews.java` 在 apply 时用 `isAnnotationPresent` 强制校验 ——
**`setColorFilter` 没有这个注解，用了会抛 `ActionException`。**

列表行里的勾选圈（`#8A8279`）和已完成态（绿色）在暗底上是 3.85:1，合格，**没动**。

**视觉确认已完成**：用户桌面上本就放着一个小组件，暗色截图确认三个顶栏图标 + 行勾选圈都正常。

### 13.6 设计语言迁移到 Android（2026-10）

依据 `design/css/tokens.css`（唯一真源）把整站观感搬到 Compose。**颜色不再有第二处定义**：
`values/colors.xml` 只剩 XML inflate 需要的兜底（widget / 启动窗口），运行期一律走 `BitSayPalette`。

| 改动 | 说明 |
|---|---|
| `Color.kt` 全量替换 | 冷灰底 `#EDF0F8` / 统一白卡 / 纯黑强调 `#000000`（暗色翻 `#FFFFFF`）/ 叶绿 `#8CA487` |
| 删掉粉彩轮转 | `cards`/`todoCards`/`sun`/`mint`/`sky`/`lilac`/`done` 全部移除，条目只有 `card` 与 `cardDone` |
| `Theme.kt` 圆角 | `CardShape` 26/16/26/16、`SmallShape` 17/11/17/11、`BlockShape` 22/14/22/14（`CuteShape*` 更名） |
| `Theme.kt` 字体 | 大标题走 `FontFamily.Serif`（中文落到 Noto Serif CJK，**不打包字体**） |
| `PaperBackground` | 去掉点阵，改天光渐变；**暗色下天光归零**（`glow` 令牌，近黑底上盖 92% 白会洗成灰） |
| `AppCard` | 去掉 1.5dp 手绘描边 —— 黑白灰体系里那条边成了全屏最响的东西 |
| 空状态 | 新增 `Sprout` 组合项（`PathMeasure` 逐段画线 + 缓出），文案拆成 `_title`/`_hint` 两键 |

**动画时长改过两次**（用户反馈太慢）：1150ms → 640ms → **320ms + ease-out**。
关键不只是时长：线性 dash 推进起步几乎不动，那才是慢的来源；改缓出后同样时长利落得多。

### 13.7 这一轮修掉的既有 bug：列表被残留搜索词过滤

`AppViewModel.closeEditor()` 把屏幕切回列表，但**没清 `query`**。从搜索结果进编辑器再返回，
列表就一直在渲染 `searchResults` —— 表头写 4 条待办、正文却是空状态。修法是离开编辑器时一并清空。
旧代码只是把这个矛盾换成了「没有找到相关内容」的文案，同样是错的。

### 13.8 图标与小组件的收尾修正

用户逐项验收后的一轮：

| 项 | 处理 |
|---|---|
| app 图标 | `ic_settings` 从 12 尖星形换成 **6 齿**齿轮（8 齿在 21dp 下糊成一片）；`ic_theme` 补闭合；`ic_language` 从两条横线换成真地球仪；back/search/close/delete/check/plus/export/import 全部按 `design/js/icons.js` 重画 |
| widget FAB | 底色改为按 `palette.accent` 染色 —— 它的 drawable 写死 `@color/accent`（永远黑），暗色下会变成黑底 + 近黑加号 |
| widget tab | 40dp/18dp 圆角 → **34dp/17dp 真药丸** |
| widget 勾选圈 | 字形盒 28dp → 22dp（画出来约 16dp），不再高过 14sp 文字；行左右留白 6/8 → 14/14 |
| 页面标题 | **全站统一 21sp**（见下） |
| 设置页 | 去掉「概览」「桌面小组件」；顺序改为 **关于 → 外观 → 备份与恢复**；图标盒 30→32dp、标题 17→15sp、说明 12sp，两行文本块与图标配平 |
| 搜索框 | 去掉框内放大镜（只把占位文字往右推，没有收益） |

⚠️ **一条未能确认**：用户反馈小组件 item 点击有水波纹。查 AOSP 源码后确认
`AppWidgetHostView` 与 `RemoteViewsAdapter` **都不添加水波纹**，所以它来自启动器；
已在 `widget_item.xml` 上加 `android:foreground="@null"` + `android:stateListAnimator="@null"`
两个标准关闭开关，**但没能实机确认这就是来源**，需用户复核。

### 13.9 页面标题：**两档**，不是一档

一度想把全站标题统一成 21sp（因为编辑器和设置当时一个 38sp、一个 21sp，来回切很跳）。
**改错了对象** —— 用户说的「笔记和待办页面」指的是**编辑器**（它的标题就是"笔记"/"待办"），
不是首页。首页那个 38sp 大标题本身是对的。

| 页面 | 字号 | 对齐 |
|---|---|---|
| 首页（列表） | **38sp** `DisplayStyle` —— 全 App 唯一的大字 | 顶端对齐，图标贴标题上沿 |
| 编辑器 / 设置 | **21sp** `PageTitleStyle` | 垂直居中，和 40dp 返回键同一量级 |

**实机实测**：首页标题 191px、编辑器 105px、设置 105px（3.5px/dp）。191/105 = 1.82 = 38/21 ✓

`EditorScreen` 原来用的是 `MaterialTheme.typography.titleLarge`（隐式继承），已改为显式 `PageTitleStyle`，
免得以后改 Typography 又把它带跑。

### 13.10 widget 勾选圈的光学对齐

用户反馈勾选圈前面空白太多。根因是**两段留白叠加**：

```
行 paddingStart 14dp  +  字形盒 34dp 里 22dp 字形的居中缝 6dp  =  圆实际落在 20dp
```

而笔记行的文字在 14dp —— 差了 6dp。

改法：**左内边距交给勾选圈自己出**。行 `paddingStart=0`，勾选圈 `paddingStart=11dp / paddingEnd=9dp`，
宽 42dp → 内容盒正好 22dp。

但 11 而不是 14：矢量里的圆只有 viewBox 的 72%（`r=8.6/24`），22dp 的字形盒画出来只有 **15.8dp** 的圆，
所以 `11 + 3.1 = 14.1dp` 才让**圆看得见的左边缘**和笔记文字对齐。按 14 去 pad 对齐的是"盒子"，
圆看起来仍然是缩进的。

⚠️ **笔记行必须在代码里补内边距**（`setViewPadding`，像素重载 —— dp 重载带 `@FlaggedApi` 不能依赖），
而且**两种行都要显式设**、不能做缓存：启动器会回收 item view，笔记行用过的视图可能被待办行复用，
沿用 14dp 又会把圆推回去。

### 13.11 去掉衬线 · 首页标题改用品牌标记（2026-10）

**衬线全部移除。** 原来是 `FontFamily.Serif`（中文落到 Noto Serif CJK）只给页面标题用。
问题不在好看与否，而在**它是另一个字族**：Android 上衬线中文和系统无衬线并排时，
页面标题和正文看起来不像同一个 App，也和桌面/设置等系统界面不一致。
`--font-display` 直接指向正文字体栈，层级交给字号 + 字重。

**页面标题 21sp / ExtraBold**（原来是 Bold）。系统中文在 21sp 下 Bold 仍偏轻，
和旁边 20dp 的图标字形不配。

**首页标题：试过换成品牌标记，已回退。** 试了一版把首页标题从"笔记/待办"文字换成
`BrandMark`（无墨底的彩色破土），用户看后觉得怪，回到文字大标题。

结论：**首页标题就是 38sp 文字，`DisplayStyle` 保留**（系统字体、Bold —— 38sp 下 ExtraBold
会把中文的字怀挤住，小一号的 21sp 页面标题才用 ExtraBold）。

品牌标记本身没浪费：`BrandMark` 现在给设置页「关于」里的圆角应用图标用
（`AppIconPlate` = 墨色圆角底板 + 标记）。

**页面标题 21sp / ExtraBold**（原来是 Bold）。系统中文在 21sp 下 Bold 仍偏轻，
和旁边 20dp 的图标字形不配。

`--fs-display` 三件套在设计板里已收敛为 `--fs-title-page`。

### 13.14 widget 水波纹：一次错误结论（留作反面教材）

> ⚠️ **这一节和 13.16 的结论都是错的，真正的根因见 13.18。**
> 保留在这里，是因为它记录了一个**重复犯的错**：在没定位到根因之前就改代码。

当时的结论是"宿主把水波纹放进 item 的 foreground"，理由是
`View.setForegroundTintList` 带 `@RemotableViewMethod`。**这个推理是错的** ——
那个注解只说明"foreground 可被染色"，不说明宿主动了 foreground。
据此加的透明染色根本没起作用。

**教训**：在 `RemoteViewsAdapter` / `AppWidgetHostView` 里搜不到水波纹
**≠** 水波纹在宿主里。我当时只查了这两个类就跳到"是启动器干的"，然后开始加各种关不掉的补丁。
**应该先把"这个 View 到底从哪来"整条链走完** —— 集合视图的布局 XML 是我自己的，
`listSelector` 就写在我自己的 `ListView` 上。

### 13.16 widget 水波纹：第三次尝试（已撤销）

> ⚠️ **结论同样是错的，见 13.18。** 当时猜"启动器把背景 drawable 包进了 RippleDrawable"，
> 于是在 apply 时用 `setBackgroundResource` 把背景盖回去。
> **这段代码已经删掉了** —— 根因不是这个，留着就是乱打补丁。

### 13.17 作者信息 · 危险色 · 标题回 Bold

- 作者行改为 `Del Wang` + `https://del.wang`；源码行补全 `https://github.com/idootop/bitsay`
  （展示用的文案带上 scheme —— 这一行是给人读和手敲的）
- **新增 `danger` 令牌**（亮 `#D23B2E` / 暗 `#FF6B5C`），确认删除的按钮改用它。
  13.15 里我按"全站只有一个响色"的原则用了强调色，用户要求改红 —— 记录一下结论：
  **删除是全app 唯一不可撤销的动作，值得第二个颜色**；danger 不做任何装饰用途。
- 首页标题 `DisplayStyle` ExtraBold → **Bold**（38sp 下 ExtraBold 会把中文字怀挤住）。
  这一条来回改过两次，最终值就是 Bold。

### 13.18 widget 那圈橙色：真正的根因与两个必要条件

**现象**：列表**第一行外面一圈橙色描边**（用户描述为"激活背景色变成橙色"）。

**根因：`ListView` 的 `listSelector`。**
集合视图 `@id/widget_list` 就在我自己的布局里（`setRemoteAdapter` 只是往里塞数据）。
`AbsListView` 从**它自己的布局属性**读 selector，并在按下时画在被按的那一行上：

```
AbsListView.java:986
    final Drawable selector = a.getDrawable(R.styleable.AbsListView_listSelector);
    if (selector != null) { setSelector(selector); }
```

不写这个属性 ≠ 没有 selector，会回落到主题的 `?android:attr/listSelector` ——
在这台机器上就是那圈橙色描边。

#### 为什么第一次改成 `@null` 完全没用

两个**互相独立**的原因，缺一不可：

**① `@null` 解析出的是 null Drawable，而 `setSelector` 只在非 null 时才被调用。**

```java
Drawable mSelector;                       // 字段没有初始值 = null
if (selector != null) { setSelector(...) }  // ← @null 时这行不执行
```

于是 `mSelector` 保持主题 style 塞进去的那个橙色 drawable。
→ 必须用**非 null 的透明 drawable**：`@android:color/transparent`。

**② 启动器复用了已经 inflate 的视图树。**

```java
// AppWidgetHostView.inflateAsync()
if (!mColorMappingChanged && remoteViews.canRecycleView(mView)) {
    ... reapplyAsync(mContext, mView, ...)   // ← 复用旧树，只回放 actions
}
```

`canRecycleView` 比的是 **layout 资源 id**。只改同一个布局文件的**内容**，id 不变，
启动器就继续用第一次 inflate 的那棵树 —— 新属性**根本没被读到**。
→ 必须**改 layout 的资源 id**：把文件重命名（`widget_bitsay.xml` → `widget_bitsay_v2.xml`）。

**修复**：

| 文件 | 改动 |
|---|---|
| `res/layout/widget_bitsay_v2.xml` | 由 `widget_bitsay.xml` 改名 —— 制造新 id，强制重新 inflate |
| 同上，ListView 上 | `android:listSelector="@android:color/transparent"`（非 null） |
| `WidgetRenderer.kt` / `xml*/widget_info_bitsay.xml` | 引用同步到新 layout |

⚠️ **`_v2` 这个名字是有功能的，不是没整理干净。** 以后再改这个布局的**内容**（不是加控件而是改属性），
只要想在已放置的组件上生效，同样需要换一个新 id。理由已写在文件头注释里。

#### 同时撤销了前三次全部改动

- `widget_item.xml` 的 `android:foreground="@null"` / `android:stateListAnimator="@null"` → 删
- `WidgetItems.kt` 的 `setForegroundTintList(TRANSPARENT)` → 删
- `WidgetItems.kt` 的 `setBackgroundResource(...)` 重设 → 删

`WidgetItems.kt` 里现在只剩两个**有实际功能**的染色：勾选圈颜色（`setImageTintList`）和
行底色（`setBackgroundTintList`）—— 这两个是需求，不是补丁。

### 13.19 首页 tab 可左右滑动 + 指示块跟随滚动

`ListScreen` 的列表区改成 `HorizontalPager`（两页 = 笔记 / 待办）。state 里本来就有
`notes` 和 `todos` 两个列表，所以相邻页能渲染真实内容，不用等状态追上来。

#### 踩到的 bug：单向可滑

第一版只有**左滑有效、右滑无效**，现象是标题写着"待办"、列表却是笔记。

原因是我把 `state.tab` 直接读进了 `LaunchedEffect(pagerState)` 的闭包里：

```kotlin
LaunchedEffect(pagerState) {                       // 只在 pagerState 变化时重启
    snapshotFlow { pagerState.settledPage }.collect { page ->
        if (tabs[page] != state.tab) onSelectTab(tabs[page])   // ← state 是首次组合时捕获的
    }
}
```

这个 effect **只启动一次**，所以里面的 `state.tab` 永远是第一次组合的值（`NOTE`）。
于是左滑 `TODO != NOTE` 成立、触发；右滑 `NOTE == NOTE` 不成立、不触发。

修法是 `rememberUpdatedState(state.tab)`（`onSelectTab` 同理）。**凡是 key 不是某个 state
的 LaunchedEffect，里面读 state 都要走 rememberUpdatedState** —— 这类 bug 不会崩，
只会让某个方向悄悄失效。

#### 指示块跟随滚动进度

`SegmentedTabs` 从"每个 item 各自带背景"改成"一个独立指示块按进度位移"：

- 新增 `position: Float?` 参数，传 pager 的连续位置
  `pagerState.currentPage + pagerState.currentPageOffsetFraction`
- 传 `null` 时回落到 `selected`（搜索页没有 pager，用这种）
- item 之间**不留间距**：位移量就是"段宽 × 序号"，有间距的话行程会随序号变
- 文字颜色按 `at > 0.5f` 切换：拖动到一半时两个标签都处于半亮，只按 `selected` 切会闪

**实机验证**：抓了滑动过程中的连续帧，指示块随拖动右移；拖动不过半程时正确回弹。

### 13.20 图标：设置齿轮 · GitHub · 链接 · widget 线性芽

| 图标 | 说明 |
|---|---|
| `ic_settings` | 换成用户指定的圆齿齿轮（外圈 evenodd 路径 + 中心圆，2px 描边） |
| `ic_github` | GitHub 章鱼，**填充**而非描边 —— 19dp 下章鱼轮廓糊成一团，而且这个形状大家认的就是填色 |
| `ic_link` | 关于里 Del Wang 那一行用链环；源码那一行换章鱼 |
| `ic_widget_open_app` | 从"实心破土标记"换成**线性镂空芽**（两叶一茎，只描边）。它旁边的邻居是 2px 描边的搜索图标，实心的双色标记放在一起像贴上去的贴纸 |

widget 线性芽的几何取自 `design/js/sprout.js`（64×88 视框）缩放进 24，**描边保持 2 个视框单位不跟着缩** ——
否则渲染出来只有 0.46dp，和搜索图标不是一个重量级。

### 13.21 设置里的应用图标对齐桌面图标

`AppIconPlate` 原来把标记画成 66% 高，比桌面上看到的小一圈。

关键几何是**自适应图标的可见区**：108×108 画布只保证中间 72×72 可见，启动器就是裁这块再套遮罩。
标记是 40×50 居中，所以在桌面上它占图标高度的 `50/72 = 69.4%`。
改成按这个比例画，两处才一致。

⚠️ 仍然是**自己画**而不是 `painterResource(R.mipmap.ic_launcher)` —— `AdaptiveIconDrawable`
没有自带遮罩，直接画出来是个大方块、内容缩在中间一圈空白里。

### 13.22 搜索页也做成滑动联动

首页那套 pager 复制到搜索页。但搜索页多一个前提：**结果原本只存当前 tab 的**，
分页必须两边的结果都在手，否则相邻页是空的、滑过去要等重查。

所以 `AppUiState` 把 `searchResults` 拆成 `searchNotes` / `searchTodos`：

- `runSearch()` **一次查两个 kind**（本地 SQLite 的 LIKE 扫描，代价可以接受）
- `searchResults` 变成按 `tab` 取值的派生属性，`visible` 不用改
- `selectSearchTab` 因此**不再需要重查**，切 tab 变成瞬时的 —— 这也是"跟手"的前提
- 结果回来时仍然只按 `query` 校验（不再按 tab），因为两个 kind 都取了

**实机验证**：搜 "1" 后左滑到待办页显示"没找到"、右滑回笔记页显示命中行，
每页渲染各自的结果 ✓

`SegmentedTabs` 全项目只有两个使用点（首页 / 搜索页），已都改成 pager 驱动。

### 13.23 关于页图标缺角的根因

现象：设置里「关于」的圆角图标，底下**右半块石头缺一个角**；桌面图标是完整的。

根因是我那个土壤辅助函数把两个瓣当成同一种形状处理了，而它们**不是**：

```
左瓣  M34,79    C34,71 41,67 50,66   L52.5,79   Z   -- 先曲线，后直线
右瓣  M57.5,79  L60,66  C69,67 74,71 74,79    Z   -- 先直线，后曲线
```

我写的 helper 固定"moveTo → cubicTo → lineTo → close"。对左瓣正确，对右瓣就错了：
它从底部顶点直接弯向 (74,71)，**从来没经过 (60,66)** —— 缺的就是这个角。

修法不只是换顺序，而是**把两条路径分别写出来**，并且加了三个局部小工具
（`Path.m/l/c`），让每段读起来和 XML 里的 `d` 一模一样，以后可以直接逐字对照
`ic_launcher_foreground.xml`，不可能再搞错顺序。

⚠️ 这次事故的教训：`BrandMark` 是"照着 vector 再画一遍"，两处几何一旦不同步，
错的那边不会报错，只会悄悄少一块。**改启动图标就必须同时改 BrandMark。**

### 13.24 widget 芽的比例

`ic_widget_open_app` 从"实心破土标记"改成**线性镂空芽**后，用户反馈茎太高。

比例**没有照搬** `design/js/sprout.js`（那是空状态那株）：节点从 29% 压到 **68%** 处，
茎只占整体高度的 **34%**，叶子同时加宽。18dp 下细高版本读起来像"一根杆顶个芽"。

描边保持 2 个视框单位**不跟着几何缩放** —— 否则渲染出来只有 0.46dp，和搜索图标不是一个重量级。

### 13.25 小组件最小尺寸改 3x3 · 去掉 header 收起逻辑

尺寸按 Android 的单元格公式 `70n - 30`：3 格 = 180dp。

| | 改前 | 改后 |
|---|---|---|
| `minWidth` / `minHeight` | 180 / 110 | **180 / 180** |
| `minResizeWidth` / `minResizeHeight` | 110 / 110 | **180 / 180** |
| `targetCellWidth` / `targetCellHeight` | 3 / 2 | **3 / 3** |

**同时删掉整套"太矮就收起 header"的逻辑**（`WidgetLayout` / `WidgetRenderer.showHeader` /
布局里的显隐调用）：最小尺寸既然是 3x3，已经不存在需要丢 header 的高度了，留着就是死代码。
`widget_header` 现在恒为可见（布局默认值）。

⚠️ **已放在桌面上的组件不会自动变大** —— 启动器只在重新添加时读 `minWidth/minHeight`。
用户需要删掉重加，或者手动拉到 3 行高。

### 13.26 页面背景改纯色

`PaperBackground` 从"天光 → 纸面 → 土壤"三段渐变 + 顶部打光，改成**一个平色**
（`--c-canvas` / `#EDF0F8`）。

理由不只是审美：底部那个偏绿的 `moss` 停靠点读起来像渍不像层次，而且**上下两张白卡会坐在
不同深浅的底上** —— 平色之后卡片和底的对比处处一致。

顺带删掉随之失效的令牌：`--c-sky` / `--c-moss` / `--c-glow` 及其 `-d` 版本，
app 侧对应 `BitSayPalette.sky/moss/glow`。

**实机验证**：从 y=300 到 y=2750 逐点采样，全是同一个 `#EDF0F8`。

⚠️ 清理令牌时我**误删了 `--c-canvas-d`**（暗色画布值），而 `dark.css` 还在引用它 ——
这会让暗色模式的底色整个失效。已加回，并且写了个校验脚本扫全部 CSS，
确认没有"被引用但未定义"的变量。

### 13.27 列表项进场动画：最终模型（重写过三版）

规格来自 `design/css/components.css` 的 `@keyframes sprout`：

```
0%   opacity:0  translateY(14px) scale(.955)
60%  opacity:1
100% opacity:1  transform:none
时长 .46s  缓动 cubic-bezier(.16,.9,.3,1)
```

**当前模型（唯一正确的版本）**：

> **每一行在它第一次被组合时都播同一个动画。**
> `stagger` 只决定**什么时候开始**，不决定播不播。

- 三个量由**一个进度值**驱动：透明度 60% 就到 1，位移和缩放要到 100% 才归位。
  三个独立动画会在不同时刻结束，观感就散了。
- **延迟只给"一起出现的一批"**（首次铺满、或一次保存产生的新行）。
  `SPROUT_MAX_ROWS = 10` 把错开限制在一屏之内。
- **靠滚动进来的行延迟为 0**，立刻开始。

#### 前三版分别错在哪

| 版本 | 做法 | 错在哪 |
|---|---|---|
| ① 照搬设计台 | 所有新 id 都进批次，错开用**整个列表的下标** | LazyColumn 里"列表下标"不等于"出现时刻"：第 20 行延迟 520ms，而它可能刚被滚出来 —— 那 520ms 里 alpha 为 0，快滑就是一片空白 |
| ② 分两档 | 新增行播完整 sprout，滚动进来的行播"无 alpha 的轻量版" | 方向反了：**越往下滚动画越弱**，而滚到下面的行才是正在看的那几行 |
| ③ 消费式 + 只给屏内行 | 批次限量到 10 行 | 顺带修掉"滚出去再滚回来会重播"，但没解决② |

最终版 = ③ 的限量 + ②里的"两档"删掉，**统一成一个动画**。

#### 关键区分：白屏不是 alpha 造成的，是**延迟**造成的

当初把白屏归咎于"从 alpha 0 淡入"，于是做了个不带 alpha 的轻量版 —— 这是误判。
真正的成因是**延迟**：一个还没上屏的行如果被延迟几百毫秒，那段时间它就是不可见的。
延迟为 0 时，alpha 从 0 淡入只占 460ms，而且立刻开始，不会出现空白。

**实机验证**：

| 检查 | 结果 |
|---|---|
| 滚动进来的行是否在动（临时把时长调到 2500ms 抓帧） | 卡片明显半透明 + 缩小 + 位移 ✓ |
| 快速甩动 6 帧，半透明卡片像素占明亮区 | 0.20%~0.52%（0.20% 是抗锯齿底噪）✓ 无白屏 |

#### 代码为什么曾经"残破"

因为上面三版是**叠加**改的：`isNew` / `settled` / `fallbackIndex` / `REVEAL_DURATION_MS` 这些分支变量
都是中途留下的，最后一版把它们**全部删掉**，`ItemCard` 里只剩：

```kotlin
val grow = remember { Animatable(0f) }         // 无 key = 一次性
var animating by remember { mutableStateOf(true) }
LaunchedEffect(Unit) { grow.animateTo(1f, tween(
    durationMillis = SPROUT_DURATION_MS,
    delayMillis = (stagger ?: 0) * SPROUT_STAGGER_MS,
    easing = SproutEasing)); animating = false }
```

⚠️ **`remember` 无 key 是"一次性"的来源**：已经组合的行不会被重建，所以不会重播；
滚出去再滚回来是一次新组合，会再播一遍 —— 这是刻意的，代价只有 460ms 的变换。

### 13.28 桌面小组件名称：能改，但不能跟随 app 内语言

结论写清楚，免得以后再查：

| 问题 | 结论 |
|---|---|
| 能和 app 名字不同吗？ | **已经不同了**。`AndroidManifest` 里 receiver 用 `@string/widget_label`（碎碎念 · 列表 / Bitsay · List），和 `@string/app_name` 是两条独立的字符串 |
| 能跟随 app 内的中英文设置吗？ | **不能。** app 的语言是靠 Activity 的 `attachBaseContext(withAppLanguage())` 覆盖的，**只在 app 自己的进程里生效**；启动器是另一个进程，它通过 `PackageManager` 按**系统语言**解析 label。所以只有系统语言能影响它（`values` / `values-zh` 已覆盖）。要跟随 app 内设置就得改用系统级 per-app locale（`LocaleManager.setApplicationLocales`，API 33+），那是另一套机制，且会和现在的实现打架 |
| 能隐藏吗？ | 把 receiver 的 `android:label` 设为空串。**能不能真的不显示取决于启动器** —— 有的会回落到 app 名字。需要在真机上验 |

**最终采用**：用户选择直接叫「碎碎念」/「Bitsay」，去掉「· 列表」后缀。
⚠️ 启动器会缓存 widget 的 label，**已放置的组件要删掉重加才会显示新名字**。

### 13.29 从搜索进详情，返回后回到搜索

原来 `closeEditor()` 无条件回 `Screen.List` 并清空 query —— 从搜索结果点进详情，返回就掉回首页，
关键词也没了，想继续看别的结果只能重搜。

加一个 `fromSearch` 标记（`openSearchResult` 置位），`closeEditor()` 分两支：

- `fromSearch` → 回 `Screen.Search`，**query 和两份结果都不动**（本来就在 state 里，只是切屏）
- 否则 → 回 `Screen.List` 并按原逻辑清 query

**返回**和**删除**两条路径都走 `closeEditor`，所以都覆盖到了。
实机验证：搜 "1" → 点结果 → 返回，仍在搜索页、关键词还在；再进详情 → 删除，同样回搜索页且结果自动刷新。

### 13.30 seenItemIds 会不会无限增长？（结论：不会，但有个真 bug）

**不会增长。** 每轮都是 `seen.clear(); seen.addAll(当前 ids)` —— 集合大小恒等于当前列表长度。

但有个**真 bug**：它原本是**两个 pager 页共用一个集合**。笔记页跑完把集合设成笔记的 id，
待办页接着跑就认为"所有待办都是新的"，再把集合设成待办的；等笔记页下次重组又反过来。
**结果就是整列反复重播。**

修法：`seenItemIds` 改成 `MutableMap<Kind, MutableSet<Long>>`，按 kind 分开。

⚠️ **不建议**"去掉它、只要重建就播动画"。LazyColumn 会随滚动反复创建/销毁行，
那样每滚一屏、每一行都会播一遍 —— 这正是 13.31 那个白屏的成因，去掉它只会更严重。

### 13.31 入场动画跟不上滚动、快滑白屏

**根因：错开延迟用的是"整个列表的下标"。**

设计台那样写没问题，因为**设计台一次把整个列表渲染完**，所有行同一帧开始。
而这里是 LazyColumn：第 20 行的延迟是 `20 × 26 = 520ms`，而它可能在你滚到那儿时才第一次被创建 ——
创建后在延迟结束前 alpha 一直是 0，于是**快滑时看到一串空白卡片**。

**修法**：只给"变更发生时在屏幕上"的行挂动画。

- `SPROUT_MAX_ROWS = 10`：一批最多武装前 10 行
- 错开序号改用**批次内的顺序**（自上而下），不再用列表下标

新条目总是排在列表最前（按 updatedAt 倒序），所以新增单条永远是 0 延迟 ✓
首次启动时前 10 行依次冒出，第 11 行之后直接是稳定态 ✓

**实机验证**：快速甩动连拍 5 帧，"卡片白但不是纯白"（= 半透明卡片）的像素只占明亮区
**0.20%~0.94%**，就是文字和圆角抗锯齿的边缘，没有白屏。

### 13.32 列表滚动条

`ItemList` 外包一层 `Box`，右侧叠一个 3dp 圆角指示条，规格对齐小组件的 `widget_scrollbar.xml`
（同样 3dp、圆角、静止 1200ms 后 500ms 淡出）。首页和搜索页共用 `ItemList`，所以一处改动两处生效。

颜色用 `inkFaint` 而不是小组件那个 `line` —— 小组件的条贴在白卡上，这条贴在灰画布上，
`line` 在这里等于隐形。

⚠️ **踩到一个 Compose 陷阱**：alpha 一开始只在 `Canvas` 的 **draw lambda 内部**读取，
动画在跑但画面不重绘，条一直不出现（强制常显能画出来，所以一开始误判成"没绘制"）。
把读取挪到**组合期**（`val alpha = fade`）就好了。

### 13.33 只把**顶部**间距挪出列表，底部保持原样

需求是「上下留白改由容器和相邻元素负责」，我一开始**把底部也挪到了容器上** —— 那是错的。
列表容器必须**铺满剩下的空间**，FAB 才能浮在列表之上；底部间距一旦挂到容器上，
容器就被截短，语义从"FAB 浮在列表上"变成了"列表给 FAB 让出一块"。

最终改法：

| 间距 | 原来 | 现在 |
|---|---|---|
| tab 栏 → 首行 | 列表 `contentPadding.top = 14dp` | **tab 栏的 `padding(bottom = 14dp)`**（首页和搜索页都改） |
| 末行 → 底 | 列表 `contentPadding.bottom = 108dp` | **不变，仍在列表上** |
| 左右 20dp | `contentPadding` | 不变 |
| 列表容器 | — | **不加 padding，铺满剩余空间** |

`ItemList.contentPadding` = `start/end 20dp` + `bottom = bottomPadding`，**没有 top**。

**实机验证**（滚到真正的末尾）：末行卡片底边距屏幕底 **108.0dp**，FAB 占距底 26..88dp ——
末行完整露在 FAB 上方；滚动过程中卡片从 FAB 下面穿过 ✓

⚠️ 记一笔方法论：我第一次"验证"时滑动方向搞反了（`swipe 700 → 2500` 是往下拖 = **回顶部**，
不是到底部），量到的其实是视口底部被裁掉的那半张卡片，却当成结论报了出去。
**到列表末尾要往上滑（`2500 → 700`）。**

### 13.34 滚动条内移

`ScrollIndicator` 加 `padding(end = 6.dp)`。原来它贴在屏幕最右缘（距边约 2.3dp），
而卡片本身已经内缩 20dp，贴边的条读起来像被裁掉的边而不是滚动位置。

**实测**：距屏幕右缘 **8.3dp** ✓

### 13.35 小组件行底色改纯白 / 纯黑

新增调色板令牌 `widgetRow`：亮色 `#FFFFFF`、暗色 `#000000`。
**不用 `card`** —— 小组件贴在壁纸上，用户看到的对比是"行 vs 壁纸"，不是"行 vs 底板"，
中间色调会被底板吃掉。

**连带改了三处**，否则会自相矛盾：

| 位置 | 原来 | 现在 |
|---|---|---|
| 普通行底色 | `palette.background`（灰） | **`palette.widgetRow`**（纯白/纯黑） |
| 激活 tab 的胶囊 | `palette.background` | **`palette.widgetRow`** —— 底板已经变成画布色，再用画布色画胶囊就看不见了 |
| 浅色底板 `widget_bg` | `#FFFFFF` | **`#EDF0F8`**（画布色）—— 行是纯白，白底板会让行直接消失 |
| 暗色底板 `widget_bg_dark` | `#2C2A27` | **`#101119`**（暗色画布） |

改完小组件和 app 的列表是同一套结构：**画布色底板 + 白卡**。

⚠️ **如实记录一组数字**：行与底板的对比度只有 **1.14:1（浅）/ 1.12:1（暗）** ——
和 app 里"白卡 vs 灰画布"同一量级。app 里成立是因为卡片周围有大片画布，
小组件里行只隔 4dp，视觉上会更微妙。这是本次改动的固有代价，不是 bug。

⚠️ **这次不需要改 layout 资源 id**（对比 13.25 的 `_v2`）：改的是**运行期应用的**
颜色和 drawable（`setBackgroundResource` / `setBackgroundTintList`），
每次 `updateAppWidget` 都会重新应用。**只有布局 XML 里的属性**才会被启动器的视图复用挡住。

### 13.36 滚动条：`--c-line` 叠在画布上等于没有

13.35 把小组件底板换成画布色之后，滚动条还是 `#E8EAF3` —— 和底板 `#EDF0F8`
对比度只有 **1.05:1**，条是在的，但看不见。

改成 **`ink-3` 的 60%**（`#999CA1B5` / `rgba(156,161,181,.6)`），叠在画布上是 **1.58:1**。
选 60% 而不是 app 原来的 50%（1.46:1）：小组件是"瞟一眼"的场景，略强一点；
同时把 app 列表那条也对齐到 60%，免得两个列表重量不一样却看不出原因。

**和勾选圈同源**（小组件的圈就是 `ink-3` 实色，2.57:1）—— 这样它读起来是"安静的 UI"，
而不是第二个强调色。

三处一起改，设计板加了 `--c-scrollbar` 令牌（原来写的是 `var(--c-line)`）：

| 位置 | 值 |
|---|---|
| `values/colors.xml` → `widget_scrollbar` | `#999CA1B5` |
| `ItemList` 的 `ScrollIndicator` | `InkFaint.copy(alpha = 0.6f)` |
| `design/css/tokens.css` → `--c-scrollbar` | `rgba(156,161,181,.6)` |

⚠️ **教训**：`--c-line` / `#E8EAF3` 这一档是给"白卡上的发丝分隔线"用的，
**放到画布色上就不成立**了。凡是叠在画布上的细线、细条，都要用 `ink-3` 系，
不能用 `line` 系。

### 13.37 暗色小组件：问题不在对比度大小，在**方向**

13.35 把行改成"纯白 / 纯黑"后，暗色下看着不对。把每一对配色都算了一遍：

```
亮色  行 #FFFFFF / 底板 #EDF0F8 = 1.14:1
暗色  行 #000000 / 底板 #101119 = 1.12:1      ← 数值几乎一样
```

**比值没问题，方向反了。** 亮色是"灰底上的白卡"（凸起），暗色成了"深灰底上的黑洞"（凹陷）。
同一个数字，读起来是两回事 —— 这正是只看对比度会漏掉的东西。

暗色的 UI 里，面**越靠前越亮**。要让行"凸起"，行就必须比底板亮，所以**纯黑只能给底板**：

| | 底板 | 行 | 已完成 |
|---|---|---|---|
| 亮色 | `#EDF0F8`（画布） | `#FFFFFF`（纯白） | `#E7EAF2` |
| 暗色 | **`#000000`（纯黑）** | **`#1B1D29`**（= app 暗色卡片） | `#171923` |

改后两种模式的方向一致了：

```
亮色  行/底板 1.14:1   已完成/底板 1.06:1   行比底板亮 ✓
暗色  行/底板 1.25:1   已完成/底板 1.20:1   行比底板亮 ✓
```

⚠️ **`widgetRow` 这个名字的含义要记住**：它是"**凸起的那一层**"，不是"某个固定颜色"。
换主题时不能只换数值，要先问"这个主题里什么算凸起"。

#### 顺带做的一次全量审计

把小组件每一对配色的对比度都算了（行/底板、已完成/行、正文/行、时间/行、
未选中 tab/底板、FAB/底板、FAB 字形/FAB、嫩芽/底板、滚动条/行、滚动条/底板），两个模式各 13 项。
除上面那条外全部达标 —— **这类问题不该靠眼睛抽查，算一遍就出来了**。

### 13.38 亮色重设 + 勾选圈对齐小组件

三条一起改。

#### ① 已完成项的底色（用户：亮色不如暗色舒服）

根因不在"已完成"这个颜色本身，在**画布太浅**：亮色画布 `#EDF0F8` 对白卡只有 **1.14:1**，
整屏是一团灰白，卡片结构要"找"才看得见；`#E7EAF2` 的已完成色叠上去只有 1.06:1，
和画布几乎同色。暗色那边是 1.12:1，但深色底上白字对比高，所以"稳"。

#### ② 亮色配色重设

| 令牌 | 原值 | 新值 | 理由 |
|---|---|---|---|
| 画布 | `#EDF0F8` | **`#E6E9F2`** | 卡/画布 1.14 → **1.21:1**，白卡立起来 |
| `ink-3` | `#9CA1B5` | **`#8A90A6`** | 勾选圈在白卡上 2.57 → **3.17:1**，越过 UI 元件 3:1 的底线 |
| 已完成 | `#E7EAF2` | **`#FFFFFF`**（同普通卡） | 见下：试过两版灰底都不行，最终取消底色差异 |

`ink`(#191B26) 和 `ink-2`(#666B80) 不动 —— 它们本来就是合格的（17.1:1 / 5.3:1）。

小组件底板 `widget_bg` 跟着画布一起改（它俩是同一个值），滚动条改成新 `ink-3` 的 60%。

#### ③ 已完成项的底色：两次都不对，最后取消

第一版 `#E7EAF2`：画布压深后只有 1.01:1，等于和画布同色。
第二版 `#EFF1F7`：1.13:1，数值上"夹在卡和画布之间"，**用户反馈仍然不舒服**。

原因是方向错了 —— 在白底上一张**灰卡配灰删除线**读起来是"**失效**"，不是"做完了"。
暗色那边同一招成立（把卡片压暗一档读作"退后"），亮色不成立。

**最终：亮色已完成项的底色 = 白，和未完成项完全一样。**
状态由三处表达：勾选圈里的勾、删除线、文字色。

⚠️ **连带必须改文字色**：底色不再区分之后，文字就是唯一的信息载体，
而 `InkFaint` 在白卡上只有 **3.17:1**（正文要 4.5:1）。已改成 `InkSoft`（**5.28:1**）。

暗色保持 `cardDone = #171923`（比卡片暗一档）—— 那里的"退后"读法成立。

#### ④ 勾选圈改成小组件那套

原来是**实心强调色圆盘 + 反色勾**。暗色下强调色翻成白色，于是**六个白圆盘比唯一的未完成项还抢眼** ✗

小组件的做法才是对的：`ic_todo_open` = 一个圈，`ic_todo_done` = 同一个圈加一个勾，
**都是 `ink-3` 线描**，状态由"有没有勾"表达，不靠变色或填充。app 现在照抄这一套。

⚠️ 多选用的 `Pick` 没动 —— 那是"选中"不是"完成"，语义不同，实心圆盘在那里是合适的。

### 13.39 小组件空视图对齐 app

原来只是一行居中文字（`widget_empty` = "这里空空的\n去 App 里记一条吧"）——
看着像**加载失败**，不像"等你写第一条"。

改成和 app 的 `EmptyState` 同一个结构：**嫩芽 + 标题 + 说明**，右下角的 `+` 本来就在。

| 部分 | 来源 |
|---|---|
| 嫩芽 | 新增 `drawable/ic_widget_sprout.xml`，几何取自 `design/js/sprout.js`（空状态那株），缩进 24 视框；运行时用 `palette.leaf` 着色 |
| 标题 / 说明 | **直接复用 app 的字符串** `empty_{notes,todos}_title/hint`，按 widget 的 kind 选 |
| 原来的 `widget_empty` 字符串 | 已删除（中英各一条） |

⚠️ **RemoteViews 不能跑自绘代码**，所以嫩芽必须是 vector 资源，不能像 app 那样用 `Canvas` 画。

⚠️ **文案里的 `**` 要去掉**：app 的 hint 用 `**+**` 标粗，RemoteViews 的文字是纯文本，
不去掉就会原样显示两个星号。

⚠️ **尺寸按 3×3 最小值算过**：180 − 12(内边距) − 52(顶栏) = 116dp 可用；
内容 44(嫩芽) + 9 + 17(标题) + 4 + 30(说明两行) ≈ 104dp ✓ 余 12dp。所以**不需要按尺寸分支**。

⚠️ **又换了一次 layout id**：`widget_bitsay_v2` → **`widget_bitsay_v3`**。
这次改的是**布局属性**（新增了三个子 View），不换 id 就到不了桌面上已有那个组件 ——
和 13.18 的 `listSelector`、13.25 的 3×3 是同一条规则。
**运行期应用的颜色/drawable 不需要换**（如 13.35），只有布局 XML 需要。

### 13.40 空视图标题颜色错：宿主按**系统**深色模式解析布局

**现象**：暗色下小组件空视图的标题几乎看不见。

**根因**（就是 13.5 那条规则，我这次又踩了一遍）：

> 小组件布局是**启动器用它自己的配置** inflate 的，
> 所以布局里写 `@color/ink` 会按**系统**深色模式解析，**不是 app 内设置的主题**。

实测环境正好是两者不一致：

```
系统深色模式 = no（亮色）
app 内主题   = DARK
```

`@color/ink` 于是解析成亮色的 `#191B26`（近黑），落在纯黑的组件底上 —— 看不见。

**为什么只有这两个 TextView 出问题**：小组件里其他文字（tab、行文字）都在代码里用
`setTextColor` 显式设了色；**只有 13.39 新加的标题和说明是直接用布局属性**。

修法：

```kotlin
views.setTextColor(R.id.widget_empty_title, palette.ink.toArgb())
views.setTextColor(R.id.widget_empty_hint, palette.inkSoft.toArgb())
```

并在布局文件头部写死这条规则：

> **COLOURS.** Every android:textColor / android:src tint in this file is only an inflation default.
> The launcher inflates this layout with ITS configuration, so `@color/ink` here follows the
> *system* night mode, not the app's theme setting. Anything that must follow the app's theme has
> to be assigned in WidgetRenderer from BitSayPalette. **Check that before adding a view.**

⚠️ 这条是**运行期设色**，所以**不需要换 layout id**（对比 13.39 加子 View 时必须换）。

### 13.41 系统切换亮暗 / 语言时刷新小组件

需求：app 主题设为「跟随系统」时，系统切亮暗或语言，桌面小组件要跟着变。

**先查清可行途径**（这一步决定了方案，不能靠试）：

| 途径 | 结论 |
|---|---|
| manifest 注册 `ACTION_CONFIGURATION_CHANGED` | **不行**。系统给它加了 `FLAG_RECEIVER_REGISTERED_ONLY`，只能动态注册 |
| `AppWidgetHostView.onConfigurationChanged` | **不存在**（AOSP 源码里没有）。宿主在配置变化时**不会**重新应用 RemoteViews，所以**资源驱动的颜色也救不了** |
| `Application.onConfigurationChanged` | **可行** —— 进程收到配置变化时回调 |
| `AppWidgetProvider.onUpdate` | 只在首次添加 / `updatePeriodMillis`（最短 30 分钟）/ 包替换时来 |

**实现**（`BitSayApp`）：

```kotlin
override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    val follows = themePrefs.current() == ThemeMode.SYSTEM ||
                  languagePrefs.current() == AppLanguage.SYSTEM
    if (follows) WidgetUpdater.refreshAll(this)
}
```

外加 `onCreate` 里补一次 `refreshAll` —— 原来那次 `repository.reload()` 被 `change` 流的 `.drop(1)` 吃掉了，
冷启动时没有任何东西会推组件；而"跟随系统"是在**渲染时**解析的，进程死着的时候系统换过主题，
组件就还停在旧的那一版。

固定选择（亮/暗、中文/英文）不随系统动，所以那种情况下直接跳过，不做无谓渲染。

⚠️ **已知边界**：`onConfigurationChanged` 只在**进程活着**时来。进程被缓存且系统切主题时，
组件要等到下一次 app 启动才会更新 —— 上面那条 `onCreate` 刷新就是为这个场景兜底的。
没有常驻组件（Service）就无法被纯主题切换唤醒，这是 Android 的限制，不是实现偷懒。

#### 方法论：这台设备**抑制了 app 日志**

排查时我在 `onCreate` 和 `onConfigurationChanged` 各加了一条 `Log.d`，
结果**连 `onCreate` 那条都不出现** —— 一度误判成"回调没触发"。
`logcat` 里只有系统的 `ActivityThread` 行，我们 app 自己的日志一条都没有。

**结论：在这台 vivo 上，logcat 不能用来判断"我的代码有没有跑"。**
要验证就**直接量可见结果**（这次是量组件底板的颜色），别依赖日志。
临时日志已全部移除。

### 13.15 空内容不保存 · 删除二次确认 · 作者区块

**空内容**：仓库层 `ItemRepository.saveDraft` 对空文本 `return`，所以**输入过程中永远不会新建行**。
缺的一半在 `AppViewModel.saveDraft()`：它是**退出编辑页**时才调用的（全项目唯一调用点在
`BitSayRoot.kt` 的返回键处理），此时若 `draft` 为空且已有行，则删除并给 `Notice.Empty`。
用户特别澄清过时序 —— 是**退出时删**，不是清空时就删。

**实机验证**（直接读 `databases/bitsay.db` 含 WAL）：

| 时刻 | 结果 |
|---|---|
| 清空后仍在编辑页 | 9 行，目标行**还在**（保留最后自动保存的内容） |
| 按返回退出后 | 8 行，目标行**已删除** |

⚠️ 顺带发现一个小瑕疵：清空后状态行仍显示"已自动保存"，其实那一笔没有写入（`dirty` 判的是
`draft.isNotBlank()`）。因为退出时整条会被删掉，暂未处理。

**删除二次确认**：新增 `ConfirmDialog`（和选择弹窗同一套形状：右侧安静的取消 + 强调色的确认）。
单条删除（编辑器）与批量删除（多选）都走它。**这个 app 唯一的"响"色是纯黑/纯白，不是红色**，
所以确认按钮用强调色而不是危险红。

**作者区块**：设置页最后加 `作者 → Del Wang（个人主页）/ 源代码`。
两个 URL 放在 `core/util/Author.kt`，**只有一处定义** —— 链接写错在点开之前是看不出来的。

**首页标题**：`DisplayStyle` 字重 Bold → **ExtraBold**。38sp 的 Bold 看起来比 21sp 的页面标题还轻，
层级是反的：屏幕最上方应该最重，不是最轻。

### 13.12 主题 / 语言弹窗重做

原来直接用 Material 的 `AlertDialog` + `RadioButton`：大标题居中、选项稀疏，
像是在设计好的界面上盖了一个系统弹窗。改成自己画（`Dialog` + Column）：

- 标题是**小号大写 + 大字距**的 `labelSmall`，和设置页的分组标签同一套（设计板 `.dialog__title`）
- 选项前面留一个**固定 18dp 的勾选槽**：打勾不能把文字挤动，而且空圈会读成"你必须选一个"，
  实际语义只是"当前是这个"
- 选中项文字加粗
- 底部右对齐一个安静的 `Cancel`

### 13.13 关于图标 · 备份文案

- 「关于」卡片加了**圆角应用图标**：`AppIconPlate` 自己画墨色圆角底板 + 标记。
  **不要直接 `painterResource(R.mipmap.ic_launcher)`** —— AdaptiveIconDrawable 自己没有遮罩，
  直接画出来是个大方块、内容缩在中间一圈空白里，和桌面上看到的完全不一样。
- 备份/还原描述改成**单行短句**，且不提 `.bitsay.gz`：
  「把全部笔记和待办打包成一个备份文件」/「从备份文件恢复，可覆盖或并入现有内容」。

### 13.12 主题 / 语言弹窗重做

原来直接用 Material 的 `AlertDialog` + `RadioButton`：大标题居中、选项稀疏，
像是在设计好的界面上盖了一个系统弹窗。改成自己画（`Dialog` + Column）：

- 标题是**小号大写 + 大字距**的 `labelSmall`，和设置页的分组标签同一套（设计板 `.dialog__title`）
- 选项前面留一个**固定 18dp 的勾选槽**：打勾不能把文字挤动，而且空圈会读成"你必须选一个"，
  实际语义只是"当前是这个"
- 选中项文字加粗
- 底部右对齐一个安静的 `Cancel`

### 13.42 宽屏适配：横屏 / 平板 / 折叠屏（左右两栏）

需求：「最后适配横屏、平板、折叠屏等设备尺寸和布局，宽屏建议左右布局，注意安全宽度」。

#### 分档与规则（`ui/theme/Theme.kt`，对应设计台 `tokens.css` 的「宽屏」段）

| 令牌 | 值 | 作用 |
|---|---|---|
| `WideBreakpoint` | 600dp | 到这个宽度才分两栏 |
| `PaneListWidth` | 344dp | 左栏理想宽 |
| `MeasureWidth` | 720dp | 右栏正文最大阅读宽度 |

`listPaneWidth(w) = min(344dp, w × 45%)`。**45% 这一档不是装饰**：600dp 的临界宽度上
固定 344dp 会把右栏挤到 256dp，写不下一句话。实测量过：640dp 窗口下接缝正好落在
567px = 288dp = 640×0.45（`wm density 315` 模拟，见下）。

布局规则只有一条，`BitSayRoot` 里两个分支：

- **窄**：`when (screen)` 整屏切换，和以前一字不差 —— 宽屏是**新增分支**，没有回头改手机。
- **宽**：`Row { 左栏永远列表 ｜ 1dp 接缝 ｜ 右栏 = 当前 screen }`。
  右栏内容再套 `widthIn(max = 720dp)` 居中，所以 1400dp 的桌面级窗口得到的是一列居中的正文，
  不是拉满一整行的段落。

右栏的 `Screen.List` 退化成占位（列表已经在左栏了）——**没有第二套状态机**，
`state.screen` 在两个分支里是同一个，切分栏不会额外引入任何状态。

**搜索和设置也走右栏**：它们都是"从这个窗口里打开的东西"，背后的列表还是你刚才在读的那张列表。
点进设置时整屏被接管，在平板上是浪费。

#### 设计台先做（`design/`）

新增 `css/pages/wide.css` + `index.html` 第 ② 节（原来 ②–⑦ 顺延为 ③–⑧），
三台活的宽屏样机：800×360 手机横屏 / 720×900 折叠屏展开 / 1120×700 平板横屏。
左栏点一条右栏就打开、点 + 就在右栏新建 —— 和 Android 侧是同一套规则。

原型阶段踩到两个只有放进两栏才会暴露的坑，**都写进了注释**：

1. `.wlist/.wdetail` 必须是 `position:absolute; inset:0`（和 `base.css` 的 `.host` 同理）。
   写成普通 div 时高度由内容决定，页面里 `position:absolute` 的 FAB 会挂到"内容底部"
   而不是"这一栏的底部" —— 实测**掉到栏底下方 800px**。手机上被 `.phone__screen`
   的固定高度掩盖了，一放进两栏就露出来。
2. `.wide__measure` 是 column flex 的子项，只写 `width:auto` 会被**收缩成内容宽**
   （实测 395dp 的栏里只占 272dp），里面的页面也跟着只占内容高，栏底露出一条画布色 ——
   深色下特别刺眼。必须 `width:100%; display:flex; flex-direction:column` 自己当容器。
   对应到 Compose 就是：**栏的尺寸必须由栏定死，不能让内容决定**。

另外分栏线**不能用 `--c-line`**（底是画布色，叠上去 1.05:1，等于没有），
新加 `--c-divider` = ink-3 的 34%（暗色 42%），Compose 侧 `BitSayPalette.divider`。

#### 空状态在矮窗口里会被腰斩（顺带修掉的真实缺陷）

`EmptyState` 画出来约 300dp 内容 + 上下留白，而横屏手机的列表区只有 **171dp**（实测）。
原来的写法直接顶对齐，结果**嫩芽在、句子没了**。

改成两形态（`EmptyStateRoomyHeight = 400dp`）：

- ≥ 400dp：原样（顶对齐、72dp 顶距、嫩芽 92dp、标题 + 提示）
- < 400dp：嫩芽 ×0.45 + 只留标题，**垂直居中**

**为什么短形态可以不管 FAB**：居中的内容只有 ~40dp 嫩芽 + ~104dp 标题，
在 344dp 的栏里止于 x≈112dp，而 FAB 从 x=256dp 才开始 —— 两者碰不到。
第一版短形态"照抄"了长形态的 108dp 底部留白，在 171dp 的页里把标题**压成了 0 高**
（`Column` 的剩余高度算成负数），比它要防的问题更糟。这也是 `EmptyStateRoomyHeight`
从 340dp 提到 400dp 的原因：长形态的正文要止于「页高 − 108dp」之上。

400dp 这个门限对竖屏是安全的：实测竖屏列表区 ≈ 586dp，远在门限之上。

#### 实机验证（Android 16 / vivo V2309A / 1260×2800 @560dpi）

| 场景 | 操作 | 实测 |
|---|---|---|
| 竖屏单栏 | 直接看 | 无占位文案；空状态**完整形态**，标题 y=408dp = 189(页首)+72(顶距)+126.5(嫩芽)+20 ✓ |
| 横屏两栏 | `settings put system user_rotation 1` | 占位文案出现；列表内容区 `[120,663][1324,1260]` = 344×171dp |
| 接缝 | 逐像素扫 y=900 | x=1324..1327，恰好 `#C7CBD8` = `0x8A90A6 @34.1%` 叠 `#E6E9F2` 的预测值 ✓ |
| 横屏空状态 | 同上 | 嫩芽 49×57dp、标题 104×25dp，两者中心 x=722 = 页面中心 ✓；标题右缘 904 vs FAB 左缘 1037，**相距 38dp** ✓ |
| 两栏交互 | 点左栏卡片 | 左栏卡片仍在（x1=71dp），右栏同时出现编辑器（x1=443dp）✓ |
| 右栏设置 | 点齿轮 | 左栏列表不动，设置整页落在右栏（x1≥401dp）✓ |
| 45% 收缩 | `wm density 315` → 640dp 宽 | 接缝在 567px = **288dp = 640×0.45** ✓ |
| 安全区 | 全程 | 横屏左侧 34dp 挖孔内边距由 `windowInsetsPadding(safeDrawing)` 让出，内容从 x=120px 起 ✓ |

设备已还原：`wm density reset`（Physical 560，无 override）、`wm size`（1260x2800，无 override）、
`accelerometer_rotation 1`、`user_rotation 0`、ROTATION_0 —— 均逐个复查过。

#### 教训

- **「内容决定尺寸」在两栏里会静默出错。** 单栏时外层有固定高度兜着，怎么写都看不出来；
  一分成两栏，谁没撑满就立刻露底。设计台和 Compose 两侧都是同一个错。
- **留白要留对方向。** 短形态第一版把 FAB 的 108dp 留白抄了过去，等于在一个 171dp 高的盒子里
  先扣掉 108dp 再放 95dp 的内容 —— 标题被挤成 0 高。留白不是"照着上面抄一个数"，
  得先算清楚这个盒子里还剩多少。
- **`maxWidth` 在 `Row {}` 里读会被 `RowScope` 遮住**（编译期就报错，是好事）；
  在 `BoxWithConstraints` 里先取出来再进 `Row`。

### 13.43 横屏矮窗口：把 tab 提到标题行（一行顶栏）

需求：「横屏高度不够时，把笔记/待办的 tab 缩小放到首页左上角标题区域、和搜索/设置图标在一行，
去掉 xx 笔记/待办那一行，节省空间」，紧接着：「把搜索放在 tab 右边，设置放在左边，平衡一下布局」。

#### 为什么必须动顶栏

竖屏顶栏是**三层**：38sp 大标题 / 计数行 / 40dp 的 tab 行，实测吃掉约 160dp。
横屏一共才 360dp，列表只剩 **171dp** —— 一屏不到三条，空状态连句子都放不下（§13.42）。
这不是"再挤一挤"能解决的，得少一层。

#### 规则

`CompactHeaderHeight = 480dp`（`ui/theme/Theme.kt`），判的是**窗口高度**不是宽度：

- ≥ 480dp：竖屏手机（640+）、平板（700+）、折叠屏展开（900+）全部不受影响，顶栏一字未改。
- < 480dp：横屏手机（360–430）、分屏下半屏 → 顶栏压成**一行**：
  `⚙️ ｜ [笔记][待办] ｜ 🔍`

标题和计数行**都去掉**。计数不是信息是氛围；「笔记/待办」这两个字 tab 上已经有了 ——
两处在说同一件事的时候，先砍掉的是比较小的那一行。

#### 两个图标分开站，不是堆在右边

第一版做成 `[笔记][待办]  🔍 ⚙️`，实测不好看：整行头重脚轻，tab 被挤到左边和图标连成一片。
改成设置在最左、搜索在最右之后，**两个按钮都是 40dp，所以 tab 正好落在中线上**。
实机量过：tab 轨道中心 722px，栏中心 722px，**完全重合**。

左右内边距取同一个数（12dp）才成立。`RoundIconButton` 自己带约 9dp 内边距，
所以 12dp 让**字形**落在页面 20dp 的视觉留白上；写成 20/12 会让 tab 偏 4px。

tab 缩一号（`SegmentedTabs(compact = true)`）：轨内边距 4→3dp、按钮高 40→30dp、字号 14→13sp。
不缩的话按钮的 40dp 会重新撑起整行高度，等于白搬。

#### 实机验证（Android 16 / vivo V2309A / 横屏 800×360dp）

| 项 | 改前 | 改后 |
|---|---|---|
| 顶栏高度 | 161dp（标题 75 + tab 行 50 + 状态栏 36） | **56dp** |
| 列表可用高度 | 171dp | **268dp（+57%）** |
| 计数行 | 有 | 无 |
| 顶栏行数 | 3 | 1 |

- tab 轨道 `[341,141][1103,309]`，中心 722 = 栏中心 `(120+1324)/2 = 722` ✓
- 设置按钮 `[148..316]`、搜索按钮 `[1128..1296]`：距栏边各 28px = 8dp **对称** ✓
  （28px = 12dp 内边距 − 4dp 触摸目标外扩，`RoundIconButton` 的 48dp 触摸区比 40dp 视觉大一圈）
- **竖屏逐项复测未变**：标题 y=50dp / 计数 y=98dp / tab y=143dp / 列表区 611dp、空状态完整形态 ✓

#### 留下的口子

- **搜索页没有跟着紧凑**。它自己的头是「输入框 + tab」两层，横屏下同样偏高。
  这次没动是因为需求只说首页；但横屏下从紧凑首页点进搜索会觉得头又变重了 ——
  要收拾的话是同一个 `compactTop` 传下去。
- 空状态在 268dp 下仍是短形态（门限 400dp），**提示句没有回来**，这是有意的：
  268dp 里塞下提示句后，居中的内容右缘（中文 245dp）离 FAB 左缘（256dp）只剩 11dp，
  英文文案更长会直接压上去。宁可少一句，不要压字。

### 13.44 搜索只搜一种类型（去掉搜索页的 tab）+ 三处文案/间距修正

需求：「去掉搜索页的笔记和待办搜索选项，由路由来源 tab 决定单一搜索类型，
注意桌面小组件点击搜索时激活的 tab 是笔记还是待办」。
随后追加：「搜索页面输入框和搜索结果列表的 padding 有问题」、
「宽屏下列表留在原地…这个文案不太好，用户不需要知道宽屏这个开发者才需要知道的概念」、
「只在 xx 里找的文案也很奇怪…要从用户视角简洁文案」。

#### 数据层：两份结果 → 一份

`AppUiState` 里 `searchNotes` + `searchTodos` 换成 `searchResults` 一份，新增 `searchKind`。
`AppUiState.searchResults`（原来那个按 `tab` 挑的派生属性）和 `visible` 一并删掉 ——
它们存在的唯一理由是"首页列表可能渲染搜索结果"，而 `openList()` 早就会清 query，本来就已经是死代码。

`selectSearchTab()` 删除。`runSearch()` 只查一种，并且把**类型**也纳入竞态判断：

```kotlin
if (_state.value.query == needle && _state.value.searchKind == kind) { … }
```

原来的注释写着"两种都查了，所以切 tab 不会让结果过期"—— 那个前提现在不成立了。

#### 类型从哪来：由调用方决定，不由 app 的 tab 决定

`openSearch(kind, fromWidget)` 收一个必填参数。两个调用方：

| 来源 | 传什么 |
|---|---|
| 首页列表 | `state.tab`（你正在看哪一栏） |
| 小组件搜索按钮 | **小组件自己的 tab**，跟着 PendingIntent 传进来 |

⚠️ **小组件那一半原来就是错的**：`WidgetRenderer.searchIntent()` 根本没带 `EXTRA_KIND`，
`WidgetEntryActivity` 调的是 `openSearch()`，于是"待办的小组件点搜索"会去搜 app 上次停留的那一栏。
现在 `searchIntent(context, widgetId, kind)` 补上 `putExtra(EXTRA_KIND, kind.code)`；
每次渲染都重发这个 PendingIntent（`FLAG_UPDATE_CURRENT`），所以切了小组件的 tab 再点搜索带的就是新的类型。

`openSearch` **故意不写 `state.tab`**：打开某条**命中**会让首页列表对齐到那条的类型（`openItem` 里本来就这么做），
但"只是搜了一下"不该在背后把列表换掉 —— 从小组件搜尤其如此，小组件的 tab 是它自己的。

#### 实测（Android 16 / vivo V2309A / 360×800dp）

| 场景 | 结果 |
|---|---|
| 首页在「笔记」→ 搜索 `word` | 1 条命中，文字 x1=**37.1dp**（笔记行，无勾选圈） |
| 首页在「待办」→ 搜索 `word` | 1 条命中，文字 x1=**73.1dp**（待办行，前面有勾选圈）→ 是另一条 ✓ |
| **小组件**（tab=待办，`bitsay_widgets.xml` 里 `kind_33=1`）点搜索，而 app 停在「笔记」 | 空状态是「想找什么？」，**没有**"只在笔记里找"；结果显示的是**待办**那条 ✓ |
| 小组件搜索退出后 | app 首页仍停在「笔记」✓ 没有被小组件带跑 |

小组件那一步只能真点桌面组件：`WidgetEntryActivity` 正确地 `exported="false"`，
`adb shell am start` 会被 `SecurityException: not exported from uid` 拒绝；
`dumpsys appwidget` 也看不到 PendingIntent 的 extras。这两条都试过，别绕。

#### 删 tab 时一起删掉了它的 14dp 底距（真实缺陷，量出来的）

搜索结果的第一张卡片 **y=93.4dp 起，正好等于输入框底边 93.4dp** —— 两者严丝合缝。
根因：`ItemList` 的 `LazyColumn` **没有 top contentPadding**（这是刻意的，见 ListScreen 的注释），
"tab 与首行之间的间距"一直挂在 `SegmentedTabs` 的 `padding(bottom = 14.dp)` 上。tab 一删，间距跟着没了。

修法沿用同一套机制：间距给"列表上方那个元素"，也就是搜索栏 → `padding(bottom = 14.dp)`。

同时修了横向：搜索栏原来是 `start=14, end=14`，而列表左右各 20dp，
所以**输入框右缘在 346dp、卡片右缘在 340dp**，差 6dp。改成 `end = 20.dp`。

**像素复测**（纵向扫 x=180dp 的白色区段）：

| | 改前 | 改后 |
|---|---|---|
| 输入框白底 | …..92.0dp | 51.1..92.0dp |
| 首张卡片 | 93.4dp 起（压进输入框的底衬） | 107.4dp 起 |
| 间距 | **0** | **15.1dp**（14dp padding + 1px 取整） |
| 输入框 / 卡片右缘 | 346 / 340dp ✗ | **340 / 340dp** ✓ |

#### 文案：把"解释实现"的句子删掉

| 位置 | 原文 | 现在 |
|---|---|---|
| 宽屏右栏占位第二行 | 宽屏下列表留在原地，不用来回跳 | **整句删除**（`wide_blank_hint` 已从两个语言文件移除） |
| 搜索空状态提示 | 输入关键词，只在笔记里找 | **整句删除**（`empty_search_idle_hint` 已移除），只留「想找什么？」 |

理由是同一条：**解释这一页怎么工作的文案是写给评审的，不是写给用户的**。
用户脑子里没有"宽屏"这个概念，他只看到列表还在那儿；他也不知道（也不需要知道）搜索被收窄成了单一类型 ——
输入框的占位文字「搜索笔记 / 搜索待办」已经用最自然的方式说了这件事，那是 placeholder 的标准用法，
和「Search contacts」是一回事。

因此 `EmptyState` 的 `hint` 改成**可选**（`String? = null`）：提示只有在"说了用户看不见的东西"时才值得存在，
比如列表空状态的「点右下角的 **+** 写下第一条」—— 那个按钮没有文字标签，不说就不知道。

#### 留的口子

- 搜索页没有跟着矮窗口紧凑（§13.43）。它自己的头只有「输入框」一层了，比之前矮了不少，
  横屏下实测还剩得下，暂时不用再压。

### 13.45 宽屏左右两栏的「标题基线」对齐（矮窗口 + 高窗口两档）

需求：「手机横屏时的顶部紧凑布局的行高有问题，会导致和右侧的搜索/设置等页面的标题高度不对齐，
建议保持原来的组件高度」，随后：「高度充足时左右布局的标题高度建议也要对齐，现在似乎有一点点偏差」。

这是同一件事的两档，根因不同，要分开修。

#### 先厘清一件事：两栏的「行高」由谁决定

| | 内容高 | 说明 |
|---|---|---|
| 右栏页面头部（编辑器 / 搜索 / 设置） | **40dp** | 一个 `RoundIconButton` |
| 左栏紧凑头部（矮窗口） | **48dp** | tab 药丸轨道 = 40dp item + 4dp × 2 |
| 左栏完整头部（高窗口） | 64dp | 38sp 标题行（42dp）+ 4 + 计数行（18dp） |

**行高不同 + 同样的 padding ⟹ 内容中心不同**。48 与 40 差 8，中心就差 4dp —— 这就是"看起来差一点"的来源。

#### 矮窗口（< 480dp 高）：用 padding 补差值，组件保持原尺寸

`CompactHeaderPadding = PageHeaderPadding − (48 − 40) / 2 = 10dp`
（`ListScreen` 里写成 `PageHeaderPadding - (TabTroughHeight - PageHeaderHeight) / 2`，两个高度都是命名常量，改一个另一个跟着走。）

⚠️ **中途试错过一次**：先把 tab 从 30dp 还原成 40dp（"保持原来的组件高度"），
行高从 40 变成 48，中心反而比右栏**低 4dp**，比改之前更糟。第一版是 8dp padding + 30dp tab，
行高 40dp（被图标按钮撑着）但 padding 比页面少 6dp → 高 6dp。
**结论：tab 尺寸不是问题，padding 才是；但既然要保留组件原尺寸，就得用 padding 去补行高差。**

#### 高窗口（≥ 480dp 高，平板 / 折叠屏展开）：差的是字体度量

完整头部左栏大标题 **72.6dp**，右栏页面标题 **70.6dp** —— 差 2.0dp。
原因不是 padding（两边都是 14），而是 **38sp 的中文字形在 42sp 行盒里并不居中，墨迹偏下约 2dp**；
次级页的标题是在 48dp 的行里垂直居中的，没有这个问题。

`HomeHeaderPadding = PageHeaderPadding − 2dp = 12dp`。

⚠️ 这个偏差**竖屏里一直存在**，只是首页标题和页面标题从来不同框，没人看见。
宽屏把它们并排放在一起才暴露出来 —— 这也是为什么"左右布局"值得单独验一遍。

#### 实测（Android 16 / vivo V2309A）

**矮窗口 · 横屏 800×360dp**（字形中心，距窗口顶）

| 右栏是 | 左栏 | 右栏 | 差 |
|---|---|---|---|
| 设置页 | 70.0 / 70.1 / 70.1 / 70.0（设置·笔记·待办·搜索） | 70.0（返回）/ 70.0（标题） | **0.0dp** |
| 搜索页 | 同上 | 71.7 | 1.7dp |

搜索页那 1.7dp 来自**输入框比图标按钮高**（43.4dp vs 40dp），返回键是在 43.4dp 的行里居中的。
要抹平就得把输入框压到 40dp（改它的 12dp 内边距），代价比收益大，留着。

**高窗口** 用 `wm density 280` 把窗口变成 **720×1600dp / 1600×720dp**（宽高都过线）实测：

| | 左栏标题 | 左栏图标 | 右栏返回 | 右栏标题 | 差 |
|---|---|---|---|---|---|
| 竖屏 720×1600dp | 102.86 | 103.14 | 103.14 | 103.14 | **0.28dp** |
| 横屏 1600×720dp | 70.29 | 70.57 | 70.57 | 70.57 | **0.28dp** |

（`wm density` 会把整机 UI 一起缩放，属于临时手段；已 `wm density reset`，复查过 Physical 560、无 override。）

#### 设计台同步

`pages/list.css` 的 `.list-page .topbar` padding-top 14px → **19px**：
设计台里首页标题和次级页标题**都是 21px**，但首页栏是 `align-items:flex-start` 且内容更高，
标题字形中心本来比次级页**高 5.5px**（27.5 vs 33.0，方向和应用里相反）。改后 32.5 vs 33.0。

⚠️ **顺带发现一个既有的设计台 / Compose 漂移，本次没动**：
设计台的首页标题是 `--fs-title-page: 21px`，而 Compose 是 `DisplayStyle` **38sp**；
`design/README.md` 的实测表里写的又是 28sp/36sp。三处各不相同。
因为字号不同，两边的"对齐修正"方向也就相反（设计台 +5px，应用 −2dp）—— 已在两边注释里写明原因，
但**设计台的字号本身该对齐到 38sp**，这是一件独立的事，牵动整个设计台列表页的观感，留给下一次专门做。

### 13.46 把 App 的改动同步回设计台（字号 / 小组件配色 / 图标 / 组件度量）

需求：「把 app 的修改同步进 web 设计台，比如标题字体大小，小组件 item 和画布底色等，还有图标」。

做了一次**逐项对账**，不是只改点名的三处。方法：把 Compose 的取值写成一张真值表，
在设计台里逐条读 `getComputedStyle` 比对 —— 这样"看起来差不多"没法蒙混过去。

#### ① 颜色：30 个 token 全量比对

写了个探针把 `ui/theme/Color.kt` 的 30 个值（亮/暗两套 + 小组件 4 个）和设计台的
`getComputedStyle(body)` 逐条比。改完之后 **30/30 全等**。
（过程里发现并修掉的是小组件的四个，见 ②。）

#### ② 小组件：底板和行**装反了**（用户点名的那处）

| | 设计台（错） | App（对） |
|---|---|---|
| 底板 | `--c-surface` = **#FFFFFF** | `widget_bg` = **画布色 #E6E9F2**（暗色 **纯黑 #000000**）|
| 行 item | 默认 `--c-canvas` = **#E6E9F2** | `widgetRow` = **白 #FFFFFF**（暗色 #1B1D29）|
| 点亮的 tab | `--c-canvas` | `widgetRow`（**和一行 item 同一个面**）|

新增 `--w-tile` / `--w-row`（+ `-d`）四个 token。这不是"再定义一个白色"，而是**提层关系**：
底板在下、行在上，行必须比底板亮。反过来白行会消失在白底板上，整张组件读成一块空白。
暗色底板是纯黑而不是 canvas 的 #101119 —— 那点蓝在壁纸上反而发灰。

#### ③ 图标

| 图标 | 问题 | 处理 |
|---|---|---|
| `settings` | 设计台是"多边形 6 齿 + 圆孔"，App 是**连续圆角齿轮廓**，两条完全不同的路径 | 换成 `ic_settings.xml` 的逐字路径（含中心孔 `M12 15C…`）|
| widget `+` | 用了 FAB 的 `plus`（5.5→18.5） | 新增 `widgetPlus` = `ic_widget_add`（5→19）|
| widget 搜索 | 用了 App 顶栏的 `search`（r6.2 + 长手柄） | 新增 `widgetSearch`（r6.6 + 短手柄，2.1 描边）|
| widget 空状态嫩芽 | 用了空状态页那株（64×88 的 `sprout.js`） | 新增 `widgetSprout` = `ic_widget_sprout`（茎更长、叶更窄，节点 7.88/22 vs 36/84）|
| `github` | **设计台完全没有** | 补上（实心路径，`fill` 不是 `stroke`）|
| 作者区 | 个人主页用了 `lang`（地球）、仓库用了 `link` | 改成 `link` + `github`，与设置页一致 |

#### ④ 顺手抓到的四处漂移（用户说的"等"）

| 位置 | 设计台（错） | App（对） |
|---|---|---|
| 首页标题 | 21px/27px，800 | **38px/42px，700，字距 −.035em**（`DisplayStyle`）|
| 列表勾选圈·完成态 | **填充的强调色圆盘** | 同一个圈 + 对勾，**同色 `inkFaint`** —— 状态由"勾在不在"表达 |
| 完成条目 | 文字 `ink-3`、时间戳压到 55% | 文字 `ink-2`、时间戳**不变淡**（都是 `InkSoft.copy(.85f)`）|
| 编辑器正文 | 16px | **16.5px**（`bodyLarge`）|
| 设置行 | 15/30/9，标题 16px SemiBold | padding 16/17、图标盒 **32**、间距 **12**，标题 **15px/21px Medium** |
| 搜索框里的放大镜 | 有 | App 早就删了（图标把占位文字往右挤）|

勾选圈那条最值得记：它和 `Pick`（多选圈）是**两个东西** —— `Pick` 选中时确实是填充的强调色圆盘 ✓，
`Tick` 完成时**不是**。设计台把两者混成了一个 `.tick--on`。

#### ⑤ 首页标题基线：设计台和 App 现在**同一条推导**

改字号之前，设计台的首页标题是 21px，比次级页**高 5.5px**（方向和应用相反），当时用 19px padding 硬补的。
换成 38px 之后：14px → 35.0 vs 33.0，**低 2.0px** —— 和 Compose 完全同向同量，
因为就是同一条字体度量（38px 中文字形在 42px 行盒里墨迹偏下约 2px）。
两边现在都是 `PageHeaderPadding − 2`。实测 33.0 vs 33.0。

#### ⚠️ 量测陷阱：headless 截图里 `transition` 不推进

`--virtual-time-budget` 会推进 `setTimeout`，但**不推进 CSS transition**。
带 `transition: background` 的元素，`getComputedStyle` 会停在过渡起点的旧值上 ——
小组件底板就这样被量成浅色（实际是纯黑），一度以为 token 没生效。

排除方法：往页面里插一个内联 `style="background:var(--w-tile)"` 的探针元素。
变量本身是对的（`#000000`），探针也是黑的，**只有带 transition 的那个元素是浅的**；
`style.transition='none'` 之后立刻变黑 ⟹ 确认是量测假象。

**截图 / 量色一律走 `/tmp/bitsay/noanim.html`**：iframe 载入后注入
`*{transition:none!important;animation:none!important}` 再定位截图，
支持 `?dark=1&el=<id>` 和 `?dark=1&sel=<选择器>`。已写进 `design/README.md`。

#### 验证

- 颜色 token 探针：**30/30 全等**
- 标题基线探针：首页 33.0px = 次级页 33.0px
- `boardCheck`：0 个 JS 错误，8 个板块、3 台宽屏样机、8 台页面样机、3 个小组件全部渲染
- CSS 变量泄漏扫描：无新增未定义 `var()`
- 截图（关动画）逐张看过：小组件亮/暗、列表页、设置页、搜索页

#### 还没做

- **`design/js/icons.js` 里仍有 App 没有的图标**（`widget` 四宫格、`swap`、`selected/unselected`）。
  App 侧 `ic_swap` / `ic_selected` / `ic_unselected` 是**死文件**（grep 全仓库无引用，
  且 `ic_selected` 还留着旧的 `#7ED0A0` 绿色）。设计台那几个也无人调用。留着不影响，但该清。
- App 的 `res/values/colors.xml` 里 `paper` / `line` / `accent_ink` / `widget_row` 四个颜色
  **零引用**（`widget_row` 的值 #EDF0F8 还是旧的，行现在是白色）；`bg` 只被启动窗口用。

### 13.47 小组件空状态对齐 + 一次全仓清理

#### 小组件空状态：设计台自己编了一句

| | 设计台（错） | App（对） |
|---|---|---|
| 文案 | 一行「这里还什么都没有 / 去 App 里加一条吧」 | **标题 + 提示两行**，就是 `empty_notes_title/hint`（`**` 去掉）|
| 颜色 | 整块 `--c-ink-3` | 标题 `ink`、提示 `ink_soft` |
| 字号 | 12.5px 一行 | 标题 **14px bold**、提示 **11.5px**（间距 9 / 4）|
| 嫩芽 | 74 / 60 / 38 按高度分三档 | **固定 44dp**（宿主 0.91 倍 ≈ 40px），不分档 |
| 嫩芽颜色 | 继承容器的灰 | `--c-leaf`（全站唯一那点绿）|

App 布局里那句注释就是答案：「Its size is fixed at what fits the 3x3 minimum,
so there is no size-dependent branch here」—— 设计台凭空造了一个不存在的分支。

⚠️ 改的时候踩了个签名坑：`BitSay.sprout.svg({size})` 收**对象**，而我新加的
`I.widgetSprout(size)` 收**数字**。按对象传进去 `width="{size: 40}"` 无效，
SVG 撑满整块组件（描边被放大到填满叶子，看起来像实心绿芽）。**参数形式和函数名一样重要。**

#### 清理：先证明「真的没人用」，再删

审计必须同时覆盖 `R.string.x` **和** XML/manifest 里的 `@string/x` ——
第一版只查了前者，把 `widget_label`（manifest 在用）误判成死字符串。

| 删了什么 | 数量 | 怎么确认的 |
|---|---|---|
| `ic_swap` / `ic_selected` / `ic_unselected` | 3 个 drawable | 全仓库 0 引用（`ic_selected` 还留着旧配色 `#7ED0A0`）|
| 失效字符串（`action_save`、`deleted`、`export_fail`、`widget_switch`、`count_todos_short`、`backup_error_unknown` …）| 14 × 2 语言 | 同时查 `R.string.` 与 `@string/`；两语言 key 仍完全一致 |
| `colors.xml` 的 `paper` / `line` / `accent_ink` / `widget_row` | 4（+ night 的 `paper`）| 0 引用；`widget_row` 的值还是旧的 `#EDF0F8`（行早就是白的）|
| 设计台 `--sp-1`…`--sp-7` 整套间距刻度 | 7 | 全站 `var(--sp-*)` 出现 **0** 次，各页都直接写 px |
| 设计台 `I.widget`（四宫格图标）| 1 | 从未被调用 |

**没有删**但看起来"没人用"的，都查清了原因：`--c-accent-soft` 在设计台只出现在调色板展示列表里
（App 把它映射进 Material 的 `primaryContainer` 等，是活的）；`--bp-wide` 是断点的**文字记录**
（CSS 变量本来就不能用在 media query 里）。

#### 顺带补上一个缺失的组件

`--c-danger` 和 `--r-block` 在设计台**定义了却没人用** —— 因为**删除二次确认弹窗整个缺失**：
设计台点删除是直接删，App 是弹 `ConfirmDialog`（危险红确认）。这不是 token 冗余，
是组件缺口。所以补弹窗而不是删 token：

- `js/ui.js` 加 `confirm(host, title, hint, confirmLabel, onConfirm)`
- `css/components.css` 加 `.dialog--confirm`（`--r-block` 圆角、17px 标题、12.5px 说明、危险色确认）
- `pages/list.js` 的批量删除、`pages/editor.js` 的单条删除都先走确认

实测：确认按钮 `rgb(210,59,46)` = `#D23B2E` ✓、圆角 `22px 14px` ✓、标题「删除这 2 项？」✓

⚠️ 这里自己写了个 bug 并踩了一会儿：`editor.js` 里我写的是 `const U = BitSay.ui`（对象），
而 `list.js` 的约定是 `const U = () => BitSay.ui`（函数），于是 `U()` 抛
`U is not a function` —— **异常被事件派发吞掉**，点删除毫无反应，而页面看上去一切正常。

#### 新增验证工具（进仓库，不再放 /tmp）

| 文件 | 作用 |
|---|---|
| `design/tools/noanim.html` | 关掉 transition/animation 后定位截图 —— 修掉 headless 下量色量到过渡起点的问题 |
| `design/tools/smoke.html` | **冒烟测试**：装载 + 走一遍主要交互，每步收集 `window.onerror` 并断言关键元素出现 |

`smoke.html` 是冲上面那个坑做的：装载检查只能抓载入期异常，点击后才触发的错误会被吞。
现在 **22 项全通过**（含小组件空状态两行、删除二次确认、危险色、BlockShape 圆角、
勾选圈完成态无填充、首页标题 38px、深浅色切换、全程无异常）。

#### 清理后的复查

- `assembleDebug` / `assembleRelease` 均通过 —— 资源缺失会直接编译失败，所以这是强证据
- 设计台：0 个 JS 错误、`var()` 无新增未定义、调色板 30 个 token 与 `Color.kt` 全等
- `design/README.md` 补了工具用法和量测陷阱说明

### 13.48 组件列表里的预览图（`android:previewImage`）

需求：「桌面小组件在添加的时候，在组件列表里是支持设置预览图的吧，建议加一下预览效果图」。

#### 之前的状态：两个 widget_info 文件，且都缺预览图

| | `xml/widget_info_bitsay.xml` | `xml-v31/widget_info_bitsay.xml` |
|---|---|---|
| `description` / `previewLayout` / `targetCell*` / `widgetFeatures` | **都没有** | 有 |
| `previewImage` | 没有 | 没有 |

**而 `xml/` 那份在受支持的设备上永远不会被选中** —— minSdk 已经是 31，`-v31` 命中一切。
两份还漂移了（老的那份少 5 个属性），纯粹是负债。合并成一份，去掉 `-v31` 限定符。

#### 预览图画什么

- 画布 **180×180**，和组件的 `minWidth/minHeight=180dp` 一致，也就是它的最小形态
- 内容：底板 + 顶栏（进 App ｜ [笔记][待办] ｜ 搜索）+ 3 行笔记 + 右下角的 **+**
- 文字用**灰条占位，不写真实文案**：预览图不跟语言走，写死中文在英文系统上就露馅
- 浅色 / 深色两张（`drawable/` + `drawable-night/`）—— 按**系统**深色模式取。
  组件本身跟随的是 App 的设置，但静态图拿不到这个信息，只能按系统猜；
  `previewLayout` 被宿主 inflate 时也是同样的处境（§13.40 那个 bug 就是这么来的）

先在设计台画（`js/app.js` 的 `widgetPreviewSvg`，第 ④ 节末尾新增一块），确认之后再落成 vector。

#### 验证：把 vector 的 pathData 直接喂给 SVG 渲染

手算了圆角矩形 / 胶囊（stadium）/ 圆的 arc 路径和 `<group>` 的 translate+scale，
这部分最容易算错。用一个一次性 harness 把 `widget_preview.xml` 的
`<path>`/`<group>` 翻成 SVG（pathData 语法本来就相同，只需换颜色和变换），
和设计台那张**并排渲染对比 —— 完全一致** ✓

产物核对（`aapt2 dump`）：

```
debug   : previewImage=@0x7f04002d  →  () res/drawable/widget_preview.xml
                                      (night) res/drawable-night-v8/widget_preview.xml
release : previewImage=@0x7f04002d  →  () res/81.xml  (night) res/yi.xml   ← 资源名被混淆
```
两个 variant 都带上了 `previewImage` / `previewLayout` / `description` / `targetCell*` ✓

#### ⚠️ 这台设备上验证不了「选择器里显示预览图」

vivo 的组件选择器是**列表式**的（应用图标 + 名称），不显示预览图；AOSP 风格的宫格选择器才会用。
好消息是不用碰桌面就能打开它：

```bash
adb shell am start -a android.appwidget.action.APPWIDGET_PICK --ei appWidgetId 1
# → com.android.settings/AppWidgetPickActivity
```

（注意：这个列表里往上下滑会**把选择器关掉**并落到桌面。看完按 BACK 关掉，不要点条目 —— 那会真的添加组件。）

**顺带拿到的真实证据**：关掉之后桌面上那个组件正好是空状态 ——
绿色嫩芽 + 「还没有笔记」+「点右下角的 + 写下第一条」，
和设计台、App 三边完全一致（这正是 13.47 修的那处）。

#### ⚠️ 另一个坑：删掉带限定符的 res 目录后，构建缓存会还魂

`rm -rf res/xml-v31/` 之后 debug 和 release 都报
`AAPT: error: resource xml/widget_info_bitsay not found`，**连 `./gradlew clean` 都修不好** ——
Gradle 的构建缓存把陈旧的 `mergeResources` 结果又还原了回来。
必须 `./gradlew :app:mergeReleaseResources --rerun-tasks`（对应 variant）
强制重跑一次资源合并。

**结论：删/改带限定符的 res 目录（`-v31`、`-night`、`-zh` …）之后，
不要相信增量构建，直接强制重跑对应 variant 的 merge 任务。**

### 13.49 发版流水线：GitHub Actions（推 tag / 手动触发）

需求：「添加 GitHub Action 支持推送 tag 或手动打包发版（使用最新的 action 版本），
带 release note 和支持的安卓版本说明」+「考虑怎么把我本地的正式签名密钥放到 GitHub 上安全打包发版」
+「当前只是测试阶段，不要真的发布一个 public 的 release」+「action 不需要钉到 commit SHA，
钉在最新大版本的最新版本即可」。

这同时清掉了 §10 里挂着的那条：「CI / 版本号自动化（现在 `versionCode = 1`，手改）」。

#### 版本号：tag 是唯一事实来源

`app/build.gradle.kts` 接受 `-PversionName` / `-PversionCode` 覆盖，字面量只是**本地构建的默认值**：

```kotlin
val appVersionName = (findProperty("versionName") as String?) ?: "1.0.0"
val appVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 10_000
```

`versionCode` 由版本号**推导**，不手写：`major*10000 + minor*100 + patch`。
v1.2.3 → 10203，v2.0.0 → 20000 —— 只增不减（Android 唯一的要求），而且能读回版本号。
默认值取 10000 而不是 1，就是为了和这条公式对齐，否则本地包和 CI 包的 versionCode 会对不上。

实测：`-PversionName=1.2.3 -PversionCode=10203` → `versionCode='10203' versionName='1.2.3-debug'`；
不带参数 → `10000 / 1.0.0` ✓

#### 触发与"不会误发"

| 入口 | 行为 |
|---|---|
| 推 tag `v1.2.3` | 构建 + 签名 + 上传构建产物 + 建 **draft** release |
| 手动触发 | 自己填版本号；`publish` 选 `none` / `draft` / `release`，**默认 `none`** |

`none` 连 release 都不建，只在 Actions 页面留一个构建产物。推 tag 只出 **draft** ——
draft 只有协作者看得到，资产和 notes 都生成好了，确认无误再手动 Publish。
**「推了个 tag 结果冒出一个公开 release」这件事不会发生。**

#### 签名密钥：放 Environment，不放 repo secret

这是整个需求里最需要想清楚的一步，写进了 `.github/SIGNING.md`：

- 四个值：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
- 放**名为 `release` 的 Environment**，并配上 **Required reviewers**。
  workflow 里 `environment: release` 会让 job **先停下来等人点批准**，密钥批准后才下发。
  **即使误推了 tag，也不会有任何东西被签出来。**
- 密钥只以**环境变量**形式出现，不写进任何命令行参数（命令行会完整进日志；环境变量的值会被自动打码）
- `keystore.properties` 用 `printf` 生成而不是 heredoc —— 口令里若有引号/反斜杠，heredoc 会被再解释一遍
- ⚠️ **AGP 在没有 `keystore.properties` 时会安静地出未签名的包**，所以有一道 `apksigner verify`
  兜底，未签名直接让 job 失败
- 文档里也重申了密钥丢失的后果（同一个包名必须同一个签名，丢了就永远发不了更新）

#### action 版本

按用户要求钉在**最新大版本的最新小版本**（不钉 SHA）：checkout `v7.0.1`、
setup-java `v6.0.1`、gradle/actions `v6.4.0`、upload-artifact `v7.0.1`、action-gh-release `v3.0.3`。
版本号是查各仓库 release API 得到的，不是猜的。文件顶部列了清单，升级时一起改。

#### 真跑一遍才发现的两个问题

**① bash 会把 `$VAR` 后面的多字节字符算进变量名。**
release notes 里写 `$VERSION（versionCode $CODE）`，全角括号紧跟在变量名后面，
bash 把变量名解析成 `VERSION（` → `unbound variable`，CI 上会直接挂。
**中文文案里的变量必须写 `${VAR}` 带花括号。** 已加注释说明原因，并留了一个检查脚本。

**② 顶层 `permissions: contents: read` 会让建 release 的步骤没权限。**
改成顶层只读、**job 级** `contents: write`（最小权限）。

#### 验证方式：把 YAML 里的 run 段抽出来真跑

不是"看一眼觉得对"，而是用 ruby 把 `jobs.release.steps[].run` 逐段导出成 `.sh`，
替换掉 GitHub 表达式（`${{ ... }}`）后用**真实签名密钥**跑一遍：

| 步骤 | 结果 |
|---|---|
| 版本解析 | tag `v1.2.3` → `1.2.3 / 10203`；手动 `2.0.0` → `20000`；非法 `1.2` → `::error::` 退出 ✓ |
| 发布方式 | 推 tag → `draft`；手动 → 按输入 ✓ |
| 写密钥 + 构建 | `BUILD SUCCESSFUL`，产物 `versionCode=10000 versionName=1.0.0` ✓ |
| 验签 | V2 signer `CN=bitsay,…`，证书 SHA-256 `1f644851…`，APK SHA-256 已算出 ✓ |
| release notes | 生成完整（含支持版本 / 安装 / 校验 / 指纹）✓ |
| job summary | 表格正常 ✓ |
| 全部 run 段 `bash -n` | 8/8 通过 ✓ |

临时文件（`certs.txt` / `release-notes.md`）改用 `$RUNNER_TEMP`，不再落在仓库根目录 ——
实测跑完 `git status` 干净 ✓

本机状态已还原：`keystore.properties` 指回 `keystore/bitsay-release.jks`，测试用的
`keystore/release.jks` 已删除，本地 `assembleRelease` 复测正常 ✓

#### 还没做

- **没有加 PR/push 的基础 CI**（只做了发版）。要的话是一个独立的 `ci.yml`：`assembleDebug` + lint。
- 密钥目前**只有本机一份**。文档里强调了要另行备份，但备份本身得你自己做。
