package com.qin.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VoiceActivity extends Activity {

    TextView status, log;
    SpeechRecognizer recognizer;
    List<ResolveInfo> apps;
    StringBuilder logText = new StringBuilder();

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
        status.setText("اضغط الزر وتحدث باسم التطبيق (مثال: يوتيوب، خرائط)");
        root.addView(status);

        Button talk = new Button(this);
        talk.setText("🎤 اضغط وتحدث");
        talk.setTextSize(20);
        talk.setOnClickListener(v -> startListening());
        root.addView(talk);

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

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            appendLog("تحذير: لا توجد خدمة تعرّف على الصوت متاحة على هذا الجهاز (SpeechRecognizer.isRecognitionAvailable = false). قد لا تعمل هذه الميزة على شاشة السيارة.");
        } else {
            appendLog("خدمة التعرف على الصوت متوفرة. جاهز.");
        }
    }

    void appendLog(String s) {
        logText.append(s).append("\n");
        log.setText(logText.toString());
    }

    void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("الرجاء منح إذن الميكروفون ثم إعادة المحاولة.");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2);
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("خدمة التعرف على الصوت غير متوفرة على هذا الجهاز.");
            return;
        }

        if (recognizer != null) {
            recognizer.destroy();
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) { status.setText("تحدث الآن..."); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float v) { }
            @Override public void onBufferReceived(byte[] b) { }
            @Override public void onEndOfSpeech() { status.setText("جارٍ المعالجة..."); }

            @Override
            public void onError(int error) {
                appendLog("خطأ في التعرف على الصوت، كود: " + error);
                status.setText("حدث خطأ، اضغط وتحدث من جديد.");
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (heard == null || heard.isEmpty()) {
                    status.setText("لم أفهم، حاول مرة أخرى.");
                    return;
                }
                appendLog("سمعت: " + heard);
                handleCommand(heard);
            }

            @Override public void onPartialResults(Bundle p) { }
            @Override public void onEvent(int e, Bundle p) { }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        try {
            recognizer.startListening(intent);
        } catch (Throwable t) {
            appendLog("فشل بدء الاستماع: " + t);
        }
    }

    void handleCommand(List<String> heard) {
        final PackageManager pm = getPackageManager();
        for (String phrase : heard) {
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
                    return;
                }
            }
        }
        status.setText("لم أجد تطبيقًا مطابقًا لما قلته.");
    }

    String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT)
                .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
                .replace("ة", "ه").replace("ى", "ي");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) recognizer.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            status.setText("تم منح الإذن. اضغط وتحدث.");
        } else {
            status.setText("تم رفض إذن الميكروفون، لن تعمل الميزة بدونه.");
        }
    }
}
