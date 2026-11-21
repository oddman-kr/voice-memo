package kr.oddman.voicememo;

import android.Manifest;
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
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

public class MainActivity extends Activity {
    static final int HOME = 0, CAT = 1, HIST = 2;
    static final int REQ_CAL = 7;

    static final int BG = 0xFFF2F3F5, WHITE = 0xFFFFFFFF, T1 = 0xFF191F28, T2 = 0xFF4E5968, T3 = 0xFF8B95A1,
            T4 = 0xFFB0B8C1, DIV = 0xFFF2F3F5, NAVLINE = 0xFFEEF0F2, CHECK = 0xFFD1D6DB,
            RED = 0xFFF04452, ORANGE = 0xFFFF8A00, AMBER_BG = 0xFFFFF6E5, AMBER_T = 0xFF8A5A00;
    static final int[][] EXTRA = {{0xFF3182F6, 0xFFE8F3FF}, {0xFFD99A00, 0xFFFFF6E0}, {0xFF0098B3, 0xFFE3F6FA}, {0xFF8B5E3C, 0xFFF5EEE8}};

    private int screen = HOME;
    private String catName = null;
    private LinearLayout list, navHome, navHist;
    private ScrollView scroll;
    private MediaPlayer player;
    private long playingMemo = -1;
    private Typeface fR, fM, fS, fB;

    // ---------- 도구 ----------

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /** w: 0=보통 1=중간 2=약간굵게 3=굵게 */
    private TextView tv(String s, float sp, int color, int w) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(w == 3 ? fB : w == 2 ? fS : w == 1 ? fM : fR);
        t.setLetterSpacing(-0.01f);
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }

    private View iconBox(int res, int fg, int bg, int box, int icon, float r) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(r < 0 ? oval(bg) : round(bg, r));
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(fg);
        f.addView(iv, new FrameLayout.LayoutParams(dp(icon), dp(icon), Gravity.CENTER));
        f.setLayoutParams(lp(dp(box), dp(box)));
        return f;
    }

    private LinearLayout group() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(round(WHITE, 20));
        LinearLayout.LayoutParams l = lp(-1, -2);
        l.bottomMargin = dp(12);
        g.setLayoutParams(l);
        return g;
    }

    private View divider(int leftDp) {
        View v = new View(this);
        v.setBackgroundColor(DIV);
        LinearLayout.LayoutParams l = lp(-1, dp(1));
        l.leftMargin = dp(leftDp);
        v.setLayoutParams(l);
        return v;
    }

    private TextView section(String s) {
        TextView t = tv(s, 14, T3, 2);
        t.setPadding(dp(6), dp(14), 0, dp(10));
        return t;
    }

    // ---------- 카테고리 색과 아이콘 ----------

    int[] catColors(String cat) {
        switch (cat) {
            case "할 일": return new int[]{0xFF6B5CE7, 0xFFF3F0FF};
            case "송금": return new int[]{0xFF119E7A, 0xFFE8F7F1};
            case "쿠팡": return new int[]{0xFFF2633A, 0xFFFFF1EC};
            case "다이소": return new int[]{0xFFE0468A, 0xFFFFEEF5};
            case "기타": return new int[]{0xFF6B7684, 0xFFF2F3F5};
        }
        List<String> cats = allCats();
        return EXTRA[Math.max(0, cats.indexOf(cat)) % EXTRA.length];
    }

    static int catIcon(String cat) {
        switch (cat) {
            case "할 일": return R.drawable.ic_task;
            case "송금": return R.drawable.ic_send;
            case "쿠팡": return R.drawable.ic_cart;
            case "다이소": return R.drawable.ic_basket;
            case "기타": return R.drawable.ic_more;
        }
        return R.drawable.ic_label;
    }

    private List<String> allCats() {
        List<String> cats = new ArrayList<>(Prefs.cats(this));
        for (String c : Db.get(this).usedCategories()) if (!cats.contains(c)) cats.add(cats.size() - 1, c);
        return cats;
    }

    // ---------- 시간 ----------

    static long dayStart(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long daysFromToday(long ms) {
        return Math.round((dayStart(ms) - dayStart(System.currentTimeMillis())) / 86_400_000.0);
    }

    static String dayName(long ms) {
        long d = daysFromToday(ms);
        if (d == 0) return "오늘";
        if (d == -1) return "어제";
        return new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(new Date(ms));
    }

    static String clock(long ms) { return new SimpleDateFormat("a h:mm", Locale.KOREAN).format(new Date(ms)); }

    static String when(long ms) {
        long diff = System.currentTimeMillis() - ms;
        if (diff < 60_000) return "방금";
        if (diff < 3_600_000) return (diff / 60_000) + "분 전";
        long d = daysFromToday(ms);
        if (d == 0) return "오늘 " + clock(ms);
        if (d == -1) return "어제 " + clock(ms);
        return new SimpleDateFormat("M월 d일", Locale.KOREAN).format(new Date(ms));
    }

    static String dur(long ms) {
        long s = Math.max(1, Math.round(ms / 1000.0));
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** 기한 → {글자, 색} */
    Object[] dueLabel(String due) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(due.substring(0, 10));
            long days = daysFromToday(d.getTime());
            String time = "";
            if (due.length() >= 16) {
                Date t = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse(due.substring(0, 16));
                time = " " + clock(t.getTime());
            }
            if (days < 0) return new Object[]{(-days) + "일 지남", RED};
            if (days == 0) return new Object[]{"오늘" + time + "까지", ORANGE};
            if (days == 1) return new Object[]{"내일" + time + "까지", T2};
            return new Object[]{new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(d) + time + "까지", T3};
        } catch (Exception e) {
            return new Object[]{due, T3};
        }
    }

    // ---------- 화면 뼈대 ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        fR = getResources().getFont(R.font.pretendard_regular);
        fM = getResources().getFont(R.font.pretendard_medium);
        fS = getResources().getFont(R.font.pretendard_semibold);
        fB = getResources().getFont(R.font.pretendard_bold);
        if (b != null) {
            screen = b.getInt("screen", HOME);
            catName = b.getString("cat");
        }

        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(WHITE);
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

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(20), dp(16), dp(40));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // 아래 탭
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(WHITE);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        View navLine = new View(this);
        navLine.setBackgroundColor(NAVLINE);
        root.addView(navLine, lp(-1, dp(1)));
        navHome = navItem(R.drawable.ic_list, "정리", () -> go(HOME, null));
        navHist = navItem(R.drawable.ic_history, "기록", () -> go(HIST, null));
        nav.addView(navHome, weight());
        nav.addView(new View(this), lp(dp(88), 1));
        nav.addView(navHist, weight());
        root.addView(nav, lp(-1, dp(64)));

        // 가운데 녹음 버튼
        FrameLayout mic = new FrameLayout(this);
        GradientDrawable mg = oval(T1);
        mg.setStroke(dp(5), BG);
        mic.setBackground(mg);
        mic.setElevation(dp(6));
        ImageView mi = new ImageView(this);
        mi.setImageResource(R.drawable.ic_mic);
        mic.addView(mi, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        mic.setContentDescription("녹음");
        mic.setOnClickListener(v -> startActivity(new Intent(this, RecordActivity.class)));
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(dp(68), dp(68), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        ml.bottomMargin = dp(26);
        frame.addView(mic, ml);

        setContentView(frame);
    }

    private LinearLayout navItem(int icon, String label, Runnable onClick) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        l.addView(iv, lp(dp(24), dp(24)));
        TextView t = tv(label, 11, T4, 2);
        t.setPadding(0, dp(4), 0, 0);
        l.addView(t);
        l.setOnClickListener(v -> onClick.run());
        return l;
    }

    private void paintNav(LinearLayout item, boolean on) {
        ((ImageView) item.getChildAt(0)).setColorFilter(on ? T1 : T4);
        ((TextView) item.getChildAt(1)).setTextColor(on ? T1 : T4);
    }

    private void go(int s, String cat) {
        screen = s;
        catName = cat;
        stopPlayer();
        refresh();
        scroll.scrollTo(0, 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (screen == CAT) go(HOME, null);
        else if (screen == HIST) go(HOME, null);
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("screen", screen);
        out.putString("cat", catName);
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

    private void refresh() {
        paintNav(navHome, screen != HIST);
        paintNav(navHist, screen == HIST);
        list.removeAllViews();
        if (screen == CAT && catName != null) buildCat(catName);
        else if (screen == HIST) buildHist();
        else { screen = HOME; buildHome(); }
    }

    // ---------- 정리 홈 ----------

    private void buildHome() {
        List<Db.Item> open = Db.get(this).allOpen();
        Map<String, List<Db.Item>> by = new LinkedHashMap<>();
        for (String c : allCats()) by.put(c, new ArrayList<>());
        for (Db.Item it : open) {
            if (!by.containsKey(it.category)) by.put(it.category, new ArrayList<>());
            by.get(it.category).add(it);
        }

        TextView date = tv(new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(new Date()), 14, T3, 1);
        date.setPadding(dp(6), dp(4), 0, 0);
        list.addView(date);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(6), dp(8), 0, dp(18));
        head.addView(tv(open.isEmpty() ? "다 끝냈어요" : "남은 것 " + open.size() + "개", 26, T1, 3), weight());
        View gear = iconBox(R.drawable.ic_settings, T3, BG, 40, 22, -1);
        gear.setOnClickListener(v -> openSettings());
        gear.setContentDescription("설정");
        head.addView(gear);
        list.addView(head);

        if (Prefs.key(this).isEmpty()) list.addView(keyBanner());
        addRunningNotice();

        if (open.isEmpty()) {
            boolean anyMemo = !Db.get(this).memos().isEmpty();
            empty(R.drawable.ic_mic, anyMemo ? "남은 게 없어요" : "떠오르면 바로 말해 보세요",
                    anyMemo ? "새로 생긴 일은 아래 버튼으로 말해 두세요." : "아래 버튼이나 홈 화면 위젯을 누르고 말하면\n살 것, 할 일, 보낼 돈으로 알아서 나눠 드려요.");
            return;
        }

        for (Map.Entry<String, List<Db.Item>> e : by.entrySet()) {
            List<Db.Item> items = e.getValue();
            if (items.isEmpty()) continue;
            sortItems(items);
            String cat = e.getKey();
            int[] cc = catColors(cat);
            LinearLayout g = group();

            LinearLayout gh = new LinearLayout(this);
            gh.setGravity(Gravity.CENTER_VERTICAL);
            gh.setPadding(dp(16), dp(16), dp(14), dp(6));
            gh.addView(iconBox(catIcon(cat), cc[0], cc[1], 30, 17, 10));
            TextView name = tv(cat, 17, T1, 3);
            name.setPadding(dp(10), 0, 0, 0);
            gh.addView(name, weight());
            gh.addView(tv(String.valueOf(items.size()), 15, T3, 2));
            ImageView ch = new ImageView(this);
            ch.setImageResource(R.drawable.ic_chevron);
            ch.setColorFilter(T4);
            LinearLayout.LayoutParams cl = lp(dp(20), dp(20));
            cl.leftMargin = dp(2);
            gh.addView(ch, cl);
            gh.setOnClickListener(v -> go(CAT, cat));
            g.addView(gh);

            int show = Math.min(3, items.size());
            for (int i = 0; i < show; i++) g.addView(itemRow(items.get(i), cc, false));
            if (items.size() > show) {
                TextView more = tv((items.size() - show) + "개 더 보기", 14, T3, 2);
                more.setPadding(dp(54), dp(4), 0, dp(16));
                more.setOnClickListener(v -> go(CAT, cat));
                g.addView(more);
            } else {
                View pad = new View(this);
                g.addView(pad, lp(-1, dp(8)));
            }
            list.addView(g);
        }
    }

    private void sortItems(List<Db.Item> items) {
        Collections.sort(items, (x, y) -> {
            if (x.due != null && y.due != null && !x.due.equals(y.due)) return x.due.compareTo(y.due);
            if (x.due != null && y.due == null) return -1;
            if (x.due == null && y.due != null) return 1;
            return Long.compare(y.created, x.created);
        });
    }

    private void addRunningNotice() {
        int running = 0;
        for (Db.Memo m : Db.get(this).memos()) if (Classifier.isRunning(m.id)) running++;
        if (running == 0) return;
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(round(WHITE, 16));
        r.setPadding(dp(16), dp(14), dp(16), dp(14));
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        r.addView(pb, lp(dp(18), dp(18)));
        TextView t = tv("방금 녹음을 정리하고 있어요", 15, T2, 1);
        t.setPadding(dp(12), 0, 0, 0);
        r.addView(t);
        LinearLayout.LayoutParams l = lp(-1, -2);
        l.bottomMargin = dp(12);
        list.addView(r, l);
    }

    private View keyBanner() {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(round(AMBER_BG, 18));
        c.setPadding(dp(16), dp(16), dp(14), dp(16));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(tv("자동 정리를 켜 주세요", 16, AMBER_T, 3));
        TextView s = tv("제미나이 API 키를 넣으면 녹음이 알아서 나뉘어요.", 14, AMBER_T, 0);
        s.setPadding(0, dp(6), 0, 0);
        col.addView(s);
        c.addView(col, weight());
        ImageView ch = new ImageView(this);
        ch.setImageResource(R.drawable.ic_chevron);
        ch.setColorFilter(AMBER_T);
        c.addView(ch, lp(dp(20), dp(20)));
        c.setOnClickListener(v -> openSettings());
        LinearLayout.LayoutParams l = lp(-1, -2);
        l.bottomMargin = dp(12);
        c.setLayoutParams(l);
        return c;
    }

    private void empty(int icon, String head, String body) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(24), dp(48), dp(24), dp(24));
        c.addView(iconBox(icon, T3, WHITE, 64, 28, -1));
        TextView h = tv(head, 18, T1, 3);
        h.setPadding(0, dp(18), 0, dp(8));
        c.addView(h);
        TextView b = tv(body, 15, T3, 0);
        b.setGravity(Gravity.CENTER);
        b.setLineSpacing(dp(4), 1f);
        c.addView(b);
        list.addView(c);
    }

    // ---------- 항목 줄 ----------

    private View itemRow(Db.Item it, int[] cc, boolean detail) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        r.setMinimumHeight(dp(56));

        FrameLayout check = new FrameLayout(this);
        if (it.done) {
            check.setBackground(oval(cc[0]));
            ImageView iv = new ImageView(this);
            iv.setImageResource(R.drawable.ic_check);
            check.addView(iv, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        } else {
            GradientDrawable g = oval(Color.TRANSPARENT);
            g.setStroke(dp(2), CHECK);
            check.setBackground(g);
        }
        FrameLayout hit = new FrameLayout(this);
        hit.addView(check, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        hit.setOnClickListener(v -> toggleDone(it));
        hit.setContentDescription(it.done ? "완료 취소" : "완료");
        LinearLayout.LayoutParams hl = lp(dp(30), dp(36));
        hl.leftMargin = -dp(3);
        hl.rightMargin = dp(11);
        r.addView(hit, hl);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = tv(it.text, 16, it.done ? T4 : T1, 1);
        t.setLineSpacing(dp(3), 1f);
        if (it.done) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        col.addView(t);

        String metaText = null;
        int metaColor = T3;
        boolean strong = false;
        if (it.due != null && !it.done) {
            Object[] dl = dueLabel(it.due);
            metaText = (String) dl[0];
            metaColor = (Integer) dl[1];
            strong = metaColor == RED || metaColor == ORANGE;
        }
        if (detail) {
            Db.Memo src = Db.get(this).memo(it.memoId);
            String rec = src == null ? null : when(src.created);
            if (rec != null) metaText = metaText == null ? rec : metaText + " · " + rec;
        }
        if (metaText != null || it.eventId > 0) {
            LinearLayout mr = new LinearLayout(this);
            mr.setGravity(Gravity.CENTER_VERTICAL);
            mr.setPadding(0, dp(5), 0, 0);
            if (it.eventId > 0 && !it.done) {
                ImageView ev = new ImageView(this);
                ev.setImageResource(R.drawable.ic_event);
                ev.setColorFilter(T3);
                LinearLayout.LayoutParams el = lp(dp(13), dp(13));
                el.rightMargin = dp(4);
                mr.addView(ev, el);
            }
            if (metaText != null) {
                TextView m = tv(metaText, 13, metaColor, strong ? 2 : 0);
                m.setSingleLine(true);
                m.setEllipsize(TextUtils.TruncateAt.END);
                mr.addView(m);
            }
            col.addView(mr);
        }
        r.addView(col, weight());

        if (it.amount != null) {
            TextView a = tv(it.amount, 16, it.done ? T4 : T1, 3);
            a.setPadding(dp(12), 0, 0, 0);
            r.addView(a);
        }
        r.setOnClickListener(v -> itemMenu(it));
        return r;
    }

    private void toggleDone(Db.Item it) {
        Db.get(this).setDone(it.id, !it.done);
        refresh();
    }

    // ---------- 카테고리 상세 ----------

    private void buildCat(String cat) {
        int[] cc = catColors(cat);
        LinearLayout back = new LinearLayout(this);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(0), dp(2), dp(12), dp(14));
        ImageView bi = new ImageView(this);
        bi.setImageResource(R.drawable.ic_back);
        bi.setColorFilter(T2);
        back.addView(bi, lp(dp(24), dp(24)));
        back.addView(tv("정리", 16, T2, 1));
        back.setOnClickListener(v -> go(HOME, null));
        list.addView(back, lp(-2, -2));

        List<Db.Item> items = Db.get(this).items(cat);
        List<Db.Item> open = new ArrayList<>(), done = new ArrayList<>();
        for (Db.Item it : items) (it.done ? done : open).add(it);
        sortItems(open);
        Collections.sort(done, (x, y) -> Long.compare(y.created, x.created));

        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(6), 0, 0, dp(20));
        h.addView(iconBox(catIcon(cat), cc[0], cc[1], 44, 24, 14));
        LinearLayout hc = new LinearLayout(this);
        hc.setOrientation(LinearLayout.VERTICAL);
        hc.setPadding(dp(14), 0, 0, 0);
        hc.addView(tv(cat, 24, T1, 3));
        TextView sub = tv(open.isEmpty() ? "남은 게 없어요" : "남은 것 " + open.size() + "개", 14, T3, 1);
        sub.setPadding(0, dp(6), 0, 0);
        hc.addView(sub);
        h.addView(hc);
        list.addView(h);

        if (items.isEmpty()) {
            empty(catIcon(cat), "아직 아무것도 없어요", "'" + cat + "'에 해당하는 걸 말하면\n여기로 들어와요.");
            return;
        }
        if (!open.isEmpty()) list.addView(rows(open, cc));
        if (!done.isEmpty()) {
            list.addView(section("완료 " + done.size()));
            list.addView(rows(done, cc));
        }
    }

    private View rows(List<Db.Item> items, int[] cc) {
        LinearLayout g = group();
        g.setPadding(0, dp(4), 0, dp(4));
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) g.addView(divider(56));
            g.addView(itemRow(items.get(i), cc, true));
        }
        return g;
    }

    // ---------- 녹음 기록 ----------

    private void buildHist() {
        TextView title = tv("녹음 기록", 26, T1, 3);
        title.setPadding(dp(6), dp(18), 0, dp(8));
        list.addView(title);
        List<Db.Memo> memos = Db.get(this).memos();
        if (memos.isEmpty()) {
            empty(R.drawable.ic_history, "아직 녹음이 없어요", "가운데 버튼을 누르고 말해 보세요.");
            return;
        }
        String curDay = null;
        LinearLayout g = null;
        for (Db.Memo m : memos) {
            String day = dayName(m.created);
            if (!day.equals(curDay)) {
                curDay = day;
                list.addView(section(day));
                g = group();
                g.setPadding(0, dp(4), 0, dp(4));
                list.addView(g);
            } else {
                g.addView(divider(66));
            }
            g.addView(memoRow(m));
        }
    }

    private View memoRow(Db.Memo m) {
        boolean running = Classifier.isRunning(m.id);
        boolean error = !running && "error".equals(m.status);
        int count = Db.get(this).itemsOfMemo(m.id).size();

        LinearLayout r = new LinearLayout(this);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));

        View lead;
        if (running) {
            FrameLayout f = new FrameLayout(this);
            f.setBackground(oval(BG));
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
            f.addView(pb, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
            f.setLayoutParams(lp(dp(38), dp(38)));
            lead = f;
        } else if (error) {
            lead = iconBox(R.drawable.ic_refresh, RED, 0xFFFFF0F1, 38, 20, -1);
            lead.setOnClickListener(v -> reclassify(m));
            lead.setContentDescription("다시 정리");
        } else {
            lead = iconBox(playingMemo == m.id ? R.drawable.ic_pause : R.drawable.ic_play, T1, BG, 38, 18, -1);
            lead.setOnClickListener(v -> togglePlay(m));
            lead.setContentDescription("듣기");
        }
        r.addView(lead);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(1), 0, 0);
        String head;
        int headColor = T1;
        if (m.transcript != null && !m.transcript.isEmpty()) head = m.transcript;
        else if (error) { head = "정리하지 못했어요"; headColor = T2; }
        else if (running) { head = "정리하고 있어요"; headColor = T2; }
        else { head = "아직 정리 전이에요"; headColor = T2; }
        TextView t = tv(head, 16, headColor, 1);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setLineSpacing(dp(4), 1f);
        col.addView(t);

        String tail;
        if (running) tail = "정리 중…";
        else if (error) tail = "눌러서 다시 시도";
        else if ("nokey".equals(m.status)) tail = "API 키를 넣으면 정리돼요";
        else if ("pending".equals(m.status)) tail = "정리 대기 중";
        else tail = count > 0 ? count + "개 정리됨" : "정리할 내용 없음";
        TextView mt = tv(clock(m.created) + " · " + dur(m.duration) + " · " + tail, 13, error ? RED : T3, 0);
        mt.setPadding(0, dp(6), 0, 0);
        col.addView(mt);
        r.addView(col, weight());

        r.setOnClickListener(v -> { if (error) reclassify(m); else memoSheet(m); });
        return r;
    }

    private void reclassify(Db.Memo m) {
        if (Prefs.key(this).isEmpty()) { openSettings(); return; }
        Classifier.enqueue(this, m.id);
    }

    private void memoSheet(Db.Memo m) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv(dayName(m.created) + " " + clock(m.created), 20, T1, 3));
        TextView d = tv(dur(m.duration) + " 녹음", 14, T3, 0);
        d.setPadding(0, dp(6), 0, 0);
        box.addView(d);

        if (m.transcript != null && !m.transcript.isEmpty()) {
            TextView tr = tv(m.transcript, 16, T1, 0);
            tr.setLineSpacing(dp(6), 1f);
            tr.setTextIsSelectable(true);
            tr.setPadding(dp(16), dp(14), dp(16), dp(14));
            tr.setBackground(round(BG, 16));
            LinearLayout.LayoutParams tl = lp(-1, -2);
            tl.topMargin = dp(16);
            box.addView(tr, tl);
        }

        List<Db.Item> items = Db.get(this).itemsOfMemo(m.id);
        if (!items.isEmpty()) {
            TextView s = tv("정리한 것", 14, T3, 2);
            s.setPadding(dp(2), dp(20), 0, dp(4));
            box.addView(s);
            for (Db.Item it : items) {
                int[] cc = catColors(it.category);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(9), 0, dp(9));
                row.addView(iconBox(catIcon(it.category), cc[0], cc[1], 26, 15, 8));
                TextView tt = tv(it.text, 15, it.done ? T4 : T1, 1);
                tt.setPadding(dp(10), 0, 0, 0);
                row.addView(tt, weight());
                row.addView(tv(it.category, 13, T3, 1));
                box.addView(row);
            }
        }

        final Dialog[] ref = new Dialog[1];
        LinearLayout btns = new LinearLayout(this);
        TextView play = button(playingMemo == m.id ? "멈추기" : "듣기", true);
        play.setOnClickListener(v -> { ref[0].dismiss(); togglePlay(m); });
        btns.addView(play, new LinearLayout.LayoutParams(0, dp(54), 1));
        TextView again = button("다시 정리", false);
        again.setOnClickListener(v -> { ref[0].dismiss(); reclassify(m); });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, dp(54), 1);
        al.leftMargin = dp(8);
        btns.addView(again, al);
        LinearLayout.LayoutParams bl = lp(-1, -2);
        bl.topMargin = dp(22);
        box.addView(btns, bl);

        TextView del = tv("녹음 삭제", 15, RED, 1);
        del.setGravity(Gravity.CENTER);
        del.setPadding(0, dp(18), 0, dp(4));
        del.setOnClickListener(v -> {
            ref[0].dismiss();
            confirm("이 녹음을 지울까요?", "녹음과 여기서 나온 항목, 캘린더 일정이 모두 지워져요.", "삭제", () -> {
                if (playingMemo == m.id) stopPlayer();
                for (Db.Item it : Db.get(this).itemsOfMemo(m.id)) Cal.deleteEvent(this, it.eventId);
                Db.get(this).deleteMemo(m.id);
                refresh();
            });
        });
        box.addView(del);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        ref[0] = sheet(sv);
    }

    // ---------- 항목 메뉴 ----------

    private void itemMenu(Db.Item it) {
        Db.Memo src = Db.get(this).memo(it.memoId);
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        labels.add(it.done ? "다시 남은 것으로" : "완료로 표시"); colors.add(T1);
        acts.add(() -> toggleDone(it));
        if (src != null) {
            labels.add(playingMemo == src.id ? "듣기 멈추기" : "원래 녹음 듣기"); colors.add(T1);
            acts.add(() -> togglePlay(src));
        }
        if (it.eventId > 0) {
            labels.add("캘린더에서 보기"); colors.add(T1);
            acts.add(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Cal.eventUri(it.eventId))); }
                catch (Exception e) { Toast.makeText(this, "캘린더를 열 수 없어요", Toast.LENGTH_SHORT).show(); }
            });
        } else if (it.due != null && Cal.active(this)) {
            labels.add("캘린더에 추가"); colors.add(T1);
            acts.add(() -> {
                long ev = Cal.addEvent(this, it, src == null ? System.currentTimeMillis() : src.created);
                if (ev > 0) { Db.get(this).setEventId(it.id, ev); Toast.makeText(this, "캘린더에 넣었어요", Toast.LENGTH_SHORT).show(); }
                else Toast.makeText(this, "캘린더에 넣지 못했어요", Toast.LENGTH_SHORT).show();
                refresh();
            });
        }
        labels.add("다른 곳으로 옮기기"); colors.add(T1);
        acts.add(() -> {
            List<String> cats = allCats();
            cats.remove(it.category);
            String[] arr = cats.toArray(new String[0]);
            int[] icons = new int[arr.length];
            int[] cols = new int[arr.length];
            for (int i = 0; i < arr.length; i++) { icons[i] = catIcon(arr[i]); cols[i] = catColors(arr[i])[0]; }
            sheetMenu("어디로 옮길까요?", arr, cols, icons, w -> { Db.get(this).moveItem(it.id, arr[w]); refresh(); });
        });
        labels.add("내용 고치기"); colors.add(T1);
        acts.add(() -> editItem(it));
        labels.add("삭제"); colors.add(RED);
        acts.add(() -> {
            Cal.deleteEvent(this, it.eventId);
            Db.get(this).deleteItem(it.id);
            refresh();
        });

        int[] cols = new int[colors.size()];
        for (int i = 0; i < cols.length; i++) cols[i] = colors.get(i);
        sheetMenu(it.text, labels.toArray(new String[0]), cols, null, w -> acts.get(w).run());
    }

    // ---------- 바텀 시트 ----------

    private Dialog sheet(View content) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(22), dp(12), dp(22), dp(28));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(WHITE);
        float r = dp(28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        wrap.setBackground(bg);
        View handle = new View(this);
        handle.setBackground(round(0xFFE5E8EB, 3));
        LinearLayout.LayoutParams hl = lp(dp(36), dp(5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(18);
        wrap.addView(handle, hl);
        wrap.addView(content);
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setDimAmount(0.4f);
        }
        d.show();
        return d;
    }

    private void sheetMenu(String title, String[] labels, int[] colors, int[] icons, IntConsumer onPick) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView h = tv(title, 14, T3, 2);
        h.setSingleLine(true);
        h.setEllipsize(TextUtils.TruncateAt.END);
        h.setPadding(dp(2), 0, dp(2), dp(8));
        box.addView(h);
        final Dialog[] ref = new Dialog[1];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), 0, dp(2), 0);
            if (icons != null) {
                int[] cc = catColors(labels[i]);
                View ib = iconBox(icons[i], cc[0], cc[1], 30, 17, 10);
                LinearLayout.LayoutParams il = lp(dp(30), dp(30));
                il.rightMargin = dp(12);
                row.addView(ib, il);
            }
            row.addView(tv(labels[i], 17, icons != null ? T1 : colors[i], 1));
            row.setOnClickListener(v -> { ref[0].dismiss(); onPick.accept(idx); });
            box.addView(row, lp(-1, dp(54)));
        }
        ref[0] = sheet(box);
    }

    private void confirm(String title, String body, String yes, Runnable onYes) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv(title, 20, T1, 3));
        TextView b = tv(body, 15, T3, 0);
        b.setLineSpacing(dp(4), 1f);
        b.setPadding(0, dp(10), 0, dp(24));
        box.addView(b);
        final Dialog[] ref = new Dialog[1];
        LinearLayout row = new LinearLayout(this);
        TextView no = button("취소", false);
        no.setOnClickListener(v -> ref[0].dismiss());
        row.addView(no, new LinearLayout.LayoutParams(0, dp(54), 1));
        TextView ok = button(yes, true);
        ok.setBackground(round(RED, 16));
        ok.setOnClickListener(v -> { ref[0].dismiss(); onYes.run(); });
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(0, dp(54), 1);
        ol.leftMargin = dp(8);
        row.addView(ok, ol);
        box.addView(row);
        ref[0] = sheet(box);
    }

    private TextView button(String s, boolean primary) {
        TextView t = tv(s, 16, primary ? Color.WHITE : T2, 2);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(primary ? T1 : BG, 16));
        return t;
    }

    private EditText input(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTypeface(fM);
        e.setTextColor(T1);
        e.setHintTextColor(T4);
        e.setBackground(round(BG, 14));
        e.setPadding(dp(16), dp(14), dp(16), dp(14));
        return e;
    }

    private TextView fieldLabel(String s) {
        TextView t = tv(s, 14, T3, 2);
        t.setPadding(dp(2), dp(22), 0, dp(10));
        return t;
    }

    private void editItem(Db.Item it) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("내용 고치기", 20, T1, 3));
        EditText e = input(it.text, "");
        e.setSelection(e.getText().length());
        LinearLayout.LayoutParams el = lp(-1, -2);
        el.topMargin = dp(18);
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
        box.addView(save, lp(-1, dp(54)));
        ref[0] = sheet(box);
        if (ref[0].getWindow() != null)
            ref[0].getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        e.requestFocus();
    }

    // ---------- 설정 ----------

    private void openSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("설정", 24, T1, 3));

        box.addView(fieldLabel("제미나이 API 키"));
        EditText key = input(Prefs.key(this), "AIza로 시작하는 키");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        key.setSingleLine(true);
        box.addView(key);
        TextView link = tv("키 받으러 가기", 14, 0xFF3182F6, 2);
        link.setPadding(dp(2), dp(12), 0, 0);
        link.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))));
        box.addView(link);

        box.addView(fieldLabel("캘린더"));
        LinearLayout calRow = new LinearLayout(this);
        calRow.setGravity(Gravity.CENTER_VERTICAL);
        calRow.setBackground(round(BG, 14));
        calRow.setPadding(dp(16), dp(14), dp(12), dp(14));
        LinearLayout cc = new LinearLayout(this);
        cc.setOrientation(LinearLayout.VERTICAL);
        cc.addView(tv("기한 있는 항목을 캘린더에 추가", 16, T1, 1));
        Cal.Entry chosen = Cal.active(this) ? Cal.chosen(this) : null;
        TextView calSub = tv(chosen != null ? chosen.name + (chosen.account != null && !chosen.account.equals(chosen.name) ? " · " + chosen.account : "") : "날짜·시간을 말하면 일정으로 들어가요", 13, T3, 0);
        calSub.setPadding(0, dp(5), 0, 0);
        cc.addView(calSub);
        calRow.addView(cc, weight());
        Switch sw = new Switch(this);
        sw.setChecked(Cal.active(this));
        calRow.addView(sw);
        box.addView(calRow);
        final Dialog[] ref = new Dialog[1];
        sw.setOnCheckedChangeListener((b, on) -> {
            if (on && !Cal.hasPerm(this)) {
                ref[0].dismiss();
                requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_CAL);
                return;
            }
            Prefs.setCalOn(this, on);
            ref[0].dismiss();
            openSettings();
        });
        if (Cal.active(this)) {
            TextView pick = tv("넣을 캘린더 바꾸기", 14, 0xFF3182F6, 2);
            pick.setPadding(dp(2), dp(12), 0, 0);
            pick.setOnClickListener(v -> {
                List<Cal.Entry> all = Cal.calendars(this);
                if (all.isEmpty()) { Toast.makeText(this, "쓸 수 있는 캘린더가 없어요", Toast.LENGTH_SHORT).show(); return; }
                String[] names = new String[all.size()];
                int[] cols = new int[all.size()];
                for (int i = 0; i < names.length; i++) {
                    Cal.Entry e = all.get(i);
                    names[i] = e.name + (e.account != null && !e.account.equals(e.name) ? " (" + e.account + ")" : "");
                    cols[i] = T1;
                }
                ref[0].dismiss();
                sheetMenu("어느 캘린더에 넣을까요?", names, cols, null, w -> { Prefs.setCalId(this, all.get(w).id); openSettings(); });
            });
            box.addView(pick);
        }

        box.addView(fieldLabel("나눌 목록 (쉼표로 구분, '기타'는 항상 마지막)"));
        EditText cats = input(Prefs.catsRaw(this), Prefs.DEFAULT_CATS);
        box.addView(cats);

        box.addView(fieldLabel("AI 모델 (보통은 그대로 두세요)"));
        EditText model = input(Prefs.model(this), Prefs.DEFAULT_MODEL);
        model.setSingleLine(true);
        box.addView(model);

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
        LinearLayout.LayoutParams sl = lp(-1, dp(54));
        sl.topMargin = dp(28);
        box.addView(save, sl);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        ref[0] = sheet(sv);
        if (ref[0].getWindow() != null)
            ref[0].getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req != REQ_CAL) return;
        if (Cal.hasPerm(this)) {
            Prefs.setCalOn(this, true);
            Cal.Entry e = Cal.chosen(this);
            if (e != null) Prefs.setCalId(this, e.id);
            Toast.makeText(this, "캘린더 연결됨", Toast.LENGTH_SHORT).show();
            for (Db.Item it : Db.get(this).allOpen()) {
                if (it.due != null && it.eventId == 0) {
                    Db.Memo src = Db.get(this).memo(it.memoId);
                    long ev = Cal.addEvent(this, it, src == null ? System.currentTimeMillis() : src.created);
                    if (ev > 0) Db.get(this).setEventId(it.id, ev);
                }
            }
        } else {
            Toast.makeText(this, "캘린더 권한이 있어야 일정을 넣을 수 있어요", Toast.LENGTH_LONG).show();
        }
        refresh();
        openSettings();
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
