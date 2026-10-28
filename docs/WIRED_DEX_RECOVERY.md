# Wired DeX startup recovery

The Shizuku server process alone does not establish readiness. A wired DeX
session also needs the manager provider, the authorized LibreDeX UserService,
root privileges, and a live physical external display.

## Startup behavior

Before binding the helper, a worker probes the manager provider with a harmless
`content call`. Android 16 on SM-F7310 throws in the content shell's `gettype`
path even with a running manager, so `gettype` is not a readiness signal. If Samsung
blocks provider startup for a sleeping manager, recovery explicitly opens the
manager once and probes it again. When an activity is available, it returns to
LibreDeX after recovery. A failed helper bind is removed and retried once.
The probe is advisory: even if it fails, the actual UserService bind is attempted;
its live binder and timeout decide readiness. The UI remains responsive while these bounded operations run. Failure leaves
an actionable message directing the user to Shizuku rather than reporting a
running DeX session.

For root-required DeX, two unsuccessful helper bindings are followed by one
restart through Shizuku's official root starter and a real helper/root check.
This also recovers the observed case of two boot-started Shizuku servers whose
client and manager binders disagree and reject the helper token. Root restarts
inside LibreDeX are serialized. Ordinary mirroring does not trigger this final
root restart.

On Samsung firmware, allow LibreDeX unrestricted background operation in its
app battery settings before testing automatic startup. A foreground service
cannot undo Samsung MARs force-stopping an app configured as restricted.

Framework access is initialized in Application.attachBaseContext, including
receiver/service-only starts. Initializing it only in the activity caused
boot-started input routing to throw NoSuchMethodError and restart the app.
Input device enumeration uses public InputDevice APIs; privileged IPC is
reserved for the routing changes themselves.

The existing **Automatically start DeX when connected** switch is opt-in. When
enabled, a `specialUse` foreground service owns the cable listener and displays
a notification. It does not require screen-capture permission just to monitor
the cable. The listener survives activity backgrounding, handles an already
connected display at service startup, and is restored after normal boot or an
app update. Turning the switch off stops monitoring without ending an active
desktop session. Android force-stop still requires opening the app again.

The listener debounces the latest display event and tracks display identity,
so a fast unplug/replug with a new logical display ID replaces the old session.
Startup attempts invalidated by a stop or display removal cannot proceed after
the helper finishes recovering.

## Validation

Automated checks:

```text
./gradlew :xposed-stubs:test :app:testDebugUnitTest :caption-fix:testDebugUnitTest
python scripts/check_i18n_consistency.py
```

The Xposed test enforces the runtime `setResult(Object)` descriptor. The display
policy tests cover startup with an attached display, repeated mode events,
disconnect/reconnect, and replacement with a new display ID.

For a Java-only rebuild, a previously verified release's unchanged
`arm64-v8a/libsunshine_android.so` may be supplied using
`-PlibredexPrebuiltNativeDir=/absolute/path/to/jni-libraries`. The directory must
contain the ABI subdirectory. Default builds continue to compile the engine
from source. Record the source APK checksum with any validation artifacts.

Device validation still requires all of the following:

1. Start DP output while the manager UI has not been opened; verify helper
   connection and SecondaryLauncher on the physical display.
2. Enable automatic wired DeX, background the app, and connect a display.
3. Open the app with a display already attached.
4. Disconnect/reconnect repeatedly and change output modes; verify no duplicate
   desktop starts, stale input bindings, or unexpected phone reboot.
5. Reboot, unlock, and verify opt-in monitoring and the root Shizuku helper.

Do not enable both an original and a debug LibreDeX Xposed module at once.
Debug builds use `com.libredex.debug` and do not overwrite the release package.
Updating a release package requires its original signing key.
