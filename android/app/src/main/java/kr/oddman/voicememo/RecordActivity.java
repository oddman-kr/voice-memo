package kr.oddman.voicememo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecordActivity extends Activity {
    public static final String EXTRA_TOGGLE = "toggle";
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView timer;
    private boolean sawRecording = false;
    private long openedAt;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (RecordService.recording) {
                sawRecording = true;
                long s = (System.currentTimeMillis() - RecordService.startedAt) / 1000;
                long left = RecordService.MAX_MS / 1000 - s;
                timer.setText(String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60));
                if (left <= 15) timer.setTextColor(0xFFE5484D);
            } else if (sawRecording || System.currentTimeMillis() - openedAt > 4000) {
                finish();
                return;
            }
            h.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        openedAt = System.currentTimeMillis();
        setFinishOnTouchOutside(false);
        if (RecordService.recording && getIntent().getBooleanExtra(EXTRA_TOGGLE, false)) {
            send(RecordService.ACT_STOP);
            finish();
            return;
        }
        buildUi();
        if (RecordService.recording) {
            h.post(tick);
            return;
        }
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("askedNotif", false)) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
            getSharedPreferences("cfg", MODE_PRIVATE).edit().putBoolean("askedNotif", true).apply();
        }
        if (need.isEmpty()) begin();
        else requestPermissions(need.toArray(new String[0]), 1);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            begin();
        } else {
            Toast.makeText(this, "마이크 권한을 허용해야 녹음할 수 있어요", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (RecordService.recording && i.getBooleanExtra(EXTRA_TOGGLE, false)) {
            send(RecordService.ACT_STOP);
            finish();
        }
    }

    private void begin() {
        openedAt = System.currentTimeMillis();
        Intent i = new Intent(this, RecordService.class).setAction(RecordService.ACT_START);
        startForegroundService(i);
        h.post(tick);
    }

    private void send(String action) {
        startService(new Intent(this, RecordService.class).setAction(action));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(16));

        TextView title = new TextView(this);
        title.setText("● 녹음 중");
        title.setTextColor(0xFFE5484D);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        timer = new TextView(this);
        timer.setText("0:00");
        timer.setTextColor(0xFF1C1E21);
        timer.setTextSize(44);
        timer.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        timer.setPadding(0, dp(6), 0, dp(2));
        root.addView(timer);

        TextView hint = new TextView(this);
        hint.setText("말을 마치면 저장을 눌러 주세요 (최대 3분)\n위젯을 한 번 더 눌러도 저장돼요");
        hint.setTextColor(0xFF8A8F98);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);

        TextView save = new TextView(this);
        save.setText("저장");
        save.setTextColor(Color.WHITE);
        save.setTextSize(20);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFFE5484D);
        save.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(96), dp(96));
        lp.topMargin = dp(20);
        save.setOnClickListener(v -> { send(RecordService.ACT_STOP); finish(); });
        root.addView(save, lp);

        TextView cancel = new TextView(this);
        cancel.setText("취소");
        cancel.setTextColor(0xFF8A8F98);
        cancel.setTextSize(15);
        cancel.setPadding(dp(20), dp(14), dp(20), dp(6));
        cancel.setOnClickListener(v -> { send(RecordService.ACT_CANCEL); finish(); });
        root.addView(cancel);

        setContentView(root);
        getWindow().setLayout(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(tick);
        super.onDestroy();
    }
}
