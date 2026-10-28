package com.dex.lspmirror;

import org.junit.Test;
import static org.junit.Assert.*;

public class DpTaskCleanupPolicyTest {
    @Test public void retiredUnorganizedHomeCanBeRemoved() {
        assertTrue(DpTaskCleanupPolicy.shouldRemove(7, 7, 2, false));
    }

    @Test public void organizerDeskRootsAndHomesArePreserved() {
        assertFalse(DpTaskCleanupPolicy.shouldRemove(7, 7, 0, true));
        assertFalse(DpTaskCleanupPolicy.shouldRemove(7, 7, 2, true));
    }

    @Test public void ordinaryAppTasksArePreserved() {
        assertFalse(DpTaskCleanupPolicy.shouldRemove(7, 7, 1, false));
    }

    @Test public void migratedAndReplacementHomesArePreserved() {
        assertFalse(DpTaskCleanupPolicy.shouldRemove(7, 0, 2, false));
        assertFalse(DpTaskCleanupPolicy.shouldRemove(7, 8, 2, false));
    }

    @Test public void unknownOwnershipAndInvalidDisplayFailClosed() {
        assertFalse(DpTaskCleanupPolicy.shouldRemove(7, 7, 2, null));
        assertFalse(DpTaskCleanupPolicy.shouldRemove(0, 0, 2, false));
        assertFalse(DpTaskCleanupPolicy.shouldRemove(-1, -1, 2, false));
    }
}
