package com.connect_screen.mirror.shizuku;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserServiceRecoveryTest {
    @Test
    public void acceptsEmptyCallResponses() {
        assertTrue(UserServiceRecovery.providerReady("Result: Bundle[{}]\n"));
        assertTrue(UserServiceRecovery.providerReady("Result: null\n"));
    }

    @Test
    public void rejectsAndroid16GetTypeCrash() {
        assertFalse(UserServiceRecovery.providerReady(
                "Error while accessing provider:moe.shizuku.privileged.api.shizuku\n"
                        + "java.lang.NullPointerException: Attempt to read from null array\n"));
    }

    @Test
    public void rejectsMissingProviderAndDeniedCalls() {
        assertFalse(UserServiceRecovery.providerReady("Could not find provider\n"));
        assertFalse(UserServiceRecovery.providerReady("Result: null\nPermission Denial\n"));
        assertFalse(UserServiceRecovery.providerReady("Result: null\njava.lang.SecurityException\n"));
    }

    @Test
    public void rejectsMissingOrUnrecognizedOutput() {
        assertFalse(UserServiceRecovery.providerReady(null));
        assertFalse(UserServiceRecovery.providerReady(""));
        assertFalse(UserServiceRecovery.providerReady("Permission denied\n"));
    }
}
