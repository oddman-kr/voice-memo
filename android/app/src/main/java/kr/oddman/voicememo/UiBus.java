package kr.oddman.voicememo;

import android.os.Handler;
import android.os.Looper;

public class UiBus {
    public static volatile Runnable listener;
    private static final Handler H = new Handler(Looper.getMainLooper());

    public static void changed() {
        H.post(() -> {
            Runnable r = listener;
            if (r != null) r.run();
        });
    }
}
