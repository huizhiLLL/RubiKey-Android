# RubiKey 架构边界

日期：`2026-07-14`

## 文档职责

本文记录稳定的模块职责、依赖方向和平台约束。当前完成度见 `project.md`，阶段路线见 `roadmap.md`，本次具体实施步骤见 `poc.md`。

## 总体结构

```text
Compose UI
    │ 用户命令 / 只读状态
    ▼
CubeBleService ── CubeProtocolRegistry ── Moyu32CubeProtocol
    │                                      │
    │ 连接状态                             │ CubeEventSink
    └──────────────────┬───────────────────┘
                       ▼
             ActionMappingRepository
                       │
                       ▼
              GestureActionExecutor
                       │
                       ▼
         RubiKeyAccessibilityService
                       │
                       ▼
                  外部应用
```

依赖只允许自上而下流动。协议实现不得依赖 Activity、Compose、动作映射或辅助功能服务。

## 模块职责

### UI

- 展示蓝牙、辅助功能和映射状态。
- 发起扫描、连接、断开及映射编辑命令。
- 不持有 GATT，不直接调用协议，也不直接构造 Android 手势。

### BLE 服务

- `CubeBleService` 是扫描、GATT 连接和前台通知的生命周期所有者。
- 服务只在用户从可见页面主动连接后进入前台，避免违反 Android 14/15 的后台启动限制。
- 连接断开时清理协议实例、队列和设备状态，不进行无限自动重连。

### 多品牌协议入口

- `CubeProtocol` 定义启动、清理和 GATT 回调入口。
- `CubeProtocolProvider` 定义扫描结果识别、服务确认和协议创建。
- `CubeProtocolRegistry` 按注册顺序选择唯一匹配的 Provider；无法识别或多重匹配都作为显式错误上报。
- `CubeEventSink` 输出标准转动、电量、同步状态和协议错误。
- POC 只实现并注册 Moyu32 Provider；GAN 和 QiYi 后续以新增 Provider 的方式接入。

### 魔方状态

- `CubeMove` 使用稳定的 18 种三阶转动：`U/U2/U'`、`R/R2/R'`、`F/F2/F'`、`D/D2/D'`、`L/L2/L'`、`B/B2/B'`。
- `CubeStateTracker` 只保留协议校验、状态同步和历史转动补回需要的数据，不承载计时、成绩、训练或求解业务。
- 协议原始编号必须在协议层转换为 `CubeMove`，业务层不识别品牌编号。

### 映射与持久化

- 每个 `CubeMove` 对应一个 `NONE`、`TAP` 或 `SWIPE`。
- 坐标保存为 `[0, 1]` 闭区间内的归一化值。
- POC 使用 `SharedPreferences` 保存带 `schemaVersion = 1` 的 JSON，不引入数据库。
- 所有转动初始值均为 `NONE`；读取失败时拒绝加载损坏配置并回退为空映射，同时记录可见错误。

### 辅助功能手势

- `RubiKeyAccessibilityService` 是唯一允许调用 `dispatchGesture()` 的组件。
- `GestureActionExecutor` 串行分发手势，最多保留 4 个待执行动作。
- 队列满时丢弃最旧的待执行动作，保留正在执行的动作和最新输入。
- 点击持续 `40ms`，滑动默认持续 `100ms`。
- 服务未启用、显示方向不匹配、坐标非法或手势被系统拒绝时，不执行替代输入，并向 UI 暴露错误状态。

## Android 平台约束

- `minSdk 31`，只使用 Android 12 及以上的蓝牙权限模型。
- BLE 使用 `BLUETOOTH_SCAN` 和 `BLUETOOTH_CONNECT` 运行时权限。
- 后台连接使用 `connectedDevice` 类型前台服务及常驻通知。
- 辅助功能必须由用户在系统设置中显式启用；Android 13 及以上侧载安装可能还需用户放行“受限设置”。
- POC 仅支持默认显示器和竖屏。坐标按当前屏幕边界换算，不承诺跨方向复用。

## 许可证边界

协议参考仓库 DCTimer-BLE 使用 GPLv3。移植时保留原文件版权声明，并在提交记录或源码注释中标明来源。分享 APK 时同步提供与该 APK 对应的完整源码和 GPLv3 许可证。

