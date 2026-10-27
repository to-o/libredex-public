package io.github.to_o.libredex.captionfix;

import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Replay Samsung's native eligibility callback when LibreDeX starts after display discovery. */
final class DesktopInitHook {
    private static final AtomicBoolean WATCHING = new AtomicBoolean();

    static void install(ClassLoader loader) {
        try {
            Class<?> handlerClass = XposedHelpers.findClass(
                    "com.android.wm.shell.desktopmode.DesktopDisplayEventHandler", loader);
            Class<?> stateClass = XposedHelpers.findClass(
                    "com.android.wm.shell.shared.desktopmode.DesktopStateImpl", loader);
            Class<?> companion = XposedHelpers.findClass(
                    "com.android.wm.shell.shared.desktopmode.DesktopStateImpl$Companion", loader);
            Method setDisplay = companion.getDeclaredMethod("setDesktopExternalDisplayId", int.class);
            setDisplay.setAccessible(true);
            Field nativeDisplay = field(stateClass, "desktopExternalDisplayId");
            Method eligibleChanged = handlerClass.getDeclaredMethod("onDesktopModeEligibleChanged", int.class);
            eligibleChanged.setAccessible(true);
            Method get = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class);
            for (Constructor<?> constructor : handlerClass.getDeclaredConstructors()) {
                XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!WATCHING.compareAndSet(false, true)) return;
                        try {
                            Object target = param.thisObject;
                            Object state = field(handlerClass, "desktopState").get(target);
                            Object windowManager = field(stateClass, "windowManager").get(state);
                            Method eligible = windowManager.getClass().getMethod("isEligibleForDesktopMode", int.class);
                            eligible.setAccessible(true);
                            Object controller = field(handlerClass, "displayController").get(target);
                            Executor executor = (Executor) field(controller.getClass(), "mMainExecutor").get(controller);
                            Handler timer = new Handler(Looper.getMainLooper());
                            timer.postDelayed(new Runnable() {
                                private int requested = -1;
                                private boolean failureLogged;
                                @Override public void run() {
                                    try {
                                        // This compatibility path is verified against this firmware only.
                                        String firmware = (String) get.invoke(null, "ro.build.PDA", "");
                                        boolean enabled = "1".equals(get.invoke(null,
                                                "persist.dex.lspmirror.desktop_init", "1"));
                                        boolean active = "1".equals(get.invoke(null,
                                                "dex.lspmirror.dp_session_active", "0"));
                                        if (!active) { requested = -1; return; }
                                        int display;
                                        try {
                                            display = Integer.parseInt((String) get.invoke(null,
                                                    "persist.dex.lspmirror.dp_display_id", "-1"));
                                        } catch (NumberFormatException ignored) { return; }
                                        if (DesktopInitPolicy.shouldInitialize(enabled, firmware, active,
                                                display, nativeDisplay.getInt(null), requested)
                                                && Boolean.TRUE.equals(eligible.invoke(windowManager, display))) {
                                            requested = display;
                                            executor.execute(() -> {
                                                try {
                                                    if (!"1".equals(get.invoke(null,
                                                            "dex.lspmirror.dp_session_active", "0"))
                                                            || !String.valueOf(display).equals(get.invoke(null,
                                                            "persist.dex.lspmirror.dp_display_id", "-1"))) return;
                                                    int previous = nativeDisplay.getInt(null);
                                                    if (previous == display) return;
                                                    setDisplay.invoke(null, display);
                                                    try {
                                                        eligibleChanged.invoke(target, display);
                                                    } catch (Throwable failure) {
                                                        setDisplay.invoke(null, previous);
                                                        throw failure;
                                                    }
                                                    XposedBridge.log("LibreDeXCaption: native desktop initialized display=" + display);
                                                } catch (Throwable failure) {
                                                    XposedBridge.log("LibreDeXCaption: native desktop replay failed: " + failure);
                                                }
                                            });
                                        }
                                    } catch (Throwable failure) {
                                        if (!failureLogged) {
                                            failureLogged = true;
                                            XposedBridge.log("LibreDeXCaption: native desktop watch skipped: " + failure);
                                        }
                                    } finally {
                                        timer.postDelayed(this, 2000);
                                    }
                                }
                            }, 2000);
                            XposedBridge.log("LibreDeXCaption: native desktop eligibility watch installed");
                        } catch (Throwable failure) {
                            XposedBridge.log("LibreDeXCaption: native desktop watch unavailable: " + failure);
                        }
                    }
                });
            }
        } catch (Throwable failure) {
            XposedBridge.log("LibreDeXCaption: native desktop initialization unavailable: " + failure);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
