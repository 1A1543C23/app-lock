package com.xiaozhan.yyT;

import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 前台应用监测。两条采集通道：
 *  1) 无障碍服务的窗口切换事件（实时、最准）
 *  2) 使用情况统计 API 轮询（无障碍不灵时兜底，需要「使用情况访问」权限）
 * 命中锁定清单就把悬浮窗锁界面拉起来。
 */
public class LockAccessibilityService extends AccessibilityService {

    private static boolean running;

    public static boolean isRunning() {
        return running;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());

    private String lastPkg = "";
    private long lastTime;
    private long lastNote;
    private long lastUsageCheck;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            try {
                checkUsage();
            } catch (Exception ignored) {
            }
            ui.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = true;
        Prefs.setRunning(this, true);
        Prefs.noteSeen(this, "服务已连接");
        log("服务已连接（无障碍通道就绪）");
        // 拉起同进程的前台服务，让守护进程更难被划掉
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, GuardForegroundService.class));
            } else {
                startService(new Intent(this, GuardForegroundService.class));
            }
        } catch (Exception ignored) {
        }
        ui.removeCallbacks(poll);
        ui.postDelayed(poll, 1200);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence name = event.getPackageName();
        if (name == null) {
            return;
        }
        handleForeground(name.toString(), "无障碍");
    }

    /** 轮询最近的前台应用，防止无障碍通道被 ROM 掐掉。 */
    private void checkUsage() {
        long now = System.currentTimeMillis();
        if (now - lastUsageCheck < 1000L) {
            return;
        }
        lastUsageCheck = now;

        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return;
        }
        UsageEvents events = usm.queryEvents(now - 5000L, now);
        if (events == null) {
            return;
        }

        UsageEvents.Event e = new UsageEvents.Event();
        String last = null;
        long t = 0L;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            // 1 = MOVE_TO_FOREGROUND（29 之后叫 ACTIVITY_RESUMED，值一样）
            if (e.getEventType() == 1 && e.getTimeStamp() > t) {
                t = e.getTimeStamp();
                last = e.getPackageName();
            }
        }
        if (last != null) {
            handleForeground(last, "轮询");
        }
    }

    private void handleForeground(String pkg, String from) {
        if (pkg == null || pkg.length() == 0) {
            return;
        }
        // 自己的界面不拦
        if (pkg.equals(getPackageName())) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastNote > 900L) {
            lastNote = now;
            Prefs.noteSeen(this, pkg);
            boolean locked = Prefs.isLocked(this, pkg);
            log(pkg + (locked ? "  [已锁]" : "  [未锁]") + "  via " + from);
        }

        if (pkg.equals("com.android.systemui")) {
            return;
        }

        if (!Prefs.isEnabled(this)) {
            return;
        }

        // 锁界面已经在前台，别重复弹
        if (LockOverlay.isShowing() || LockActivity.isShowing()) {
            return;
        }

        if (!Prefs.isLocked(this, pkg)) {
            // 切到没锁的应用，清掉上一次的放行记录
            Prefs.clearUnlocked(this);
            return;
        }

        if (Prefs.isUnlockedRecently(this, pkg)) {
            return;
        }

        if (pkg.equals(lastPkg) && now - lastTime < 1000L) {
            return;
        }
        lastPkg = pkg;
        lastTime = now;

        Prefs.noteBlock(this, pkg);
        log("拦截 " + pkg);
        LockOverlay.show(this, pkg);
    }

    private void log(String s) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        Prefs.appendLog(this, time + "  " + s);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        running = false;
        Prefs.setRunning(this, false);
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        running = false;
        Prefs.setRunning(this, false);
        ui.removeCallbacks(poll);
        return super.onUnbind(intent);
    }
}
