package com.connect_screen.mirror.shizuku;

import android.os.Looper;
import android.os.SystemClock;

import com.connect_screen.mirror.State;
import com.connect_screen.mirror.job.AcquireShizuku;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Bounded recovery for a live Shizuku server with an unavailable UserService. */
public final class UserServiceRecovery {
    private static final String MANAGER_URI = "content://moe.shizuku.privileged.api.shizuku";
    // gettype crashes in the Android 16 content shell on SM-F7310 even when
    // this provider is available. An unknown call is side-effect-free in
    // ShizukuProvider and still acquires/starts the manager provider.
    private static final String MANAGER_PROBE = "content call --uri " + MANAGER_URI
            + " --method libredex_probe";

    private UserServiceRecovery() {}

    /** Called on the binding worker before starting the Shizuku child process. */
    public static boolean prepareManager() {
        String result = AcquireShizuku.runRootCommand(MANAGER_PROBE);
        if (providerReady(result)) return true;
        State.log("Shizuku manager provider unavailable; waking manager once");
        // Samsung can block provider auto-start for a sleeping manager even
        // while shizuku_server is alive. An explicit launch releases that state.
        String launch = AcquireShizuku.runRootCommand(
                "am start -n moe.shizuku.privileged.api/moe.shizuku.manager.MainActivity");
        if (launch == null || launch.contains("Error:")) return false;
        for (int attempt = 0; attempt < 3; attempt++) {
            SystemClock.sleep(500);
            result = AcquireShizuku.runRootCommand(MANAGER_PROBE);
            if (providerReady(result)) {
                State.bringMainActivityToFront();
                return true;
            }
        }
        return false;
    }

    static boolean providerReady(String output) {
        // Both an empty Bundle and null are valid replies to an unknown call.
        return output != null && output.contains("Result:")
                && !output.contains("Could not find provider")
                && !output.contains("Error while accessing provider")
                && !output.contains("Exception")
                && !output.contains("Permission Denial");
    }

    public static synchronized boolean ensureReady(boolean requireRoot) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("UserService recovery requires a worker thread");
        }
        return ensureReady(requireRoot, DEVICE);
    }

    /** Verify the same live helper before and after its root RPC. */
    public static boolean isReady(boolean requireRoot) {
        return isReady(requireRoot, () -> State.userService, State::isUserServiceAlive);
    }

    static boolean isReady(boolean requireRoot, Supplier<IUserService> currentService,
                           BooleanSupplier isAlive) {
        try {
            IUserService service = currentService.get();
            if (service == null || !isAlive.getAsBoolean()) return false;
            if (requireRoot && !service.isRooted()) return false;
            // The remote process can die, or the callback can replace the
            // helper, while isRooted() is in flight. Never certify that replacement.
            return service == currentService.get() && isAlive.getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    interface Backend {
        boolean isServerHealthy();
        boolean hasPermission();
        void bindAndAwait();
        boolean isReady(boolean requireRoot);
        boolean restartAsRoot();
        void unbind();
        void log(String message);
    }

    private static final Backend DEVICE = new Backend() {
        public boolean isServerHealthy() { return ShizukuUtils.isShizukuServerHealthy(); }
        public boolean hasPermission() { return ShizukuUtils.hasPermission(); }
        public boolean isReady(boolean requireRoot) { return UserServiceRecovery.isReady(requireRoot); }
        public boolean restartAsRoot() { return AcquireShizuku.fixRootShizuku(); }
        public void unbind() { State.unbindUserService(); }
        public void log(String message) { State.log(message); }
        public void bindAndAwait() {
            State.ensureUserServiceBound();
            long deadline = SystemClock.elapsedRealtime() + 15_000;
            while (!State.isUserServiceAlive() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100);
            }
        }
    };

    static boolean ensureReady(boolean requireRoot, Backend backend) {
        if (!backend.isServerHealthy() && !restartAndVerify(backend)) return false;
        if (!backend.hasPermission()) return false;
        for (int attempt = 0; attempt < 2; attempt++) {
            backend.bindAndAwait();
            if (backend.isReady(requireRoot)) return true;
            if (requireRoot && backend.isReady(false)) {
                return restartAndVerify(backend);
            }
            backend.log("UserService bind failed, removing stale record (attempt " + (attempt + 1) + ")");
            backend.unbind();
        }
        // A live server binder does not guarantee that the manager and client
        // use the same server. Concurrent boot starts were observed to leave
        // two root servers and reject helper tokens. For root-required DeX,
        // use the official starter once after ordinary binding retries; it
        // replaces the stale servers and then verifies a real root helper.
        if (requireRoot) {
            backend.log("UserService retries exhausted; restarting root Shizuku once");
            return restartAndVerify(backend);
        }
        return false;
    }

    private static boolean restartAndVerify(Backend backend) {
        // A recovery result is a hint until the caller checks the resulting
        // helper again. DP must never proceed with a dead or shell helper.
        return backend.restartAsRoot() && backend.isReady(true);
    }
}
