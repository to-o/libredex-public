package com.connect_screen.mirror;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.connect_screen.mirror.transport.TransportRegistry;

import rikka.shizuku.Shizuku;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

public class LibreDeXApp extends Application {
    public static LibreDeXApp instance;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // Boot receivers/services run without MirrorMainActivity. Initialize
        // access before any of them resolve the privileged framework methods.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
                Log.i("LibreDeXApp", "Hidden API access initialized for background startup");
            } catch (Throwable t) {
                Log.w("LibreDeXApp", "Hidden API initialization failed", t);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        TransportRegistry.discover();
        Pref.init(this);
        int lastVersion = Pref.getLastRunVersionCode();
        int currentVersion = BuildConfig.VERSION_CODE;
        String lastCommit = Pref.getLastRunCommit();
        String currentCommit = BuildConfig.COMMIT;
        // Restart the Shizuku UserService when the code changed: either the
        // version number bumped or the build commit differs (dev builds often
        // reinstall the same version with new code). Same-version same-commit
        // launches keep the daemon alive (avoids frequent restarts that could
        // trip the Samsung process manager).
        boolean codeChanged = lastVersion != currentVersion
                || !currentCommit.equals(lastCommit);
        Pref.setLastRunVersionCode(currentVersion);
        Pref.setLastRunCommit(currentCommit);
        if (codeChanged) {
            recycleStaleUserServiceAfterUpdate();
        } else {
            recycleStaleUserService();
        }
        if (Pref.getDpSessionStarted()) {
            State.ensureUserServiceBound();
        }
    }

    /**
     * The language override is now applied in MirrorMainActivity.onCreate
     * (only when a non-system language was chosen), so the default path makes
     * zero AppCompat locale calls and stays identical to pre-i18n behavior.
     * The empty-list "follow system" call is intentionally not made at app
     * startup to avoid spurious locale-change activity recreations.
     */
    private void recycleStaleUserService() {
        // A Shizuku user service is a daemon: force-stopping or swiping away
        // the app does not destroy it, so a stale process can keep an old
        // virtual display alive. Always remove the previous service when the
        // app process starts fresh; a new one is started on demand.
        try {
            Shizuku.unbindUserService(State.userServiceArgs, State.userServiceConnection, true);
        } catch (Throwable ignored) {
        }
    }

    private void recycleStaleUserServiceAfterUpdate() {
        // Do not bind and then kill a service concurrently with the activity's
        // startup bind. Removing the old record first avoids killing the fresh
        // service just as DP output starts; the normal recovery path rebinds.
        State.unbindUserService();
    }
}
