package com.dex.lspmirror;

import java.lang.reflect.Field;
import java.util.function.Supplier;

/** Repairs only the missing native broadcast state observed on this firmware. */
final class DexPowerIntentState {
    static final String FIRMWARE = "F7310ZCS7FZA1";
    static final String FIELD = "mInternalDisplayOffByPowerKeyIntent";

    private DexPowerIntentState() {}

    static boolean initializeMissing(String firmware, int bootPhase, Object powerService,
            Supplier<Object> intentFactory) throws ReflectiveOperationException {
        if (!FIRMWARE.equals(firmware) || bootPhase < 500 || powerService == null) {
            return false;
        }
        Field field = powerService.getClass().getDeclaredField(FIELD);
        field.setAccessible(true);
        if (field.get(powerService) != null) {
            return false;
        }
        Object intent = intentFactory.get();
        if (intent == null) {
            throw new IllegalStateException("Native DeX power intent factory returned null");
        }
        field.set(powerService, intent);
        return true;
    }
}
