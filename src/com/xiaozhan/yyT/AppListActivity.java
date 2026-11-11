package com.xiaozhan.yyT;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 已安装应用列表，可按名称或包名搜索，点一下切换锁定状态。 */
public class AppListActivity extends Activity {

    private final List<Entry> all = new ArrayList<Entry>();
    private final List<Entry> shown = new ArrayList<Entry>();

    private java.util.Set<String> lockedSet = new java.util.HashSet<String>();
    private EditText etSearch;
    private TextView tvCount;
    private ListView lv;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_applist);

        etSearch = (EditText) findViewById(R.id.et_search);
        tvCount = (TextView) findViewById(R.id.tv_count);
        lv = (ListView) findViewById(R.id.lv_apps);

        loadApps();

        adapter = new Adapter();
        lv.setAdapter(adapter);
        filter("");

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter(s == null ? "" : s.toString().trim().toLowerCase());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void loadApps() {
        all.clear();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris;
        try {
            ris = pm.queryIntentActivities(main, 0);
        } catch (Exception e) {
            ris = new ArrayList<ResolveInfo>();
        }

        Set<String> seen = new HashSet<String>();
        for (ResolveInfo ri : ris) {
            if (ri == null || ri.activityInfo == null || ri.activityInfo.applicationInfo == null) {
                continue;
            }
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            String pkg = ai.packageName;
            if (getPackageName().equals(pkg) || seen.contains(pkg)) {
                continue;
            }
            seen.add(pkg);
            String label;
            try {
                label = pm.getApplicationLabel(ai).toString();
            } catch (Exception e) {
                label = pkg;
            }
            Drawable icon;
            try {
                icon = ai.loadIcon(pm);
            } catch (Exception e) {
                icon = null;
            }
            all.add(new Entry(pkg, label, icon));
        }

        Collections.sort(all, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    private void filter(String q) {
        shown.clear();
        for (Entry e : all) {
            if (q.length() == 0
                    || e.label.toLowerCase().contains(q)
                    || e.pkg.toLowerCase().contains(q)) {
                shown.add(e);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        tvCount.setText(q.length() == 0
                ? ("共 " + shown.size() + " 个应用，点一下加锁")
                : ("搜到 " + shown.size() + " 个"));
    }

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private static class Entry {
        final String pkg;
        final String label;
        final Drawable icon;

        Entry(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private static class Holder {
        ImageView icon;
        TextView name;
        TextView pkg;
        TextView mark;
    }

    private class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(AppListActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), dp(10), dp(18), dp(10));

                ImageView iv = new ImageView(AppListActivity.this);
                iv.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
                row.addView(iv);

                LinearLayout col = new LinearLayout(AppListActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                colLp.leftMargin = dp(12);
                col.setLayoutParams(colLp);

                TextView name = new TextView(AppListActivity.this);
                name.setTextColor(0xFF111827);
                name.setTextSize(15);

                TextView pkg = new TextView(AppListActivity.this);
                pkg.setTextColor(0xFF9CA3AF);
                pkg.setTextSize(11);

                col.addView(name);
                col.addView(pkg);
                row.addView(col);

                TextView mark = new TextView(AppListActivity.this);
                mark.setTextSize(12);
                row.addView(mark);

                h = new Holder();
                h.icon = iv;
                h.name = name;
                h.pkg = pkg;
                h.mark = mark;
                row.setTag(h);
                convertView = row;
            } else {
                h = (Holder) convertView.getTag();
            }

            Entry e = shown.get(position);
            if (e.icon != null) {
                h.icon.setImageDrawable(e.icon);
            } else {
                h.icon.setImageDrawable(null);
            }
            h.name.setText(e.label);
            h.pkg.setText(e.pkg);

            boolean locked = lockedSet.contains(e.pkg);
            h.mark.setVisibility(View.VISIBLE);
            h.mark.setText(locked ? "已锁" : "未锁");
            h.mark.setTextColor(locked ? 0xFF16A34A : 0xFF9CA3AF);

            return convertView;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lockedSet = Config.locked(this);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= shown.size()) {
                    return;
                }
                Entry e = shown.get(position);
                boolean nowLocked = !lockedSet.contains(e.pkg);
                Config.setLocked(AppListActivity.this, e.pkg, nowLocked);
                if (nowLocked) {
                    lockedSet.add(e.pkg);
                } else {
                    lockedSet.remove(e.pkg);
                }
                adapter.notifyDataSetChanged();
                Toast.makeText(AppListActivity.this,
                        nowLocked ? ("已锁定：" + e.label) : ("已解除：" + e.label),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
