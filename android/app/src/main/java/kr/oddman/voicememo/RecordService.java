package kr.oddman.voicememo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;

public class RecordService extends Service {
    public static final String ACT_START = "kr.oddman.voicememo.START";
    public static final String ACT_STOP = "kr.oddman.voicememo.STOP";
    public static final String ACT_CANCEL = "kr.oddman.voicememo.CANCEL";
    public static final String EXTRA_MEETING = "meeting";
    public static final long MAX_MS = 3 * 60 * 1000;
    public static final long MEETING_MAX_MS = 3 * 60 * 60 * 1000L;
    public static volatile boolean meeting = false;

    public static long maxMs() { return meeting ? MEETING_MAX_MS : MAX_MS; }

    public static volatile boolean recording = false;
    public static volatile long startedAt = 0;

    private static volatile MediaRecorder rec;

    /** 0..1 마이크 소리 크기 (녹음 화면 애니메이션용) */
    public static float level() {
        MediaRecorder r = rec;
        if (r == null || !recording) return 0f;
        try {
            int a = r.getMaxAmplitude();
            if (a <= 0) return 0f;
            double db = 20 * Math.log10(a / 32767.0);
            return (float) Math.max(0, Math.min(1, (db + 45) / 45));
        } catch (Exception e) {
            return 0f;
        }
    }
    private File file;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = () -> finishRec(true);

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String a = i == null ? null : i.getAction();
        if (ACT_START.equals(a)) start(i.getBooleanExtra(EXTRA_MEETING, false));
        else if (ACT_STOP.equals(a)) finishRec(true);
        else if (ACT_CANCEL.equals(a)) finishRec(false);
        else if (!recording) stopSelf();
        return START_NOT_STICKY;
    }

    private void toast(String s) { Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show(); }

    private PendingIntent self(String action, int req) {
        Intent i = new Intent(this, RecordService.class).setAction(action);
        return PendingIntent.getService(this, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("deprecation")
    private Notification notif() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("rec", "녹음 중 표시", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, RecordActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, "rec")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(meeting ? "회의 녹음 중" : "녹음 중")
                .setContentText(meeting ? "'저장'을 누르면 끝내고 정리해요" : "위젯을 다시 누르거나 '저장'을 누르면 저장돼요")
                .setOngoing(true)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(0, "저장", self(ACT_STOP, 1)).build())
                .addAction(new Notification.Action.Builder(0, "취소", self(ACT_CANCEL, 2)).build())
                .build();
    }

    @SuppressWarnings("deprecation")
    private void start(boolean isMeeting) {
        if (recording) return;
        meeting = isMeeting;
        try {
            Notification n = notif();
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(1, n);
        } catch (Exception e) {
            toast("녹음을 시작하지 못했어요");
            stopSelf();
            return;
        }
        try {
            File dir = new File(getFilesDir(), "memos");
            dir.mkdirs();
            file = new File(dir, (meeting ? "meeting_" : "memo_") + System.currentTimeMillis() + ".aac");
            MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            rec = r;
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioChannels(1);
            r.setAudioSamplingRate(16000);
            r.setAudioEncodingBitRate(meeting ? 24000 : 32000);
            r.setOutputFile(file.getAbsolutePath());
            r.prepare();
            r.start();
            startedAt = System.currentTimeMillis();
            recording = true;
            h.postDelayed(autoStop, maxMs());
            MemoWidget.updateAll(this);
        } catch (Exception e) {
            releaseRec();
            if (file != null) file.delete();
            toast("마이크를 쓸 수 없어요");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void releaseRec() {
        MediaRecorder r = rec;
        rec = null;
        if (r != null) {
            try { r.release(); } catch (Exception ignored) { }
        }
    }

    private void finishRec(boolean save) {
        h.removeCallbacks(autoStop);
        if (!recording) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            MemoWidget.updateAll(this);
            stopSelf();
            return;
        }
        recording = false;
        long dur = System.currentTimeMillis() - startedAt;
        boolean ok = true;
        try { rec.stop(); } catch (RuntimeException e) { ok = false; }
        releaseRec();
        stopForeground(STOP_FOREGROUND_REMOVE);
        MemoWidget.updateAll(this);
        if (save && ok && dur >= 800 && file.length() > 0) {
            long id = Db.get(this).addMemo(file.getAbsolutePath(), startedAt, dur, meeting ? "meeting" : "memo");
            toast(meeting ? "회의 녹음을 저장했어요. 정리하는 데 시간이 조금 걸려요" : "저장했어요");
            UiBus.changed();
            Classifier.enqueue(this, id);
        } else {
            file.delete();
            if (save) toast("너무 짧아서 저장하지 않았어요");
            else toast("녹음을 취소했어요");
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (recording) finishRec(true);
        super.onDestroy();
    }
}
