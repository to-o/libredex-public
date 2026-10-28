package com.connect_screen.mirror;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/** Opt-in foreground owner of the cable listener, independent of the activity. */
public class DpAutoSwitchService extends Service {
    private static final String CHANNEL = "dex_auto_start";

    public static void update(Context context) {
        Intent intent = new Intent(context, DpAutoSwitchService.class);
        try {
            if (Pref.getAutoDexOnHotplug()) {
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.stopService(intent);
            }
        } catch (RuntimeException e) {
            State.log("DeX cable monitor could not start: " + e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Pref.getAutoDexOnHotplug()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.dp_auto_notification_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MirrorMainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.dp_auto_notification_text))
                .setSmallIcon(R.drawable.ic_launcher_monochrome_v2)
                .setContentIntent(open).setOngoing(true).build();
        // Monitoring the cable does not capture media and needs no projection grant.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1025, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1025, notification);
        }
        DpAutoSwitch.attach(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        DpAutoSwitch.detach();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
