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

## 设备要求

Android 12+

## 安装与使用

1. [GitHub Releases](https://github.com/huizhiLLL/RubiKey-Android/releases) 下载 APK 并安装
2. 进入 RubiKey，在系统设置中启用 RubiKey 无障碍服务；部分 Android 13 及以上设备需要允许受限设置
3. 扫描并连接智能魔方
4. 为 12 种转动配置点击或滑动，进入目标应用开始使用

应用退到后台后，BLE 连接由前台服务保持；全局输入只通过用户主动启用的无障碍服务完成


## 许可证

GPLv3
