# Moyu32 POC 实施方案

日期：`2026-07-14`

## 前置状态

Git、Android 工程、文档、许可证和基础构建已建立。本文描述 Moyu32 POC 的实现范围、验收标准和后续补全项，不重复工程初始化步骤。

## 实施状态

截至 `2026-07-14`，步骤一至步骤七均已完成。JVM 单元测试与 Debug 构建通过；Moyu32 的连接、转动解析、后台事件接收、动作映射、辅助功能手势和目标竖屏跑酷类游戏均已在 Android 15 真机验证通过。真实加密报文的脱敏回归样本作为发行版质量项补充，不影响 POC 结项。本文以下内容保留为验收记录和后续补全依据。

参考实现位于同级仓库 `../DCTimer-BLE`。移植以该仓库当前代码为准，重点参考：

- `app/src/main/java/com/dctimer/util/BluetoothTools.java`
- `app/src/main/java/com/dctimer/util/SmartCubeProtocol.java`
- `app/src/main/java/com/dctimer/util/Moyu32CubeProtocol.java`
- `app/src/main/java/com/dctimer/model/SmartCube.java`

## 范围

实现 Moyu32 单设备扫描、连接、后台事件接收、单动作映射和外部应用全局手势。多品牌统一入口必须可用，但 POC 不包含 GAN、QiYi 或其他协议实现。

## 步骤一：标准事件与协议入口

在 `com.huizhi.rubikey.cube` 下建立：

- `CubeMove`：稳定的 18 种标准转动及显示记号。
- `CubeDevice`：名称、地址、品牌和连接状态。
- `CubeEventSink`：转动、电量、同步状态和协议错误回调。
- `CubeStateTracker`：最小状态校验和转动应用能力。
- `CubeProtocol`：协议生命周期与 GATT 回调接口。
- `CubeProtocolProvider`：设备识别、服务确认和协议创建接口。
- `CubeProtocolRegistry`：Provider 注册和唯一匹配。

单元测试覆盖 18 种编号转换、未知编号拒绝、Provider 无匹配和多重匹配错误。

## 步骤二：移植 Moyu32 协议

从 DCTimer-BLE 移植 `Moyu32CubeProtocol` 的以下能力：

- 设备名称与 GATT 服务识别。
- MAC 派生、握手、加解密和通知启用。
- 电量、状态、单次及批量转动解析。
- 时间差计算和丢失转动恢复。

删除 `MainActivity`、计时、打乱、3D 和陀螺仪 UI 依赖。所有结果改由 `CubeEventSink` 输出；陀螺仪数据在 POC 中直接忽略。原始转动在协议层转换为 `CubeMove`。

用固定字节样本为解密、批量转动顺序、非法包长度和未知转动编号建立 JVM 测试。测试样本不得包含真实用户标识或密钥。

## 步骤三：BLE 前台服务

建立 `ble/CubeBleService` 和 `ble/CubeConnectionManager`：

- Activity 可见时请求 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` 并发起扫描。
- 扫描列表通过不可变状态暴露给 UI，相同地址只保留一个设备。
- 用户选择设备后停止扫描并连接 GATT。
- 连接成功后解析服务，由注册表创建 Moyu32 协议。
- 连接期间使用 `connectedDevice` 前台服务和常驻通知。
- 用户主动断开、GATT 断开或服务销毁时关闭 GATT、清理协议并清空待执行动作。
- POC 不做无限自动重连；断开后由用户手动重连。

Manifest 增加蓝牙、前台服务和通知权限。Android 13 及以上在显示通知前请求 `POST_NOTIFICATIONS`；拒绝通知权限时仍按平台允许的前台服务行为运行，并在 UI 显示状态提示。

## 步骤四：动作模型与持久化

在 `mapping` 下建立：

- `CubeAction`：`None`、`Tap`、`Swipe` 密封模型。
- `ActionMapping`：18 种转动到动作的完整映射。
- `ActionMappingRepository`：`SharedPreferences + JSON` 持久化。
- `CubeActionRouter`：接收 `CubeMove` 并提交已配置动作。

JSON 固定使用 `schemaVersion = 1`。点击保存 `x/y`，滑动保存 `startX/startY/endX/endY/durationMs`；坐标必须位于 `[0, 1]`，滑动时长限制为 `40..500ms`，默认 `100ms`。损坏配置回退为全 `None` 并暴露错误，不覆盖原始损坏字符串，便于排查。

单元测试覆盖序列化往返、缺失字段、未知版本、越界坐标、非法时长及默认空映射。

## 步骤五：辅助功能与手势队列

建立 `accessibility/RubiKeyAccessibilityService` 和 `accessibility/GestureActionExecutor`：

- 服务声明 `canPerformGestures="true"`，只申请手势能力，不读取窗口内容或节点树。
- 使用当前默认显示器边界把归一化坐标换算为像素。
- 点击持续 `40ms`；滑动使用配置时长。
- 同一时间只分发一个手势，最多保留 4 个待执行动作。
- 队列满时丢弃最旧的待执行动作，并记录可见的丢弃计数。
- 手势成功、取消、服务关闭和方向不匹配时都要结束当前项并推进或清空队列。

Manifest 和 `res/xml/accessibility_service_config.xml` 只声明必要能力。UI 提供系统辅助功能设置入口，并明确显示“未启用 / 已启用 / 已断开”。

## 步骤六：Compose 操作界面

主页面保持单屏工具布局，包含：

- 顶部连接状态和连接/断开命令。
- 辅助功能状态与设置入口。
- 最近一次标准转动和内部处理延迟。
- 18 项转动映射列表。

动作编辑使用 Material 3 底部面板：可选择无动作、点击或滑动；点击使用全屏比例坐标画布，滑动提供上、下、左、右预设和自定义起终点。界面不使用悬浮窗录制，不堆叠装饰卡片，不用颜色替代文字状态。

状态变化不得让按钮、列表行或坐标画布发生尺寸跳动。所有可操作图标提供内容描述，触控目标不小于 `48dp`。

## 步骤七：验证与停止条件

自动验证：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Android 15 真机手动验收：

1. 侧载 Debug APK，完成蓝牙、通知、受限设置和辅助功能授权。
2. 扫描并连接 Moyu32，连续执行 18 种转动，确认标准事件和映射一致。
3. 退到目标游戏，确认 BLE 连接保持且常驻通知存在。
4. 分别验证点击、上滑、下滑、左滑和右滑。
5. 快速连续转动 20 次，确认无崩溃、无无限队列，丢弃行为符合约定。
6. 关闭辅助功能、断开蓝牙和重启应用，确认错误状态及映射恢复正确。
7. 从协议事件进入动作路由到调用 `dispatchGesture()` 的内部延迟达到 `p95 < 50ms`。

若目标游戏不接受 `AccessibilityService.dispatchGesture()` 输入，立即停止 POC，不继续迁移其他品牌协议。若仅固定坐标不适配游戏沉浸模式，先校准屏幕边界和系统栏换算，不增加 Root、Shizuku 或 ADB 方案。

## 回滚

POC 数据只存在应用私有 `SharedPreferences`，没有数据库和外部文件。失败时卸载应用即可清除数据；代码可按独立阶段提交逐步回退，不影响 DCTimer-BLE 参考仓库。
