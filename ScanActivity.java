package com.qin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ScanActivity extends Activity {
    TextView out;
    static final Pattern P = Pattern.compile("L[A-Za-z0-9_/$]*bydauto[A-Za-z0-9_/$]*;", Pattern.CASE_INSENSITIVE);
    TreeSet<String> names = new TreeSet<>();
    StringBuilder log = new StringBuilder();
    int filesScanned = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(buttons);
        ScrollView sv = new ScrollView(this);
        out = new TextView(this);
        out.setTextIsSelectable(true);
        out.setTextSize(14);
        out.setPadding(24, 24, 24, 24);
        out.setText("جارٍ الفحص... قد يستغرق دقيقة أو دقيقتين");
        sv.addView(out);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        new Thread(() -> {
            String r;
            try {
                r = scan();
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
                        i.putExtra(Intent.EXTRA_TEXT, "الجزء " + num + "\n" + chunk);
                        startActivity(Intent.createChooser(i, "إرسال"));
                    });
                    buttons.addView(bt);
                    pos = end;
                    part++;
                }
            });
            try {
                FileWriter w = new FileWriter(new File(getExternalFilesDir(null), "scan.txt"));
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

    void scanFile(File f, String label) {
        int before = names.size();
        filesScanned++;
        try {
            String n = f.getName();
            if (n.endsWith(".jar") || n.endsWith(".apk")) {
                try (ZipFile z = new ZipFile(f)) {
                    Enumeration<? extends ZipEntry> en = z.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry e = en.nextElement();
                        if (e.getName().endsWith(".dex")) {
                            InputStream in = z.getInputStream(e);
                            scanStream(in);
                            in.close();
                        }
                    }
                }
            } else {
                try (FileInputStream in = new FileInputStream(f)) {
                    scanStream(in);
                }
            }
        } catch (Throwable t) {
            log.append("تعذر قراءة ").append(f.getName()).append(": ").append(t.getClass().getSimpleName()).append("\n");
        }
        if (names.size() > before) {
            log.append("وُجد في ").append(label).append(" : ").append(f.getName())
               .append(" (+").append(names.size() - before).append(")\n");
        }
    }

    void walk(File dir, int depth) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                if (depth < 3) walk(f, depth + 1);
            } else {
                scanFile(f, "framework");
            }
        }
    }

    String scan() throws Exception {
        walk(new File("/system/framework"), 0);
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            String pn = ai.packageName.toLowerCase();
            if (pn.contains("byd") || pn.contains("kramskyi") || pn.contains("dilink")) {
                scanFile(new File(ai.sourceDir), ai.packageName);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ملفات تم فحصها: ").append(filesScanned).append("\n");
        sb.append("عدد الأصناف المكتشفة: ").append(names.size()).append("\n\n");
        sb.append(log).append("\n");
        for (String n : names) if (!n.contains("$")) sb.append(n).append("\n");
        sb.append("\n===== الأصناف القابلة للتحميل ودوالها =====\n");
        int loadable = 0;
        for (String n : names) {
            if (n.contains("$")) continue;
            try {
                Class<?> c = Class.forName(n, false, getClassLoader());
                loadable++;
                sb.append("\n[").append(n.substring(n.lastIndexOf('.') + 1)).append("]\n");
                if (!n.endsWith("Device")) continue;
                for (Method mt : c.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isPublic(mt.getModifiers())) continue;
                    sb.append("  ").append(mt.getReturnType().getSimpleName()).append(" ")
                      .append(mt.getName()).append("(");
                    Class<?>[] pt = mt.getParameterTypes();
                    for (int i = 0; i < pt.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(pt[i].getSimpleName());
                    }
                    sb.append(")\n");
                }
            } catch (Throwable t) {
                // not loadable
            }
            if (sb.length() > 60000) { sb.append("\n...اقتُطع..."); break; }
        }
        sb.append("\nعدد القابل للتحميل: ").append(loadable).append("\n");
        return sb.toString();
    }
}
