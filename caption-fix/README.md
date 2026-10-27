# Wired DeX window controls (experimental)

This fork's optional companion Xposed module targets the centered floating controls observed on a Galaxy Z Flip5 SM-F7310, firmware F7310ZCS7FZA1. It does not replace the signed upstream LibreDeX application.

The device's Samsung SystemUI `DesktopModeWindowDecoration.getCaptionType(RunningTaskInfo)` returns type 1 for desktop headers. This module promotes a type-0 handle to a type-1 header only for freeform tasks on the physical display currently managed by an active LibreDeX DP/HDMI session. Built-in displays 0/1, other displays, fullscreen/PiP tasks and existing headers are excluded. Unavailable APIs leave stock behavior unchanged.

The APK has no application permissions or launcher activity. Enable **LibreDeX window controls** in Vector/LSPosed and select **System UI** (`com.android.systemui`) only, then reboot. The existing LibreDeX module and its scopes stay enabled. Start DP output normally afterward.

Build and test: `./gradlew :caption-fix:testDebugUnitTest :caption-fix:assembleDebug`. The fork's Actions workflow publishes the APK, checksum and unit-test results as an artifact. A successful build does not establish device compatibility; visually verify the controls before relying on the patch.

Rollback: disable this companion module in Vector and reboot. For a temporary runtime opt-out, run `adb shell su -c 'setprop persist.dex.lspmirror.full_caption 0'`, then reopen app windows. Delete that property or set it to 1 to enable again. The original LibreDeX installation remains available throughout.

Only self-authored compatibility code is included here. Device firmware, SystemUI APKs, decompiled code and personal logs are not part of this repository.
