package kr.oddman.voicememo;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class Prefs {
    /** 짧은 메모: 가장 빠른 Flash-Lite (생각 단계 최소) */
    public static final String DEFAULT_MODEL = "gemini-flash-lite-latest";
    /** 회의 요약: 조금 더 똑똑한 Flash */
    public static final String MEETING_MODEL = "gemini-flash-latest";
    /** 모델 이름이 없을 때 차례로 시도 */
    public static final String[] FALLBACKS = {"gemini-3.5-flash-lite", "gemini-2.5-flash-lite", "gemini-2.5-flash"};
    public static final String FALLBACK_MODEL = "gemini-2.5-flash";
    public static final String DEFAULT_CATS = "할 일, 송금, 쿠팡, 다이소, 기타";
    public static final String OTHER = "기타";

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    public static String key(Context c) { return sp(c).getString("key", "").trim(); }

    public static String model(Context c) {
        String m = sp(c).getString("model", "").trim();
        // 예전 기본값(느린 모델)으로 저장된 경우 새 기본값으로
        return m.isEmpty() || m.equals("gemini-flash-latest") ? DEFAULT_MODEL : m;
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

    public static boolean briefOn(Context c) { return sp(c).getBoolean("briefOn", true); }

    public static int briefMinutes(Context c) { return sp(c).getInt("briefMin", 8 * 60 + 30); }

    public static void setBrief(Context c, boolean on, int minutes) {
        sp(c).edit().putBoolean("briefOn", on).putInt("briefMin", minutes).apply();
    }

    public static boolean calOn(Context c) { return sp(c).getBoolean("calOn", false); }

    public static void setCalOn(Context c, boolean on) { sp(c).edit().putBoolean("calOn", on).apply(); }

    public static long calId(Context c) { return sp(c).getLong("calId", -1); }

    public static void setCalId(Context c, long id) { sp(c).edit().putLong("calId", id).apply(); }

    public static void save(Context c, String key, String cats, String model) {
        sp(c).edit().putString("key", key.trim()).putString("cats", cats).putString("model", model.trim()).apply();
    }
}
