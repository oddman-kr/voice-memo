package kr.oddman.voicememo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    static final String TAB_REC = "__rec__";
    static final int BLUE = 0xFF2F6BFF, RED = 0xFFE5484D, GRAY = 0xFF8A8F98, TEXT = 0xFF1C1E21, BG = 0xFFF4F5F7;

    private String tab = TAB_REC;
    private LinearLayout tabBar, list;
    private TextView banner;
    private MediaPlayer player;
    private long playingMemo = -1;

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) tab = b.getString("tab", TAB_REC);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        frame.setFitsSystemWindows(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(14), dp(8), dp(4));
        head.addView(tv("음성 메모", 22, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView gear = tv("설정", 15, BLUE, false);
        gear.setPadding(dp(12), dp(8), dp(12), dp(8));
        gear.setOnClickListener(v -> openSettings());
        head.addView(gear);
        root.addView(head);

        banner = tv("", 14, 0xFF8A4B00, false);
        banner.setBackground(round(0xFFFFF1D6, dp(10)));
        banner.setPadding(dp(14), dp(10), dp(14), dp(10));
        banner.setOnClickListener(v -> openSettings());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.setMargins(dp(14), dp(4), dp(14), dp(4));
        root.addView(banner, bl);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabBar = new LinearLayout(this);
        tabBar.setPadding(dp(12), dp(6), dp(12), dp(8));
        hs.addView(tabBar);
        root.addView(hs);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(2), dp(14), dp(110));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView fab = tv("●  녹음", 18, Color.WHITE, true);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(round(RED, dp(30)));
        fab.setElevation(dp(6));
        fab.setOnClickListener(v -> startActivity(new Intent(this, RecordActivity.class)));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(160), dp(60), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fl.bottomMargin = dp(24);
        frame.addView(fab, fl);

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
        boolean noKey = Prefs.key(this).isEmpty();
        banner.setVisibility(noKey ? View.VISIBLE : View.GONE);
        banner.setText("자동 분류를 쓰려면 제미나이 API 키를 넣어 주세요. 여기를 누르면 설정이 열려요. (녹음은 키 없이도 저장돼요)");

        List<String> cats = allCats();
        if (!TAB_REC.equals(tab) && !cats.contains(tab)) tab = TAB_REC;
        Map<String, Integer> counts = Db.get(this).openCounts();
        tabBar.removeAllViews();
        addChip("녹음", TAB_REC);
        for (String c : cats) {
            Integer n = counts.get(c);
            addChip(n == null || n == 0 ? c : c + " " + n, c);
        }

        list.removeAllViews();
        if (TAB_REC.equals(tab)) buildRecordings();
        else buildCategory(tab);
    }

    private void addChip(String label, String key) {
        boolean sel = key.equals(tab);
        TextView t = tv(label, 15, sel ? Color.WHITE : TEXT, sel);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable g = round(sel ? BLUE : Color.WHITE, dp(18));
        if (!sel) g.setStroke(dp(1), 0xFFE1E3E8);
        t.setBackground(g);
        t.setOnClickListener(v -> { tab = key; stopPlayer(); refresh(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        tabBar.addView(t, lp);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(Color.WHITE, dp(14)));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private void empty(String s) {
        TextView t = tv(s, 15, GRAY, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(60), dp(20), dp(20));
        list.addView(t);
    }

    static String when(long ms) {
        return new SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREAN).format(new Date(ms));
    }

    static String dur(long ms) {
        long s = Math.max(1, Math.round(ms / 1000.0));
        return s < 60 ? s + "초" : (s / 60) + "분 " + (s % 60) + "초";
    }

    private TextView smallBtn(String s, int color, View.OnClickListener l) {
        TextView t = tv(s, 14, color, true);
        t.setPadding(dp(4), dp(10), dp(18), dp(2));
        t.setOnClickListener(l);
        return t;
    }

    private void buildRecordings() {
        List<Db.Memo> memos = Db.get(this).memos();
        if (memos.isEmpty()) {
            empty("아직 녹음이 없어요.\n아래 녹음 버튼이나 홈 화면의 '원탭 녹음' 위젯으로 시작해 보세요.");
            return;
        }
        for (Db.Memo m : memos) {
            LinearLayout c = card();
            c.addView(tv(when(m.created) + " · " + dur(m.duration), 13, GRAY, false));

            String status = null;
            int sc = GRAY;
            if (Classifier.isRunning(m.id)) { status = "AI가 정리하는 중…"; sc = BLUE; }
            else if ("pending".equals(m.status)) status = "정리 대기 중";
            else if ("nokey".equals(m.status)) status = "API 키를 넣으면 자동으로 정리돼요";
            else if ("error".equals(m.status)) { status = "정리 실패: " + m.error; sc = RED; }
            if (status != null) {
                TextView st = tv(status, 13, sc, true);
                st.setPadding(0, dp(4), 0, 0);
                c.addView(st);
            }

            if (m.transcript != null && !m.transcript.isEmpty()) {
                TextView tr = tv(m.transcript, 16, TEXT, false);
                tr.setPadding(0, dp(6), 0, 0);
                tr.setLineSpacing(0, 1.15f);
                c.addView(tr);
            }

            List<Db.Item> items = Db.get(this).itemsOfMemo(m.id);
            if (!items.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Db.Item it : items) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append("[").append(it.category).append("] ").append(it.text);
                }
                TextView s = tv(sb.toString(), 14, 0xFF4A5060, false);
                s.setPadding(dp(10), dp(8), dp(10), dp(8));
                s.setBackground(round(0xFFF0F3FA, dp(8)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(8);
                c.addView(s, lp);
            } else if ("done".equals(m.status)) {
                TextView s = tv("분류할 내용이 없었어요", 13, GRAY, false);
                s.setPadding(0, dp(4), 0, 0);
                c.addView(s);
            }

            LinearLayout row = new LinearLayout(this);
            row.addView(smallBtn(playingMemo == m.id ? "■ 정지" : "▶ 듣기", BLUE, v -> togglePlay(m)));
            if (!Classifier.isRunning(m.id)) {
                row.addView(smallBtn("다시 정리", BLUE, v -> {
                    if (Prefs.key(this).isEmpty()) { openSettings(); return; }
                    Classifier.enqueue(this, m.id);
                }));
            }
            row.addView(smallBtn("삭제", GRAY, v -> new AlertDialog.Builder(this)
                    .setMessage("이 녹음과, 여기서 나온 항목을 모두 지울까요?")
                    .setPositiveButton("삭제", (d, w) -> {
                        if (playingMemo == m.id) stopPlayer();
                        Db.get(this).deleteMemo(m.id);
                        refresh();
                    })
                    .setNegativeButton("취소", null).show()));
            c.addView(row);
            list.addView(c);
        }
    }

    private void buildCategory(String cat) {
        List<Db.Item> items = Db.get(this).items(cat);
        if (items.isEmpty()) {
            empty("'" + cat + "' 탭에 아직 항목이 없어요.");
            return;
        }
        Collections.sort(items, (x, y) -> {
            if (x.done != y.done) return x.done ? 1 : -1;
            if (x.due != null && y.due != null && !x.due.equals(y.due)) return x.due.compareTo(y.due);
            if (x.due != null && y.due == null) return -1;
            if (x.due == null && y.due != null) return 1;
            return Long.compare(y.created, x.created);
        });
        for (Db.Item it : items) {
            LinearLayout c = card();
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPadding(dp(8), dp(10), dp(14), dp(10));

            CheckBox cb = new CheckBox(this);
            cb.setChecked(it.done);
            cb.setOnCheckedChangeListener((b, on) -> { Db.get(this).setDone(it.id, on); refresh(); });
            c.addView(cb);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t = tv(it.text, 16, it.done ? GRAY : TEXT, false);
            if (it.done) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            col.addView(t);

            List<String> meta = new ArrayList<>();
            if (it.due != null) meta.add("~" + it.due);
            if (it.amount != null) meta.add(it.amount);
            if (it.person != null) meta.add(it.person);
            Db.Memo src = Db.get(this).memo(it.memoId);
            if (src != null) meta.add(when(src.created) + " 녹음");
            if (!meta.isEmpty()) {
                TextView mt = tv(String.join(" · ", meta), 13, it.due != null && !it.done ? BLUE : GRAY, false);
                mt.setPadding(0, dp(2), 0, 0);
                col.addView(mt);
            }
            c.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
            c.setOnClickListener(v -> itemMenu(it, src));
            list.addView(c);
        }
    }

    private void itemMenu(Db.Item it, Db.Memo src) {
        String[] opts = {src != null && playingMemo == src.id ? "듣기 멈추기" : "원래 녹음 듣기", "다른 탭으로 옮기기", "내용 고치기", "삭제"};
        new AlertDialog.Builder(this).setTitle(it.text).setItems(opts, (d, w) -> {
            if (w == 0) {
                if (src != null) togglePlay(src);
            } else if (w == 1) {
                List<String> cats = allCats();
                cats.remove(it.category);
                String[] arr = cats.toArray(new String[0]);
                new AlertDialog.Builder(this).setTitle("어느 탭으로 옮길까요?").setItems(arr, (d2, w2) -> {
                    Db.get(this).moveItem(it.id, arr[w2]);
                    refresh();
                }).show();
            } else if (w == 2) {
                EditText e = new EditText(this);
                e.setText(it.text);
                e.setSelection(e.getText().length());
                FrameLayout box = new FrameLayout(this);
                box.setPadding(dp(20), dp(8), dp(20), 0);
                box.addView(e);
                new AlertDialog.Builder(this).setTitle("내용 고치기").setView(box)
                        .setPositiveButton("저장", (d2, w2) -> {
                            String s = e.getText().toString().trim();
                            if (!s.isEmpty()) Db.get(this).editItem(it.id, s);
                            refresh();
                        }).setNegativeButton("취소", null).show();
            } else {
                Db.get(this).deleteItem(it.id);
                refresh();
            }
        }).show();
    }

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

    private EditText field(LinearLayout box, String label, String value, String hint) {
        TextView l = tv(label, 13, GRAY, true);
        l.setPadding(0, dp(12), 0, 0);
        box.addView(l);
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(15);
        box.addView(e);
        return e;
    }

    private void openSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), 0);

        EditText key = field(box, "제미나이 API 키", Prefs.key(this), "AIza로 시작하는 키");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        key.setSingleLine(true);
        TextView link = tv("키 받으러 가기 (aistudio.google.com) →", 13, BLUE, false);
        link.setPadding(0, dp(2), 0, dp(4));
        link.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))));
        box.addView(link);

        EditText cats = field(box, "탭 목록 (쉼표로 구분)", Prefs.catsRaw(this), Prefs.DEFAULT_CATS);
        TextView ch = tv("'기타' 탭은 항상 마지막에 붙어요", 12, GRAY, false);
        box.addView(ch);

        EditText model = field(box, "AI 모델 (보통은 그대로 두세요)", Prefs.model(this), Prefs.DEFAULT_MODEL);
        model.setSingleLine(true);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this).setTitle("설정").setView(sv)
                .setPositiveButton("저장", (d, w) -> {
                    String c = cats.getText().toString().trim();
                    Prefs.save(this, key.getText().toString(), c.isEmpty() ? Prefs.DEFAULT_CATS : c, model.getText().toString());
                    if (!Prefs.key(this).isEmpty()) {
                        for (Db.Memo m : Db.get(this).memos())
                            if (!"done".equals(m.status)) Classifier.enqueue(this, m.id);
                    }
                    refresh();
                })
                .setNegativeButton("취소", null).show();
    }
}
