package com.dex.lspmirror;

import android.content.Intent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Complete native initialization without intercepting power presses or broadcasts. */
final class SamsungDexPowerHook {
    private static final String TAG = "DexLspMirror";
    private static final String ACTION =
            "com.samsung.android.server.power.ACTION_INTERNAL_DISPLAY_OFF_BY_POWERKEY";

    private SamsungDexPowerHook() {}

    static void install(ClassLoader loader) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            final String firmware = (String) properties.getMethod(
                    "get", String.class, String.class).invoke(null, "ro.build.PDA", "");
            if (!DexPowerIntentState.FIRMWARE.equals(firmware)) {
                return;
            }
            Class<?> powerService = XposedHelpers.findClass(
                    "com.android.server.power.PowerManagerService", loader);
            XposedBridge.hookMethod(powerService.getDeclaredMethod("onBootPhase", int.class),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                // FZA1 onBootPhase(500) creates this Intent only when
                                // SEC_FEATURE_DEX_DUAL_VIEW is true. Flip5 has false,
                                // but the native wakefulness listener still sends it
                                // after a desktop is activated. Match its exact action
                                // and flags; preserve all native sleep/broadcast logic.
                                boolean repaired = DexPowerIntentState.initializeMissing(
                                        firmware, (Integer) param.args[0], param.thisObject,
                                        () -> new Intent(ACTION).addFlags(0x50000000));
                                if (repaired) {
                                    XposedBridge.log(TAG + ": initialized missing native DeX power-key broadcast");
                                }
                            } catch (Throwable failure) {
                                XposedBridge.log(TAG + ": native DeX power-key initialization unavailable: " + failure);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": FZA1 native DeX power-key initialization hook installed");
        } catch (Throwable failure) {
            XposedBridge.log(TAG + ": native DeX power-key hook unavailable: " + failure);
        }
    }
}
