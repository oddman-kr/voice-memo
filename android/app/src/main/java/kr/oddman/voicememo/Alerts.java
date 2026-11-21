package kr.oddman.voicememo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 장소 알림, 아침 요약, 재부팅 후 다시 등록 */
public class Alerts extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Places.ACT_NEAR.equals(a)) Places.onNear(c, i);
        else if (Brief.ACT_BRIEF.equals(a)) Brief.fire(c);
        else {
            Brief.schedule(c);
            Places.registerAll(c);
        }
    }
}
