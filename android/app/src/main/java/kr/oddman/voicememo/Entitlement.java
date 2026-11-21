package kr.oddman.voicememo;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 정리(AI 분류) 권한을 한곳에서 관리해요.
 * 지금은 폰 안에 저장하지만, 서버가 생기면 이 클래스만 서버 호출로 바꾸면 돼요.
 *  - 무료 체험: 정리 10회
 *  - 광고 이용권: 1개=1일, 3개=3일, 5개=7일 (본 개수는 기억)
 *  - 구독: 결제 연동 전이라 자리만 (subscribed 플래그)
 * 녹음 자체는 언제나 무료·무제한이에요. 여기서는 '정리'만 다뤄요.
 */
public class Entitlement {
    public static final int FREE_LIMIT = 10;
    public static final int[][] PACKS = {{1, 1}, {3, 3}, {5, 7}}; // {광고 개수, 이용 일수}
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("entitlement", Context.MODE_PRIVATE);
    }

    public static int freeUsed(Context c) { return sp(c).getInt("freeUsed", 0); }

    public static int freeLeft(Context c) { return Math.max(0, FREE_LIMIT - freeUsed(c)); }

    public static long passUntil(Context c) { return sp(c).getLong("passUntil", 0); }

    public static boolean passActive(Context c) { return passUntil(c) > System.currentTimeMillis(); }

    public static boolean subscribed(Context c) { return sp(c).getBoolean("subscribed", false); }

    /** 테스트용: 제한 없이 쓰기 (설정에서 켬) */
    public static boolean unlimited(Context c) { return sp(c).getBoolean("unlimited", false); }

    public static void setUnlimited(Context c, boolean on) { sp(c).edit().putBoolean("unlimited", on).apply(); }

    /** 무료 체험이나 이용권 없이도 정리되는 상태 */
    public static boolean openAccess(Context c) { return unlimited(c) || subscribed(c) || passActive(c); }

    public static boolean canClassify(Context c) { return openAccess(c) || freeLeft(c) > 0; }

    /** 정리 한 번을 예약해요. 반환: -1 못 함, 0 이용권·구독으로 가능, 1 무료 체험 1회 차감 */
    public static synchronized int reserve(Context c) {
        if (openAccess(c)) return 0;
        int used = freeUsed(c);
        if (used >= FREE_LIMIT) return -1;
        sp(c).edit().putInt("freeUsed", used + 1).apply();
        return 1;
    }

    /** 정리에 실패했을 때 무료 1회를 돌려줘요 */
    public static synchronized void refund(Context c, int reserved) {
        if (reserved != 1) return;
        sp(c).edit().putInt("freeUsed", Math.max(0, freeUsed(c) - 1)).apply();
    }

    // ----- 광고 이용권 -----

    public static int adProgress(Context c, int pack) { return sp(c).getInt("adProgress_" + pack, 0); }

    /** 광고 하나를 끝까지 봤어요. 다 채우면 이용권을 주고 true */
    public static synchronized boolean adWatched(Context c, int pack) {
        int need = PACKS[pack][0];
        int got = adProgress(c, pack) + 1;
        if (got >= need) {
            long base = Math.max(System.currentTimeMillis(), passUntil(c));
            sp(c).edit().putLong("passUntil", base + PACKS[pack][1] * DAY).putInt("adProgress_" + pack, 0).apply();
            return true;
        }
        sp(c).edit().putInt("adProgress_" + pack, got).apply();
        return false;
    }

    /** 결제 연동 뒤 구독 상태를 반영 */
    public static void setSubscribed(Context c, boolean on) { sp(c).edit().putBoolean("subscribed", on).apply(); }
}
