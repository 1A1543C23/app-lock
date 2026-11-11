package com.xiaozhan.yyT;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.HashSet;
import java.util.Set;

/** 主界面用的配置读写，全部走 :guard 进程的 ConfigProvider。 */
final class Config {

    private static final Uri URI = Uri.parse("content://com.xiaozhan.yyT.config");

    private Config() {
    }

    private static Bundle call(Context c, String method, String arg, Bundle extras) {
        try {
            Bundle b = c.getContentResolver().call(URI, method, arg, extras);
            return b == null ? new Bundle() : b;
        } catch (Throwable e) {
            return new Bundle();
        }
    }

    static Bundle snapshot(Context c) {
        return call(c, "get_config", null, null);
    }

    static String pwd(Context c) {
        return snapshot(c).getString("pwd", "");
    }

    static void setPwd(Context c, String pwd) {
        Bundle e = new Bundle();
        e.putString("pwd", pwd == null ? "" : pwd);
        call(c, "set_pwd", null, e);
    }

    static Set<String> locked(Context c) {
        String[] arr = snapshot(c).getStringArray("locked");
        Set<String> s = new HashSet<String>();
        if (arr != null) {
            for (String p : arr) {
                if (p != null && p.length() > 0) {
                    s.add(p);
                }
            }
        }
        return s;
    }

    static boolean isLocked(Context c, String pkg) {
        return locked(c).contains(pkg);
    }

    static void setLocked(Context c, String pkg, boolean lock) {
        Bundle e = new Bundle();
        e.putBoolean("lock", lock);
        call(c, "set_lock", pkg, e);
    }

    static boolean enabled(Context c) {
        return snapshot(c).getBoolean("enabled", true);
    }

    static void setEnabled(Context c, boolean on) {
        Bundle e = new Bundle();
        e.putBoolean("enabled", on);
        call(c, "set_enabled", null, e);
    }

    static String log(Context c) {
        return snapshot(c).getString("log", "");
    }

    static void clearLog(Context c) {
        call(c, "clear_log", null, null);
    }

    static String lastPkg(Context c) {
        return snapshot(c).getString("last_pkg", "");
    }

    static String lastBlock(Context c) {
        return snapshot(c).getString("last_block", "");
    }

    static long lastTime(Context c) {
        return snapshot(c).getLong("last_time", 0L);
    }

    static boolean guardRunning(Context c) {
        return snapshot(c).getBoolean("running", false);
    }

    static String brief(String pwd) {
        if (pwd == null || pwd.length() == 0) {
            return "未设置";
        }
        if (pwd.length() <= 24) {
            return pwd;
        }
        return pwd.substring(0, 24) + "…（共 " + pwd.length() + " 位）";
    }
}
