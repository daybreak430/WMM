# WMM 记账

安卓原生个人账本。记一笔时只需输入金额、选一个自建标签；账户可以留空。账本显示本月结余和最近流水，统计支持日、月、年以及按标签查看明细。资产账户余额可手动校准。

## 使用

- 个人设置在账本页右上角。这里可以设置头像和昵称、管理标签、配置模型、导出和恢复备份。
- 如需联网报告，选 DeepSeek 或 MiniMax 国内站并保存对应 API Key。在统计页选择日或月，再手动生成。首次生成展示发送范围；模型只收到期间汇总和标签名称。
- 主动导出的 JSON 备份未加密，可用于迁移设备。恢复会替换本机数据。API Key 不包含在备份中。

## 构建

Android 8.0（API 26）起。使用 Android Studio 自带 JDK、Android SDK 37 及 Build Tools 36.0.0。安装 SDK 后打开仓库根目录，或设置 `JAVA_HOME` 和 `ANDROID_HOME` 后运行：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

本机签名出包：在 macOS 运行 `./scripts/build-release.sh`，生成 `release/WMM-<版本>-release.apk`。脚本首次运行时创建项目专用签名密钥；密钥库位于 `~/Library/Application Support/WMM/signing`，密码存放在登录钥匙串。请保管好这两项，后续覆盖安装需要同一签名。调试包使用独立包名 `com.wmm.ledger.debug`。

[实现设计与数据规则](docs/architecture.md)
