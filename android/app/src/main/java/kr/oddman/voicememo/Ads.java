package kr.oddman.voicememo;

import android.app.Activity;
import android.content.Context;


import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/** AdMob 보상형 광고. 지금은 구글 테스트 광고 ID를 써요 (출시 전 실제 ID로 교체). */
public class Ads {
    /** 구글이 공개한 보상형 테스트 광고 단위 */
    static final String REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917";
    private static boolean inited = false;

    public interface Result {
        /** rewarded: 끝까지 봐서 보상을 받았는지. failed: 광고를 불러오지 못했는지 */
        void done(boolean rewarded, boolean failed);
    }

    public static void init(Context c) {
        if (inited) return;
        inited = true;
        try { MobileAds.initialize(c.getApplicationContext(), status -> { }); } catch (Throwable ignored) { }
    }

    /** 광고 하나를 불러와서 보여줘요. 결과는 화면 스레드에서 한 번만 와요 */
    public static void showOne(Activity a, Result r) {
        init(a);
        final boolean[] fired = {false};
        final boolean[] rewarded = {false};
        Result once = (rw, failed) -> { if (!fired[0]) { fired[0] = true; r.done(rw, failed); } };
        try {
            RewardedAd.load(a, REWARDED_UNIT, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(RewardedAd ad) {
                    if (a.isFinishing() || a.isDestroyed()) { once.done(false, true); return; }
                    ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                        @Override public void onAdDismissedFullScreenContent() { once.done(rewarded[0], false); }
                        @Override public void onAdFailedToShowFullScreenContent(AdError e) { once.done(false, true); }
                    });
                    ad.show(a, item -> rewarded[0] = true);
                }

                @Override
                public void onAdFailedToLoad(LoadAdError e) { once.done(false, true); }
            });
        } catch (Throwable t) {
            once.done(false, true);
        }
    }
}
