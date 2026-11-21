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
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecordActivity extends Activity {
    public static final String EXTRA_TOGGLE = "toggle";
    public static final String EXTRA_MODE = "mode";

    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView timer, title, hint, tap;
    private boolean sawRecording = false;
    private boolean wantMeeting = false;
    private boolean cancelArmed = false;
    private long openedAt;
    private Typeface fR, fM, fS;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (RecordService.recording) {
                if (!sawRecording) { sawRecording = true; applyMode(RecordService.meeting); }
                long s = (System.currentTimeMillis() - RecordService.startedAt) / 1000;
                long left = RecordService.maxMs() / 1000 - s;
                timer.setText(s >= 3600
                        ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                        : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60));
                timer.setTextColor(left <= 15 ? 0xFFFF5A4E : Color.WHITE);
            } else if (sawRecording || System.currentTimeMillis() - openedAt > 4000) {
                finish();
                return;
            }
            h.postDelayed(this, 250);
        }
    };

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fR = getResources().getFont(R.font.pretendard_regular);
        fM = getResources().getFont(R.font.pretendard_medium);
        fS = getResources().getFont(R.font.pretendard_semibold);
        openedAt = System.currentTimeMillis();
        wantMeeting = "meeting".equals(getIntent().getStringExtra(EXTRA_MODE));

        if (RecordService.recording && isToggle(getIntent())) {
            send(RecordService.ACT_STOP);
            finish();
            return;
        }
        buildUi();
        if (RecordService.recording) {
            applyMode(RecordService.meeting);
            h.post(tick);
            return;
        }
        applyMode(wantMeeting);
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Prefs.sp(this).getBoolean("askedNotif", false)) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
            Prefs.sp(this).edit().putBoolean("askedNotif", true).apply();
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
        if (RecordService.recording && isToggle(i)) {
            send(RecordService.ACT_STOP);
            finish();
        }
    }

    /** 위젯·빠른 설정·측면 버튼 아이콘은 한 번 더 누르면 저장 */
    private static boolean isToggle(Intent i) {
        if (i.getBooleanExtra(EXTRA_TOGGLE, false)) return true;
        return i.getComponent() != null && i.getComponent().getClassName().endsWith("QuickRecord");
    }

    private void begin() {
        openedAt = System.currentTimeMillis();
        Intent i = new Intent(this, RecordService.class)
                .setAction(RecordService.ACT_START)
                .putExtra(RecordService.EXTRA_MEETING, wantMeeting);
        startForegroundService(i);
        h.post(tick);
    }

    private void send(String action) {
        startService(new Intent(this, RecordService.class).setAction(action));
    }

    private TextView text(String s, float sp, int color, Typeface f) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(f);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void applyMode(boolean meeting) {
        title.setText(meeting ? "● 회의 녹음 중" : "● 듣고 있어요");
        hint.setText(meeting ? "휴대폰을 테이블 가운데에 두세요\n화면이 꺼져도 계속 녹음돼요 · 최대 3시간"
                : "살 것, 할 일, 보낼 돈을 편하게 말해 주세요\n'건전지 샀어'처럼 끝낸 것도 말하면 지워져요");
        tap.setText(meeting ? "가운데를 누르면 끝내고 요약해요" : "가운데를 누르면 저장돼요 · 최대 3분");
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
        root.setPadding(dp(24), dp(52), dp(24), dp(28));

        title = text("", 16, 0xFFFF5A4E, fS);
        root.addView(title);

        timer = text("0:00", 64, Color.WHITE, fR);
        timer.setPadding(0, dp(12), 0, dp(12));
        root.addView(timer);

        hint = text("", 15, 0x99FFFFFF, fR);
        hint.setLineSpacing(dp(5), 1f);
        root.addView(hint);

        PulseView pulse = new PulseView(this);
        pulse.setOnClickListener(v -> { send(RecordService.ACT_STOP); finish(); });
        pulse.setContentDescription("저장");
        root.addView(pulse, new LinearLayout.LayoutParams(-1, 0, 1));

        tap = text("", 14, 0x99FFFFFF, fR);
        root.addView(tap);

        TextView cancel = text("취소", 16, Color.WHITE, fM);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x1AFFFFFF);
        g.setCornerRadius(dp(26));
        cancel.setBackground(g);
        cancel.setOnClickListener(v -> {
            if (RecordService.meeting && !cancelArmed) {
                cancelArmed = true;
                cancel.setText("한 번 더 누르면 취소");
                h.postDelayed(() -> { cancelArmed = false; cancel.setText("취소"); }, 3000);
                return;
            }
            send(RecordService.ACT_CANCEL);
            finish();
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(190), dp(52));
        cl.topMargin = dp(20);
        root.addView(cancel, cl);

        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(tick);
        super.onDestroy();
    }
}
