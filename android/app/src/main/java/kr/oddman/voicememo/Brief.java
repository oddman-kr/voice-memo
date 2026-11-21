package kr.oddman.voicememo;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** 아침 요약 알림 */
public class Brief {
    public static final String ACT_BRIEF = "kr.oddman.voicememo.BRIEF";

    private static PendingIntent pi(Context c) {
        Intent i = new Intent(c, Alerts.class).setAction(ACT_BRIEF);
        return PendingIntent.getBroadcast(c, 500, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent p = pi(c);
        am.cancel(p);
        if (!Prefs.briefOn(c)) return;
        int min = Prefs.briefMinutes(c);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, min / 60);
        cal.set(Calendar.MINUTE, min % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 30_000) cal.add(Calendar.DAY_OF_MONTH, 1);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), p);
    }

    public static void fire(Context c) {
        schedule(c);
        List<Db.Item> open = new ArrayList<>(Db.get(c).allOpen());
        if (open.isEmpty()) return;
        int overdue = 0, today = 0, money = 0;
        List<Db.Item> dated = new ArrayList<>();
        for (Db.Item it : open) {
            if ("송금".equals(it.category)) money++;
            if (it.due == null) continue;
            long d = daysOf(it.due);
            if (d < 0) overdue++;
            else if (d == 0) today++;
            if (d <= 1) dated.add(it);
        }
        Collections.sort(dated, (a, b) -> a.due.compareTo(b.due));

        StringBuilder head = new StringBuilder();
        if (overdue > 0) head.append("지난 것 ").append(overdue);
        if (today > 0) { if (head.length() > 0) head.append(" · "); head.append("오늘까지 ").append(today); }
        if (money > 0) { if (head.length() > 0) head.append(" · "); head.append("보낼 돈 ").append(money); }
        if (head.length() == 0) head.append("기한 급한 건 없어요");

        StringBuilder body = new StringBuilder();
        List<Db.Item> show = dated.isEmpty() ? open : dated;
        for (int i = 0; i < Math.min(5, show.size()); i++) {
            Db.Item it = show.get(i);
            if (body.length() > 0) body.append('\n');
            body.append("• ").append(it.text);
            if (it.due != null) {
                long d = daysOf(it.due);
                body.append(d < 0 ? "  (" + (-d) + "일 지남)" : d == 0 ? "  (오늘)" : d == 1 ? "  (내일)" : "");
            }
        }

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("brief", "아침 요약", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open2 = PendingIntent.getActivity(c, 501, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, "brief")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("오늘 남은 것 " + open.size() + "개 · " + head)
                .setContentText(body.toString().replace('\n', ' '))
                .setStyle(new Notification.BigTextStyle().bigText(body.toString()))
                .setContentIntent(open2)
                .setAutoCancel(true)
                .build();
        try { nm.notify(500, n); } catch (SecurityException ignored) { }
    }

    static long daysOf(String due) {
        try {
            java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).parse(due.substring(0, 10));
            return MainActivity.daysFromToday(d.getTime());
        } catch (Exception e) {
            return 99;
        }
    }
}
