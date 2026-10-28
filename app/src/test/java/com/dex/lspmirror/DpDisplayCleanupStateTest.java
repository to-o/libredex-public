package com.dex.lspmirror;

import org.junit.Test;

import static org.junit.Assert.*;

public class DpDisplayCleanupStateTest {
    @Test public void disconnectKeepsTheRetiredIdAfterConfigurationClears() {
        DpDisplayCleanupState state = new DpDisplayCleanupState();
        assertNull(state.observe(7));
        DpDisplayCleanupState.Request cleanup = state.observe(-1);
        assertEquals(7, cleanup.displayId);
        assertTrue(state.isPending(cleanup, -1));
    }

    @Test public void replacementNeverTargetsTheNewDisplay() {
        DpDisplayCleanupState state = new DpDisplayCleanupState();
        state.observe(7);
        DpDisplayCleanupState.Request cleanup = state.observe(8);
        assertEquals(7, cleanup.displayId);
        assertTrue(state.isPending(cleanup, 8));
    }

    @Test public void sameIdRestartCancelsOldCleanupPermanently() {
        DpDisplayCleanupState state = new DpDisplayCleanupState();
        state.observe(7);
        DpDisplayCleanupState.Request oldCleanup = state.observe(-1);
        state.observe(7);
        assertFalse(state.isPending(oldCleanup, 7));
        DpDisplayCleanupState.Request newCleanup = state.observe(-1);
        assertFalse(state.isPending(oldCleanup, -1));
        assertTrue(state.isPending(newCleanup, -1));
        // Completion of the stale callback must not remove the newer request.
        state.finish(oldCleanup);
        assertTrue(state.isPending(newCleanup, -1));
    }

    @Test public void switchBackProtectsTheReactivatedDisplay() {
        DpDisplayCleanupState state = new DpDisplayCleanupState();
        state.observe(7);
        DpDisplayCleanupState.Request first = state.observe(8);
        DpDisplayCleanupState.Request second = state.observe(7);
        assertFalse(state.isPending(first, 7));
        assertTrue(state.isPending(second, 7));
        assertEquals(8, second.displayId);
    }

    @Test public void differentRetiredDisplaysKeepIndependentRequests() {
        DpDisplayCleanupState state = new DpDisplayCleanupState();
        state.observe(7);
        DpDisplayCleanupState.Request seven = state.observe(8);
        DpDisplayCleanupState.Request eight = state.observe(9);
        assertTrue(state.isPending(seven, 9));
        assertTrue(state.isPending(eight, 9));
        state.finish(seven);
        assertFalse(state.isPending(seven, 9));
        assertTrue(state.isPending(eight, 9));
    }

    @Test public void liveConfigurationBlocksCleanupBeforeObservationCatchesUp() {
        DpDisplayCleanupState state = new DpDisplayCleanupState();
        state.observe(7);
        DpDisplayCleanupState.Request cleanup = state.observe(-1);
        assertFalse(state.isPending(cleanup, 7));
    }

    @Test public void duplicateDisplayQueriesDoNotCancelPendingCleanup() {
        DpDisplayCleanupState state = new DpDisplayCleanupState();
        state.observe(7);
        DpDisplayCleanupState.Request cleanup = state.observe(8);
        assertNull(state.observe(8));
        assertTrue(state.isPending(cleanup, 8));
    }

    @Test public void phoneAndInvalidIdsNeverProduceCleanupTargets() {
        DpDisplayCleanupState state = new DpDisplayCleanupState();
        assertNull(state.observe(0));
        assertNull(state.observe(-1));
        assertNull(state.observe(-3));
        assertNull(state.observe(7));
        assertNull(state.observe(7));
    }
}
