# Shizuku 单入口开机启动（Magisk）

这是设备端可选配置，适用于已确认 Shizuku 开机重复启动的 Magisk、arm64 设备。LibreDeX APK 不会自动安装此配置。

在 SM-F7310、Android 16、Shizuku 13.6.0.r1086.2650830c 上，隔离 LibreDeX 开机启动后仍观察到两个 Root server。该版本的 [BootCompleteReceiver](https://github.com/RikkaApps/Shizuku/blob/2650830c/manager/src/main/java/moe/shizuku/manager/receiver/BootCompleteReceiver.kt) 同时处理 `LOCKED_BOOT_COMPLETED` 和 `BOOT_COMPLETED`；启动过程中 Binder 尚未就绪时，两个广播可能重复调用启动器。

此配置关闭 Shizuku 原开机接收器，由 Magisk 脚本调用官方启动器。脚本每次开机只执行一次，动态查询 APK 路径，已有单个 Root server 时跳过启动。Shizuku 自启独立于 LibreDeX 和外接显示器。

## 安装

先确认 `/data/adb/service.d/20-shizuku-root.sh` 没有其他用途，并记录 Shizuku 接收器原来的启用状态。将本目录的 `20-shizuku-root.sh` 推送到手机：

```text
adb push scripts/shizuku-single-boot/20-shizuku-root.sh /data/local/tmp/20-shizuku-root.sh
adb shell
su
```

在手机 Root shell 内执行：

```sh
cp /data/local/tmp/20-shizuku-root.sh /data/adb/service.d/20-shizuku-root.sh
chown 0:0 /data/adb/service.d/20-shizuku-root.sh
chmod 700 /data/adb/service.d/20-shizuku-root.sh
restorecon /data/adb/service.d/20-shizuku-root.sh
pm disable --user 0 moe.shizuku.privileged.api/moe.shizuku.manager.receiver.BootCompleteReceiver
sleep 15
```

确认命令成功、组件状态已保存，再重启。LibreDeX 的有线自动启动仍需在应用中启用，应用需有 Root 和 Shizuku 授权，Vector 模块及作用域也需启用。若安装 Shizuku 更新后接收器状态被重置，需要重新检查。

## 验证

启动日志位于 `/data/adb/shizuku-root-boot.log`，每次开机覆盖。检查 Root server 数量、LibreDeX helper 的实际用户以及 DP 启动日志。脚本中的进程检查只用于避免重复启动；DP 是否可以继续，仍由应用对 UserService 的 Binder 存活和 Root 权限检查决定。

真机验证中，单入口配置后的三个启动均观察到一个 Root server；恢复 LibreDeX 开机接收器后的完整自动启动流程中，Root helper 连接成功，DP 从自动启动触发到输出约 4.47 秒，鼠标路由正常，无绑定超时或 Shizuku 重启。外屏编号从 6 变为 7 后也正常工作。该耗时是一次实测结果。

## 回滚

将本目录的 `rollback-single-shizuku-boot.sh` 推送到手机，用 Root shell 执行。它删除新增的固定路径开机脚本，并恢复 Shizuku 接收器的默认状态。若安装前已有自定义组件状态，应恢复先前记录的状态。回滚脚本已准备；真机验证未执行回滚。

删除 Magisk 启动脚本和恢复原接收器需要配套进行。运行中的 Shizuku server 不会因回滚被停止，下一次开机由原接收器负责启动。
