package com.qin.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    TextView clockView, speedView;
    Handler handler = new Handler(Looper.getMainLooper());
    Runnable tick = new Runnable() {
        public void run() {
            clockView.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1220"));
        root.setPadding(32, 24, 32, 24);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView buildTag = new TextView(this);
        buildTag.setText("Aldarawsheh Launcher - build v9");
        buildTag.setTextSize(12);
        buildTag.setTextColor(Color.parseColor("#888888"));
        root.addView(buildTag);

        clockView = new TextView(this);
        clockView.setTextSize(40);
        clockView.setTextColor(Color.WHITE);
        speedView = new TextView(this);
        speedView.setTextSize(40);
        speedView.setTextColor(Color.parseColor("#2DD4BF"));
        speedView.setText("-- km/h");
        speedView.setGravity(Gravity.CENTER);
        Button scan = new Button(this);
        scan.setText("فحص واجهات السيارة (BYD)");
        scan.setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)));
                  Button probe = new Button(this);
                  probe.setText("قراءة بيانات السيارة");
                  probe.setOnClickListener(v -> startActivity(new Intent(this, ProbeActivity.class)));
                  Button provider = new Button(this);
                  provider.setText("اختبار مزود البيانات");
                  provider.setOnClickListener(v -> startActivity(new Intent(this, ProviderTestActivity.class)));
        top.addView(clockView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(speedView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(probe, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                  top.addView(provider, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                  top.addView(scan, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(top);

        final PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(q, 0));
        Collections.sort(apps, (a, c) -> a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString()));

        GridView grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(24);
        grid.setAdapter(new ArrayAdapter<ResolveInfo>(this, 0, apps) {
            @Override
            public View getView(int pos, View v, ViewGroup parent) {
                ResolveInfo ri = apps.get(pos);
                LinearLayout cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                ImageView icon = new ImageView(MainActivity.this);
                icon.setImageDrawable(ri.loadIcon(pm));
                cell.addView(icon, new LinearLayout.LayoutParams(110, 110));
                TextView t = new TextView(MainActivity.this);
                t.setText(ri.loadLabel(pm));
                t.setTextColor(Color.WHITE);
                t.setTextSize(14);
                t.setGravity(Gravity.CENTER);
                t.setMaxLines(1);
                cell.addView(t);
                return cell;
            }
        });
        grid.setOnItemClickListener((p, v, pos, id) -> {
            ResolveInfo ri = apps.get(pos);
            Intent li = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
            if (li != null) startActivity(li);
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        startGps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        try {
            ((LocationManager) getSystemService(Context.LOCATION_SERVICE)).removeUpdates(this);
        } catch (Throwable ignored) { }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        startGps();
    }

    void startGps() {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                ((LocationManager) getSystemService(Context.LOCATION_SERVICE))
                        .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            }
        } catch (Throwable ignored) { }
    }

    @Override
    public void onLocationChanged(Location l) {
        speedView.setText(Math.round(l.getSpeed() * 3.6f) + " km/h");
    }

    @Override
    public void onStatusChanged(String p, int s, Bundle e) { }

    @Override
    public void onProviderEnabled(String p) { }

    @Override
    public void onProviderDisabled(String p) { }
}
