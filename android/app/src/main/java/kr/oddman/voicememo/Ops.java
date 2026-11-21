package kr.oddman.voicememo;

import android.content.Context;

/** 항목 처리 (반복·캘린더까지 같이 챙김) */
public class Ops {
    public static void setDone(Context c, Db.Item it, boolean done) {
        Db db = Db.get(c);
        if (done && !it.done && it.repeat != null) {
            String next = Recur.next(it.due, it.repeat);
            if (next != null) {
                Db.Item n = new Db.Item();
                n.memoId = it.memoId;
                n.category = it.category;
                n.text = it.text;
                n.due = next;
                n.amount = it.amount;
                n.person = it.person;
                n.repeat = it.repeat;
                n.place = it.place;
                n.eventId = it.eventId;
                n.created = System.currentTimeMillis();
                db.insertItem(n);
                db.setEventId(it.id, 0);
            }
        }
        db.setDone(it.id, done);
        Places.refreshSoon(c);
    }

    public static void delete(Context c, Db.Item it) {
        Cal.deleteEvent(c, it.eventId);
        Db.get(c).deleteItem(it.id);
    }

    public static void update(Context c, Db.Item it, String due, String text) {
        Db db = Db.get(c);
        if (text != null && !text.isEmpty()) { db.editItem(it.id, text); it.text = text; }
        if (due != null) { db.setDue(it.id, due); it.due = due; }
        if (it.eventId > 0 || (due != null && Cal.active(c))) {
            Cal.deleteEvent(c, it.eventId);
            long ev = Cal.addEvent(c, it, System.currentTimeMillis());
            db.setEventId(it.id, ev);
            it.eventId = ev;
        }
    }
}
