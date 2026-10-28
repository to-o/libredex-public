package com.connect_screen.mirror.shizuku;

import android.os.Looper;
import android.os.SystemClock;

import com.connect_screen.mirror.State;
import com.connect_screen.mirror.job.AcquireShizuku;

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
        if (!ShizukuUtils.isShizukuServerHealthy()) {
            if (!AcquireShizuku.fixRootShizuku()) return false;
        }
        if (!ShizukuUtils.hasPermission()) return false;
        for (int attempt = 0; attempt < 2; attempt++) {
            State.ensureUserServiceBound();
            long deadline = SystemClock.elapsedRealtime() + 15_000;
            while (!State.isUserServiceAlive() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100);
            }
            if (State.isUserServiceAlive()) {
                try {
                    if (!requireRoot || State.userService.isRooted()) return true;
                    return AcquireShizuku.fixRootShizuku();
                } catch (Throwable t) {
                    State.log("UserService readiness failed: " + t);
                }
            }
            State.log("UserService bind failed, removing stale record (attempt " + (attempt + 1) + ")");
            State.unbindUserService();
        }
        return false;
    }
}
