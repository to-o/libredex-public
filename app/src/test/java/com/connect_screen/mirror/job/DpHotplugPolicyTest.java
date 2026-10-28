package com.connect_screen.mirror.job;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static com.connect_screen.mirror.job.DpHotplugPolicy.Change.*;

public class DpHotplugPolicyTest {
    @Test public void alreadyConnectedAtStartupStartsOnce() {
        DpHotplugPolicy policy = new DpHotplugPolicy();
        assertEquals(CONNECT, policy.update(8));
        assertEquals(NONE, policy.update(8));
    }

    @Test public void modeAndRefreshChangesDoNotRestartDesktop() {
        DpHotplugPolicy policy = new DpHotplugPolicy();
        policy.update(8);
        for (int event = 0; event < 20; event++) assertEquals(NONE, policy.update(8));
    }

    @Test public void fastReplugWithNewDisplayIdReplacesSession() {
        DpHotplugPolicy policy = new DpHotplugPolicy();
        policy.update(8);
        assertEquals(REPLACE, policy.update(9));
        assertEquals(NONE, policy.update(9));
    }

    @Test public void disconnectAndReconnectProduceBothEdges() {
        DpHotplugPolicy policy = new DpHotplugPolicy();
        assertEquals(NONE, policy.update(0));
        assertEquals(CONNECT, policy.update(8));
        assertEquals(DISCONNECT, policy.update(-1));
        assertEquals(NONE, policy.update(0));
        assertEquals(CONNECT, policy.update(9));
    }
}
