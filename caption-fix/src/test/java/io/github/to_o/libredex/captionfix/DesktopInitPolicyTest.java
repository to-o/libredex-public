package io.github.to_o.libredex.captionfix;

import org.junit.Test;
import static org.junit.Assert.*;

public class DesktopInitPolicyTest {
    @Test public void lateWiredSessionRequestsNativeInitialization() {
        assertTrue(DesktopInitPolicy.shouldInitialize(true, "F7310ZCS7FZA1", true, 6, -1, -1));
    }
    @Test public void existingOrAlreadyRequestedDesktopIsNotReinitialized() {
        assertFalse(DesktopInitPolicy.shouldInitialize(true, "F7310ZCS7FZA1", true, 6, 6, -1));
        assertFalse(DesktopInitPolicy.shouldInitialize(true, "F7310ZCS7FZA1", true, 6, -1, 6));
    }
    @Test public void inactiveDisabledAndUntestedFirmwareAreExcluded() {
        assertFalse(DesktopInitPolicy.shouldInitialize(false, "F7310ZCS7FZA1", true, 6, -1, -1));
        assertFalse(DesktopInitPolicy.shouldInitialize(true, "F7310ZCS7FZA1", false, 6, -1, -1));
        assertFalse(DesktopInitPolicy.shouldInitialize(true, "other", true, 6, -1, -1));
    }
    @Test public void builtInAndInvalidDisplaysAreExcluded() {
        for (int display : new int[]{-1, 0, 1}) {
            assertFalse(DesktopInitPolicy.shouldInitialize(true, "F7310ZCS7FZA1", true, display, -1, -1));
        }
    }
}
