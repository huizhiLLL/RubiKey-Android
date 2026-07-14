# RubiKey Beta 发布说明

## 当前版本

`0.2.0-beta.1`，包名 `com.huizhi.rubikey`，面向 Android 12 及以上设备的个人侧载与小范围分享。

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

## 构建与侧载

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

产物为 `app/build/outputs/apk/release/app-release.apk`。安装 Release APK 前，如设备已安装由不同密钥签名的同包名版本，必须先卸载旧版本；同一密钥签名的版本可直接覆盖安装。

首次侧载后，依次完成蓝牙、通知、辅助功能和 Android 受限设置授权。应用不使用 Root、Shizuku、ADB 常驻或悬浮窗输入。

## 发布前验收

1. 从未安装状态侧载 Release APK，确认应用图标、首次授权和连接流程正常。
2. 配置至少一项动作映射，使用同签名的新版本覆盖安装，确认映射仍在。
3. 连接 Moyu32 后退到目标游戏，确认前台服务通知、点击和四方向滑动保持可用。
4. 关闭辅助功能、断开蓝牙和拒绝通知权限，确认应用显示可恢复的错误状态。

## GPLv3 源码交付

每次分享 APK 时，必须同时提供该 APK 对应版本的完整源码、`LICENSE` 和构建说明。协议移植部分继续保留 DCTimer-BLE 的 GPLv3 来源说明。

## 回滚

停止分享当前 APK 即可停止发布。若需要回退到旧版本，旧 APK 也必须使用同一 RubiKey 密钥签名；应用私有映射数据不会因覆盖安装而清除，卸载才会清除数据。
