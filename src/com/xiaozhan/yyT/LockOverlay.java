package com.xiaozhan.yyT;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 用系统悬浮窗直接盖在屏幕上。
 * 它不是 Activity，所以按 Home 键不会消失，只能输密码或者点「取消」。
 */
public final class LockOverlay {

    private static LockOverlay sInst;
    private static boolean rebuilding;

    public static boolean isShowing() {
        return sInst != null && !sInst.closed;
    }

    /** 目标应用已经在锁着，就不用再弹一次；换了应用就重建一个。 */
    public static void show(Context c, String pkg) {
        final Context app = c.getApplicationContext();
        final String target = pkg == null ? "" : pkg;
        final Handler h = new Handler(Looper.getMainLooper());

        if (sInst != null) {
            LockOverlay cur = sInst;
            if (target.equals(cur.pkg) && !cur.closed) {
                return;
            }
            cur.dismiss0();
            sInst = null;
            // removeView 是排队的，等它真正落地再 addView，否则新窗口会加不上去
            if (!rebuilding) {
                rebuilding = true;
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        rebuilding = false;
                        create(app, target);
                    }
                }, 120);
            }
            return;
        }
        create(app, target);
    }

    private static void create(Context app, String target) {
        if (sInst != null && !sInst.closed) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
            Prefs.appendLog(app, "没有悬浮窗权限 -> 退回Activity");
            LockActivity.launch(app, target);
            return;
        }
        try {
            LockOverlay o = new LockOverlay(app, target);
            o.attach();
            sInst = o;
            Prefs.appendLog(app, "锁界面已显示 " + target);
        } catch (Throwable e) {
            sInst = null;
            Prefs.appendLog(app, "悬浮窗失败 " + e.getClass().getSimpleName() + " -> 退回Activity");
            LockActivity.launch(app, target);
        }
    }

    private final Context ctx;
    private final String pkg;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private View root;
    private FrameLayout container;
    private TextView tvHint;
    private GridLayout keypad;
    private EditText etPwd;

    private final StringBuilder input = new StringBuilder();
    private final String stored;
    private boolean closed;
    private View imeLayer;

    private LockOverlay(Context c, String pkg) {
        this.ctx = c;
        this.pkg = pkg == null ? "" : pkg;
        this.wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        this.stored = Prefs.getPwd(c);
    }

    private int dp(float v) {
        return (int) (ctx.getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private void attach() {
        root = LayoutInflater.from(ctx).inflate(R.layout.activity_lock, null);
        container = (FrameLayout) root;

        etPwd = (EditText) root.findViewById(R.id.et_pwd);
        keypad = (GridLayout) root.findViewById(R.id.keypad);
        tvHint = (TextView) root.findViewById(R.id.tv_hint);
        TextView tvApp = (TextView) root.findViewById(R.id.tv_app);
        TextView tvCancel = (TextView) root.findViewById(R.id.tv_cancel);

        tvApp.setText(appLabel());
        if (pkg.length() == 0) {
            tvApp.setVisibility(View.GONE);
        }
        tvCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancel();
            }
        });

        for (int i = 0; i < keypad.getChildCount(); i++) {
            View key = keypad.getChildAt(i);
            key.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onKey(String.valueOf(v.getTag()));
                }
            });
        }

        final GestureDetector gd = new GestureDetector(ctx,
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
                        showIme();
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

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;

        wm.addView(root, lp);

        root.setFocusableInTouchMode(true);
        root.requestFocus();
        root.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // 返回键吞掉，不让退出
                return keyCode == KeyEvent.KEYCODE_BACK;
            }
        });
    }

    private String appLabel() {
        if (pkg.length() == 0) {
            return "";
        }
        try {
            android.content.pm.ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return "「" + ctx.getPackageManager().getApplicationLabel(ai) + "」已锁定";
        } catch (Exception e) {
            return "「" + pkg + "」已锁定";
        }
    }

    private void showKeypad() {
        if (closed) {
            return;
        }
        if (keypad.getVisibility() != View.VISIBLE) {
            keypad.setVisibility(View.VISIBLE);
            tvHint.setTextColor(0x99FFFFFF);
            tvHint.setText("输入密码后点 ✓ 确认");
        }
    }

    private void onKey(String tag) {
        if (closed) {
            return;
        }
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
        if (closed) {
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
            tvHint.setTextColor(0xFFFF6B6B);
            tvHint.setText("密码错误，重新输一遍");
            input.setLength(0);
            render();
        }
    }

    /** 双击密码框：弹系统输入框，输 6767 应急解锁。 */
    private void showIme() {
        if (closed || imeLayer != null) {
            return;
        }

        FrameLayout layer = new FrameLayout(ctx);
        layer.setBackgroundColor(0xCC000000);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        int pad = dp(20);
        card.setPadding(pad, pad, pad, pad);

        final EditText et = new EditText(ctx);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setSingleLine(true);
        et.setHint("输入解锁码");

        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.addView(et, etLp);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(10), 0, 0);

        Button cancelBt = new Button(ctx);
        cancelBt.setText("关闭");
        Button okBt = new Button(ctx);
        okBt.setText("确定");

        row.addView(cancelBt);
        row.addView(okBt);
        card.addView(row);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        cardLp.leftMargin = dp(24);
        cardLp.rightMargin = dp(24);
        layer.addView(card, cardLp);

        container.addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imeLayer = layer;

        okBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Prefs.BACKDOOR.equals(et.getText().toString())) {
                    unlock();
                } else {
                    tvHint.setTextColor(0xFFFF6B6B);
                    tvHint.setText("解锁码不对");
                }
            }
        });

        cancelBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideIme(et);
            }
        });

        et.requestFocus();
        et.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager)
                        ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
    }

    private void hideIme(EditText et) {
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
        }
        if (imeLayer != null) {
            container.removeView(imeLayer);
            imeLayer = null;
        }
    }

    private void unlock() {
        if (closed) {
            return;
        }
        closed = true;
        if (pkg.length() > 0) {
            Prefs.markUnlocked(ctx, pkg);
        }
        if (sInst == this) {
            sInst = null;
        }
        dismiss0();
    }

    /** 取消：关掉锁界面、退回桌面，那个应用依然打不开。 */
    private void cancel() {
        if (closed) {
            return;
        }
        closed = true;
        Prefs.clearUnlocked(ctx);
        if (sInst == this) {
            sInst = null;
        }

        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(home);
        } catch (Exception ignored) {
        }

        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismiss0();
            }
        }, 260);
    }

    void dismiss0() {
        closed = true;
        ui.removeCallbacksAndMessages(null);
        if (root != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
            root = null;
        }
        if (sInst == this) {
            sInst = null;
        }
    }
}
