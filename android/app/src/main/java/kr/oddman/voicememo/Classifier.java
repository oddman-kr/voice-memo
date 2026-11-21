package kr.oddman.voicememo;

import android.content.Context;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Classifier {
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
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
            Gemini.Result r = Gemini.classify(key, Prefs.model(app), f, Prefs.cats(app), m.created);
            db.setResult(id, r.transcript, r.items);
        } catch (java.net.UnknownHostException | java.net.SocketTimeoutException | java.net.ConnectException e) {
            db.setStatus(id, "error", "인터넷 연결을 확인해 주세요");
        } catch (Exception e) {
            String msg = e.getMessage();
            db.setStatus(id, "error", msg == null ? e.getClass().getSimpleName() : msg);
        }
    }
}
