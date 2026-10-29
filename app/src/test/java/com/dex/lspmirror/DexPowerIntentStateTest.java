package com.dex.lspmirror;

import org.junit.Test;
import static org.junit.Assert.*;

public class DexPowerIntentStateTest {
    private static final class PowerService {
        private Object mInternalDisplayOffByPowerKeyIntent;
    }

    @Test public void missingNativeIntentIsInitializedOnlyOnce() throws Exception {
        PowerService service = new PowerService();
        Object intent = new Object();
        assertTrue(DexPowerIntentState.initializeMissing(
                DexPowerIntentState.FIRMWARE, 500, service, () -> intent));
        assertSame(intent, service.mInternalDisplayOffByPowerKeyIntent);
        assertFalse(DexPowerIntentState.initializeMissing(
                DexPowerIntentState.FIRMWARE, 1000, service, () -> {
                    throw new AssertionError("An initialized native intent must be preserved");
                }));
    }

    @Test public void existingNativeIntentIsNeverReplaced() throws Exception {
        PowerService service = new PowerService();
        Object original = new Object();
        service.mInternalDisplayOffByPowerKeyIntent = original;
        assertFalse(DexPowerIntentState.initializeMissing(
                DexPowerIntentState.FIRMWARE, 500, service, () -> new Object()));
        assertSame(original, service.mInternalDisplayOffByPowerKeyIntent);
    }

    @Test public void unknownFirmwareAndEarlyBootAreUntouched() throws Exception {
        PowerService service = new PowerService();
        assertFalse(DexPowerIntentState.initializeMissing("other", 500, service, () -> {
            throw new AssertionError("Unsupported firmware must not be modified");
        }));
        assertFalse(DexPowerIntentState.initializeMissing(
                DexPowerIntentState.FIRMWARE, 499, service, () -> {
                    throw new AssertionError("Native initialization has not completed");
                }));
        assertNull(service.mInternalDisplayOffByPowerKeyIntent);
    }

    @Test public void constructionFailureLeavesNativeStateUntouchedAndCanRetry() throws Exception {
        PowerService service = new PowerService();
        try {
            DexPowerIntentState.initializeMissing(
                    DexPowerIntentState.FIRMWARE, 500, service, () -> {
                        throw new IllegalStateException("construction failed");
                    });
            fail("Construction failure must be reported to the hook");
        } catch (IllegalStateException expected) {
            assertNull(service.mInternalDisplayOffByPowerKeyIntent);
        }
        assertTrue(DexPowerIntentState.initializeMissing(
                DexPowerIntentState.FIRMWARE, 1000, service, Object::new));
    }

    @Test(expected = NoSuchFieldException.class)
    public void missingFirmwareFieldIsReportedInsteadOfChangingAnotherField() throws Exception {
        DexPowerIntentState.initializeMissing(
                DexPowerIntentState.FIRMWARE, 500, new Object(), Object::new);
    }

    @Test(expected = IllegalStateException.class)
    public void nullFactoryResultCannotClaimSuccessfulRepair() throws Exception {
        DexPowerIntentState.initializeMissing(
                DexPowerIntentState.FIRMWARE, 500, new PowerService(), () -> null);
    }
}
