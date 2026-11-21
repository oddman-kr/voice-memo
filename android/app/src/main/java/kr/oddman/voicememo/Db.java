package kr.oddman.voicememo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Db extends SQLiteOpenHelper {
    private static Db inst;

    public static synchronized Db get(Context c) {
        if (inst == null) inst = new Db(c.getApplicationContext());
        return inst;
    }

    private Db(Context c) { super(c, "memo.db", null, 3); }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE memo(id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT, created INTEGER, duration INTEGER, transcript TEXT, status TEXT, error TEXT, kind TEXT DEFAULT 'memo', title TEXT, summary TEXT, actions TEXT)");
        d.execSQL("CREATE TABLE item(id INTEGER PRIMARY KEY AUTOINCREMENT, memo_id INTEGER, category TEXT, text TEXT, due TEXT, amount TEXT, person TEXT, done INTEGER DEFAULT 0, created INTEGER, event_id INTEGER DEFAULT 0, repeat TEXT, place TEXT)");
        d.execSQL("CREATE TABLE place(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, name TEXT, lat REAL, lng REAL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int o, int n) {
        if (o < 2) d.execSQL("ALTER TABLE item ADD COLUMN event_id INTEGER DEFAULT 0");
        if (o < 3) {
            d.execSQL("ALTER TABLE memo ADD COLUMN kind TEXT DEFAULT 'memo'");
            d.execSQL("ALTER TABLE memo ADD COLUMN title TEXT");
            d.execSQL("ALTER TABLE memo ADD COLUMN summary TEXT");
            d.execSQL("ALTER TABLE memo ADD COLUMN actions TEXT");
            d.execSQL("ALTER TABLE item ADD COLUMN repeat TEXT");
            d.execSQL("ALTER TABLE item ADD COLUMN place TEXT");
            d.execSQL("CREATE TABLE IF NOT EXISTS place(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT, name TEXT, lat REAL, lng REAL)");
        }
    }

    public static class Memo {
        public long id, created, duration;
        public String path, transcript, status, error, kind, title, summary, actions;

        public boolean meeting() { return "meeting".equals(kind); }
    }

    public static class Item {
        public long id, memoId, created, eventId;
        public String category, text, due, amount, person, repeat, place;
        public boolean done;
    }

    public static class Place {
        public long id;
        public String category, name;
        public double lat, lng;
    }

    private static String[] a(long v) { return new String[]{String.valueOf(v)}; }

    private static String str(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? null : c.getString(i);
    }

    private static long lng(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i < 0 || c.isNull(i) ? 0 : c.getLong(i);
    }

    // ---------- 녹음 ----------

    public long addMemo(String path, long created, long dur, String kind) {
        ContentValues v = new ContentValues();
        v.put("path", path);
        v.put("created", created);
        v.put("duration", dur);
        v.put("status", "pending");
        v.put("kind", kind);
        return getWritableDatabase().insert("memo", null, v);
    }

    public void setStatus(long id, String status, String error) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        if (error == null) v.putNull("error"); else v.put("error", error);
        getWritableDatabase().update("memo", v, "id=?", a(id));
    }

    /** 새 항목을 넣고(각 Item.id 채움) 녹음 결과를 저장 */
    public void setResult(long id, String transcript, String title, String summary, String actions, List<Item> items) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            d.delete("item", "memo_id=?", a(id));
            long now = System.currentTimeMillis();
            for (Item it : items) {
                it.memoId = id;
                it.created = now;
                it.id = d.insert("item", null, itemValues(it));
            }
            ContentValues m = new ContentValues();
            m.put("transcript", transcript);
            m.put("title", title);
            m.put("summary", summary);
            m.put("actions", actions);
            m.put("status", "done");
            m.putNull("error");
            d.update("memo", m, "id=?", a(id));
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    private static ContentValues itemValues(Item it) {
        ContentValues v = new ContentValues();
        v.put("memo_id", it.memoId);
        v.put("category", it.category);
        v.put("text", it.text);
        v.put("due", it.due);
        v.put("amount", it.amount);
        v.put("person", it.person);
        v.put("done", it.done ? 1 : 0);
        v.put("created", it.created);
        v.put("event_id", it.eventId);
        v.put("repeat", it.repeat);
        v.put("place", it.place);
        return v;
    }

    public long insertItem(Item it) {
        it.id = getWritableDatabase().insert("item", null, itemValues(it));
        return it.id;
    }

    private static Memo memoFrom(Cursor c) {
        Memo m = new Memo();
        m.id = lng(c, "id");
        m.path = str(c, "path");
        m.created = lng(c, "created");
        m.duration = lng(c, "duration");
        m.transcript = str(c, "transcript");
        m.status = str(c, "status");
        m.error = str(c, "error");
        m.kind = str(c, "kind");
        if (m.kind == null) m.kind = "memo";
        m.title = str(c, "title");
        m.summary = str(c, "summary");
        m.actions = str(c, "actions");
        return m;
    }

    private static Item itemFrom(Cursor c) {
        Item i = new Item();
        i.id = lng(c, "id");
        i.memoId = lng(c, "memo_id");
        i.category = str(c, "category");
        i.text = str(c, "text");
        i.due = str(c, "due");
        i.amount = str(c, "amount");
        i.person = str(c, "person");
        i.done = lng(c, "done") != 0;
        i.created = lng(c, "created");
        i.eventId = lng(c, "event_id");
        i.repeat = str(c, "repeat");
        i.place = str(c, "place");
        if (i.category == null) i.category = Prefs.OTHER;
        if (i.text == null) i.text = "";
        return i;
    }

    private List<Memo> memoQuery(String sql, String[] args) {
        List<Memo> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(memoFrom(c));
        }
        return out;
    }

    private List<Item> itemQuery(String sql, String[] args) {
        List<Item> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(itemFrom(c));
        }
        return out;
    }

    public List<Memo> memos() { return memoQuery("SELECT * FROM memo ORDER BY created DESC", null); }

    public Memo memo(long id) {
        List<Memo> l = memoQuery("SELECT * FROM memo WHERE id=?", a(id));
        return l.isEmpty() ? null : l.get(0);
    }

    // ---------- 항목 ----------

    public List<Item> items(String category) { return itemQuery("SELECT * FROM item WHERE category=?", new String[]{category}); }

    public List<Item> itemsOfMemo(long memoId) { return itemQuery("SELECT * FROM item WHERE memo_id=? ORDER BY id", a(memoId)); }

    public List<Item> itemsOfPerson(String person) { return itemQuery("SELECT * FROM item WHERE person=?", new String[]{person}); }

    public List<Item> allOpen() { return itemQuery("SELECT * FROM item WHERE done=0", null); }

    public Item item(long id) {
        List<Item> l = itemQuery("SELECT * FROM item WHERE id=?", a(id));
        return l.isEmpty() ? null : l.get(0);
    }

    public List<String> usedCategories() {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT category FROM item", null)) {
            while (c.moveToNext()) if (c.getString(0) != null) out.add(c.getString(0));
        }
        return out;
    }

    /** 남은 항목이 있는 사람과 개수 (많은 순) */
    public Map<String, Integer> openPeople() {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT person, COUNT(*) n FROM item WHERE done=0 AND person IS NOT NULL AND person<>'' GROUP BY person ORDER BY n DESC", null)) {
            while (c.moveToNext()) out.put(c.getString(0), c.getInt(1));
        }
        return out;
    }

    public Map<String, Integer> openCounts() {
        Map<String, Integer> out = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT category, COUNT(*) FROM item WHERE done=0 GROUP BY category", null)) {
            while (c.moveToNext()) out.put(c.getString(0), c.getInt(1));
        }
        return out;
    }

    private void upd(long id, ContentValues v) { getWritableDatabase().update("item", v, "id=?", a(id)); }

    public void setDone(long id, boolean done) {
        ContentValues v = new ContentValues();
        v.put("done", done ? 1 : 0);
        upd(id, v);
    }

    public void moveItem(long id, String category) {
        ContentValues v = new ContentValues();
        v.put("category", category);
        upd(id, v);
    }

    public void editItem(long id, String text) {
        ContentValues v = new ContentValues();
        v.put("text", text);
        upd(id, v);
    }

    public void setDue(long id, String due) {
        ContentValues v = new ContentValues();
        if (due == null) v.putNull("due"); else v.put("due", due);
        upd(id, v);
    }

    public void setEventId(long id, long eventId) {
        ContentValues v = new ContentValues();
        v.put("event_id", eventId);
        upd(id, v);
    }

    public void deleteItem(long id) { getWritableDatabase().delete("item", "id=?", a(id)); }

    public void deleteMemo(long id) {
        Memo m = memo(id);
        if (m != null && m.path != null) new File(m.path).delete();
        getWritableDatabase().delete("item", "memo_id=?", a(id));
        getWritableDatabase().delete("memo", "id=?", a(id));
    }

    // ---------- 장소 ----------

    public List<Place> places() {
        List<Place> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM place ORDER BY id", null)) {
            while (c.moveToNext()) {
                Place p = new Place();
                p.id = lng(c, "id");
                p.category = str(c, "category");
                p.name = str(c, "name");
                p.lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
                p.lng = c.getDouble(c.getColumnIndexOrThrow("lng"));
                out.add(p);
            }
        }
        return out;
    }

    public long addPlace(String category, String name, double lat, double lng) {
        ContentValues v = new ContentValues();
        v.put("category", category);
        v.put("name", name);
        v.put("lat", lat);
        v.put("lng", lng);
        return getWritableDatabase().insert("place", null, v);
    }

    public void deletePlace(long id) { getWritableDatabase().delete("place", "id=?", a(id)); }
}
