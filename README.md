# RubiKey Android

RubiKey 是一个 Android 工具客户端，用于把智能蓝牙魔方的转动映射为设备上的全局点击或滑动操作。应用面向个人侧载和小范围分享，不以应用商店发布为目标。

Moyu32 POC 已在 Android 15 真机与目标游戏完成验收。首个验证设备为 Moyu32，首个目标场景为竖屏跑酷类游戏；具体范围与验收记录见 [`docs/poc.md`](docs/poc.md)。

多品牌协议层已接入 GAN v2/v3/v4 与 QiYi QYSC/Tornado V4，沿用统一的 Provider、标准转动和动作映射链路。新增品牌当前已通过 JVM 单元测试和 Debug 构建，仍需对应真机完成连接、补帧和外部游戏输入验收。

## 技术基线

- Kotlin 2.1.20 与 Jetpack Compose Material 3
- 可直接复用 Java 协议实现的 Kotlin/Java 混合工程
- Android Gradle Plugin 8.9.2、Gradle 8.11.1、JDK 17
- `minSdk 31`、`compileSdk 35`、`targetSdk 35`
- 应用包名：`com.huizhi.rubikey`

## 本地构建

项目依赖 Android 35 SDK 和 JDK 17。首次构建前，在 `local.properties` 中配置 Android SDK 路径，然后执行：

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## Beta 发布构建

当前 Beta 版本为 `0.2.0-beta.1`。Release 构建需要本机持有签名密钥；默认读取 `~/.rubikey/rubikey-release.properties`，也支持在项目根目录放置已忽略的 `key.properties`。配置格式见 `key.properties.example`，其中的密码不得提交到 Git。

```powershell
.\gradlew.bat assembleRelease
```

签名 APK 输出到 `app/build/outputs/apk/release/app-release.apk`。侧载、更新和 GPLv3 源码交付要求见 [`docs/release.md`](docs/release.md)。

## 文档

- [`docs/project.md`](docs/project.md)：项目目标、范围和当前状态
- [`docs/architecture.md`](docs/architecture.md)：模块职责、数据流与平台约束
- [`docs/roadmap.md`](docs/roadmap.md)：阶段路线和已收口决策
- [`docs/poc.md`](docs/poc.md)：Moyu32 POC 的可执行实施方案

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE)。后续将复用同为 GPLv3 的 DCTimer-BLE 智能魔方协议代码；分享编译产物时应同时提供对应源码和许可证。
