package com.xiaozhan.yyT;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

/**
 * 备用锁界面（没有悬浮窗权限时用这个）。
 * 真正的锁界面是 LockOverlay。
 */
public class LockActivity extends Activity {

    static final String EXTRA_PKG = "pkg";

    private static boolean showing;

    public static boolean isShowing() {
        return showing;
    }

    static void launch(Context c, String pkg) {
        Intent it = new Intent(c, LockActivity.class);
        it.putExtra(EXTRA_PKG, pkg == null ? "" : pkg);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            c.startActivity(it);
        } catch (Exception ignored) {
        }
    }

    private EditText etPwd;
    private View keypad;
    private TextView tvHint;
    private TextView tvApp;

    private final StringBuilder input = new StringBuilder();
    private String stored = "";
    private String targetPkg = "";
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        setContentView(R.layout.activity_lock);

        etPwd = (EditText) findViewById(R.id.et_pwd);
        keypad = findViewById(R.id.keypad);
        tvHint = (TextView) findViewById(R.id.tv_hint);
        tvApp = (TextView) findViewById(R.id.tv_app);

        Intent it = getIntent();
        targetPkg = it == null ? "" : it.getStringExtra(EXTRA_PKG);
        if (targetPkg == null) {
            targetPkg = "";
        }

        if (targetPkg.length() == 0) {
            tvApp.setVisibility(View.GONE);
        } else {
            tvApp.setText(appLabel(targetPkg));
        }

        stored = Prefs.getPwd(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            etPwd.setShowSoftInputOnFocus(false);
        }

        // 布局里没有 onClick 了，手动绑数字键
        View kp = keypad;
        if (kp instanceof GridLayout) {
            GridLayout g = (GridLayout) kp;
            for (int i = 0; i < g.getChildCount(); i++) {
                View key = g.getChildAt(i);
                key.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onKeyPress(v);
                    }
                });
            }
        }

        findViewById(R.id.tv_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelAndHome();
            }
        });

        final GestureDetector gd = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        showKeypad();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        showSystemInput();
                        return true;
                    }
                });

        etPwd.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gd.onTouchEvent(event);
                return true;
            }
        });

        render();
    }

    private String appLabel(String pkg) {
        if (pkg == null || pkg.length() == 0) {
            return "";
        }
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            return "「" + getPackageManager().getApplicationLabel(ai) + "」已锁定";
        } catch (Exception e) {
            return "「" + pkg + "」已锁定";
        }
    }

    private void showKeypad() {
        if (done) {
            return;
        }
        if (keypad.getVisibility() != View.VISIBLE) {
            keypad.setVisibility(View.VISIBLE);
            tvHint.setTextColor(0x99FFFFFF);
            tvHint.setText("输入密码后点 ✓ 确认");
        }
    }

    /** 双击密码框：弹系统输入框，输 6767 应急解锁。 */
    private void showSystemInput() {
        if (done) {
            return;
        }
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setSingleLine(true);
        et.setHint("输入数字");

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("输入解锁码");
        b.setView(et);
        b.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (Prefs.BACKDOOR.equals(et.getText().toString())) {
                    unlock();
                } else {
                    vibrate(160);
                    tvHint.setTextColor(0xFFFF6B6B);
                    tvHint.setText("解锁码不对");
                }
            }
        });
        b.setNegativeButton("取消", null);

        final AlertDialog dlg = b.create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                et.requestFocus();
                if (dlg.getWindow() != null) {
                    dlg.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });
        dlg.show();
    }

    public void onKeyPress(View v) {
        if (done) {
            return;
        }
        String tag = String.valueOf(v.getTag());
        vibrate(12);

        if ("del".equals(tag)) {
            if (input.length() > 0) {
                input.deleteCharAt(input.length() - 1);
            }
            render();
            return;
        }

        if ("ok".equals(tag)) {
            submit();
            return;
        }

        if (input.length() >= Prefs.MAX_LEN) {
            tvHint.setTextColor(0xFFFFC46B);
            tvHint.setText("已经到上限 " + Prefs.MAX_LEN + " 位了");
            return;
        }
        input.append(tag);
        render();
    }

    private void render() {
        if (input.length() == 0) {
            etPwd.setText("");
            return;
        }
        StringBuilder dots = new StringBuilder();
        int shown = Math.min(input.length(), 20);
        for (int i = 0; i < shown; i++) {
            dots.append('●');
        }
        if (input.length() > 20) {
            dots.append("  ").append(input.length()).append(" 位");
        }
        etPwd.setText(dots.toString());
    }

    private void submit() {
        if (done) {
            return;
        }
        if (stored.length() == 0) {
            tvHint.setTextColor(0xFFFFC46B);
            tvHint.setText("还没设密码，先去应用锁里设置");
            return;
        }
        if (input.toString().equals(stored)) {
            unlock();
        } else {
            vibrate(220);
            tvHint.setTextColor(0xFFFF6B6B);
            tvHint.setText("密码错误，重新输一遍");
            input.setLength(0);
            render();
        }
    }

    private void unlock() {
        if (done) {
            return;
        }
        done = true;
        vibrate(60);
        if (targetPkg.length() > 0) {
            Prefs.markUnlocked(this, targetPkg);
        }
        finish();
        overridePendingTransition(0, 0);
    }

    /** 取消：关掉界面并退回桌面，那个应用依然锁着。 */
    private void cancelAndHome() {
        if (done) {
            return;
        }
        done = true;
        Prefs.clearUnlocked(this);

        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (Exception ignored) {
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void vibrate(int ms) {
        Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vb == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vb.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vb.vibrate(ms);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        showing = true;
    }

    @Override
    protected void onPause() {
        showing = false;
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // 返回键无效，必须输密码或者点取消
    }

    @Override
    protected void onDestroy() {
        showing = false;
        super.onDestroy();
    }
}
