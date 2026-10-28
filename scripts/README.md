# scripts

一键构建、签名、安装与真机回归辅助脚本。

- `build.ps1`：版本号入口（`-VersionName` / `-VersionCode`）-> clean -> assembleDebug/assembleRelease -> 产物校验（APK 存在、签名、versionName/versionCode）-> Release 复制到 `dist\libredex-<version>-release.apk` -> 可选 `adb install` 并回读验证。
- `gen-keystore.ps1`：生成本机 release keystore（不入库），打印构建所需环境变量。
- 签名与版本说明见 `signing/README.md`。
- 已 Root 设备的 Shizuku 单入口开机启动与回滚见 [shizuku-single-boot/README.md](shizuku-single-boot/README.md)。
