package kr.oddman.voicememo;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

public class MainActivity extends Activity {
    static final String TAB_REC = "__rec__";

    static final int BG = 0xFFF6F5F2, CARD = 0xFFFFFFFF, LINE = 0xFFEBE9E4, INK = 0xFF17171A,
            SUB = 0xFF6E6E73, MUTE = 0xFFA5A5AA, RED = 0xFFE5484D, AMBER = 0xFFD98100;
    static final int[] EXTRA = {0xFF2F80ED, 0xFFD99A00, 0xFF0098B3, 0xFF8B5E3C};

    private String tab = TAB_REC;
    private LinearLayout tabBar, list;
    private TextView dateLine;
    private MediaPlayer player;
    private long playingMemo = -1;
    private boolean showDone = false;
    private final Set<Long> expanded = new HashSet<>();

    // ---------- 작은 도구들 ----------

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private TextView tv(String s, float sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (medium) t.setTypeface(MEDIUM);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static int tint(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }

    int catColor(String cat) {
        switch (cat) {
            case "쿠팡": return 0xFFF2633A;
            case "다이소": return 0xFFE0468A;
            case "할 일": return 0xFF6B5CE7;
            case "송금": return 0xFF119E7A;
            case "기타": return 0xFF8E8E93;
        }
        List<String> cats = allCats();
        int i = Math.max(0, cats.indexOf(cat));
        return EXTRA[i % EXTRA.length];
    }

    private ImageView icon(int res, int color, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(color);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    // ---------- 화면 만들기 ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) tab = b.getString("tab", TAB_REC);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int f = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(f, f);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.setFitsSystemWindows(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        // 머리말
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(22), dp(18), dp(16), dp(8));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        dateLine = tv("", 13, SUB, false);
        titles.addView(dateLine);
        TextView title = tv("음성 메모", 28, INK, true);
        title.setPadding(0, dp(6), 0, 0);
        titles.addView(title);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout gear = new FrameLayout(this);
        gear.setBackground(oval(CARD));
        gear.setElevation(dp(1));
        ImageView gi = new ImageView(this);
        gi.setImageResource(R.drawable.ic_settings);
        gi.setColorFilter(INK);
        gear.addView(gi, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        gear.setOnClickListener(v -> openSettings());
        gear.setContentDescription("설정");
        head.addView(gear, lp(dp(44), dp(44)));
        root.addView(head);

        // 탭
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        tabBar = new LinearLayout(this);
        tabBar.setPadding(dp(16), dp(10), dp(16), dp(12));
        hs.addView(tabBar);
        root.addView(hs);

        // 목록
        ScrollView sv = new ScrollView(this);
        sv.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(2), dp(16), dp(120));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        // 녹음 버튼
        LinearLayout fab = new LinearLayout(this);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(round(INK, 32));
        fab.setElevation(dp(10));
        fab.setPadding(dp(26), 0, dp(30), 0);
        FrameLayout dot = new FrameLayout(this);
        dot.setBackground(oval(0xFFFF5A4E));
        ImageView mic = new ImageView(this);
        mic.setImageResource(R.drawable.ic_mic);
        dot.addView(mic, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        fab.addView(dot, lp(dp(34), dp(34)));
        TextView fl = tv("눌러서 말하기", 16, Color.WHITE, true);
        fl.setPadding(dp(12), 0, 0, 0);
        fab.addView(fl);
        fab.setOnClickListener(v -> startActivity(new Intent(this, RecordActivity.class)));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-2, dp(62), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.bottomMargin = dp(22);
        frame.addView(fab, fp);

        setContentView(frame);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("tab", tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiBus.listener = this::refresh;
        if (!Prefs.key(this).isEmpty()) {
            for (Db.Memo m : Db.get(this).memos())
                if ("pending".equals(m.status) || "nokey".equals(m.status)) Classifier.enqueue(this, m.id);
        }
        MemoWidget.updateAll(this);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        UiBus.listener = null;
        stopPlayer();
    }

    private List<String> allCats() {
        List<String> cats = new ArrayList<>(Prefs.cats(this));
        for (String c : Db.get(this).usedCategories()) if (!cats.contains(c)) cats.add(cats.size() - 1, c);
        return cats;
    }

    private void refresh() {
        dateLine.setText(new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(new Date()));
        List<String> cats = allCats();
        if (!TAB_REC.equals(tab) && !cats.contains(tab)) tab = TAB_REC;
        Map<String, Integer> counts = Db.get(this).openCounts();

        tabBar.removeAllViews();
        addTab("녹음", TAB_REC, INK, -1);
        for (String c : cats) {
            Integer n = counts.get(c);
            addTab(c, c, catColor(c), n == null ? 0 : n);
        }

        list.removeAllViews();
        if (Prefs.key(this).isEmpty()) list.addView(keyBanner());
        if (TAB_REC.equals(tab)) buildRecordings(cats, counts);
        else buildCategory(tab);
    }

    private void addTab(String label, String key, int color, int count) {
        boolean sel = key.equals(tab);
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14), 0, dp(14), 0);
        if (sel) t.setBackground(round(INK, 20));
        else {
            GradientDrawable g = round(CARD, 20);
            g.setStroke(dp(1), LINE);
            t.setBackground(g);
        }
        if (count >= 0) {
            View d = new View(this);
            d.setBackground(oval(color));
            LinearLayout.LayoutParams dl = lp(dp(7), dp(7));
            dl.rightMargin = dp(7);
            t.addView(d, dl);
        }
        t.addView(tv(label, 15, sel ? Color.WHITE : INK, true));
        if (count > 0) {
            TextView n = tv(String.valueOf(count), 13, sel ? 0xB3FFFFFF : SUB, true);
            n.setPadding(dp(6), 0, 0, 0);
            t.addView(n);
        }
        t.setOnClickListener(v -> { tab = key; showDone = false; stopPlayer(); refresh(); });
        LinearLayout.LayoutParams l = lp(-2, dp(40));
        l.rightMargin = dp(8);
        tabBar.addView(t, l);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = round(CARD, 20);
        g.setStroke(dp(1), LINE);
        c.setBackground(g);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams l = lp(-1, -2);
        l.bottomMargin = dp(10);
        c.setLayoutParams(l);
        return c;
    }

    private View keyBanner() {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(round(0xFFFFF4E0, 16));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(tv("자동 분류를 켜 주세요", 15, 0xFF7A4A00, true));
        TextView s = tv("제미나이 API 키를 넣으면 녹음이 탭별로 정리돼요. 녹음은 키 없이도 저장돼요.", 13, 0xFF9A6A1E, false);
        s.setPadding(0, dp(5), 0, 0);
        s.setLineSpacing(dp(2), 1f);
        col.addView(s);
        c.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        TextView go = tv("설정", 14, 0xFF7A4A00, true);
        go.setPadding(dp(14), dp(8), dp(4), dp(8));
        c.addView(go);
        c.setOnClickListener(v -> openSettings());
        LinearLayout.LayoutParams l = lp(-1, -2);
        l.bottomMargin = dp(12);
        c.setLayoutParams(l);
        return c;
    }

    private void empty(String head, String body, int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(24), dp(56), dp(24), dp(24));
        FrameLayout ic = new FrameLayout(this);
        ic.setBackground(oval(tint(color, 0x1F)));
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_mic);
        iv.setColorFilter(color);
        ic.addView(iv, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        c.addView(ic, lp(dp(72), dp(72)));
        TextView h = tv(head, 18, INK, true);
        h.setPadding(0, dp(18), 0, dp(8));
        c.addView(h);
        TextView b = tv(body, 14, SUB, false);
        b.setGravity(Gravity.CENTER);
        b.setLineSpacing(dp(3), 1f);
        c.addView(b);
        list.addView(c);
    }

    private TextView sectionLabel(String s) {
        TextView t = tv(s, 13, SUB, true);
        t.setPadding(dp(4), dp(10), 0, dp(10));
        return t;
    }

    // ---------- 시간 표시 ----------

    static long dayStart(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static String when(long ms) {
        long now = System.currentTimeMillis();
        long diff = now - ms;
        if (diff < 60_000) return "방금";
        if (diff < 3_600_000) return (diff / 60_000) + "분 전";
        String time = new SimpleDateFormat("a h:mm", Locale.KOREAN).format(new Date(ms));
        long days = (dayStart(now) - dayStart(ms)) / 86_400_000L;
        if (days == 0) return "오늘 " + time;
        if (days == 1) return "어제 " + time;
        return new SimpleDateFormat("M월 d일 (E) ", Locale.KOREAN).format(new Date(ms)) + time;
    }

    static String dur(long ms) {
        long s = Math.max(1, Math.round(ms / 1000.0));
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** 기한 글자와 색. [0]=글자, [1]=색 */
    Object[] dueLabel(String due) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(due.substring(0, 10));
            long days = (dayStart(d.getTime()) - dayStart(System.currentTimeMillis())) / 86_400_000L;
            String time = due.length() >= 16 ? " " + due.substring(11, 16) : "";
            if (days < 0) return new Object[]{(-days) + "일 지남", RED};
            if (days == 0) return new Object[]{"오늘" + time, AMBER};
            if (days == 1) return new Object[]{"내일" + time, catColor("할 일")};
            return new Object[]{new SimpleDateFormat("M/d (E)", Locale.KOREAN).format(d) + time, SUB};
        } catch (Exception e) {
            return new Object[]{due, SUB};
        }
    }

    private TextView pill(String s, int color) {
        TextView t = tv(s, 12, color, true);
        t.setBackground(round(tint(color, 0x1A), 8));
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        return t;
    }

    // ---------- 녹음 탭 ----------

    private void buildRecordings(List<String> cats, Map<String, Integer> counts) {
        List<Db.Memo> memos = Db.get(this).memos();
        if (memos.isEmpty()) {
            empty("첫 메모를 남겨 보세요", "아래 버튼이나 홈 화면의 '원탭 녹음' 위젯을 누르고\n떠오른 걸 말하면 알아서 정리해 드려요.", INK);
            return;
        }

        // 남은 것 요약
        LinearLayout sum = new LinearLayout(this);
        int shown = 0;
        for (String c : cats) {
            Integer n = counts.get(c);
            if (n == null || n == 0) continue;
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(round(tint(catColor(c), 0x14), 16));
            box.setPadding(dp(14), dp(12), dp(14), dp(12));
            box.addView(tv(c, 13, catColor(c), true));
            TextView num = tv(String.valueOf(n), 24, INK, true);
            num.setPadding(0, dp(8), 0, 0);
            box.addView(num);
            box.setOnClickListener(v -> { tab = c; refresh(); });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, 1);
            if (shown > 0) l.leftMargin = dp(8);
            sum.addView(box, l);
            if (++shown == 4) break;
        }
        if (shown > 0) {
            list.addView(sectionLabel("남은 것"));
            list.addView(sum);
        }
        list.addView(sectionLabel("최근 녹음"));

        for (Db.Memo m : memos) list.addView(memoCard(m));
    }

    private View memoCard(Db.Memo m) {
        LinearLayout c = card();
        boolean running = Classifier.isRunning(m.id);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout play = new FrameLayout(this);
        play.setBackground(oval(INK));
        ImageView pi = new ImageView(this);
        pi.setImageResource(playingMemo == m.id ? R.drawable.ic_pause : R.drawable.ic_play);
        play.addView(pi, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        play.setOnClickListener(v -> togglePlay(m));
        play.setContentDescription("듣기");
        top.addView(play, lp(dp(40), dp(40)));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(dp(12), 0, 0, 0);
        meta.addView(tv(when(m.created), 15, INK, true));
        TextView d = tv(dur(m.duration), 13, SUB, false);
        d.setPadding(0, dp(4), 0, 0);
        meta.addView(d);
        top.addView(meta, new LinearLayout.LayoutParams(0, -2, 1));

        if (running) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
            top.addView(pb, lp(dp(18), dp(18)));
            TextView rt = tv("정리 중", 13, SUB, true);
            rt.setPadding(dp(6), 0, dp(4), 0);
            top.addView(rt);
        } else {
            TextView more = tv("···", 18, MUTE, true);
            more.setPadding(dp(12), dp(6), dp(4), dp(6));
            more.setOnClickListener(v -> memoMenu(m));
            top.addView(more);
        }
        c.addView(top);

        if (m.transcript != null && !m.transcript.isEmpty()) {
            TextView tr = tv(m.transcript, 15, INK, false);
            tr.setLineSpacing(dp(4), 1f);
            tr.setPadding(0, dp(14), 0, 0);
            if (!expanded.contains(m.id)) {
                tr.setMaxLines(3);
                tr.setEllipsize(TextUtils.TruncateAt.END);
            }
            tr.setOnClickListener(v -> {
                if (!expanded.remove(m.id)) expanded.add(m.id);
                refresh();
            });
            c.addView(tr);
        }

        if (!running && "error".equals(m.status)) {
            LinearLayout e = new LinearLayout(this);
            e.setGravity(Gravity.CENTER_VERTICAL);
            e.setBackground(round(tint(RED, 0x14), 12));
            e.setPadding(dp(12), dp(10), dp(12), dp(10));
            TextView et = tv(m.error == null ? "정리하지 못했어요" : m.error, 13, RED, false);
            et.setLineSpacing(dp(2), 1f);
            e.addView(et, new LinearLayout.LayoutParams(0, -2, 1));
            TextView again = tv("다시 시도", 13, RED, true);
            again.setPadding(dp(12), dp(4), 0, dp(4));
            again.setOnClickListener(v -> reclassify(m));
            e.addView(again);
            LinearLayout.LayoutParams l = lp(-1, -2);
            l.topMargin = dp(12);
            c.addView(e, l);
        } else if (!running && "nokey".equals(m.status)) {
            TextView t = tv("API 키를 넣으면 자동으로 정리돼요", 13, AMBER, false);
            t.setPadding(0, dp(12), 0, 0);
            c.addView(t);
        } else if (!running && "pending".equals(m.status)) {
            TextView t = tv("정리 대기 중", 13, SUB, false);
            t.setPadding(0, dp(12), 0, 0);
            c.addView(t);
        }

        List<Db.Item> items = Db.get(this).itemsOfMemo(m.id);
        if (!items.isEmpty()) {
            View line = new View(this);
            line.setBackgroundColor(LINE);
            LinearLayout.LayoutParams ll = lp(-1, dp(1));
            ll.topMargin = dp(14);
            ll.bottomMargin = dp(4);
            c.addView(line, ll);
            for (Db.Item it : items) {
                LinearLayout r = new LinearLayout(this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(0, dp(8), 0, 0);
                r.addView(pill(it.category, catColor(it.category)));
                TextView t = tv(it.text, 14, it.done ? MUTE : INK, false);
                if (it.done) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                t.setPadding(dp(10), 0, 0, 0);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
                r.setOnClickListener(v -> { tab = it.category; refresh(); });
                c.addView(r);
            }
        } else if (!running && "done".equals(m.status)) {
            TextView t = tv("정리할 내용이 없었어요", 13, MUTE, false);
            t.setPadding(0, dp(12), 0, 0);
            c.addView(t);
        }
        return c;
    }

    private void reclassify(Db.Memo m) {
        if (Prefs.key(this).isEmpty()) { openSettings(); return; }
        Classifier.enqueue(this, m.id);
    }

    private void memoMenu(Db.Memo m) {
        sheetMenu(when(m.created) + " 녹음",
                new String[]{playingMemo == m.id ? "듣기 멈추기" : "듣기", "다시 정리하기", "삭제"},
                new int[]{INK, INK, RED},
                w -> {
                    if (w == 0) togglePlay(m);
                    else if (w == 1) reclassify(m);
                    else confirm("이 녹음을 지울까요?", "녹음과 여기서 나온 항목이 모두 지워져요.", "삭제", () -> {
                        if (playingMemo == m.id) stopPlayer();
                        Db.get(this).deleteMemo(m.id);
                        refresh();
                    });
                });
    }

    // ---------- 분류 탭 ----------

    private void buildCategory(String cat) {
        int color = catColor(cat);
        List<Db.Item> items = Db.get(this).items(cat);
        if (items.isEmpty()) {
            empty("'" + cat + "'에 아직 아무것도 없어요", "녹음하면 여기로 알아서 들어와요.", color);
            return;
        }
        Collections.sort(items, (x, y) -> {
            if (x.due != null && y.due != null && !x.due.equals(y.due)) return x.due.compareTo(y.due);
            if (x.due != null && y.due == null) return -1;
            if (x.due == null && y.due != null) return 1;
            return Long.compare(y.created, x.created);
        });
        List<Db.Item> open = new ArrayList<>(), done = new ArrayList<>();
        for (Db.Item it : items) (it.done ? done : open).add(it);

        if (open.isEmpty()) {
            TextView t = tv("모두 끝냈어요", 15, color, true);
            t.setPadding(dp(4), dp(16), 0, dp(12));
            list.addView(t);
        } else {
            list.addView(sectionLabel("남은 것 " + open.size()));
            for (Db.Item it : open) list.addView(itemCard(it, color));
        }

        if (!done.isEmpty()) {
            TextView toggle = tv((showDone ? "완료한 것 숨기기 " : "완료한 것 보기 ") + done.size(), 13, SUB, true);
            toggle.setPadding(dp(4), dp(14), 0, dp(10));
            toggle.setOnClickListener(v -> { showDone = !showDone; refresh(); });
            list.addView(toggle);
            if (showDone) for (Db.Item it : done) list.addView(itemCard(it, color));
        }
    }

    private View itemCard(Db.Item it, int color) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(14), dp(14), dp(16), dp(14));

        FrameLayout check = new FrameLayout(this);
        if (it.done) {
            check.setBackground(oval(color));
            TextView mark = tv("✓", 14, Color.WHITE, true);
            check.addView(mark, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        } else {
            GradientDrawable g = oval(Color.TRANSPARENT);
            g.setStroke(dp(2), tint(color, 0x99));
            check.setBackground(g);
        }
        check.setOnClickListener(v -> { Db.get(this).setDone(it.id, !it.done); refresh(); });
        check.setContentDescription(it.done ? "완료 취소" : "완료");
        LinearLayout.LayoutParams cl = lp(dp(26), dp(26));
        cl.rightMargin = dp(14);
        c.addView(check, cl);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = tv(it.text, 16, it.done ? MUTE : INK, false);
        t.setLineSpacing(dp(3), 1f);
        if (it.done) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        col.addView(t);

        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, dp(7), 0, 0);
        if (it.due != null && !it.done) {
            Object[] dl = dueLabel(it.due);
            TextView p = pill((String) dl[0], (Integer) dl[1]);
            LinearLayout.LayoutParams pl = lp(-2, -2);
            pl.rightMargin = dp(8);
            meta.addView(p, pl);
        }
        Db.Memo src = Db.get(this).memo(it.memoId);
        StringBuilder sb = new StringBuilder();
        if (it.person != null) sb.append(it.person);
        if (src != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(when(src.created));
        }
        TextView mt = tv(sb.toString(), 12, MUTE, false);
        mt.setSingleLine(true);
        mt.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(mt);
        col.addView(meta);
        c.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        if (it.amount != null) {
            TextView a = tv(it.amount, 16, it.done ? MUTE : INK, true);
            a.setPadding(dp(10), 0, 0, 0);
            c.addView(a);
        }
        c.setOnClickListener(v -> itemMenu(it, src));
        return c;
    }

    private void itemMenu(Db.Item it, Db.Memo src) {
        String[] opts = {it.done ? "다시 남은 것으로" : "완료로 표시",
                src != null && playingMemo == src.id ? "듣기 멈추기" : "원래 녹음 듣기",
                "다른 탭으로 옮기기", "내용 고치기", "삭제"};
        sheetMenu(it.text, opts, new int[]{INK, INK, INK, INK, RED}, w -> {
            if (w == 0) { Db.get(this).setDone(it.id, !it.done); refresh(); }
            else if (w == 1) { if (src != null) togglePlay(src); }
            else if (w == 2) {
                List<String> cats = allCats();
                cats.remove(it.category);
                String[] arr = cats.toArray(new String[0]);
                int[] cols = new int[arr.length];
                for (int i = 0; i < arr.length; i++) cols[i] = catColor(arr[i]);
                sheetMenu("어느 탭으로 옮길까요?", arr, cols, w2 -> { Db.get(this).moveItem(it.id, arr[w2]); refresh(); });
            } else if (w == 3) editItem(it);
            else { Db.get(this).deleteItem(it.id); refresh(); }
        });
    }

    // ---------- 바텀 시트 ----------

    private Dialog sheet(View content) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(10), dp(20), dp(28));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        float r = dp(26);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        wrap.setBackground(bg);
        View handle = new View(this);
        handle.setBackground(round(LINE, 3));
        LinearLayout.LayoutParams hl = lp(dp(40), dp(5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(16);
        wrap.addView(handle, hl);
        wrap.addView(content);
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.35f);
        }
        d.show();
        return d;
    }

    private void sheetMenu(String title, String[] labels, int[] colors, IntConsumer onPick) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView h = tv(title, 14, SUB, true);
        h.setSingleLine(true);
        h.setEllipsize(TextUtils.TruncateAt.END);
        h.setPadding(dp(4), 0, dp(4), dp(8));
        box.addView(h);
        final Dialog[] ref = new Dialog[1];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), 0, dp(4), 0);
            boolean dotted = colors[i] != INK && colors[i] != RED;
            if (dotted) {
                View d = new View(this);
                d.setBackground(oval(colors[i]));
                LinearLayout.LayoutParams dl = lp(dp(10), dp(10));
                dl.rightMargin = dp(12);
                row.addView(d, dl);
            }
            row.addView(tv(labels[i], 17, dotted ? INK : colors[i], false));
            row.setOnClickListener(v -> { ref[0].dismiss(); onPick.accept(idx); });
            box.addView(row, lp(-1, dp(54)));
        }
        ref[0] = sheet(box);
    }

    private void confirm(String title, String body, String yes, Runnable onYes) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv(title, 19, INK, true));
        TextView b = tv(body, 14, SUB, false);
        b.setPadding(0, dp(10), 0, dp(22));
        box.addView(b);
        final Dialog[] ref = new Dialog[1];
        LinearLayout row = new LinearLayout(this);
        TextView no = button("취소", false);
        no.setOnClickListener(v -> ref[0].dismiss());
        row.addView(no, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView ok = button(yes, true);
        ok.setBackground(round(RED, 16));
        ok.setOnClickListener(v -> { ref[0].dismiss(); onYes.run(); });
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(0, dp(52), 1);
        ol.leftMargin = dp(10);
        row.addView(ok, ol);
        box.addView(row);
        ref[0] = sheet(box);
    }

    private TextView button(String s, boolean primary) {
        TextView t = tv(s, 16, primary ? Color.WHITE : INK, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(primary ? INK : 0xFFF0EFEB, 16));
        return t;
    }

    private EditText input(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(INK);
        e.setHintTextColor(MUTE);
        e.setBackground(round(0xFFF3F2EE, 14));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private TextView fieldLabel(String s) {
        TextView t = tv(s, 13, SUB, true);
        t.setPadding(dp(2), dp(16), 0, dp(8));
        return t;
    }

    private void editItem(Db.Item it) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("내용 고치기", 19, INK, true));
        EditText e = input(it.text, "");
        e.setSelection(e.getText().length());
        LinearLayout.LayoutParams el = lp(-1, -2);
        el.topMargin = dp(16);
        el.bottomMargin = dp(18);
        box.addView(e, el);
        final Dialog[] ref = new Dialog[1];
        TextView save = button("저장", true);
        save.setOnClickListener(v -> {
            String s = e.getText().toString().trim();
            if (!s.isEmpty()) Db.get(this).editItem(it.id, s);
            ref[0].dismiss();
            refresh();
        });
        box.addView(save, lp(-1, dp(52)));
        ref[0] = sheet(box);
        if (ref[0].getWindow() != null)
            ref[0].getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        e.requestFocus();
    }

    private void openSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("설정", 22, INK, true));

        box.addView(fieldLabel("제미나이 API 키"));
        EditText key = input(Prefs.key(this), "AIza로 시작하는 키");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        key.setSingleLine(true);
        box.addView(key);
        TextView link = tv("키 받으러 가기 →", 13, catColor("할 일"), true);
        link.setPadding(dp(2), dp(10), 0, 0);
        link.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))));
        box.addView(link);

        box.addView(fieldLabel("탭 목록 (쉼표로 구분, '기타'는 항상 마지막)"));
        EditText cats = input(Prefs.catsRaw(this), Prefs.DEFAULT_CATS);
        box.addView(cats);

        box.addView(fieldLabel("AI 모델 (보통은 그대로 두세요)"));
        EditText model = input(Prefs.model(this), Prefs.DEFAULT_MODEL);
        model.setSingleLine(true);
        box.addView(model);

        final Dialog[] ref = new Dialog[1];
        TextView save = button("저장", true);
        save.setOnClickListener(v -> {
            String c = cats.getText().toString().trim();
            Prefs.save(this, key.getText().toString(), c.isEmpty() ? Prefs.DEFAULT_CATS : c, model.getText().toString());
            if (!Prefs.key(this).isEmpty()) {
                for (Db.Memo m : Db.get(this).memos())
                    if (!"done".equals(m.status)) Classifier.enqueue(this, m.id);
            }
            ref[0].dismiss();
            Toast.makeText(this, "저장했어요", Toast.LENGTH_SHORT).show();
            refresh();
        });
        LinearLayout.LayoutParams sl = lp(-1, dp(52));
        sl.topMargin = dp(24);
        box.addView(save, sl);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        ref[0] = sheet(sv);
        if (ref[0].getWindow() != null)
            ref[0].getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    // ---------- 재생 ----------

    private void togglePlay(Db.Memo m) {
        if (playingMemo == m.id) { stopPlayer(); refresh(); return; }
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(m.path);
            player.setOnCompletionListener(p -> { stopPlayer(); refresh(); });
            player.prepare();
            player.start();
            playingMemo = m.id;
        } catch (Exception e) {
            stopPlayer();
            Toast.makeText(this, "재생할 수 없어요", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        playingMemo = -1;
    }
}
