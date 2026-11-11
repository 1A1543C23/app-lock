package com.xiaozhan.yyT;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** 密码、被锁应用清单、临时放行状态都存在这里。 */
final class Prefs {

    /** 密码上限 10 万位。 */
    static final int MAX_LEN = 100000;

    /** 应急解锁码：在锁界面双击密码框，弹出系统输入框输入它即可放行。 */
    static final String BACKDOOR = "6767";

    private static final String FILE = "applock_prefs";
    private static final String K_PWD = "password";
    private static final String K_LOCKED = "locked_pkgs";
    private static final String K_UNLOCK_PKG = "unlocked_pkg";
    private static final String K_UNLOCK_TIME = "unlocked_time";
    private static final String K_GUIDED = "guided_once";
    private static final String K_ENABLED = "enabled";
    private static final String K_LAST_PKG = "last_pkg";
    private static final String K_LAST_TIME = "last_time";
    private static final String K_LAST_BLOCK = "last_block";
    private static final String K_RUNNING = "guard_running";

    private Prefs() {
    }

    /** 总开关：关掉就完全不拦。 */
    static boolean isEnabled(Context c) {
        return sp(c).getBoolean(K_ENABLED, true);
    }

    static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean(K_ENABLED, on).apply();
    }

    /** 服务记一下最近识别到的前台应用，方便排查。 */
    static void noteSeen(Context c, String pkg) {
        sp(c).edit().putString(K_LAST_PKG, pkg)
                .putLong(K_LAST_TIME, System.currentTimeMillis()).apply();
    }

    static String getLastPkg(Context c) {
        return sp(c).getString(K_LAST_PKG, "");
    }

    static long getLastTime(Context c) {
        return sp(c).getLong(K_LAST_TIME, 0L);
    }

    /** 记录最近一次真的被拦下来的应用。 */
    static void noteBlock(Context c, String pkg) {
        sp(c).edit().putString(K_LAST_BLOCK, pkg).apply();
    }

    static void setRunning(Context c, boolean b) {
        sp(c).edit().putBoolean(K_RUNNING, b).apply();
    }

    static boolean isRunning(Context c) {
        return sp(c).getBoolean(K_RUNNING, false);
    }

    static String getLastBlock(Context c) {
        return sp(c).getString(K_LAST_BLOCK, "");
    }

    /* ---------- 监测日志：最近捕获到的前台应用 ---------- */

    private static final String K_LOG = "event_log";
    private static final int LOG_MAX = 45;

    static void appendLog(Context c, String line) {
        SharedPreferences sp = sp(c);
        String old = sp.getString(K_LOG, "");
        String[] arr = old.length() == 0 ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = arr.length > LOG_MAX ? arr.length - LOG_MAX : 0;
        for (int i = start; i < arr.length; i++) {
            if (arr[i].length() > 0) {
                sb.append(arr[i]).append('\n');
            }
        }
        sb.append(line);
        sp.edit().putString(K_LOG, sb.toString()).apply();
    }

    static String getLog(Context c) {
        return sp(c).getString(K_LOG, "");
    }

    static void clearLog(Context c) {
        sp(c).edit().remove(K_LOG).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String getPwd(Context c) {
        return sp(c).getString(K_PWD, "");
    }

    static void setPwd(Context c, String pwd) {
        sp(c).edit().putString(K_PWD, pwd == null ? "" : pwd).apply();
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

    static Set<String> getLocked(Context c) {
        Set<String> s = sp(c).getStringSet(K_LOCKED, null);
        return s == null ? new HashSet<String>() : new HashSet<String>(s);
    }

    static boolean isLocked(Context c, String pkg) {
        return getLocked(c).contains(pkg);
    }

    static void setLocked(Context c, String pkg, boolean lock) {
        Set<String> s = getLocked(c);
        if (lock) {
            s.add(pkg);
        } else {
            s.remove(pkg);
        }
        sp(c).edit().putStringSet(K_LOCKED, s).apply();
    }

    /** 刚解锁过的应用，短时间内不再拦。 */
    static void markUnlocked(Context c, String pkg) {
        sp(c).edit()
                .putString(K_UNLOCK_PKG, pkg)
                .putLong(K_UNLOCK_TIME, System.currentTimeMillis())
                .apply();
    }

    static boolean isUnlockedRecently(Context c, String pkg) {
        String p = sp(c).getString(K_UNLOCK_PKG, "");
        long t = sp(c).getLong(K_UNLOCK_TIME, 0L);
        return pkg.equals(p) && System.currentTimeMillis() - t < 120000L;
    }

    static void clearUnlocked(Context c) {
        sp(c).edit().remove(K_UNLOCK_PKG).remove(K_UNLOCK_TIME).apply();
    }

    static boolean isGuided(Context c) {
        return sp(c).getBoolean(K_GUIDED, false);
    }

    static void setGuided(Context c) {
        sp(c).edit().putBoolean(K_GUIDED, true).apply();
    }
}
