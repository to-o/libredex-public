package com.connect_screen.mirror.shizuku;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserServiceReadinessTest {
    @Test
    public void connectedShellRecoveryCannotReportSuccessWithoutNewConnection() {
        FakeBackend backend = FakeBackend.connected(false);
        backend.afterRestart = () -> backend.service = null;

        assertFalse(UserServiceRecovery.ensureReady(true, backend));
        assertEquals(1, backend.restarts);
    }

    @Test
    public void connectedShellRecoveryRejectsDeadRootBinder() {
        FakeBackend backend = FakeBackend.connected(false);
        backend.afterRestart = () -> {
            backend.service = new TestService(true);
            backend.alive = false;
        };

        assertFalse(UserServiceRecovery.ensureReady(true, backend));
        assertEquals(1, backend.restarts);
    }

    @Test
    public void connectedShellRecoveryRejectsStillUnprivilegedHelper() {
        FakeBackend backend = FakeBackend.connected(false);

        assertFalse(UserServiceRecovery.ensureReady(true, backend));
        assertEquals(1, backend.restarts);
    }

    @Test
    public void connectedShellRecoveryAcceptsNewLiveRootHelper() {
        FakeBackend backend = FakeBackend.connected(false);
        backend.afterRestart = () -> backend.service = new TestService(true);

        assertTrue(UserServiceRecovery.ensureReady(true, backend));
        assertEquals(1, backend.restarts);
    }

    @Test
    public void failedRecoveryDoesNotPassEvenIfHelperLooksReady() {
        FakeBackend backend = FakeBackend.connected(false);
        backend.recoverySucceeded = false;
        backend.afterRestart = () -> backend.service = new TestService(true);

        assertFalse(UserServiceRecovery.ensureReady(true, backend));
    }

    @Test
    public void existingLiveRootHelperDoesNotRestart() {
        FakeBackend backend = FakeBackend.connected(true);

        assertTrue(UserServiceRecovery.ensureReady(true, backend));
        assertEquals(0, backend.restarts);
    }

    @Test
    public void mirrorSessionAcceptsLiveShellHelperWithoutRestart() {
        FakeBackend backend = FakeBackend.connected(false);

        assertTrue(UserServiceRecovery.ensureReady(false, backend));
        assertEquals(0, backend.restarts);
    }

    @Test
    public void exhaustedBindingRetriesAlsoVerifyRecoveryResult() {
        FakeBackend backend = new FakeBackend();
        backend.afterRestart = () -> backend.service = new TestService(false);

        assertFalse(UserServiceRecovery.ensureReady(true, backend));
        assertEquals(2, backend.binds);
        assertEquals(2, backend.unbinds);
        assertEquals(1, backend.restarts);
    }

    @Test
    public void unhealthyServerRecoveryMustProduceLiveRootHelper() {
        FakeBackend backend = new FakeBackend();
        backend.serverHealthy = false;

        assertFalse(UserServiceRecovery.ensureReady(true, backend));
        assertEquals(1, backend.restarts);
        assertEquals(0, backend.binds);
    }

    @Test
    public void mirrorBindingFailureDoesNotRestartRootServer() {
        FakeBackend backend = new FakeBackend();

        assertFalse(UserServiceRecovery.ensureReady(false, backend));
        assertEquals(2, backend.binds);
        assertEquals(0, backend.restarts);
    }

    @Test
    public void rootRpcFailureIsNotReadiness() {
        FakeBackend backend = FakeBackend.connected(true);
        backend.service.onRootCheck = () -> { throw new IllegalStateException("Binder died"); };

        assertFalse(backend.isReady(true));
    }

    @Test
    public void binderDeathDuringRootRpcIsNotReadiness() {
        FakeBackend backend = FakeBackend.connected(true);
        backend.service.onRootCheck = () -> backend.alive = false;

        assertFalse(backend.isReady(true));
    }

    @Test
    public void replacementDuringRootRpcIsNotCertifiedByOldRootReply() {
        FakeBackend backend = FakeBackend.connected(true);
        backend.service.onRootCheck = () -> backend.service = new TestService(false);

        assertFalse(backend.isReady(true));
    }

    private static final class TestService extends IUserService.Default {
        final boolean root;
        Runnable onRootCheck = () -> {};

        TestService(boolean root) { this.root = root; }

        @Override
        public boolean isRooted() {
            onRootCheck.run();
            return root;
        }
    }

    private static final class FakeBackend implements UserServiceRecovery.Backend {
        TestService service;
        boolean alive = true;
        boolean serverHealthy = true;
        boolean recoverySucceeded = true;
        int binds;
        int unbinds;
        int restarts;
        Runnable afterRestart = () -> {};

        static FakeBackend connected(boolean root) {
            FakeBackend backend = new FakeBackend();
            backend.service = new TestService(root);
            return backend;
        }

        public boolean isServerHealthy() { return serverHealthy; }
        public boolean hasPermission() { return true; }
        public void bindAndAwait() { binds++; }
        public boolean isReady(boolean requireRoot) {
            return UserServiceRecovery.isReady(requireRoot, () -> service, () -> alive);
        }
        public boolean restartAsRoot() {
            restarts++;
            afterRestart.run();
            return recoverySucceeded;
        }
        public void unbind() {
            unbinds++;
            service = null;
        }
        public void log(String message) {}
    }
}
