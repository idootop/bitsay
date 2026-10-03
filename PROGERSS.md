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
| ① 可交互真机 | 列表 ⇄ 编辑器 ⇄ 搜索 ⇄ 设置 真的能走通；旁边是真实桌面 + 小组件 |
| ② 全部页面总览 | 7 台手机：笔记 / 待办 / 多选 / 编辑器（查看）/ 编辑器（快捷记录）/ 搜索 / 设置 |
| ③ 桌面小组件 | 三块**真实桌面**（蓝天草地壁纸 + 图标列 + Dock），小组件分别 2×5 / 2×3 / 2×2 |
| ④ 组件库 | 卡片、分段 tab、图标按钮、勾选圈、空状态 |
| ⑤ 设计令牌 | 与 `Color.kt` 一一对应的色板（切版本/亮暗会跟着变） |
| ⑥ 版本取舍 | 四个方向的做法、Compose 成本、取舍 |

**只有一份样式**（= 当前 App 的样子），没有多套主题。调观感优先改 `design/css/tokens.css`；
单页微调去 `design/css/pages/<页面>.css` 顶部的「本页可调参数」；深色只在 `design/css/dark.css` 里替换 token。

### 13.2 文件分工（改东西先看这张表）

| 想改什么 | 改哪里 |
|---|---|
| 颜色 / 圆角 / 间距节奏 / 字号 | `design/css/tokens.css` |
| **某一个页面**的样式 | `design/css/pages/<页面>.css` 顶部的「本页可调参数」 |
| 跨页面组件（卡片 / tab / FAB） | `design/css/components.css` |
| 深色模式 | `design/css/dark.css`（只替换 token，页面样式不动） |
| 图标 | `design/js/icons.js` |
| 数据规则（排序 / 搜索 / 增删） | `design/js/store.js` |
| 某页结构与交互 | `design/js/pages/<页面>.js` |

### 13.3 对应到 Android

| 设计台 | Android |
|---|---|
| `tokens.css` 的 `--c-*` | `ui/theme/Color.kt`（`BitSayPalette`）+ `res/values/colors.xml`（小组件） |
| `--r-card` / `--r-card-sm` | `ui/theme/Theme.kt` 的 `CuteShape` / `CuteShapeSmall` |
| `--bw` / `--bc`（描边） | `CuteCard` 的 `border(1.5.dp, Ink.copy(alpha=.13f))` |
| `.app-root::before` 点阵 | `PaperBackground` 的 `Canvas` |
| `.seg` / `.seg__item` | `ui/components/SegmentedTabs.kt` |
| `js/ui.js` 的 `card()` | **`ui/components/ItemList.kt`**（首页与搜索页共用，只有这一处实现） |
| `js/pages/list.js` | `ui/screen/ListScreen.kt`（含多选顶栏） |
| `js/pages/editor.js` | `ui/screen/EditorScreen.kt`（400ms 节流自动保存） |
| `js/pages/search.js` | `ui/screen/SearchScreen.kt`（切分类保留关键词） |
| `js/pages/settings.js` | `ui/screen/SettingsScreen.kt` |
| `js/pages/widget.js` | `res/layout/widget_bitsay.xml` + `widget_item.xml` + `WidgetRenderer.kt` |
| `css/pages/homescreen.css` | 无（只是演示台用的真实桌面外壳） |
| `js/store.js` | `core/repo/ItemRepository.kt` + `AppViewModel` 的状态部分 |

> **同步规则**：颜色只改 `Color.kt` 和 `colors.xml`，设计台跟着改 `tokens.css`；
> 三者必须一致，否则设计稿就失去对照意义。

---

## 14. 多语言（i18n）

支持**简体中文 / English**，默认**跟随系统**。

### 14.1 资源布局

| 目录 | 角色 |
|---|---|
| `res/values/strings.xml` | **默认集 = 英文**。任何 locale 都匹配不到时落到这里 |
| `res/values-zh/strings.xml` | 中文 |

**默认集必须是英文**，因为"找不到就 fallback 到英文"是靠「默认资源集」实现的，
不是靠额外的 fallback 机制。新字符串一律**先加英文那份**。

`build.gradle.kts` 里把 `MissingTranslation` / `ExtraTranslation` 提升为 lint **error**，
防止两份资源悄悄漂移（另有 92 条键集的交叉校验）。

### 14.2 踩过的坑：文案藏在 Kotlin 里

i18n 的难点不是翻译，是**把埋在代码里的中文挖出来**。这次挖出四类：

1. **`core/util/TimeText`** —— "刚刚 / N 分钟前 / 今天 / 昨天" 和 `M月d日` 全是硬编码，
   而它是纯 Kotlin 且被单测覆盖（JVM 测试读不到 Android 资源）。
   解法：抽出 `TimeWording` 数据类由调用方注入；**日期 pattern 也进资源**
   （`time_pattern_month_day` = `MMM d` / `M月d日`），配合传入的 `Locale` 渲染。
   这样时间规则保持纯净可测，中英两套文案各有一组单测。
2. **核心层的异常消息** —— `BackupException` 原来直接带中文句子。
   改成携带 `BackupError` 枚举（`NOT_A_BACKUP / EMPTY / CORRUPT / NEWER_SCHEMA / IO`），
   由 UI 层查资源成句（`i18n/Strings.kt`）。
3. **列表计数、设置分区、关于页、小组件配置页** —— 直接用 `stringResource` 补全。
4. `Notice.Failed` 从"带一句现成的话"改成"带一个可命名的原因"。

### 14.3 语言设置怎么生效

- 选择存在 `bitsay_settings.xml`（`LanguagePrefs`），`SYSTEM` = 不写键。
- 通过 **`attachBaseContext` + `createConfigurationContext`** 应用，
  **没有引入 AppCompat**（`AppCompatDelegate.setApplicationLocales` 需要 AppCompat Activity
  和一整个 support library，为一个设置不值当）。
- 同时 `Locale.setDefault`：日期格式化走的是 `java.time`，读的是进程默认 locale 而不是
  Context 配置，两者必须一起改，否则界面会中英混排。
- 改语言后由 ViewModel 发 `relaunch` 事件 → Activity `recreate()`。
  屏幕上每个字符串和每个 formatter 都是按旧配置构建的，重建才是唯一诚实的做法。
- **小组件要特殊处理**：宿主（桌面）用的是**它自己的** configuration 来 inflate 布局，
  所以 XML 里写的 `android:text="@string/…"` 永远跟随系统语言。
  `WidgetRenderer` 因此用 `context.withAppLanguage()` 显式 `setTextViewText` 两个 tab 和空状态。
  实测：系统中文 + App 设为 English → 小组件显示 `Notes` / `Todos`。

四个 Activity（MainActivity / WidgetEntryActivity / WidgetConfigActivity）都覆写了
`attachBaseContext`，**新增 Activity 时别忘了**。

### 14.4 切语言必须主动刷新小组件

Activity 靠 `recreate()` 换语言，但**小组件不会自己变**：它的字符串已经被写进
RemoteViews 交给宿主了，除非重新渲染一次，否则它会一直停在旧语言。
`AppViewModel.setLanguage()` 因此在发 `relaunch` 事件的同时
`WidgetUpdater.refreshAll()`。

这条容易被漏掉，因为它和"数据变了就刷新"是两条独立的触发路径 ——
语言切换**不**会产生 `repository.change`，订阅数据的那个观察者不会醒。

---

## 15. 亮暗色模式

三档：**跟随系统（默认）/ 浅色 / 深色**，存在 `bitsay_theme.xml`，`SYSTEM` = 不写键。

### 15.1 让 100 多个调用点不用改

原来 `Ink` / `InkSoft` / `Paper` / `Bg` / `Line` / `Sun` / `Mint` … 都是**顶层颜色常量**，
散落在各页面里（光 `Ink` 就 54 处）。逐个改成 `MaterialTheme.colorScheme.xxx` 既啰嗦又容易漏。

改成**主题感知取值器**：

```kotlin
val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val CardColors: List<Color> @Composable @ReadOnlyComposable get() = LocalPalette.current.cards
```

`BitSayTheme` 用 `CompositionLocalProvider(LocalPalette provides …)` 注入亮/暗两套
`BitSayPalette`。**调用点一行没动**，整套 UI 就跟着主题走了。

只有一处例外：`SegmentedTabs` 的 `accent` 是普通 lambda（非 @Composable），
在里面调不了取值器 —— 在 `ListScreen` 顶部先取到局部变量再传进去。

### 15.2 深色不是"把颜色反一下"

浅色粉彩（#FFD34E 等）直接放到深色背景上会像"屏幕上挖了六个洞"，
而且它们配的是**深色文字**，深色模式下文字是浅色的，两者一撞就不可读。

所以 `DarkPalette` 里的粉彩是**同色相压暗**的版本（`#FFD34E → #6B5320` 等），
文字统一用浅色。**重点色（sun）也一起压暗** —— 我第一版留了亮黄，结果设置页那张
概览卡变成"亮黄底 + 近白字"，截图一看就废了。

### 15.3 小组件跟随的是 App 设置，不是系统

宿主用**它自己的** configuration inflate 布局，所以任何走资源（含 `values-night`）的颜色
都会跟随**系统**夜间模式，而不是 App 里的选择。

解法：**颜色全部在渲染时显式赋值**。关键发现是 `View.setBackgroundTintList` 是
`@RemotableViewMethod` —— 于是：

- 所有圆角形状 drawable 改成**纯白**，运行时用 `setColorStateList(..., "setBackgroundTintList", …)` 染色；
- 颜色直接取 `paletteFor(dark)`，也就是**和 App 同一套 `BitSayPalette`**，
  亮暗同步是构造上保证的，不会漂移；
- 顺带**删掉 8 个 drawable**（`widget_item_bg_1..5`、`widget_tab_notes_on/todos_on/off`
  合并成一个 `widget_item_bg.xml` / `widget_tab_bg.xml`）。

只有根卡片保留了亮/暗两个文件（它有描边，染色会把描边一起吃掉）。
FAB 保持不变的亮黄 —— 它是重点色点缀，两种模式下都成立。

### 15.4 切主题要刷新小组件

和语言一样，主题切换**不产生 `repository.change`**，数据观察者不会醒。
`setThemeMode()` 里主动 `WidgetUpdater.refreshAll()`。

和语言不同的是：**不需要 `recreate()`** —— Compose 会直接按新调色板重组。

窗口背景另外处理：Activity 在 `setContent` 之前按解析出的模式 `setBackgroundDrawable`，
否则深色启动会先闪一下主题资源里的浅色 `windowBackground`。
`values-night/colors.xml` 只负责系统夜间模式下的启动底色。

### 15.5 一个工具坑

用脚本批量清理"未使用 import"时要小心：`kotlinx.coroutines.flow.getValue` /
`androidx.compose.runtime.getValue` / `setValue` 是 **`by` 委托用的操作符扩展**，
文本里根本不出现这两个名字，正则判定为"未使用"，删掉后整个文件编译不过。

---

## 16. 搜索页

### 16.1 为什么独立成页

最早搜索是列表头里一个展开的输入框，结果和正常列表抢同一块空间，而且**没法明确地"放弃这次搜索"**。
现在 `Screen.Search` 是一页：整屏给结果、自己的结果集、一个明确的返回键。

入口有两个，**左右对称**：App 列表头右上角的搜索图标，和小组件顶栏右侧的搜索按钮
（`WidgetContract.ACTION_SEARCH` → `MainActivity` → `viewModel.openSearch()`）。
小组件顶栏顺序：`进 App | 笔记 | 待办 | 搜索`。

### 16.2 结果行复用首页的行（不要再设计第二套）

**`ui/components/ItemList.kt` 是唯一的列表实现。**

```kotlin
ItemList(
    items        = …,
    onClick      = { … },
    onToggleDone = { … },
    selecting    = …,          // 首页多选态
    selection    = …,
    onLongClick  = …,          // null = 不支持长按（搜索页）
    bottomPadding = 104.dp,    // 首页要给 FAB 留位；搜索页传 32.dp
)
```

- `ListScreen` 和 `SearchScreen` 都调它，行样式（色系轮转、圆角、描边、单行省略、
  创建时间、待办完成圈）**只有一处实现**；
- **点击语义留在调用方**，因为两个页面的语义本来就不同：首页在多选态下"点 = 选中"，
  搜索页"点 = 打开编辑器"；
- 曾经的 `SearchScreen.ResultCard`（更小的圆角、两行、无时间戳的"轻量行"）**已删除** ——
  它的存在只会让两套样式慢慢漂移。

### 16.3 切分类不清空关键词

```kotlin
fun selectSearchTab(kind: Kind) {
    if (_state.value.tab == kind) return
    _state.update { it.copy(tab = kind) }
    if (_state.value.query.isBlank()) { …clear…; return }
    searchJob?.cancel(); searchJob = viewModelScope.launch { runSearch() }
}
```

- **不能复用 `selectTab()`**：那个是首页的"切换显示哪个列表"，语义里包含了
  `query = ""`（历史遗留：搜索还在列表头里的时候，换 tab 必须丢掉搜索）。
  搜索页的 tab 是"在哪个分类里搜"，关键词必须留着。
- 切 tab 走**立即查询**，不走输入防抖（`SEARCH_DEBOUNCE_MS`）：点 tab 是一次确定动作，
  不是连打键盘；防抖会让切过去之后空一下。
- 旧分类的结果保留到新结果返回，避免闪一下"没有找到相关内容"。

### 16.4 一个 IME 相关的观感问题（不是 bug）

微信输入法的**候选词条是浮在光标附近的**。搜索框在页面顶部，所以候选条会浮在顶栏下面，
**正好盖住「笔记 / 待办」分类 tab**。真机截图见 §11 的验证过程。

这是输入法自己的悬浮窗（`type=2011` 的 IME window），App 侧盖不住也挪不动；
换输入法或关掉"候选栏跟随光标"即可。**不要为此改布局。**

