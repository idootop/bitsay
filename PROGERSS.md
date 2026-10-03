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
| 点搜索图标 | 搜索 | ✅（同时展开搜索栏） |
| App 列表点条目 | 查看 | ❌ |
| 桌面小组件点条目 | 查看 | ❌ |

实现：

- `AppUiState.autoFocusEditor`（只有 `startNew()` 置 true）+ `editorSession`（每次进入编辑器自增）；
- `EditorScreen` 用 `LaunchedEffect(state.editorSession)` 做一次性 `focusRequester.requestFocus()`
  + `keyboard?.show()`；
- **为什么用 `editorSession` 当 key 而不是 `LaunchedEffect(Unit)`**：如果编辑器已经开着，
  再从桌面点 `+`（`singleTask` → `onNewIntent`），Compose 不会重建 `EditorScreen`，
  `Unit` 版本的副作用不会重跑，键盘就弹不出来；
- 搜索栏：`SearchField` 在 `if (state.searchOpen)` 分支里，展开即新组合，
  `LaunchedEffect(Unit)` 请求焦点并 show 即可。

> 注意一个符合 Android 惯例的细节：键盘弹起时，**第一次按返回是收起键盘**，第二次才退出编辑器。
> 这是系统行为，不要用 `BackHandler` 去抢。

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

## 7. 备份格式（schema 1）

```json
{
  "app": "bitsay",
  "schema": 1,
  "appVersion": "1.0.0",
  "exportedAt": 1790849207689,
  "count": 6,
  "items": [
    {"id": 4, "kind": "todo", "text": "买牛奶和鸡蛋", "done": true,
     "createdAt": 1790848816926, "updatedAt": 1790849010549, "doneAt": 1790849010549}
  ]
}
```

- 默认文件名 `bitsay-yyyyMMdd-HHmm.json`
- 导出走 `ACTION_CREATE_DOCUMENT`，导入走 `ACTION_OPEN_DOCUMENT`，**不需要任何权限**
- 解析器刻意宽容：`kind` 接受 `"todo"` / `1` / 缺失；缺 `updatedAt` 时回落到 `createdAt`；
  `done` 接受 `true` / `1` / `"true"`；没有 `text` 或 `text` 全空白的条目直接丢弃
- `schema` 比当前大 ⇒ 明确报错「备份来自更新的版本」，不猜
- 导入两种模式：**合并**（默认，按 id + `updatedAt` 取新）/ **覆盖**（清空重灌）

加字段时：`SCHEMA` 常量 +1，`decodeItem` 里给默认值（保持向后兼容），并更新本节。

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
- [x] **63 个单元测试全绿**（repo 23 / json 10 / codec 9 / manager 5 / time 10 / widget 6）
- [x] ui 状态层：`AppViewModel`（StateFlow 单一状态源）+ 自动保存调度 + 生命周期 flush
- [x] Compose 三个页面：列表（笔记/待办分段 + 搜索 + FAB）、编辑器、设置
- [x] 桌面小组件全部交互（切换/新建/查看/勾选/缩放）+ 放置配置页
- [x] 应用图标（纯 vector 自适应图标，含 monochrome 主题图标）
- [x] 真机（Android 16 / API 36）全流程验证 —— 见 §11
- [x] release 包体积 **2.11 MB**，R8 + resource shrinking，签名并实机跑通
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

### ⚠️ 已知限制（不是 bug，是外部行为）

- **`requestPinAppWidget` 在 OriginOS（vivo）上会「假装成功」**：API 返回 `true`，
  但桌面既不弹确认框也不真的添加（实测小组件实例数不变）。
  该 API 在原生/Pixel 桌面上正常。因此设置页那句话保留手动兜底文案：
  「已请求添加到桌面；若桌面没有反应，请长按桌面空白处手动添加」。
  代码本身是对的，**不要为了这个再改逻辑**。
- 小组件被宿主（OriginOS）以约 **0.91 倍密度**渲染：声明 48dp 实测约 43.7 物理 dp。
  这是桌面自己的缩放，换桌面就不同，所以**保持声明的 48dp 不要补偿**。

### ⏳ 待办（按建议优先级）

- [ ] **UI/UX 细化**：打开 `design/ui-preview.html` 验收可爱 / 手绘方向，定稿后回填到
      `ui/theme/*` 与 `res/drawable/*`（当前 Android 端是「可用的第一版」，HTML 是设计源）
- [ ] 备份：可选的「自动定期备份到 SAF 目录」（现在只有手动导出）
- [ ] 列表：长按多选 / 批量删除；单条分享为文本
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
| **点搜索图标：搜索栏展开 + 键盘弹出 + 光标就位** | ✅ |
| 搜索输入过滤生效（输入 zz → 「没有找到相关内容」） | ✅ |
| 关闭搜索键盘自动收起 | ✅ |
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

未验证 / 待补：小组件**手动拖拽缩放**的中间档位逐级走查（只验证了当前档位的渲染结果与 `WidgetSize` 单测）、
深色模式逐屏、Android 12~15 真机（本机只有 Android 16，只能靠 minSdk 与实际 API 使用面推断）。

---

## 12. 下一步建议（给接手的 agent）

1. **先跑一遍 §2 的构建命令**，确认 63 个测试全绿、release 能出包。环境问题优先解决 `JAVA_HOME`。
2. **UI 方向**：打开 `design/ui-preview.html`（浏览器直接打开，无需构建）。
   用户明确要求「先在 HTML 上预览调整验收，确定之后再迁移到 Android」。
   定稿后按 §13 的映射表回填。
3. **没做完的功能**在 §10「待办」里，按用户的实际反馈排序，不要自己加需求。
4. 改任何业务规则，**只改 `ItemRepository`**，然后补 `ItemRepositoryTest` 的用例。
   不要为了图快把规则写进 ViewModel 或小组件。
5. 改小组件前，务必先读 §8 的「踩过的坑」。
6. 每次阶段性完成，回来更新 §10 / §11 / §12 和顶部的「最后更新」。

---

## 13. HTML 设计稿 → Android 映射表

`design/ui-preview.html` 是设计源；Android 端对应位置：

| HTML 里的元素 | Android 对应 |
|---|---|
| `--bg` / `--paper` / `--ink` … 调色板 | `ui/theme/Color.kt` + `res/values/colors.xml`（小组件共用） |
| 卡片不规则圆角 | `ui/theme/Theme.kt` 里的 `CuteShape` / `CuteShapeSmall` |
| 卡片描边 1.5dp、13% 墨色 | `CuteCard`（`ui/components/Common.kt`） |
| 纸纹点阵背景 | `PaperBackground` 的 `Canvas` |
| 分段切换（笔记/待办） | `SegmentedTabs` |
| 列表卡片配色循环 | `ListScreen.kt` 的 `CardColors` / `TODO_COLORS` |
| 小组件外观 | `res/layout/widget_bitsay.xml`、`widget_item.xml`、`res/drawable/widget_*.xml` |

**改渲染细节**（XML/代码）即可；**改颜色**记得两边同步（`colors.xml` 与 `Color.kt`）。
