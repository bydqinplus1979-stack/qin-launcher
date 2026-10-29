package com.qin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ProviderTestActivity extends Activity {
    StringBuilder out = new StringBuilder();
    TextView tv;

    String[] URIS = {
        "content://com.byd.carStatusProvider/car_status",
        "content://com.byd.carStatusProvider",
        "content://com.byd.providers.carstatus.CarStatusProvider/car_status",
        "content://com.byd.carstatus/car_status"
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1220"));
        root.setPadding(24, 24, 24, 24);

        Button back = new Button(this);
        back.setText("رجوع");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        Button share = new Button(this);
        share.setText("مشاركة النتيجة");
        share.setOnClickListener(v -> {
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(Intent.EXTRA_TEXT, out.toString());
            startActivity(Intent.createChooser(s, "مشاركة"));
        });
        root.addView(share);

        tv = new TextView(this);
        tv.setTextColor(Color.parseColor("#2DD4BF"));
        tv.setTextSize(13);
        tv.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        runTests();
    }

    void log(String s) {
        out.append(s).append("\n");
        tv.setText(out.toString());
    }

    void runTests() {
        for (String u : URIS) {
            log("=== " + u);
            try {
                Cursor c = getContentResolver().query(Uri.parse(u), null, null, null, null);
                if (c == null) {
                    log("النتيجة: null (لا يوجد مزود أو رفض صامت)");
                    continue;
                }
                log("عدد الأعمدة: " + c.getColumnCount() + " | عدد الصفوف: " + c.getCount());
                if (c.getColumnCount() > 0) {
                    log("الأعمدة: " + String.join(", ", c.getColumnNames()));
                }
                int row = 0;
                while (c.moveToNext() && row < 20) {
                    StringBuilder line = new StringBuilder("  صف " + row + ": ");
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        try {
                            line.append(c.getColumnName(i)).append("=").append(c.getString(i)).append(" | ");
                        } catch (Throwable t) {
                            line.append(c.getColumnName(i)).append("=<?> | ");
                        }
                    }
                    log(line.toString());
                    row++;
                }
                c.close();
            } catch (SecurityException se) {
                log("فشل SecurityException: " + se.getMessage());
            } catch (Throwable t) {
                log("فشل " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            log("");
        }
        log("انتهى الاختبار.");
    }
}
