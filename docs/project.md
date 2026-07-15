# RubiKey 项目状态

日期：`2026-07-15`

## 项目定位

RubiKey 将智能蓝牙魔方的物理转动转换为 Android 设备上的全局点击或滑动。它是面向个人侧载和小范围分享的工具，不以应用商店上架为目标，也不建设完整的通用自动化系统。

## POC 目标

在 Android 15 真机上完成以下闭环：

1. 扫描并连接智能魔方。
2. 在应用退到后台后继续接收转动事件。
3. 把标准转动映射为单次点击或滑动。
4. 通过辅助功能服务把手势分发到竖屏跑酷类外部游戏。
5. 在应用重启和同签名覆盖升级后保留动作映射。

## 当前状态

核心功能和首个稳定版已完成：

- Git 仓库、Kotlin、Compose、Android 基础构建配置和 GPLv3 许可证已建立。
- 标准转动模型、多品牌协议注册入口、Moyu32、GAN 和 QiYi 协议已接入。
- 单设备 BLE 扫描、GATT 连接、`connectedDevice` 前台服务和断开清理已完成。
- 动作映射 JSON 持久化、映射编辑界面和辅助功能手势串行队列已完成。
- 协议入口、品牌识别、转动解析、映射校验和队列策略共有 18 个 JVM 单元测试。
- Moyu32、GAN v2/v3/v4、QiYi QYSC/Tornado V4 已完成 Android 15 真机连接、转动解析、后台运行和目标游戏输入验收。
- Release APK 已完成 R8 压缩、独立签名、自适应图标和固定浅色黑白主题。
- `0.2.0` 稳定版使用 `versionCode = 3`，About 窗口显示当前版本号。
- Release 构建、Release lint、APK 签名校验和发布文档已纳入收尾流程。

## 后续质量项

- 使用脱敏的真实 Moyu32、GAN 和 QiYi 报文补充解密、批量顺序、补帧和异常包 golden test。
- 根据实际用户反馈建立 Android 12-14 及不同厂商系统的兼容矩阵；当前不将 Android 15 之外的环境标记为已验收。
- 已增加扫描/连接超时和旧 GATT 回调保护；继续评估动作映射导入导出和一键重新连接等体验增强。

## POC 范围外

- 同一时间连接多个品牌或多个魔方。
- 一个转动触发多个动作。
- 延迟、循环、变量、条件判断或图像识别。
- 云同步、账号、配置市场和分享协议。
- 自动按前台应用切换配置。
- 横屏、多窗口和多显示器适配。
- Root、Shizuku、ADB 常驻或悬浮窗录制。
- 应用商店发布和相关合规流程。

## 验证状态

- 本机构建环境存在 JDK 17、Gradle 8.11.1 和 Android 35 SDK。
- `.\gradlew.bat testDebugUnitTest` 已通过，包含 18 个 JVM 单元测试。
- `.\gradlew.bat lintRelease` 已通过，0 个 error；target 35 和 adaptive icon 目录保留平台提示 warning。
- `.\gradlew.bat assembleDebug` 已通过，Debug APK 可正常生成。
- `.\gradlew.bat assembleRelease` 已通过，已生成签名的 `0.2.0` APK。
- Release APK 已通过 APK Signature Scheme v2 校验，应用包名为 `com.huizhi.rubikey`。
- 已完成 Android 15 真机上的 BLE、后台保活、辅助功能服务和外部游戏输入验收。
- Android 12-14、不同厂商后台策略和脱敏真实报文回归仍待后续质量验证。
- GitHub Actions 已配置为自动执行 JVM 测试、Debug lint 和 Debug 构建；Release 签名构建仍在本地完成。
