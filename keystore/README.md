# 正式分发签名（Release signing）

这个目录放**长期正式环境的分发签名**。里面的 `.jks` 被 `.gitignore` 挡住，
**不会进版本库**——这是刻意的：密钥和口令一旦进了 Git，就等于公开了，
即使之后删掉，历史记录里依然能翻出来。

## 文件说明

| 文件 | 是否入库 | 说明 |
|---|---|---|
| `bitsay-release.jks` | ❌ 否 | RSA 4096 / SHA256withRSA，别名 `bitsay`，有效期 **36500 天（至 2126-09-07）** |
| `README.md` | ✅ 是 | 本文件 |
| `../keystore.properties` | ❌ 否 | 口令与路径，Gradle 从这里读 |

证书指纹（SHA-256）：

```
1F:64:48:51:0F:EE:FC:8D:CA:FD:FA:D9:86:20:17:65:AD:58:DE:8C:4F:28:85:93:D1:82:0B:8A:9B:56:73:2F
```

## ⚠️ 必须做的备份

`keystore/bitsay-release.jks` + `keystore.properties` **请立刻另外存一份**
（密码管理器 / 私有网盘 / U 盘，至少两处）。

丢了会怎样：**永远无法再给 `com.del.bitsay` 发布更新**。
已安装的用户无法升级，只能引导他们卸载重装（数据会丢）。没有补救办法。

## 在新电脑上恢复签名

```bash
# 1) 把备份的 bitsay-release.jks 放回 keystore/
# 2) 在项目根目录按模板创建 keystore.properties 并填入真实口令
cp keystore.properties.example keystore.properties
$EDITOR keystore.properties

# 3) 验证配置生效（应输出签名信息，而不是 "unsigned"）
./gradlew :app:assembleRelease
$ANDROID_HOME/build-tools/37.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

没有 `keystore.properties` 时，`assembleRelease` **不会失败**，只是产出一个
未签名的 APK（`app/build.gradle.kts` 里做了判断），方便纯构建场景。

## 轮换密钥（Key rotation）

现在用的是 APK Signature Scheme **v2**（minSdk 31 够用）。
若未来必须换密钥（例如密钥泄漏），需要改用 **v3 + rotation** 并在
`app/build.gradle.kts` 里配置 `signingConfigs` 的 lineage，
同时保证旧密钥仍然可用——本仓库尚未配置，届时请先读 AGP 文档。
