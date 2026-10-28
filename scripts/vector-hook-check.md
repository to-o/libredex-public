# Vector 系统框架 Hook 检查

Vector 2.2（3080-88f8e1fa）的 CLI 将系统框架作用域记为 `system/0`。`android/0` 可以出现在作用域列表里，但该版本在启动系统进程时查询的是 `system`，因此不能只凭模块开关或作用域列表判断 Hook 已执行。见 [该版本的 systemServerModuleRows()](https://github.com/JingMatrix/Vector/blob/88f8e1fa/daemon/src/main/kotlin/org/matrix/vector/daemon/data/ModuleDatabase.kt#L147)。

在已 Root 的测试机上，使用手机 Root shell 检查并补充作用域：

```sh
/data/adb/modules/zygisk_vector/cli scope ls com.libredex.debug
/data/adb/modules/zygisk_vector/cli scope add com.libredex.debug system/0
```

使用正式包时替换包名。作用域修改后需要重启，传统 Xposed 模块不会因此自动替换已在运行的系统进程代码。

重启后检查：

```sh
getprop sys.libredex.hook_active
/data/adb/modules/zygisk_vector/cli log cat
```

标记应为 `1`，日志应包含来自 `system_server` PID 的 `DexLspMirror: hooking system_server`。这只证明模块入口执行；继续确认目标 Hook 的安装日志和对应功能的运行日志。空标记或仅有 Settings / Launcher 中的载入记录时，不能将桌面显示正常作为 WMS Hook 的测试结果。

不要在同一外屏上并发执行 UI 自动化。退出清理、显示切换和设备重启测试需要与其他手机任务错开。
