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

    private Db(Context c) { super(c, "memo.db", null, 2); }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE memo(id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT, created INTEGER, duration INTEGER, transcript TEXT, status TEXT, error TEXT)");
        d.execSQL("CREATE TABLE item(id INTEGER PRIMARY KEY AUTOINCREMENT, memo_id INTEGER, category TEXT, text TEXT, due TEXT, amount TEXT, person TEXT, done INTEGER DEFAULT 0, created INTEGER, event_id INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int o, int n) {
        if (o < 2) d.execSQL("ALTER TABLE item ADD COLUMN event_id INTEGER DEFAULT 0");
    }

    public static class Memo {
        public long id, created, duration;
        public String path, transcript, status, error;
    }

    public static class Item {
        public long id, memoId, created, eventId;
        public String category, text, due, amount, person;
        public boolean done;
    }

    private static String[] a(long v) { return new String[]{String.valueOf(v)}; }

    public long addMemo(String path, long created, long dur) {
        ContentValues v = new ContentValues();
        v.put("path", path);
        v.put("created", created);
        v.put("duration", dur);
        v.put("status", "pending");
        return getWritableDatabase().insert("memo", null, v);
    }

    public void setStatus(long id, String status, String error) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        if (error == null) v.putNull("error"); else v.put("error", error);
        getWritableDatabase().update("memo", v, "id=?", a(id));
    }

    public void setResult(long id, String transcript, List<Item> items) {
        SQLiteDatabase d = getWritableDatabase();
        d.beginTransaction();
        try {
            d.delete("item", "memo_id=?", a(id));
            long now = System.currentTimeMillis();
            for (Item it : items) {
                ContentValues v = new ContentValues();
                v.put("memo_id", id);
                v.put("category", it.category);
                v.put("text", it.text);
                v.put("due", it.due);
                v.put("amount", it.amount);
                v.put("person", it.person);
                v.put("done", 0);
                v.put("created", now);
                it.id = d.insert("item", null, v);
                it.memoId = id;
            }
            ContentValues m = new ContentValues();
            m.put("transcript", transcript);
            m.put("status", "done");
            m.putNull("error");
            d.update("memo", m, "id=?", a(id));
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
    }

    private static Memo memoFrom(Cursor c) {
        Memo m = new Memo();
        m.id = c.getLong(c.getColumnIndexOrThrow("id"));
        m.path = c.getString(c.getColumnIndexOrThrow("path"));
        m.created = c.getLong(c.getColumnIndexOrThrow("created"));
        m.duration = c.getLong(c.getColumnIndexOrThrow("duration"));
        m.transcript = c.getString(c.getColumnIndexOrThrow("transcript"));
        m.status = c.getString(c.getColumnIndexOrThrow("status"));
        m.error = c.getString(c.getColumnIndexOrThrow("error"));
        return m;
    }

    private static Item itemFrom(Cursor c) {
        Item i = new Item();
        i.id = c.getLong(c.getColumnIndexOrThrow("id"));
        i.memoId = c.getLong(c.getColumnIndexOrThrow("memo_id"));
        i.category = c.getString(c.getColumnIndexOrThrow("category"));
        i.text = c.getString(c.getColumnIndexOrThrow("text"));
        i.due = c.getString(c.getColumnIndexOrThrow("due"));
        i.amount = c.getString(c.getColumnIndexOrThrow("amount"));
        i.person = c.getString(c.getColumnIndexOrThrow("person"));
        i.done = c.getInt(c.getColumnIndexOrThrow("done")) != 0;
        i.created = c.getLong(c.getColumnIndexOrThrow("created"));
        int ei = c.getColumnIndex("event_id");
        i.eventId = ei >= 0 && !c.isNull(ei) ? c.getLong(ei) : 0;
        return i;
    }

    public List<Memo> memos() {
        List<Memo> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM memo ORDER BY created DESC", null)) {
            while (c.moveToNext()) out.add(memoFrom(c));
        }
        return out;
    }

    public Memo memo(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM memo WHERE id=?", a(id))) {
            return c.moveToFirst() ? memoFrom(c) : null;
        }
    }

    public List<Item> items(String category) {
        List<Item> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM item WHERE category=?", new String[]{category})) {
            while (c.moveToNext()) out.add(itemFrom(c));
        }
        return out;
    }

    public List<Item> itemsOfMemo(long memoId) {
        List<Item> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM item WHERE memo_id=? ORDER BY id", a(memoId))) {
            while (c.moveToNext()) out.add(itemFrom(c));
        }
        return out;
    }

    public List<String> usedCategories() {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT category FROM item", null)) {
            while (c.moveToNext()) if (c.getString(0) != null) out.add(c.getString(0));
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

    public void setDone(long id, boolean done) {
        ContentValues v = new ContentValues();
        v.put("done", done ? 1 : 0);
        getWritableDatabase().update("item", v, "id=?", a(id));
    }

    public void moveItem(long id, String category) {
        ContentValues v = new ContentValues();
        v.put("category", category);
        getWritableDatabase().update("item", v, "id=?", a(id));
    }

    public void editItem(long id, String text) {
        ContentValues v = new ContentValues();
        v.put("text", text);
        getWritableDatabase().update("item", v, "id=?", a(id));
    }

    public void setEventId(long id, long eventId) {
        ContentValues v = new ContentValues();
        v.put("event_id", eventId);
        getWritableDatabase().update("item", v, "id=?", a(id));
    }

    public Item item(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM item WHERE id=?", a(id))) {
            return c.moveToFirst() ? itemFrom(c) : null;
        }
    }

    public List<Item> allOpen() {
        List<Item> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM item WHERE done=0", null)) {
            while (c.moveToNext()) out.add(itemFrom(c));
        }
        return out;
    }

    public void deleteItem(long id) { getWritableDatabase().delete("item", "id=?", a(id)); }

    public void deleteMemo(long id) {
        Memo m = memo(id);
        if (m != null && m.path != null) new File(m.path).delete();
        getWritableDatabase().delete("item", "memo_id=?", a(id));
        getWritableDatabase().delete("memo", "id=?", a(id));
    }
}
