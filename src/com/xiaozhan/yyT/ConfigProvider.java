package com.xiaozhan.yyT;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 跑在 :guard 进程里，主界面通过它读写配置。
 * 这样主进程被关掉，配置和拦截都还在守护进程手里。
 */
public class ConfigProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context c = getContext();
        Bundle out = new Bundle();
        if (c == null) {
            return out;
        }

        if ("get_config".equals(method)) {
            out.putString("pwd", Prefs.getPwd(c));
            out.putBoolean("enabled", Prefs.isEnabled(c));
            out.putStringArray("locked", Prefs.getLocked(c).toArray(new String[0]));
            out.putString("log", Prefs.getLog(c));
            out.putString("last_pkg", Prefs.getLastPkg(c));
            out.putString("last_block", Prefs.getLastBlock(c));
            out.putLong("last_time", Prefs.getLastTime(c));
            out.putBoolean("running", Prefs.isRunning(c));
        } else if ("set_pwd".equals(method)) {
            Prefs.setPwd(c, extras == null ? "" : extras.getString("pwd", ""));
        } else if ("set_lock".equals(method)) {
            Prefs.setLocked(c, arg == null ? "" : arg, extras != null && extras.getBoolean("lock"));
        } else if ("set_enabled".equals(method)) {
            Prefs.setEnabled(c, extras != null && extras.getBoolean("enabled", true));
        } else if ("clear_log".equals(method)) {
            Prefs.clearLog(c);
        }
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
