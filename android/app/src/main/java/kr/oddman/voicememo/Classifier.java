package kr.oddman.voicememo;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Classifier {
    private static final ExecutorService EX = Executors.newFixedThreadPool(3);
    private static final Set<Long> RUNNING = Collections.synchronizedSet(new HashSet<>());

    public static boolean isRunning(long id) { return RUNNING.contains(id); }

    public static void enqueue(Context c, long memoId) {
        Context app = c.getApplicationContext();
        if (!RUNNING.add(memoId)) return;
        UiBus.changed();
        EX.execute(() -> {
            try {
                work(app, memoId);
            } finally {
                RUNNING.remove(memoId);
                UiBus.changed();
            }
        });
    }

    private static void work(Context app, long id) {
        Db db = Db.get(app);
        Db.Memo m = db.memo(id);
        if (m == null) return;
        String key = Prefs.key(app);
        if (key.isEmpty()) {
            db.setStatus(id, "nokey", null);
            return;
        }
        File f = new File(m.path);
        if (!f.exists()) {
            db.setStatus(id, "error", "녹음 파일이 없어요");
            return;
        }
        try {
            // 이 녹음에서 나온 항목은 빼고, 나머지 남은 항목을 AI에게 알려줘요 (말로 완료·수정용)
            List<Db.Item> open = new ArrayList<>();
            if (!m.meeting()) {
                for (Db.Item it : db.allOpen()) if (it.memoId != id) open.add(it);
                Collections.sort(open, (a, b) -> Long.compare(b.created, a.created));
                if (open.size() > 80) open = new ArrayList<>(open.subList(0, 80));
            }
            Gemini.Result r = Gemini.analyze(key, Prefs.model(app), f, Prefs.cats(app), m.created, m.meeting(), open);

            StringBuilder done = new StringBuilder();
            for (Gemini.Action a : r.actions) {
                Db.Item it = db.item(a.itemId);
                if (it == null || it.memoId == id) continue;
                if ("complete".equals(a.type)) {
                    if (!it.done) Ops.setDone(app, it, true);
                    line(done, "완료", it.text);
                } else if ("delete".equals(a.type)) {
                    Ops.delete(app, it);
                    line(done, "삭제", it.text);
                } else if ("update".equals(a.type)) {
                    String before = it.text;
                    Ops.update(app, it, a.due, a.text);
                    line(done, "변경", before + (a.due != null ? " → " + a.due : "") + (a.text != null && !a.text.equals(before) ? " → " + a.text : ""));
                }
            }

            for (Db.Item old : db.itemsOfMemo(id)) Cal.deleteEvent(app, old.eventId);
            db.setResult(id, r.transcript, r.title, r.summary, done.length() == 0 ? null : done.toString(), r.items);
            if (Cal.active(app)) {
                for (Db.Item it : r.items) {
                    long ev = Cal.addEvent(app, it, m.created);
                    if (ev > 0) db.setEventId(it.id, ev);
                }
            }
            Places.refreshSoon(app);
        } catch (java.net.UnknownHostException | java.net.SocketTimeoutException | java.net.ConnectException e) {
            db.setStatus(id, "error", "인터넷 연결을 확인해 주세요");
        } catch (Exception e) {
            String msg = e.getMessage();
            db.setStatus(id, "error", msg == null ? e.getClass().getSimpleName() : msg);
        }
    }

    private static void line(StringBuilder sb, String kind, String text) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(kind).append(": ").append(text);
    }
}
