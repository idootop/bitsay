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
