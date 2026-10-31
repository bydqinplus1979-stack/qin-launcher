package com.qin.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VoiceActivity extends Activity implements RecognitionListener {

    TextView status, log;
    StringBuilder logText = new StringBuilder();
    Model model;
    SpeechService speechService;
    List<ResolveInfo> apps;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1220"));
        root.setPadding(32, 24, 32, 24);

        Button back = new Button(this);
        back.setText("رجوع");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 16, 0, 16);
        status.setText("جارٍ تجهيز محرك التعرف على الصوت (بدون إنترنت)...");
        root.addView(status);

        log = new TextView(this);
        log.setTextColor(Color.parseColor("#2DD4BF"));
        log.setTextSize(13);
        log.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(log);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        final PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        apps = new ArrayList<>(pm.queryIntentActivities(q, 0));

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2);
        }

        LibVosk.setLogLevel(LogLevel.INFO);
        initModel();
    }

    void appendLog(String s) {
        logText.append(s).append("\n");
        log.setText(logText.toString());
    }

    void initModel() {
        StorageService.unpack(this, "model", "model",
            (model) -> {
                this.model = model;
                appendLog("تم تحميل محرك الصوت بنجاح (بدون إنترنت).");
                setupRecognizer();
            },
            (exception) -> {
                appendLog("فشل تحميل محرك الصوت: " + exception.getMessage());
                status.setText("تعذر تشغيل التحكم الصوتي على هذا الجهاز.");
            });
    }

    void setupRecognizer() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("الرجاء منح إذن الميكروفون ثم إعادة فتح الصفحة.");
            return;
        }
        try {
            Recognizer rec = new Recognizer(model, 16000.0f);
            speechService = new SpeechService(rec, 16000.0f);
            speechService.startListening(this);
            status.setText("جاهز، تحدث باسم التطبيق (بالإنجليزية، مثال: youtube)");
        } catch (IOException e) {
            appendLog("فشل بدء الاستماع: " + e.getMessage());
        }
    }

    @Override
    public void onResult(String hypothesis) {
        handleHypothesis(hypothesis, true);
    }

    @Override
    public void onFinalResult(String hypothesis) {
        handleHypothesis(hypothesis, true);
    }

    @Override
    public void onPartialResult(String hypothesis) {
        handleHypothesis(hypothesis, false);
    }

    void handleHypothesis(String hypothesis, boolean isFinal) {
        String text = "";
        try {
            JSONObject o = new JSONObject(hypothesis);
            if (o.has("text")) text = o.optString("text");
            else if (o.has("partial")) text = o.optString("partial");
        } catch (JSONException e) {
            return;
        }
        if (text == null || text.trim().isEmpty()) return;
        if (isFinal) {
            appendLog("سمعت: " + text);
            tryLaunch(text);
        } else {
            status.setText("يسمع: " + text);
        }
    }

    void tryLaunch(String phrase) {
        final PackageManager pm = getPackageManager();
        String norm = normalize(phrase);
        ResolveInfo best = null;
        for (ResolveInfo ri : apps) {
            String label = normalize(ri.loadLabel(pm).toString());
            if (label.isEmpty()) continue;
            if (norm.contains(label) || label.contains(norm)) {
                best = ri;
                break;
            }
        }
        if (best != null) {
            Intent li = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
            if (li != null) {
                status.setText("فتح: " + best.loadLabel(pm));
                appendLog("تطابق مع تطبيق: " + best.loadLabel(pm));
                startActivity(li);
            }
        }
    }

    String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void onError(Exception e) {
        appendLog("خطأ: " + e.getMessage());
    }

    @Override
    public void onTimeout() {
        appendLog("انتهت مهلة الاستماع.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            setupRecognizer();
        } else {
            status.setText("تم رفض إذن الميكروفون، لن تعمل الميزة بدونه.");
        }
    }
}
