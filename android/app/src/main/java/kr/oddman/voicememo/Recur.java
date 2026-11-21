package kr.oddman.voicememo;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 반복 항목: daily / weekly / monthly / yearly */
public class Recur {
    public static boolean valid(String r) {
        return "daily".equals(r) || "weekly".equals(r) || "monthly".equals(r) || "yearly".equals(r);
    }

    public static String label(String r) {
        if (r == null) return null;
        switch (r) {
            case "daily": return "매일";
            case "weekly": return "매주";
            case "monthly": return "매달";
            case "yearly": return "매년";
        }
        return null;
    }

    public static String rrule(String r) {
        if (r == null) return null;
        switch (r) {
            case "daily": return "FREQ=DAILY";
            case "weekly": return "FREQ=WEEKLY";
            case "monthly": return "FREQ=MONTHLY";
            case "yearly": return "FREQ=YEARLY";
        }
        return null;
    }

    /** 다음 기한. 오늘보다 뒤가 될 때까지 넘겨요 */
    public static String next(String due, String r) {
        if (due == null || !valid(r)) return null;
        try {
            boolean hasTime = due.length() >= 16;
            SimpleDateFormat f = new SimpleDateFormat(hasTime ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd", Locale.ROOT);
            Date d = f.parse(hasTime ? due.substring(0, 16) : due.substring(0, 10));
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            long todayStart = MainActivity.dayStart(System.currentTimeMillis());
            int guard = 0;
            do {
                switch (r) {
                    case "daily": c.add(Calendar.DAY_OF_MONTH, 1); break;
                    case "weekly": c.add(Calendar.WEEK_OF_YEAR, 1); break;
                    case "monthly": c.add(Calendar.MONTH, 1); break;
                    default: c.add(Calendar.YEAR, 1); break;
                }
            } while (c.getTimeInMillis() < todayStart && ++guard < 1000);
            return f.format(c.getTime());
        } catch (Exception e) {
            return null;
        }
    }
}
