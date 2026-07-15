<div align="center">
  <img src=".github/assets/rubikey-icon.png" alt="RubiKey 图标" width="128" height="128" />

  <h1>RubiKey-Android</h1>

  <p>
    将智能魔方的转动映射为 Android 设备上的全局手势操作
  </p>

  <p>
    <img alt="Android 12+" src="https://img.shields.io/badge/Android-12%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
    <img alt="Kotlin 2.1.20" src="https://img.shields.io/badge/Kotlin-2.1.20-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
    <img alt="Jetpack Compose Material 3" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
    <img alt="GPLv3" src="https://img.shields.io/badge/License-GPLv3-A32D2D?style=for-the-badge&logo=gnu&logoColor=white" />
  </p>
</div>

<p align="center">
  <img src=".github/assets/rubikey-main-screen.png" alt="RubiKey 主页面" height="360" />&emsp;&emsp;
  <img src=".github/assets/rubikey-action-editor.png" alt="RubiKey 动作编辑" height="360" />
</p>

---

## 功能/特点

- 多品牌兼容（Moyu32 / GAN / QiYi）
- 使用无障碍服务（AccessibilityService）调用全局手势
- 12 种转动自由配置不同的操作
- 页面设计基于 MD3，简洁易用

## 支持范围

- 当前验收协议：Moyu32、GAN v2/v3/v4、QiYi QYSC/Tornado V4。
- 当前真机验收基线：Android 15、默认显示器、竖屏场景。
- 最低 Android 版本为 12（API 31）；Android 12-14 和不同厂商系统尚未建立完整兼容矩阵。
- 一次只连接一个魔方；不支持横屏、多窗口、多显示器、复杂宏和云同步。

## 安装与使用

1. 从 [GitHub Releases](https://github.com/huizhiLLL/RubiKey-Android/releases) 下载稳定版 APK 并安装。
2. 首次扫描时允许“附近设备”和通知权限。
3. 在系统设置中启用 RubiKey 无障碍服务；部分 Android 13 及以上侧载设备还需要允许受限设置。
4. 打开 RubiKey，扫描并连接已支持的智能魔方。
5. 为 12 种转动配置点击、滑动或无动作，然后进入目标应用使用。

应用退到后台后，BLE 连接由前台服务保持；全局输入只通过用户主动启用的无障碍服务完成。覆盖升级必须继续使用同一签名密钥，才能保留已有动作映射。

## 常见问题

- 扫描不到魔方：确认蓝牙已开启、附近设备权限已允许，且魔方没有被其他应用占用。
- 有转动但没有输入：确认无障碍服务已启用，并检查当前设备为竖屏且动作坐标位于目标应用可点击区域。
- 覆盖升级失败：卸载了不同签名的旧版本后再安装；同签名版本可以直接覆盖升级。

## 开发与构建

项目使用 JDK 17、Gradle 8.11.1 和 Android SDK 35。常用验证命令：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintRelease
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

## 许可证

GPLv3
