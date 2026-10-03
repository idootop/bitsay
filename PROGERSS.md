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

实现位置：`AppViewModel.setDraft / flushDraft / persistDraft` + `ItemRepository.saveDraft`。

行为：

1. **每次按键** → 600 ms 防抖（`AUTO_SAVE_DEBOUNCE_MS`）后写库；
2. 新笔记**打出第一个字就建行**，之后走 update，不会产生重复记录；
3. `ON_PAUSE` 时立即 flush（`LifecycleEventEffect`，在 `BitSayRoot` 里）；
4. 文本没变则**完全不碰数据库**（不写、不 bump `dataVersion`、不刷新小组件）；
5. 编辑器底部显示状态：`正在输入…` / `✓ 已自动保存 · 刚刚`；
6. 底部按钮语义从「保存」改为「**完成**」——保存已经不需要用户操心了。

边界情况（已实现且有测试）：

- 新建的笔记，打完又全部删光再退出 ⇒ 自动删掉这条空记录；
- 已有笔记被清空后退出 ⇒ **保留最后一版非空内容**，并提示「内容为空」；
- 空白文本 + `id = 0` ⇒ 什么都不建。

**真机验证**：输入 "IdeaautosaveXYZ" 后直接 `am force-stop`（模拟划掉/崩溃），
重启 App 该条笔记完整存在（见 §11）。

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
| 显示笔记 / 待办 | 点标题或 ⇄ 图标 → 发 `ACTION_TOGGLE_KIND` 广播 → 写 `WidgetPrefs` → 重绘 |
| 新建 | `+` → `PendingIntent.getActivity` 打开 `MainActivity`，带 `ACTION_NEW` + 当前 kind |
| 点击查看 | 列表 template（**必须 `FLAG_MUTABLE`**）+ 行 fill-in intent → 广播 → `startActivity` 打开对应条目 |
| 勾选待办 | 行内圆圈自己一个 fill-in intent → `ACTION_ITEM_CLICK` + `ITEM_ACTION_TOGGLE_DONE` → `goAsync()` 写库并刷新 |
| 宫格缩放 | `onAppWidgetOptionsChanged` → `WidgetSize.from(minWidth, minHeight)` → 三档：`COMPACT`(1 行/无头) / `REGULAR`(2 行) / `EXPANDED`(3 行+时间) |
| 数据联动 | `BitSayApp` 订阅 `repository.dataVersion`，防抖 120 ms 后 `WidgetUpdater.refreshAll()` |

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
