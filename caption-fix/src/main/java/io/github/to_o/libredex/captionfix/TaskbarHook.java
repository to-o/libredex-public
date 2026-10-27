package io.github.to_o.libredex.captionfix;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Adapt the recents envelope, without modifying the TaskInfo or actual window mode. */
final class TaskbarHook {
    private static final AtomicBoolean LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FAILED = new AtomicBoolean();

    static void install(ClassLoader loader) {
        try {
            Class<?> grouped = XposedHelpers.findClass(
                    "com.android.wm.shell.shared.GroupedTaskInfo", loader);
            Class<?> taskInfo = Class.forName("android.app.TaskInfo");
            Method freeform = grouped.getDeclaredMethod("forFreeformTasks", taskInfo);
            Method singleTask = grouped.getDeclaredMethod("forFullscreenTasks", taskInfo);
            freeform.setAccessible(true);
            singleTask.setAccessible(true);
            Method getProperty = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class);
            Method mode = taskInfo.getMethod("getWindowingMode");
            Method activityType = taskInfo.getMethod("getActivityType");
            java.lang.reflect.Field display = taskInfo.getField("displayId");

            XposedBridge.hookMethod(freeform, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        boolean enabled = "1".equals(getProperty.invoke(null,
                                "persist.dex.lspmirror.taskbar_bridge", "1"));
                        boolean active = "1".equals(getProperty.invoke(null,
                                "dex.lspmirror.dp_session_active", "0"));
                        int managed;
                        try {
                            managed = Integer.parseInt((String) getProperty.invoke(null,
                                    "persist.dex.lspmirror.dp_display_id", "-1"));
                        } catch (NumberFormatException ignored) {
                            return;
                        }
                        Object info = param.args[0];
                        if (!TaskbarPolicy.shouldBridge(enabled, active, managed,
                                display.getInt(info), (Integer) mode.invoke(info),
                                (Integer) activityType.invoke(info))) {
                            return;
                        }
                        // Samsung's Launcher handles ordinary single-task recents, but
                        // skips the freeform envelope outside registered desktop desks.
                        // Keep the original TaskInfo, including its minimized flag.
                        Object result = singleTask.invoke(null, info);
                        if (result != null) {
                            param.setResult(result);
                            if (LOGGED.compareAndSet(false, true)) {
                                XposedBridge.log("LibreDeXCaption: taskbar recents bridge active display="
                                        + managed);
                            }
                        }
                    } catch (Throwable failure) {
                        if (FAILED.compareAndSet(false, true)) {
                            XposedBridge.log("LibreDeXCaption: recents unchanged: " + failure);
                        }
                    }
                }
            });
            XposedBridge.log("LibreDeXCaption: taskbar recents hook installed");
        } catch (Throwable unavailable) {
            XposedBridge.log("LibreDeXCaption: taskbar recents hook unavailable: " + unavailable);
        }
    }
}
