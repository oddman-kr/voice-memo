package kr.oddman.voicememo;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class MemoWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { updateAll(c); }

    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, MemoWidget.class));
        if (ids == null || ids.length == 0) return;
        boolean r = RecordService.recording;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setImageViewResource(R.id.w_icon, r ? R.drawable.ic_stop : R.drawable.ic_mic);
        v.setInt(R.id.w_icon, "setBackgroundResource", r ? R.drawable.widget_bg_rec : R.drawable.widget_bg);
        v.setTextViewText(R.id.w_label, r ? "저장" : "녹음");
        Intent i = new Intent(c, RecordActivity.class)
                .putExtra(RecordActivity.EXTRA_TOGGLE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 10, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.w_root, pi);
        m.updateAppWidget(ids, v);
    }
}
