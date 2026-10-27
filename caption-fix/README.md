# Wired DeX window controls (experimental)

This fork's optional companion Xposed module targets the centered floating controls observed on a Galaxy Z Flip5 SM-F7310, firmware F7310ZCS7FZA1. It does not replace the signed upstream LibreDeX application.

The device's Samsung SystemUI `DesktopModeWindowDecoration.getCaptionType(RunningTaskInfo)` returns type 1 for desktop headers. This module promotes a type-0 handle to a type-1 header only for freeform tasks on the physical display currently managed by an active LibreDeX DP/HDMI session. Built-in displays 0/1, other displays, fullscreen/PiP tasks and existing headers are excluded. Unavailable APIs leave stock behavior unchanged.

The APK has no application permissions or launcher activity. Enable **LibreDeX window controls** in Vector/LSPosed and select **System UI** (`com.android.systemui`) only, then reboot. The existing LibreDeX module and its scopes stay enabled. Start DP output normally afterward.

Version 0.2 also bridges ordinary application windows on the active wired display into Samsung Launcher's single-task recent-app list. The system otherwise labels these as freeform recents that the desktop list skips. Only the recents envelope changes: the original task information, minimized flag, display, bounds and actual window mode are preserved. Home/recents tasks and other displays are excluded. Disable this independently with `persist.dex.lspmirror.taskbar_bridge=0` and refresh the recent-app list. Taskbar restoration still requires device validation; maximize/restore and position persistence are separate unresolved issues.

Version 0.3 adds a native desktop initialization replay specifically for firmware F7310ZCS7FZA1. On this device, restarting SystemUI after LibreDeX DP output began caused Samsung's activatable/minimized desktop roots to appear on the external display; actual minimize/taskbar restore, maximize/restore and caption dragging then worked. The hook watches for a newly active wired session, confirms WindowManager already considers the display eligible, and runs Samsung's existing eligibility handler on the Shell executor once per session. It does not restart SystemUI or force eligibility on unsupported displays. Opt out with `persist.dex.lspmirror.desktop_init=0`. Cold-boot validation of the automatic replay is required; the observation after a manual restart alone is not proof of persistence.

Actions APKs currently use per-run debug signing keys. An update may require uninstalling this data-free companion first, reinstalling, and re-enabling its SystemUI scope. Do not uninstall the original LibreDeX application.

Build and test: `./gradlew :caption-fix:testDebugUnitTest :caption-fix:assembleDebug`. The fork's Actions workflow publishes the APK, checksum and unit-test results as an artifact. A successful build does not establish device compatibility; visually verify the controls before relying on the patch.

Rollback: disable this companion module in Vector and reboot. For a temporary runtime opt-out, run `adb shell su -c 'setprop persist.dex.lspmirror.full_caption 0'`, then reopen app windows. Delete that property or set it to 1 to enable again. The original LibreDeX installation remains available throughout.

Only self-authored compatibility code is included here. Device firmware, SystemUI APKs, decompiled code and personal logs are not part of this repository.
