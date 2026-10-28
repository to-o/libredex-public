# LibreDeX 架构简化方案（待 Codex 评审）

> 状态：**设计草案，仅供评审；尚未批准实施**。基于 `master`（审查基线 `d87d412d2c8c`，2026-10-28）。
> 目标设备：三星 Galaxy Z Flip5 / SM-F7310；主要关注已测试的 One UI / Android 16 兼容环境。其他固件版本不能自动视为兼容。
> **请 Codex 先核对代码、指出错误、补充可行方案并完善本文档，不要立即开展大规模重构。**
> Git 工作方式参见根目录 `AGENTS.md`：如获得用户明确授权实施，直接在 `master` 修改、验证、提交；不创建开发分支或 PR。

## 1. 目标与非目标

**目标（按优先级）：**
1. **三星原生优先**：尽可能使用 `SecondaryLauncher`、`DesktopMode`、`WindowManager`、`SystemUI`、原生窗口/任务栏和输入能力；LibreDeX 只补 Z Flip5 上缺失的 DeX 启动/兼容能力。
2. **降低复杂度**：明确会话所有权，去除经过验证的重复实现、冲突状态和无用恢复路径；不增加新的抽象层/常驻服务。
3. **不增加性能负担**：有线 DeX 走物理 DP/HDMI、系统合成直出；不增加额外视频编码、GL 中转或常驻轮询。无线 Sunshine/Moonlight 独立按需启动。
4. **可用、可恢复**：插线即用；启动失败、快速拔插、锁屏、开机自动启动、退出恢复可靠；对原生系统的改动尽量可逆。

**非目标：** 不重写桌面、窗口管理器或任务栏；不统一有线/无线到同一视频渲染链路；不为了行数而删现有必要的 One UI 兼容逻辑；不在本轮引入新框架、复杂插件体系或新产品功能。

## 2. 期望的最小架构

```text
LibreDeX UI / 配置
      |
  单一逻辑 DeX 会话与状态管理
      |
  必要的 Root / Shizuku UserService + 最小 LSPosed/Vector Hook
      |
  三星原生 DeX: SecondaryLauncher / DesktopMode / WMS / SystemUI / Input
      |
      +-- 有线 DP/HDMI：系统物理显示直出（无 Sunshine 编码）
      +-- 无线 Sunshine/Moonlight：按需创建虚拟显示并硬件编码
```

“单一逻辑 DeX 会话”并不意味着禁止同时观看桌面：有线 DeX 运行时，Moonlight 如需观看**同一桌面**，应作为附加输出/观察者，而不是重复创建第二个 DeX 桌面。Codex 需先验证当前是否支持、是否应纳入最小范围；不得在未验证时承诺此能力。

## 3. 现状与待验证的复杂性

| 范围 | 代码入口 | 已观察到的风险 / 待评审问题 |
| --- | --- | --- |
| 生命周期 | `job/SessionLifecycle.java`、`job/ScreenKeepalive.java`、`job/ScreenSession.java` | 不同输出可能共享全局屏幕保活、会话标记与比例恢复；`stop()` 是否会过早清除其他仍活跃输出的状态？ |
| 有线输出 | `job/ProjectViaDp.java`、`ExternalDisplayMonitor.java`、`DpAutoSwitch.java`、`DpHotplugPolicy.java` | 启动、取消、拔插与显示 ID 变更是否都有确定状态？同一显示器启动失败后没有新 CONNECT 事件时如何恢复？ |
| 特权服务 | `job/AcquireShizuku.java`、`shizuku/UserServiceRecovery.java`、`shizuku/UserService.java` | 最近 Root Helper 存活/权限校验已加强；切勿误删。核查是否有重复探测或冗余重试，但以正确性为先。 |
| 系统兼容 | `com/dex/lspmirror/DexLayerStackHook.java`、`DexHook.java`、`caption-fix/` | 哪些 Hook 是开启 Samsung DeX 的**必要条件**，哪些仅用于旧固件/失效 workaround？是否有高频系统路径、反射或周期检查？ |
| 无线输出 | `job/SunshineServer.java`、`ProjectViaMoonlight.java`、`SunshineHostAdapter.java`、`SunshineService.java` | 官方/自定义 Sunshine 路径是否有维护重复？先做功能/稳定/性能对比，不能直接移除任一路径。 |
| 全量退出 | `job/ExitAll.java`、`State.java` | 跨有线、无线、输入、屏幕参数的退出清理是否重复、顺序是否安全？ |
| 任务清理 | `DexLayerStackHook.java`、`DpDisplayCleanupState.java`、`DpTaskCleanupPolicy.java` | 最近已修正旧 Display ID 的延迟清理并保护 Shell-owned tasks，**保留这些安全措施**；仍需真机验证。 |

> 以上是待核对的设计风险，并非均已复现。Codex 必须用文件/方法/调用路径证据区分“已证实”“推测”“已修复”。

## 4. 建议实施阶段（先评审，再逐项授权）

### 阶段 A：明确会话所有权（优先级 P1）

- 盘点 `ProjectViaDp`、`SunshineServer`、`SunshineService`、`ExitAll`、`SessionLifecycle` 的 start/stop 调用图。
- 约束“**同一时刻一个主 DeX 会话**”，允许的附加镜像/串流行为单独列明；明确谁持有显示、输入路由、屏幕保活、会话属性和宽高比修改的所有权。
- 全局恢复只发生在最后一个对应资源所有者退出时；启动失败、取消、重复 stop 必须幂等。
- **优先就地收敛已有类**，确实需要时才增加一个很小的状态对象，不引入庞大 `SessionManager` 框架。

### 阶段 B：最小化三星 Hook（优先级 P1）

- 建立 Hook 清单：注入进程/类/方法、触发频率、目的、固件兼容性、关闭开关、可替代的三星原生 API 或回调。
- 按“不可缺少 / 仅兼容回退 / 可删除（有验证依据）”分类。
- 优先研究让三星原生桌面初始化、任务栏、标题栏、窗口最小化/恢复自己接管；不要再实现相同能力。
- **特别注意**：`setExternalDesktopDisplayId()` 在目标设备有过 SecurityException 或阻断桌面 root 创建，不能因为“原生”二字就直接替换现有经验证的手动路径。
- `caption-fix/DesktopInitHook.java` 的周期检查可评估改为明确的会话事件触发，但须覆盖迟到的 SystemUI 初始化及重启恢复。
- 保持 `DpDisplayCleanupState` / `DpTaskCleanupPolicy` 对旧显示和 Shell-owned Task 的保护；禁止无证据恢复“大范围删除 Task”的旧做法。

### 阶段 C：有限恢复与低开销（优先级 P2）

- DP 自动启动失败时，不依赖再次插拔：评估**有上限、可取消、退避**的重试；用户主动停止后不能擅自重启，拔线后不得继续执行过期启动。
- Shizuku 单入口开机脚本仅是可选设备配置；保留 App 内 Root Helper 验证与失败处理。不能默认所有用户已安装脚本。
- 对正常运行路径做性能与复杂度对比：WMS Hook 命中次数、系统属性/反射调用、后台唤醒、编码/GL 管线数量、CPU/GPU/内存。
- 如优化默认显示模式，优先尊重实际显示器支持模式和三星限制，避免硬编码高分辨率导致兼容回退。

### 阶段 D：清理重复实现（优先级 P2，需 A/B/C 结论）

- 对 Sunshine 官方与自定义实现进行功能、输入法、音频、性能和稳定性对照；有确凿替代方案才减少实现数量。
- 移除不再需要的 Hook、状态字段、调试兼容路径，**分小步**并验证可回退。
- 默认 UI 只呈现“连接/启动/停止/状态”及必要设置，诊断与兼容开关放高级页；不要隐藏实际授权失败和风险。

## 5. 验收与回归要求

**功能：** 手动启动/停止；自动插线；已插线时启动 App；拔插/快速重插/显示 ID 改变；重启自动启动；原生窗口最大化、最小化、恢复、任务栏、鼠标键盘；屏幕开关；Moonlight 连接/断开；有线和无线切换；失败后的恢复与退出。

**稳定性：** 不产生重复 DeX/Root 服务；不残留鼠标绑定、黑屏、幽灵 Task、错误 display/layer stack；不能触发 system_server/SystemUI 崩溃；失败时返回可理解状态并可恢复。

**性能：** 对比同设备、同固件、同屏幕和负载下改造前后数据：有线连接到桌面可操作的耗时、空闲 CPU/内存、窗口动画帧耗时、GPU 负担、后台唤醒及电量消耗。没有数据时写“待测”，不声称性能提升。

**验证入口：** `./gradlew :xposed-stubs:test :app:testDebugUnitTest :caption-fix:testDebugUnitTest`、`python scripts/check_i18n_consistency.py`；按仓库 CI/构建文档完成 APK 构建与真机验证。单元测试成功不等于三星固件真机兼容。

## 6. 请 Codex 先完成的评审任务

1. 阅读 `AGENTS.md` 和本文档，基于**当前 master 最新源码**逐项核对第 3 节的问题，纠正过时的判断。
2. 输出 **实际调用链/状态所有权表**（DP、Moonlight、退出、异常、重连）和 **Hook 清单**；对每项给出代码路径、方法、风险级别、依据及是否值得保留。
3. 将改动分为“安全的小改动”“需真机验证的兼容改动”“暂不建议动”，给出最小改动量和回退手段；特别说明会不会引入额外常驻开销。
4. 直接**完善本方案文档**：具体到文件/方法、步骤、测试与判定标准；不同意见应写清技术理由，不要仅提出大规模重构。
5. **本阶段只做评审/文档，不修改运行时代码**；待用户认可实施范围后，再依照 `AGENTS.md` 直接提交 `master`。无需创建 PR。

## 7. 总结

**LibreDeX 管启动和退出，三星管原生桌面/窗口/输入，Sunshine 只管无线传输。**

保持原生功能和既有兼容性，不为架构形式增加层次，不以删代码行数替代性能测试。
