package io.github.to_o.libredex.captionfix;

import org.junit.Test;
import static org.junit.Assert.*;

public class TaskbarPolicyTest {
    @Test public void activeExternalApplicationIsIncluded() {
        assertTrue(TaskbarPolicy.shouldBridge(true, true, 6, 6, 5, 1));
    }
    @Test public void inactiveAndOptedOutSessionsAreExcluded() {
        assertFalse(TaskbarPolicy.shouldBridge(false, true, 6, 6, 5, 1));
        assertFalse(TaskbarPolicy.shouldBridge(true, false, 6, 6, 5, 1));
    }
    @Test public void builtInAndOtherDisplaysAreExcluded() {
        for (int display : new int[]{-1, 0, 1, 7}) {
            assertFalse(TaskbarPolicy.shouldBridge(true, true, 6, display, 5, 1));
        }
        for (int managed : new int[]{-1, 0, 1}) {
            assertFalse(TaskbarPolicy.shouldBridge(true, true, managed, managed, 5, 1));
        }
    }
    @Test public void homeRecentsAndOtherWindowModesAreExcluded() {
        for (int type : new int[]{0, 2, 3, 4, 5}) {
            assertFalse(TaskbarPolicy.shouldBridge(true, true, 6, 6, 5, type));
        }
        for (int mode : new int[]{0, 1, 2, 3, 4, 6}) {
            assertFalse(TaskbarPolicy.shouldBridge(true, true, 6, 6, mode, 1));
        }
    }
}
