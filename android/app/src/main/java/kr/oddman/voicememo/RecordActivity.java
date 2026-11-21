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
                if (left <= 15) timer.setTextColor(0xFFFF5A4E);
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
        int bg = 0xFF0E0E10;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(24), dp(48), dp(24), dp(28));

        TextView title = new TextView(this);
        title.setText("● 듣고 있어요");
        title.setTextColor(0xFFFF5A4E);
        title.setTextSize(15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        root.addView(title);

        timer = new TextView(this);
        timer.setText("0:00");
        timer.setTextColor(Color.WHITE);
        timer.setTextSize(60);
        timer.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        timer.setPadding(0, dp(6), 0, 0);
        root.addView(timer);

        TextView hint = new TextView(this);
        hint.setText("살 것, 할 일, 보낼 돈을 편하게 말해 주세요");
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);

        PulseView pulse = new PulseView(this);
        pulse.setOnClickListener(v -> { send(RecordService.ACT_STOP); finish(); });
        pulse.setContentDescription("저장");
        root.addView(pulse, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView tap = new TextView(this);
        tap.setText("가운데를 누르면 저장돼요 · 최대 3분");
        tap.setTextColor(0x99FFFFFF);
        tap.setTextSize(13);
        root.addView(tap);

        TextView cancel = new TextView(this);
        cancel.setText("취소");
        cancel.setTextColor(Color.WHITE);
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x1AFFFFFF);
        g.setCornerRadius(dp(24));
        cancel.setBackground(g);
        cancel.setOnClickListener(v -> { send(RecordService.ACT_CANCEL); finish(); });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(120), dp(48));
        cl.topMargin = dp(18);
        root.addView(cancel, cl);

        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(tick);
        super.onDestroy();
    }
}
