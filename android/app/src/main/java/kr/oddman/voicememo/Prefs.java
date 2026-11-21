package kr.oddman.voicememo;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class Prefs {
    public static final String DEFAULT_MODEL = "gemini-flash-latest";
    public static final String FALLBACK_MODEL = "gemini-2.5-flash";
    public static final String DEFAULT_CATS = "할 일, 송금, 쿠팡, 다이소, 기타";
    public static final String OTHER = "기타";

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    public static String key(Context c) { return sp(c).getString("key", "").trim(); }

    public static String model(Context c) {
        String m = sp(c).getString("model", "").trim();
        return m.isEmpty() ? DEFAULT_MODEL : m;
    }

    public static String catsRaw(Context c) { return sp(c).getString("cats", DEFAULT_CATS); }

    public static List<String> cats(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : catsRaw(c).split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        out.remove(OTHER);
        out.add(OTHER);
        return out;
    }

    public static boolean calOn(Context c) { return sp(c).getBoolean("calOn", false); }

    public static void setCalOn(Context c, boolean on) { sp(c).edit().putBoolean("calOn", on).apply(); }

    public static long calId(Context c) { return sp(c).getLong("calId", -1); }

    public static void setCalId(Context c, long id) { sp(c).edit().putLong("calId", id).apply(); }

    public static void save(Context c, String key, String cats, String model) {
        sp(c).edit().putString("key", key.trim()).putString("cats", cats).putString("model", model.trim()).apply();
    }
}
