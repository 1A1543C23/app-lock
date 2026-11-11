package com.xiaozhan.yyT;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 跑在 :guard 独立进程里的前台服务。
 * 主界面被划掉也不影响它，拦截照常。
 */
public class GuardForegroundService extends Service {

    private static final String CH = "guard_keep";
    private static final int ID = 3001;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
    }

    private void goForeground() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        CH, "守护", NotificationManager.IMPORTANCE_MIN);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            b = new Notification.Builder(this, CH);
        } else {
            b = new Notification.Builder(this);
        }

        b.setSmallIcon(R.drawable.ic_stat_lock)
                .setContentTitle("应用锁")
                .setContentText("守护中")
                .setContentIntent(pi)
                .setOngoing(true);

        startForeground(ID, b.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 被系统杀掉之后尽量自己回来
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 主界面被划掉，守护继续，这里重新挂一下前台
        try {
            startService(new Intent(this, GuardForegroundService.class));
        } catch (Exception ignored) {
        }
        super.onTaskRemoved(rootIntent);
    }
}
