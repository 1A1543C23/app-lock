package com.xiaozhan.yyT;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 11;
    private static final int REQ_BATTERY = 12;
    private static final int REQ_ACCESS = 13;
    private static final int REQ_USAGE = 14;

    private static final String GUIDE_FILE = "guide_tries";
    private static final int MAX_TRIES = 2;

    private TextView tvPwd;
    private TextView tvPerm;
    private TextView tvDiag;
    private TextView tvSwitchState;
    private TextView tvLockedTitle;
    private LinearLayout listLocked;
    private Switch swEnable;

    private boolean skipNextAuto;
    private boolean updatingSwitch;

    private final Handler uih = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            uih.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvPwd = (TextView) findViewById(R.id.tv_pwd);
        tvPerm = (TextView) findViewById(R.id.tv_perm);
        tvDiag = (TextView) findViewById(R.id.tv_diag);
        tvSwitchState = (TextView) findViewById(R.id.tv_switch_state);
        tvLockedTitle = (TextView) findViewById(R.id.tv_locked_title);
        listLocked = (LinearLayout) findViewById(R.id.list_locked);
        swEnable = (Switch) findViewById(R.id.sw_enable);

        swEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (updatingSwitch) {
                    return;
                }
                Config.setEnabled(MainActivity.this, isChecked);
                refresh();
            }
        });

        findViewById(R.id.btn_test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LockActivity.launch(MainActivity.this, "");
            }
        });

        findViewById(R.id.btn_monitor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, MonitorActivity.class));
            }
        });

        findViewById(R.id.btn_set).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSetDialog();
            }
        });

        findViewById(R.id.btn_random).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeRandomPwd();
            }
        });

        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AppListActivity.class));
            }
        });

        tvPerm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpFirstMissing();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (skipNextAuto) {
            skipNextAuto = false;
        } else {
            autoGrant();
        }
        refresh();
        uih.removeCallbacks(ticker);
        uih.postDelayed(ticker, 600);
    }

    @Override
    protected void onPause() {
        uih.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        skipNextAuto = true;
    }

    /** 首次打开时自动带用户去把权限开齐，不用在界面里点「开启」。 */
    private void autoGrant() {
        int tOverlay = tries(REQ_OVERLAY);
        int tBattery = tries(REQ_BATTERY);
        int tAccess = tries(REQ_ACCESS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !overlayOk() && tOverlay < MAX_TRIES) {
            incTries(REQ_OVERLAY);
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
            } catch (Exception ignored) {
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !batteryOk() && tBattery < MAX_TRIES) {
            incTries(REQ_BATTERY);
            try {
                startActivityForResult(new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())), REQ_BATTERY);
            } catch (Exception e) {
                try {
                    startActivityForResult(new Intent(
                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), REQ_BATTERY);
                } catch (Exception ignored) {
                }
            }
            return;
        }

        if (!accessibilityOn() && tAccess < MAX_TRIES) {
            incTries(REQ_ACCESS);
            try {
                startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), REQ_ACCESS);
            } catch (Exception ignored) {
            }
            return;
        }

        int tUsage = tries(REQ_USAGE);
        if (accessibilityOn() && !usageOk() && tUsage < MAX_TRIES) {
            incTries(REQ_USAGE);
            try {
                startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQ_USAGE);
            } catch (Exception ignored) {
            }
        }
    }

    /** 界面上那行提示，点一下也能去补权限。 */
    private void jumpFirstMissing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !overlayOk()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
        } else if (!accessibilityOn()) {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {
            }
        } else {
            toast("权限都齐了");
        }
    }

    private int tries(int code) {
        return guide().getInt("t" + code, 0);
    }

    private void incTries(int code) {
        guide().edit().putInt("t" + code, tries(code) + 1).apply();
    }

    private SharedPreferences guide() {
        return getSharedPreferences(GUIDE_FILE, MODE_PRIVATE);
    }

    private boolean overlayOk() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private boolean batteryOk() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean accessibilityOn() {
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) {
                return false;
            }
            return s.contains(getPackageName()) && s.contains("LockAccessibilityService");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean usageOk() {
        try {
            android.app.AppOpsManager ops =
                    (android.app.AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) {
                return false;
            }
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = ops.unsafeCheckOpNoThrow("android:get_usage_stats",
                        android.os.Process.myUid(), getPackageName());
            } else {
                mode = ops.checkOpNoThrow("android:get_usage_stats",
                        android.os.Process.myUid(), getPackageName());
            }
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private void refresh() {
        tvPwd.setText(Config.brief(Config.pwd(this)));

        boolean enabled = Config.enabled(this);
        updatingSwitch = true;
        swEnable.setChecked(enabled);
        updatingSwitch = false;
        tvSwitchState.setText(enabled ? "开：打开被锁应用会弹密码框" : "关：现在不拦任何应用");
        tvSwitchState.setTextColor(enabled ? 0xFF16A34A : 0xFF9CA3AF);

        boolean a11y = accessibilityOn();
        boolean ov = overlayOk();
        boolean svc = Config.guardRunning(this);

        if (a11y && ov) {
            tvPerm.setText("√ 权限已就绪");
            tvPerm.setTextColor(0xFF16A34A);
        } else if (!a11y) {
            tvPerm.setText("× 无障碍服务没开，现在不会拦任何应用（点这行去开）");
            tvPerm.setTextColor(0xFFDC2626);
        } else {
            tvPerm.setText("× 悬浮窗权限没开，拦截可能被系统挡住（点这行去开）");
            tvPerm.setTextColor(0xFFB45309);
        }

        String svcText;
        if (!a11y) {
            svcText = "没开启";
        } else if (svc) {
            svcText = "已开启，正在运行";
        } else {
            svcText = "已开启，但没在运行（去无障碍设置里关一下再开）";
        }

        String last = Config.lastPkg(this);
        long lt = Config.lastTime(this);
        String lastLine;
        if (last == null || last.length() == 0) {
            lastLine = "还没收到过任何应用切换";
        } else {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new Date(lt));
            lastLine = last + "（" + time + "）";
        }

        String block = Config.lastBlock(this);
        if (block == null || block.length() == 0) {
            block = "还没有拦截过任何应用";
        }

        tvDiag.setText("无障碍服务：" + svcText
                + "\n轮询兜底：" + (usageOk() ? "可用" : "没授权使用情况访问")
                + "\n最近识别到：" + lastLine
                + "\n最近拦截：" + block
                + "\n已加锁：" + Config.locked(this).size() + " 个应用");

        renderLocked();
    }

    private void renderLocked() {
        Set<String> locked = Config.locked(this);
        tvLockedTitle.setText("已锁定的应用（" + locked.size() + "）");
        listLocked.removeAllViews();

        List<String> sorted = new ArrayList<String>(locked);
        Collections.sort(sorted);

        for (String pkg : sorted) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackgroundColor(0xFFFFFFFF);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            col.setLayoutParams(colLp);

            TextView name = new TextView(this);
            name.setText(appLabel(pkg));
            name.setTextColor(0xFF111827);
            name.setTextSize(15);

            TextView sub = new TextView(this);
            sub.setText(pkg);
            sub.setTextColor(0xFF9CA3AF);
            sub.setTextSize(11);

            col.addView(name);
            col.addView(sub);
            row.addView(col);

            final String target = pkg;
            TextView del = new TextView(this);
            del.setText("移除");
            del.setTextColor(0xFFDC2626);
            del.setTextSize(13);
            del.setPadding(dp(12), dp(6), dp(4), dp(6));
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Config.setLocked(MainActivity.this, target, false);
                    refresh();
                }
            });
            row.addView(del);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(6);
            row.setLayoutParams(rowLp);

            listLocked.addView(row);
        }
    }

    private String appLabel(String pkg) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            return getPackageManager().getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private void makeRandomPwd() {
        SecureRandom r = new SecureRandom();
        int len = 6 + r.nextInt(7);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append((char) ('0' + r.nextInt(10)));
        }
        final String pwd = sb.toString();
        Config.setPwd(this, pwd);
        refresh();
        showPwdDialog("随机密码已设置", pwd);
    }

    private void showSetDialog() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setSingleLine(true);
        et.setHint("只能输入数字，最多 " + Prefs.MAX_LEN + " 位");
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Prefs.MAX_LEN)});
        String cur = Config.pwd(this);
        if (cur.length() > 0 && cur.length() <= 64) {
            et.setText(cur);
            et.setSelection(et.getText().length());
        }

        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("设置密码")
                .setView(box)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String p = et.getText().toString();
                        if (p.length() == 0) {
                            toast("密码不能为空");
                            return;
                        }
                        Config.setPwd(MainActivity.this, p);
                        refresh();
                        toast("已保存，共 " + p.length() + " 位");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showPwdDialog(String title, final String pwd) {
        TextView tv = new TextView(this);
        tv.setText(pwd);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(18);
        tv.setPadding(dp(24), dp(16), dp(24), dp(16));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(tv)
                .setPositiveButton("复制", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("password", pwd));
                            toast("已复制到剪贴板");
                        }
                    }
                })
                .setNegativeButton("知道了", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
