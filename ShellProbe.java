package com.qin.launcher;

import android.content.Context;
import android.os.Looper;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

/** يعمل بصلاحية shell عبر app_process. قراءة فقط: يستدعي دوال get بلا معاملات. */
public class ShellProbe {
    static final Pattern P = Pattern.compile("Landroid/hardware/bydauto/[A-Za-z0-9_/]*Device;");
    static TreeSet<String> names = new TreeSet<>();
    static Context ctx;

    static String rm(Throwable t) {
        while (t instanceof InvocationTargetException && t.getCause() != null) t = t.getCause();
        String m = String.valueOf(t.getMessage());
        return t.getClass().getSimpleName() + ": " + (m.length() > 200 ? m.substring(0, 200) : m);
    }

    static void scan(InputStream in) throws Exception {
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

    static boolean okType(Class<?> t) {
        return t == int.class || t == long.class || t == double.class || t == float.class
                || t == boolean.class || t == String.class || t == int[].class || t == double[].class;
    }

    static String fmt(Object v) {
        if (v instanceof int[]) return Arrays.toString((int[]) v);
        if (v instanceof double[]) return Arrays.toString((double[]) v);
        return String.valueOf(v);
    }

    static String probe(String name) throws Exception {
        String sn = name.substring(name.lastIndexOf('.') + 1).replace("BYDAuto", "").replace("Device", "");
        Class<?> c = Class.forName(name);
        Object inst;
        try {
            inst = c.getMethod("getInstance", Context.class).invoke(null, ctx);
        } catch (Throwable t) {
            return "[" + sn + "] getInstance فشل: " + rm(t);
        }
        if (inst == null) return "[" + sn + "] getInstance=null";
        Method[] ms = c.getMethods();
        Arrays.sort(ms, Comparator.comparing(Method::getName));
        int ok = 0, fail = 0;
        String first = "";
        StringBuilder vals = new StringBuilder();
        for (Method m : ms) {
            String mn = m.getName();
            if (!mn.startsWith("get") || m.getParameterTypes().length != 0) continue;
            if (Modifier.isStatic(m.getModifiers()) || !okType(m.getReturnType())) continue;
            if (mn.equals("getAllStatus") || mn.equals("getInstance") || mn.equals("getGetPermission")
                    || mn.equals("getSetPermission") || mn.equals("getDevicetype") || mn.equals("getType")
                    || mn.equals("getFeatureList")) continue;
            try {
                Object v = m.invoke(inst);
                ok++;
                if (ok <= 6) vals.append(mn.substring(3)).append("=").append(fmt(v)).append(" ");
            } catch (Throwable t) {
                fail++;
                if (first.isEmpty()) first = mn + " -> " + rm(t);
            }
        }
        return "[" + sn + "] نجح " + ok + " فشل " + fail + (first.isEmpty() ? "" : " | أول فشل: " + first)
                + (vals.length() > 0 ? "\n   " + vals : "");
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object t = at.getMethod("systemMain").invoke(null);
            ctx = (Context) at.getMethod("getSystemContext").invoke(t);
        } catch (Throwable t) {
            System.out.println("ctx فشل: " + rm(t));
        }
        System.out.println("uid=" + android.os.Process.myUid() + " ctx=" + (ctx != null));
        File[] fs = new File("/system/framework").listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isFile() && f.getName().contains("boot-framework") && f.getName().endsWith(".vdex")) {
                    try (FileInputStream in = new FileInputStream(f)) {
                        scan(in);
                    } catch (Throwable ignored) { }
                }
            }
        }
        System.out.println("أصناف: " + names.size());
        for (String n : names) {
            if (n.contains("$")) continue;
            ExecutorService ex = Executors.newSingleThreadExecutor();
            final String nn = n;
            Future<String> fu = ex.submit((Callable<String>) () -> probe(nn));
            try {
                System.out.println(fu.get(10, TimeUnit.SECONDS));
            } catch (Throwable t) {
                fu.cancel(true);
                System.out.println("[" + n.substring(n.lastIndexOf('.') + 1) + "] مهلة/خطأ: " + rm(t));
            }
            ex.shutdownNow();
        }
        System.out.println("انتهى");
        System.exit(0);
    }
}
