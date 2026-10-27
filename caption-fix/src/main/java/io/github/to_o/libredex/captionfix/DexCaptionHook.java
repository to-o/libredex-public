package io.github.to_o.libredex.captionfix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Opt-in companion hook: the existing LibreDeX APK and its signature remain untouched. */
public final class DexCaptionHook implements IXposedHookLoadPackage {
    private static final String TAG = "LibreDeXCaption";
    private static final Set<Integer> LOGGED_DISPLAYS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }
        try {
            Class<?> decoration = XposedHelpers.findClass(
                    "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration",
                    lpparam.classLoader);
            Class<?> taskInfo = Class.forName("android.app.ActivityManager$RunningTaskInfo");
            Method captionType = decoration.getDeclaredMethod("getCaptionType", taskInfo);
            captionType.setAccessible(true);
            Method getWindowingMode = taskInfo.getMethod("getWindowingMode");
            Field displayId = taskInfo.getField("displayId");
            Method getProperty = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class);
            // The upstream compile-only stubs lack getResult(). Resolve the real Xposed API
            // rather than reading the stub's public result field, which is private at runtime.
            Method getResult = XC_MethodHook.MethodHookParam.class.getMethod("getResult");

            XposedBridge.hookMethod(captionType, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object original = getResult.invoke(param);
                        if (!(original instanceof Integer)) {
                            return;
                        }
                        String managed = (String) getProperty.invoke(null,
                                "persist.dex.lspmirror.dp_display_id", "-1");
                        int managedDisplay;
                        try {
                            managedDisplay = Integer.parseInt(managed);
                        } catch (NumberFormatException ignored) {
                            return;
                        }
                        boolean enabled = "1".equals(getProperty.invoke(null,
                                "persist.dex.lspmirror.full_caption", "1"));
                        boolean active = "1".equals(getProperty.invoke(null,
                                "dex.lspmirror.dp_session_active", "0"));
                        Object info = param.args[0];
                        int taskDisplay = displayId.getInt(info);
                        int mode = (Integer) getWindowingMode.invoke(info);
                        if (!CaptionPolicy.shouldUseHeader(enabled, active, managedDisplay,
                                taskDisplay, mode, (Integer) original)) {
                            return;
                        }
                        // Samsung's F7310ZCS7FZA1 implementation returns 1 for a desktop
                        // header and 0 for a handle. Explicit Object selects the real API;
                        // the upstream stubs also declare nonstandard primitive overloads.
                        param.setResult((Object) Integer.valueOf(1));
                        if (LOGGED_DISPLAYS.add(taskDisplay)) {
                            XposedBridge.log(TAG + ": desktop header selected display="
                                    + taskDisplay);
                        }
                    } catch (Throwable failure) {
                        if (FAILURE_LOGGED.compareAndSet(false, true)) {
                            XposedBridge.log(TAG + ": leaving captions unchanged: " + failure);
                        }
                    }
                }
            });
            XposedBridge.log(TAG + ": caption hook installed");
        } catch (Throwable unavailable) {
            // Different ROM/API: do nothing rather than interrupt System UI.
            XposedBridge.log(TAG + ": caption hook unavailable: " + unavailable);
        }
    }
}
