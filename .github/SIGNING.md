# 把正式签名密钥交给 GitHub 打包

发版 workflow 需要四个值才能签出**和本地一致**的 release 包。这份文档只讲怎么把它们安全地放上去，
以及为什么这么放。

> ⚠️ **密钥丢 = 永远无法给 `com.del.bitsay` 发更新。** 用同一个包名发新版，签名必须一致，
> 否则用户只能卸载重装（数据全丢）。`.gitignore` 把密钥挡在库外是对的，但代价是
> `keystore/bitsay-release.jks` 和它的口令**必须另外备份**（密码管理器 / 离线介质 / 私密仓库），
> 不能只存在这一台电脑上。

---

## 一、要配什么

| 名称 | 内容 | 怎么来 |
|---|---|---|
| `KEYSTORE_BASE64` | `.jks` 文件的 base64 | 见下面的命令 |
| `KEYSTORE_PASSWORD` | `keystore.properties` 里的 `storePassword` | 你自己设的 |
| `KEY_ALIAS` | `keyAlias` | 你自己设的 |
| `KEY_PASSWORD` | `keyPassword` | 你自己设的 |

生成 base64（**单行**，不带换行）：

```bash
base64 -i keystore/bitsay-release.jks | tr -d '\n' | pbcopy   # macOS，直接进剪贴板
base64 -i keystore/bitsay-release.jks | tr -d '\n'            # Linux
```

> macOS 的 `base64` 默认就会折行，`tr -d '\n'` 是必须的。workflow 里也做了 `tr -d '[:space:]'`
> 兜底，但别依赖它。

---

## 二、放在 **Environment** 里，不是普通 repo secret

这是整件事最重要的一条。

普通 repo secret 对**任何**能跑 workflow 的地方都可见。而这个仓库里有一个长期有效的
分发签名密钥 —— 一旦泄露，别人就能签出「看起来是你发的」安装包，而且**没有吊销手段**。

所以：

1. 仓库 → **Settings → Environments → New environment**，名字填 **`release`**
2. 在这个 environment 里加那四个 secret（**不是**在 Repository secrets 里加）
3. 给这个 environment 配 **Required reviewers**，填上你自己

配好之后，`.github/workflows/release.yml` 里那句 `environment: release` 会让 job
**先停下来等人点批准**才继续，密钥也只有在批准之后才会下发到 runner。

**效果：即使误推了一个 `v1.0.0` tag，也不会有任何东西被签出来、被发出去，除非你亲手点了批准。**

命令行配置（需要 `gh` 且已登录）：

```bash
gh api -X PUT repos/:owner/:repo/environments/release

for name in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  gh secret set "$name" --env release --repo :owner/:repo
done

# 加必填审批人（把 <你的用户名> 和 id 换掉，id 用 gh api user --jq .id）
gh api -X PUT repos/:owner/:repo/environments/release \
  -f 'reviewers[][type]=User' -F 'reviewers[][id]=<你的数字 id>'
```

---

## 三、workflow 那边怎么用

密钥**只以环境变量形式出现**，不写进任何命令行参数 —— 命令行会被完整打进日志，
环境变量的值 GitHub 会自动打码成 `***`：

```yaml
env:
  KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
  KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
  KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
  KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
```

然后 `printf` 出 `keystore.properties`（**不用 heredoc**：口令里如果有引号或反斜杠，
heredoc 会被 shell 再解释一遍），`storeFile` 指向 runner 上临时解出来的那份。
跑完密钥留在 runner 上，runner 是一次性的，任务结束即销毁。

**签名没生效要能立刻发现。** AGP 在没有 `keystore.properties` 时会安静地出一个
**未签名**的包，所以 workflow 里有一道 `apksigner verify`，未签名直接让 job 失败。

---

## 四、不要做的事

- ❌ **不要**把 `.jks` 或 `keystore.properties` 提交进库（`.gitignore` 已经挡住，别去动那几行）
- ❌ **不要**在 workflow 里 `echo` 任何密钥；调试时也别 `set -x`
- ❌ **不要**用 `pull_request_target` 触发任何带这些 secret 的 workflow ——
  那个触发器会把 secret 交给 fork 过来的代码
- ❌ **不要**把密钥加到 Repository secrets 就完事；那样少了一道人工审批

---

## 五、验证 CI 签出来的包和本地是同一个签名

两边都跑一遍，比对 SHA-256 指纹：

```bash
apksigner verify --print-certs app/build/outputs/apk/release/*.apk \
  | grep 'certificate SHA-256'
```

当前正式密钥的指纹（`CN=bitsay, OU=bitsay, O=del, L=Beijing, ST=Beijing, C=CN`）：

```
1f6448510feefc8dcafdfad986201765ad58de8c4f288593d1820b8a9b56732f
```

CI 每次发版也会把这个指纹写进 release notes 和 job summary，用户拿到包可以自己核。

---

## 六、密钥本身

### 文件在哪

| 文件 | 入库 | 说明 |
|---|---|---|
| `keystore/bitsay-release.jks` | ❌ | 密钥本体 |
| `keystore.properties` | ❌ | 口令 + 路径，Gradle 从这里读 |
| `keystore.properties.example` | ✅ | 模板，口令位置写的是 `CHANGE_ME` |
| `.github/SIGNING.md` | ✅ | 本文件 |

`keystore/` **整个目录都不进版本库**（`.gitignore` 里一条 `keystore/`），连目录里的说明文件也不进。
目录留在本地，git 不看它。

> 这一段原来写在 `keystore/README.md` 里，而那个文件跟着目录一起被移出了版本库 ——
> 也就是说它只存在于「丢了就没了」的那份拷贝里。签名手册本身必须入库，所以挪到这里。

### 规格

| | |
|---|---|
| 算法 | RSA 4096 / SHA256withRSA |
| 别名 | `bitsay` |
| 有效期 | **36500 天（至 2126-09-07）** |
| 签名方案 | APK Signature Scheme **v2**（minSdk 31，v1/JAR 不需要） |
| 证书 DN | `CN=bitsay, OU=bitsay, O=del, L=Beijing, ST=Beijing, C=CN` |

### 在新电脑上恢复签名

```bash
# 1) 把备份的 bitsay-release.jks 放回 keystore/
# 2) 按模板创建 keystore.properties，填入真实口令
cp keystore.properties.example keystore.properties
$EDITOR keystore.properties

# 3) 验证配置真的生效
./gradlew :app:assembleRelease
"$ANDROID_HOME"/build-tools/37.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

⚠️ 没有 `keystore.properties` 时 `assembleRelease` **不会报错**，只是产出一个
**未签名**的 APK（`app/build.gradle.kts` 里做了判断，方便纯构建场景）。
所以第三步必须真的看一眼 `apksigner` 的输出，或者比对指纹是不是上面那一串。
CI 里有一道 `apksigner verify` 专门拦这个，本地没有。

---

## 七、轮换密钥

现在用的是 **v2** 方案。若将来必须换密钥（例如泄漏），不能直接换 ——
同一个包名换了签名，老用户装不上更新，只能卸载重装（数据全丢）。

正确做法是 **v3 + rotation**：在 `app/build.gradle.kts` 的 `signingConfigs` 里配置
`lineage`，让新 APK 同时带上新旧两条签名链，老设备仍认旧签名、新设备逐步迁到新签名。

**本仓库尚未配置**，真需要时先读官方文档：

- [APK Signature Scheme v3](https://source.android.com/docs/security/features/apksigning/v3) —— 轮换（rotation）本身的规则
- [Sign your app](https://developer.android.com/build/building-cmdline) —— AGP 侧怎么配

在配好之前，**现有密钥不能丢也不能换**。

---

## 八、现在默认就是公开发布

App 已经过了测试阶段，workflow 的默认值改成**直接发公开 release**：

- 推 tag → 建 **公开** release
- 手动触发 → `publish` 默认 `release`（想要草稿就选 `draft`，只想拿构建产物就选 `none`）

**唯一剩下的闸是上面那个 Environment 的人工批准** —— 不点批准，密钥不下发，
既不会签名也不会发任何东西。所以「误推一个 tag」最多让你收到一条待批准通知，
点忽略就结束了。

想回到「默认只出草稿」：把 `.github/workflows/release.yml` 里 `publish` 的 `default`
改成 `draft`，并把「决定发布方式」那一步里推 tag 的 `MODE="release"` 改成 `MODE="draft"`。
