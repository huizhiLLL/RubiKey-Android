# RubiKey Android 开发约束

## 项目概念

RubiKey 是一个 Android 工具客户端，将智能蓝牙魔方的转动事件映射为系统级点击或滑动。应用通过 BLE 接收魔方事件，通过用户主动启用的 `AccessibilityService` 向其他应用分发手势。

当前目标是验证 POC，不建设通用自动化平台。首批测试环境为 Android 15，首个设备为 Moyu32，首个目标场景为竖屏跑酷类简单操作游戏。

## 当前基线

- 应用层使用 Kotlin，UI 使用 Jetpack Compose Material 3。
- 从 DCTimer-BLE 移植的成熟协议允许保留 Java，不为统一语言而重写。
- 构建基线为 AGP 8.9.2、Gradle 8.11.1、JDK 17。
- `minSdk 31`，`compileSdk / targetSdk 35`。
- 包名为 `com.huizhi.rubikey`。
- 项目采用 GPLv3；复用协议源码时保留来源和许可证信息。

## 工作方式

- 开发或排查前先阅读任务、`README.md`、`docs/project.md`、`docs/architecture.md` 和 `docs/roadmap.md`；实施 POC 时还要阅读 `docs/poc.md`。
- 代码与文档冲突时，以代码为当前真实状态，并在本次任务范围内同步明显过期的文档。
- 默认小步、最小必要改动，不提前实现 POC 范围外的多品牌协议、复杂宏、云同步或悬浮窗录制。
- Bug 排查先定位根因，再修改和验证，不通过堆叠兜底补丁掩盖协议或生命周期问题。
- 删除大量文件、改变持久化格式、增加大型依赖、推送、发布或部署前，必须先说明影响并等待确认。
- 默认不使用 Playwright、浏览器自动化或截图验证；Android UI 以 Compose Preview、单元测试和真机手动验收为主。

## 架构约束

- BLE 生命周期、品牌协议、动作映射和辅助功能手势必须保持单向依赖，不允许协议类直接访问 Activity、Compose 状态或辅助功能服务。
- 品牌协议统一实现 `CubeProtocol`，由 `CubeProtocolProvider` 识别设备并由 `CubeProtocolRegistry` 创建实例。
- 协议只通过 `CubeEventSink` 输出标准事件；第一版只注册 Moyu32，但入口必须允许后续增加 GAN 和 QiYi，而不修改业务层。
- 蓝牙连接由 `CubeBleService` 持有。Activity 退出前台后，不得把 GATT 生命周期转移到页面对象。
- 全局输入只通过用户显式启用的 `AccessibilityService` 实现，不引入 Root、ADB 常驻、Shizuku 或悬浮窗权限。
- 一个魔方转动只映射一个 `NONE / TAP / SWIPE` 动作。POC 不增加延迟、动作序列、循环、变量或条件分支。
- 手势必须串行执行并限制待执行队列长度，避免 `dispatchGesture()` 互相取消或产生无限积压。
- 坐标以 `[0, 1]` 归一化值持久化；非法值在进入执行器前拒绝，不在执行阶段静默修正。
- POC 只承诺默认显示器和竖屏场景。横屏、多窗口和多显示器不属于当前验收范围。

## 主要目录索引

- `app/build.gradle.kts`：Android 版本、构建类型和依赖。
- `app/src/main/AndroidManifest.xml`：权限、前台服务、辅助功能服务和 Activity 声明。
- `app/src/main/java/com/huizhi/rubikey/`：应用源码根包。
- `app/src/main/java/com/huizhi/rubikey/ble/`：扫描、连接、前台服务和 GATT 生命周期。
- `app/src/main/java/com/huizhi/rubikey/cube/`：标准转动、协议接口、状态跟踪及品牌实现。
- `app/src/main/java/com/huizhi/rubikey/mapping/`：动作模型、持久化和事件到动作的映射。
- `app/src/main/java/com/huizhi/rubikey/accessibility/`：辅助功能服务与手势队列。
- `app/src/main/java/com/huizhi/rubikey/ui/`：Compose 页面、组件和主题。
- `app/src/test/`：纯 JVM 单元测试，优先覆盖协议解析、品牌识别、映射校验和队列策略。
- `app/src/androidTest/`：需要 Android 框架的集成测试。
- `docs/`：项目状态、架构、路线和 POC 实施方案。
- `../DCTimer-BLE/`：协议参考仓库，只作源代码迁移依据，不作为运行时依赖。

## 验证要求

- 构建配置、Manifest、依赖或跨目录代码变化后，运行 `.\gradlew.bat assembleDebug`。
- 纯逻辑变化运行 `.\gradlew.bat testDebugUnitTest`；涉及构建链时同时运行 `assembleDebug`。
- BLE、后台保活、辅助功能手势和外部游戏输入必须在 Android 15 真机验证，不能以模拟器或单元测试代替。
- 真机未验证的能力必须在 `docs/project.md` 和完成说明中标为“待验证”。
- 完成回复说明改动、主要文件、验证命令、文档同步情况及未验证风险。

## 提交信息

提交信息使用 `type：中文简短说明`，冒号使用中文全角字符。类型优先使用 `feat`、`fix`、`style`、`docs`、`refactor`、`chore`、`revert`。

