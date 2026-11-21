package kr.oddman.voicememo;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 폰 캘린더(구글 캘린더와 동기화)에 일정 넣기 */
public class Cal {
    public static class Entry {
        public long id;
        public String name, account;
        public boolean google, primary;
    }

    public static boolean hasPerm(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean active(Context c) { return Prefs.calOn(c) && hasPerm(c); }

    public static List<Entry> calendars(Context c) {
        List<Entry> out = new ArrayList<>();
        if (!hasPerm(c)) return out;
        String[] proj = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE};
        try (Cursor cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                if (cur.getInt(5) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue;
                Entry e = new Entry();
                e.id = cur.getLong(0);
                e.name = cur.getString(1);
                e.account = cur.getString(2);
                e.google = "com.google".equals(cur.getString(3));
                e.primary = cur.getInt(4) == 1;
                if (e.name == null) e.name = e.account;
                out.add(e);
            }
        } catch (Exception ignored) { }
        // 구글 기본 캘린더가 맨 앞
        out.sort((a, b) -> score(b) - score(a));
        return out;
    }

    private static int score(Entry e) { return (e.google ? 2 : 0) + (e.primary ? 1 : 0); }

    public static Entry chosen(Context c) {
        List<Entry> all = calendars(c);
        long want = Prefs.calId(c);
        for (Entry e : all) if (e.id == want) return e;
        return all.isEmpty() ? null : all.get(0);
    }

    /** 기한이 있는 항목을 일정으로 넣고 일정 id를 돌려줘요. 실패하면 0 */
    public static long addEvent(Context c, Db.Item it, long recordedAt) {
        if (it.due == null || !active(c)) return 0;
        Entry cal = chosen(c);
        if (cal == null) return 0;
        try {
            boolean hasTime = it.due.length() >= 16;
            ContentValues v = new ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, cal.id);
            v.put(CalendarContract.Events.TITLE, it.text);
            StringBuilder desc = new StringBuilder("음성 메모에서 추가 (").append(it.category).append(")");
            if (it.amount != null) desc.append("\n금액: ").append(it.amount);
            if (it.person != null) desc.append("\n사람: ").append(it.person);
            v.put(CalendarContract.Events.DESCRIPTION, desc.toString());
            String rrule = Recur.rrule(it.repeat);
            if (it.place != null) v.put(CalendarContract.Events.EVENT_LOCATION, it.place);
            if (hasTime) {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
                long start = f.parse(it.due.substring(0, 16)).getTime();
                v.put(CalendarContract.Events.DTSTART, start);
                if (rrule != null) v.put(CalendarContract.Events.DURATION, "PT30M");
                else v.put(CalendarContract.Events.DTEND, start + 30 * 60 * 1000L);
                v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
                v.put(CalendarContract.Events.HAS_ALARM, 1);
            } else {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = f.parse(it.due.substring(0, 10));
                v.put(CalendarContract.Events.DTSTART, d.getTime());
                if (rrule != null) v.put(CalendarContract.Events.DURATION, "P1D");
                else v.put(CalendarContract.Events.DTEND, d.getTime() + 24 * 60 * 60 * 1000L);
                v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
                v.put(CalendarContract.Events.ALL_DAY, 1);
            }
            if (rrule != null) v.put(CalendarContract.Events.RRULE, rrule);
            ContentResolver cr = c.getContentResolver();
            Uri u = cr.insert(CalendarContract.Events.CONTENT_URI, v);
            if (u == null) return 0;
            long eventId = ContentUris.parseId(u);
            if (hasTime) {
                ContentValues r = new ContentValues();
                r.put(CalendarContract.Reminders.EVENT_ID, eventId);
                r.put(CalendarContract.Reminders.MINUTES, 60);
                r.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                cr.insert(CalendarContract.Reminders.CONTENT_URI, r);
            }
            return eventId;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void deleteEvent(Context c, long eventId) {
        if (eventId <= 0 || !hasPerm(c)) return;
        try {
            c.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null);
        } catch (Exception ignored) { }
    }

    public static Uri eventUri(long eventId) {
        return ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
    }
}
