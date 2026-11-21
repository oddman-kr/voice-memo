package kr.oddman.voicememo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import java.util.List;
import java.util.function.Consumer;

/** 장소 알림: 등록한 곳 근처에 가면 그 카테고리의 남은 항목을 알려줘요 */
public class Places {
    public static final String ACT_NEAR = "kr.oddman.voicememo.NEAR";
    static final float RADIUS_M = 150f;

    public static boolean hasFine(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasBackground(Context c) {
        if (Build.VERSION.SDK_INT < 29) return hasFine(c);
        return c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static PendingIntent pi(Context c, long placeId) {
        Intent i = new Intent(c, Alerts.class).setAction(ACT_NEAR).putExtra("place", placeId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(c, (int) (1000 + placeId), i, flags);
    }

    public static void registerAll(Context c) {
        if (!hasFine(c)) return;
        LocationManager lm = c.getSystemService(LocationManager.class);
        if (lm == null) return;
        for (Db.Place p : Db.get(c).places()) {
            try {
                PendingIntent pi = pi(c, p.id);
                lm.removeProximityAlert(pi);
                lm.addProximityAlert(p.lat, p.lng, RADIUS_M, -1, pi);
            } catch (Exception ignored) { }
        }
    }

    public static void unregister(Context c, long placeId) {
        LocationManager lm = c.getSystemService(LocationManager.class);
        if (lm == null) return;
        try { lm.removeProximityAlert(pi(c, placeId)); } catch (Exception ignored) { }
    }

    /** 항목이 바뀐 뒤 호출 (지금은 알림 등록이 항목과 무관해서 할 일 없음) */
    public static void refreshSoon(Context c) { }

    public static void onNear(Context c, Intent i) {
        if (!i.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return;
        long id = i.getLongExtra("place", 0);
        Db.Place place = null;
        for (Db.Place p : Db.get(c).places()) if (p.id == id) place = p;
        if (place == null) return;
        List<Db.Item> items = Db.get(c).items(place.category);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Db.Item it : items) {
            if (it.done) continue;
            if (n < 4) { if (sb.length() > 0) sb.append(", "); sb.append(it.text); }
            n++;
        }
        if (n == 0) return;
        if (n > 4) sb.append(" 외 ").append(n - 4).append("개");
        // 같은 곳에서는 3시간에 한 번만
        String k = "near_" + id;
        long last = Prefs.sp(c).getLong(k, 0);
        if (System.currentTimeMillis() - last < 3 * 3600_000L) return;
        Prefs.sp(c).edit().putLong(k, System.currentTimeMillis()).apply();

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("near", "장소 알림", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(c, MainActivity.class).putExtra("cat", place.category).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, (int) (3000 + id), open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String what = "송금".equals(place.category) ? "보낼 돈" : "할 일".equals(place.category) ? "할 일" : "살 것";
        Notification nt = new Notification.Builder(c, "near")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(place.name + " 근처예요 · " + what + " " + n + "개")
                .setContentText(sb.toString())
                .setStyle(new Notification.BigTextStyle().bigText(sb.toString()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try { nm.notify((int) (3000 + id), nt); } catch (SecurityException ignored) { }
    }

    /** 지금 위치 한 번 받기 (실패하면 null) */
    @SuppressWarnings({"deprecation", "MissingPermission"})
    public static void current(Context c, Consumer<Location> cb) {
        LocationManager lm = c.getSystemService(LocationManager.class);
        if (lm == null || !hasFine(c)) { cb.accept(null); return; }
        String prov = null;
        if (Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER) && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) prov = LocationManager.FUSED_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) prov = LocationManager.NETWORK_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) prov = LocationManager.GPS_PROVIDER;
        if (prov == null) { cb.accept(null); return; }
        final String provider = prov;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, c.getMainExecutor(), loc -> cb.accept(loc != null ? loc : last(lm)));
            } else {
                lm.requestSingleUpdate(provider, new LocationListener() {
                    @Override public void onLocationChanged(Location l) { cb.accept(l); }
                    @Override public void onStatusChanged(String p, int s, Bundle b) { }
                    @Override public void onProviderEnabled(String p) { }
                    @Override public void onProviderDisabled(String p) { }
                }, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            cb.accept(null);
        }
    }

    @SuppressWarnings("MissingPermission")
    private static Location last(LocationManager lm) {
        Location best = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (SecurityException ignored) { }
        }
        return best;
    }
}
