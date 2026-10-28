package com.connect_screen.mirror.job;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import com.connect_screen.mirror.State;
import com.connect_screen.mirror.shizuku.ShizukuUtils;
import com.connect_screen.mirror.shizuku.UserServiceRecovery;

import rikka.shizuku.Shizuku;

public class AcquireShizuku implements Job {
    public static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;
    private static volatile boolean rootProbePassed;
    private boolean hasRequestedPermission;
    public boolean acquired = false;

    @Override
    public void start() throws YieldException {
        if (!ShizukuUtils.hasShizukuStarted()) {
            return;
        }
        if (ShizukuUtils.hasPermission()) {
            State.log("Already have Shizuku permission");
            acquired = true;
            if (hasRequestedPermission) {
                State.bindUserService();
            }
        } else {
            if (hasRequestedPermission) {
                State.log("Failed to acquire Shizuku permission");
                return;
            }
            hasRequestedPermission = true;
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            throw new YieldException("Waiting for Shizuku permission");
        }
    }

    public static synchronized boolean fixRootShizuku() {
        if (UserServiceRecovery.isReady(true)) {
            State.log("UserService already running as root");
            return true;
        }
        State.log("UserService is not root; restarting Shizuku as root");
        // Temporary root (KSU temp grant) expires after first use; force fresh probe instead of cached true
        rootProbePassed = false;
        String su = findSu();
        if (su == null) {
            State.log("su binary not found; DP DeX needs Shizuku started as root");
            return false;
        }
        if (!probeRoot(su)) {
            State.log("su exists but root probe failed; grant LibreDeX root in KSU first");
            return false;
        }
        try {
            State.unbindUserService();
        } catch (Throwable ignored) {
        }
        String[] starter = findShizukuStarter();
        if (starter == null) {
            return false;
        }
        String out = runRootCommand(shellQuote(starter[0])
                + " --apk=" + shellQuote(starter[1]));
        State.log("root Shizuku starter out="
                + (out == null ? "null" : out.trim()));
        if (out == null || !out.contains("info: shizuku_starter exit with 0")) {
            State.log("root Shizuku starter failed, check Shizuku/root setup");
            return false;
        }
        // Let the old shell server die before waiting for the root server.
        sleep(1000);

        long serverDeadline = SystemClock.elapsedRealtime() + 10_000;
        while (!ShizukuUtils.isShizukuServerHealthy()
                && SystemClock.elapsedRealtime() < serverDeadline) {
            sleep(300);
        }
        if (!ShizukuUtils.isShizukuServerHealthy()) {
            State.log("Shizuku server unavailable after root restart");
            return false;
        }
        // 绑定 UserService 需要 Shizuku 已授权本应用；未授权时 bind 会抛
        // SecurityException（addUserService requires permission）直接崩线程，这里先做权限防护。
        if (ShizukuUtils.hasPermission()) {
            State.bindUserService();
        } else {
            State.log("Shizuku not granted, cannot bind root UserService");
            return false;
        }
        // Server startup must not consume the helper's binding timeout.
        long bindDeadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < bindDeadline) {
            if (UserServiceRecovery.isReady(true)) {
                State.log("UserService restarted as root");
                return true;
            }
            sleep(300);
        }
        State.log("UserService unavailable or not root after restart");
        return false;
    }

    /**
     * 应用启动时在后台探活本地 root 与 Shizuku：
     * su 可用 → 只证明本地可执行 root 命令，不代表远端 UserService 以 uid 0 运行；
     *           server 健康时只尝试绑定，server 掉线时才用官方 starter 恢复；
     * su 不可用 → 仅记录日志，Shizuku 授权兜底由 onResume 主线程的 AcquireShizuku 任务负责。
     * 全程后台线程，不阻塞主线程；失败只记录日志，不打扰用户。
     */
    public static void autoAcquireRoot() {
        new Thread(() -> {
            try {
                if (UserServiceRecovery.isReady(true)) {
                    State.log("Auto root acquisition: already has root, skipping");
                    return;
                }
                // 直接 su 探活，不经过 Shizuku/root 重启。
                String su = findSu();
                if (su == null || !probeRoot(su)) {
                    State.log("Auto root acquisition: no root/su, falling back to main-thread Shizuku grant flow");
                    return;
                }
                // 根自愈：server 掉线时 hasPermission()==false（checkSelfPermission 依赖
                // 活的 server binder），这里不再当“没授权”直接放弃，而是用 root 拉起。
                if (!ShizukuUtils.isShizukuServerHealthy()) {
                    State.log("Auto root acquisition: Shizuku server unhealthy, auto-restarting as root");
                    fixRootShizuku();
                    return;
                }
                State.log("Auto root acquisition: root available (su=" + su + "), ensuring UserService is bound");
                State.ensureUserServiceBound();
            } catch (Throwable t) {
                State.log("Auto root acquisition failed: " + t.getMessage());
            }
        }, "auto-root-acquire").start();
    }

    private static String[] findShizukuStarter() {
        String pmOut = runRootCommand("pm path moe.shizuku.privileged.api");
        String apkPath = null;
        if (pmOut != null) {
            for (String line : pmOut.split("\n")) {
                String t = line.trim();
                if (t.startsWith("package:")) {
                    apkPath = t.substring("package:".length()).trim();
                    break;
                }
            }
        }
        if (apkPath == null || apkPath.isEmpty()) {
            State.log("Shizuku manager APK not found");
            return null;
        }
        File apkFile = new File(apkPath);
        String starter = apkFile.getParentFile() + "/lib/arm64/libshizuku.so";
        String check = runRootCommand("ls -l " + shellQuote(starter) + " 2>&1");
        if (check != null
                && !check.contains("No such file")
                && !check.contains("cannot access")) {
            return new String[]{starter, apkPath};
        }
        String lsOut = runRootCommand(
                "find /data/app -name libshizuku.so 2>/dev/null | head -1");
        if (lsOut != null) {
            for (String line : lsOut.split("\n")) {
                String t = line.trim();
                if (!t.isEmpty()) {
                    return new String[]{t, apkPath};
                }
            }
        }
        State.log("libshizuku.so not found");
        return null;
    }

    private static boolean probeRoot(String su) {
        try {
            ProcessBuilder builder = new ProcessBuilder(su, "-c", "id");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            State.log("root probe exit=" + process.exitValue()
                    + " out=" + output.toString().trim());
            boolean ok = process.exitValue() == 0 && output.indexOf("uid=0") >= 0;
            rootProbePassed = ok;
            return ok;
        } catch (Throwable t) {
            State.log("root probe failed: " + t.getMessage());
            rootProbePassed = false;
            return false;
        }
    }

    public static String runRootCommand(String command) {
        String su = findSu();
        if (su == null || !probeRoot(su)) {
            return null;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(su, "-c", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            return output.toString();
        } catch (Throwable t) {
            State.log("runRootCommand failed: " + t.getMessage());
            return null;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String findSu() {
        String[] candidates = {
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/vendor/bin/su",
                "/product/bin/su",
                "/product/xbin/su",
                "/data/adb/ksu/bin/su",
                "/data/adb/ap/bin/su"
        };
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.isFile() || f.canExecute()) {
                return candidate;
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) {
                if (dir == null || dir.isEmpty()) {
                    continue;
                }
                String candidate = dir.endsWith("/") ? dir + "su" : dir + "/su";
                File f = new File(candidate);
                if (f.isFile() || f.canExecute()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
