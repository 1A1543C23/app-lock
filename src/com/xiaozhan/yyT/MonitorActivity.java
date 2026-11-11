package com.xiaozhan.yyT;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * 专门看「监测到底有没有在工作」：
 * 无障碍是否连着、使用情况权限有没有、最近抓到的前台应用是什么。
 */
public class MonitorActivity extends Activity {

    private TextView tvState;
    private TextView tvLog;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);

        tvState = (TextView) findViewById(R.id.tv_state);
        tvLog = (TextView) findViewById(R.id.tv_log);

        ((Button) findViewById(R.id.btn_clear)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Config.clearLog(MonitorActivity.this);
                refresh();
            }
        });

        ((Button) findViewById(R.id.btn_check)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!accessibilityOn()) {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ignored) {
                    }
                } else if (!usageOk()) {
                    try {
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        ui.postDelayed(ticker, 500);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(ticker);
        super.onPause();
    }

    private boolean accessibilityOn() {
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(getPackageName()) && s.contains("LockAccessibilityService");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean usageOk() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return false;
            }
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = ops.unsafeCheckOpNoThrow("android:get_usage_stats",
                        Process.myUid(), getPackageName());
            } else {
                mode = ops.checkOpNoThrow("android:get_usage_stats",
                        Process.myUid(), getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void refresh() {
        boolean a11y = accessibilityOn();
        boolean svc = Config.guardRunning(this);
        boolean usage = usageOk();

        StringBuilder sb = new StringBuilder();
        sb.append("无障碍：").append(a11y ? "已开启" : "没开启")
                .append(" / 服务").append(svc ? "运行中" : "没运行");
        sb.append("\n使用情况访问：").append(usage ? "已授权" : "没授权（轮询兜底不可用）");
        sb.append("\n最近识别：");
        String last = Config.lastPkg(this);
        sb.append(last == null || last.length() == 0 ? "还没抓到" : last);

        if (!a11y) {
            sb.append("\n\n→ 无障碍没开，抓不到任何应用，点右下「检查权限」去开");
        } else if (!svc) {
            sb.append("\n\n→ 无障碍开关是开的，但服务没跑起来，去无障碍设置里关一下再开");
        } else if (!usage) {
            sb.append("\n\n→ 无障碍正常，如果还是抓不到，去把「使用情况访问」也给它");
        }

        tvState.setText(sb.toString());

        String log = Config.log(this);
        tvLog.setText(log == null || log.length() == 0
                ? "（日志是空的。点开任意一个应用，再切回来看这里有没有记录）"
                : log);
    }
}
