package com.qin.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PermissionInfo;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** قراءة فقط: يستدعي دوال get بلا معاملات في أصناف bydauto ويعرض القيم. */
public class ProbeActivity extends Activity {
    TextView out;
    static final Pattern P = Pattern.compile("Landroid/hardware/bydauto/[A-Za-z0-9_/]*Device;");
    TreeSet<String> names = new TreeSet<>();
    LinearLayout buttons;
    static final String[] BASES = {"AC","ADAS","AUDIO","AUX","BIGDATA","BODYWORK","CHARGING","COLLISION","DOORLOCK",
        "REAR_VIEW_MIRROR","DTC","ENERGY","ENGINE","FUNCNOTICE","GB","GEARBOX","INSTRUMENT","LIGHT","LOCATION","MOTOR",
        "MQTT","OTA","PANORAMA","PHONE","RADAR","SAFETYBELT","SECURITY","SENSOR","SETTING","SPEED","STATISTIC","TYRE",
        "PM2P5","WIPER","SEAT","MULTIMEDIA"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(buttons);
        ScrollView sv = new ScrollView(this);
        out = new TextView(this);
        out.setTextIsSelectable(true);
        out.setTextSize(14);
        out.setPadding(24, 24, 24, 24);
        out.setText("جارٍ قراءة بيانات السيارة... انتظر حتى دقيقتين");
        sv.addView(out);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        ArrayList<String> req = new ArrayList<>();
        for (String x : BASES) {
            String p = "android.permission.BYDAUTO_" + x + "_GET";
            if (checkSelfPermission(p) != 0) req.add(p);
        }
        if (req.isEmpty()) startProbe();
        else requestPermissions(req.toArray(new String[0]), 7);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        startProbe();
    }

    boolean started = false;

    void startProbe() {
        if (started) return;
        started = true;
        new Thread(() -> {
            String r;
            try {
                r = probe();
            } catch (Throwable t) {
                r = "خطأ: " + t;
            }
            final String f = r;
            runOnUiThread(() -> {
                out.setText(f);
                int pos = 0, part = 1;
                while (pos < f.length()) {
                    int end = Math.min(f.length(), pos + 25000);
                    if (end < f.length()) {
                        int nl = f.lastIndexOf('\n', end);
                        if (nl > pos) end = nl;
                    }
                    final String chunk = f.substring(pos, end);
                    final int num = part;
                    Button bt = new Button(this);
                    bt.setText("إرسال الجزء " + num);
                    bt.setOnClickListener(v -> {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_TEXT, "قراءة - الجزء " + num + "\n" + chunk);
                        startActivity(Intent.createChooser(i, "إرسال"));
                    });
                    buttons.addView(bt);
                    pos = end;
                    part++;
                }
            });
            try {
                FileWriter w = new FileWriter(new File(getExternalFilesDir(null), "probe.txt"));
                w.write(f);
                w.close();
            } catch (Throwable ignored) { }
        }).start();
    }

    void scanStream(InputStream in) throws Exception {
        byte[] buf = new byte[1 << 20];
        String carry = "";
        int n;
        while ((n = in.read(buf)) > 0) {
            String s = carry + new String(buf, 0, n, "ISO-8859-1");
            Matcher m = P.matcher(s);
            while (m.find()) {
                String d = m.group();
                names.add(d.substring(1, d.length() - 1).replace('/', '.'));
            }
            carry = s.length() > 300 ? s.substring(s.length() - 300) : s;
        }
    }

    void findClasses() {
        File[] fs = new File("/system/framework").listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isFile() && f.getName().contains("boot-framework") && f.getName().endsWith(".vdex")) {
                try (FileInputStream in = new FileInputStream(f)) {
                    scanStream(in);
                } catch (Throwable ignored) { }
            }
        }
    }

    static String root(Throwable t) {
        while (t instanceof InvocationTargetException && t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName();
    }

    static String fmt(Object v) {
        if (v == null) return "null";
        if (v instanceof int[]) return Arrays.toString(Arrays.copyOf((int[]) v, Math.min(12, ((int[]) v).length)));
        if (v instanceof byte[]) return Arrays.toString(Arrays.copyOf((byte[]) v, Math.min(12, ((byte[]) v).length)));
        if (v instanceof double[]) return Arrays.toString(Arrays.copyOf((double[]) v, Math.min(12, ((double[]) v).length)));
        return String.valueOf(v);
    }

    static boolean okType(Class<?> t) {
        return t == int.class || t == long.class || t == double.class || t == float.class
                || t == boolean.class || t == String.class || t == int[].class
                || t == byte[].class || t == double[].class;
    }

    String probeDevice(String name) throws Exception {
        String shortName = name.substring(name.lastIndexOf('.') + 1);
        StringBuilder sb = new StringBuilder("\n[" + shortName + "]\n");
        Class<?> c = Class.forName(name, false, getClassLoader());
        Object inst;
        try {
            inst = c.getMethod("getInstance", Context.class).invoke(null, this);
        } catch (Throwable t) {
            return sb.append("  getInstance فشل: ").append(root(t)).append("\n").toString();
        }
        if (inst == null) return sb.append("  getInstance = null\n").toString();
        for (String pm : new String[]{"getGetPermission", "getSetPermission"}) {
            try {
                String p = String.valueOf(c.getMethod(pm).invoke(inst));
                boolean g = p.startsWith("android.") && checkSelfPermission(p) == 0;
                sb.append("  ").append(pm.equals("getGetPermission") ? "GET" : "SET").append(": ")
                  .append(p.replace("android.permission.", "")).append(g ? " (ممنوحة)" : " (غير ممنوحة)").append("\n");
            } catch (Throwable ignored) { }
        }
        Method[] ms = c.getMethods();
        Arrays.sort(ms, Comparator.comparing(Method::getName));
        int ok = 0, fail = 0;
        String firstFail = "";
        for (Method m : ms) {
            String mn = m.getName();
            if (!mn.startsWith("get") || m.getParameterTypes().length != 0) continue;
            if (Modifier.isStatic(m.getModifiers()) || !okType(m.getReturnType())) continue;
            if (mn.equals("getAllStatus") || mn.equals("getInstance") || mn.equals("getGetPermission")
                    || mn.equals("getSetPermission") || mn.equals("getDevicetype") || mn.equals("getType")) continue;
            try {
                Object v = m.invoke(inst);
                ok++;
                sb.append("  ").append(mn.substring(3)).append(" = ").append(fmt(v)).append("\n");
            } catch (Throwable t) {
                fail++;
                if (firstFail.isEmpty()) firstFail = root(t);
            }
        }
        sb.append("  (نجح ").append(ok).append(" / فشل ").append(fail);
        if (!firstFail.isEmpty()) sb.append(" - ").append(firstFail);
        sb.append(")\n");
        return sb.toString();
    }

    String sh(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder o = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l;
            while ((l = r.readLine()) != null && o.length() < 1500) o.append(l).append("\n");
            p.waitFor(15, TimeUnit.SECONDS);
            return o.toString();
        } catch (Throwable t) {
            return "ERR " + t;
        }
    }

    String permReport() {
        StringBuilder sb = new StringBuilder("== الصلاحيات (قراءة GET فقط) ==\n");
        int granted = 0;
        ArrayList<String> missing = new ArrayList<>();
        for (String x : BASES) {
            String p = "android.permission.BYDAUTO_" + x + "_GET";
            String lvl;
            try {
                PermissionInfo pi = getPackageManager().getPermissionInfo(p, 0);
                int base = pi.protectionLevel & 0xf;
                lvl = (base == 0 ? "normal" : base == 1 ? "dangerous" : base == 2 ? "signature" : "sigOrSystem")
                        + ((pi.protectionLevel & 0x10) != 0 ? "+privileged" : "")
                        + ((pi.protectionLevel & 0x20) != 0 ? "+development" : "");
            } catch (Throwable t) {
                lvl = "غير معرّفة";
            }
            boolean g = checkSelfPermission(p) == 0;
            if (g) granted++; else missing.add(p);
            sb.append(x).append(": ").append(lvl).append(g ? " ✔" : " ✘").append("\n");
        }
        sb.append("ممنوحة: ").append(granted).append(" من ").append(BASES.length).append("\n");
        if (!missing.isEmpty()) {
            StringBuilder cmd = new StringBuilder("id; ");
            for (String p : missing) cmd.append("pm grant com.qin.launcher ").append(p).append(" 2>&1 | head -n 1; ");
            sb.append("\n== محاولة منح الصلاحيات عبر root ==\n").append(sh(cmd.toString()));
            int g2 = 0;
            for (String p : missing) if (checkSelfPermission(p) == 0) g2++;
            sb.append("بعد المحاولة، ممنوحة إضافية: ").append(g2).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    String probe() throws Exception {
        findClasses();
        StringBuilder sb = new StringBuilder();
        sb.append(permReport());
        sb.append("أصناف Device المكتشفة: ").append(names.size()).append("\n");
        for (String n : names) {
            if (n.contains("$")) continue;
            ExecutorService ex = Executors.newSingleThreadExecutor();
            Future<String> fu = ex.submit((Callable<String>) () -> probeDevice(n));
            try {
                sb.append(fu.get(12, TimeUnit.SECONDS));
            } catch (Throwable t) {
                fu.cancel(true);
                sb.append("\n[").append(n.substring(n.lastIndexOf('.') + 1)).append("] مهلة/خطأ: ")
                  .append(root(t)).append("\n");
            }
            ex.shutdownNow();
            if (sb.length() > 70000) { sb.append("\n...اقتُطع..."); break; }
        }
        return sb.toString();
    }
}
