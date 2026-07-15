# RubiKey 0.2.0 稳定版发布说明

## 当前版本

`0.2.0`，`versionCode = 3`，包名 `com.huizhi.rubikey`，面向个人侧载与小范围分享。

当前 Release 构建基线为 Android 12 及以上可安装，已在 Android 15 真机完成验收。已验收协议包括 Moyu32、GAN v2/v3/v4 和 QiYi QYSC/Tornado V4。

## 签名密钥

Release APK 必须始终使用同一 RubiKey 专用密钥签名，否则 Android 无法覆盖升级，用户保存的动作映射也无法随升级保留。

默认配置文件为 `~/.rubikey/rubikey-release.properties`；也可使用项目根目录的 `key.properties`。两者均包含以下键：

| 键 | 用途 |
| --- | --- |
| `storeFile` | JKS 密钥库路径，Windows 路径使用正斜杠或双反斜杠。 |
| `storePassword` | 密钥库密码。 |
| `keyAlias` | 签名别名。 |
| `keyPassword` | 私钥密码。 |

配置示例见 `key.properties.example`。密钥库、配置文件及密码均不得提交到 Git；应将密钥库和凭据文件加密备份至独立位置。

## 构建与验证

构建环境：JDK 17、Gradle 8.11.1、Android SDK 35。

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintRelease
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

GitHub Actions 会在 push、Pull Request 和手动触发时执行 JVM 测试、Debug lint 和 Debug 构建；Release 签名构建需要本地密钥配置。

Release 产物为 `app/build/outputs/apk/release/app-release.apk`。构建后使用以下命令记录校验值：

```powershell
Get-FileHash app/build/outputs/apk/release/app-release.apk -Algorithm SHA256
```

分享时应同时提供 APK、校验值、对应 Git Tag 的完整源码、`LICENSE` 和本构建说明。协议移植部分继续保留 DCTimer-BLE 的 GPLv3 来源说明。

## 侧载与使用

安装 Release APK 前，如设备已安装由不同密钥签名的同包名版本，必须先卸载旧版本；同一密钥签名的版本可直接覆盖安装。

首次侧载后，依次完成：

1. 允许“附近设备”权限。
2. Android 13 及以上按需允许通知权限。
3. 在系统设置中启用 RubiKey 无障碍服务。
4. Android 13 及以上侧载设备如出现受限设置提示，允许对应设置。
5. 扫描并连接已支持的智能魔方。
6. 配置转动映射后进入目标竖屏应用。

应用不使用 Root、Shizuku、ADB 常驻或悬浮窗输入。BLE 连接由 `connectedDevice` 前台服务持有，全局输入只通过用户主动启用的无障碍服务完成。

## 发布前验收

1. 从未安装状态侧载 Release APK，确认应用图标、授权流程和 About 版本号为 `0.2.0`。
2. 分别连接 Moyu32、GAN 和 QiYi 已验收设备，确认品牌识别、连接、转动、电量和补帧处理正常。
3. 为点击、上滑、下滑、左滑和右滑配置映射，确认目标游戏能够接收输入。
4. 退到后台运行，确认前台服务通知存在且快速连续输入不会造成无限队列。
5. 配置至少一项动作映射，使用同签名的新版本覆盖安装，确认映射仍在。
6. 关闭辅助功能、断开蓝牙和拒绝通知权限，确认应用显示可恢复的错误状态。

## 已知限制

- 当前只承诺 Android 15、默认显示器和竖屏场景。
- 一次只连接一个魔方，不支持多个动作、宏、循环、变量或条件分支。
- Android 12-14 和不同厂商后台策略尚未建立完整兼容矩阵。

## 回滚

停止分享当前 APK 即可停止发布。若需要回退到旧版本，旧 APK 也必须使用同一 RubiKey 密钥签名；应用私有映射数据不会因覆盖安装而清除，卸载才会清除数据。
