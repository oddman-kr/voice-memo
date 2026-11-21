package kr.oddman.voicememo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 녹음 화면 가운데: 소리 크기에 따라 퍼지는 원 + 남은 시간 링 + 저장 버튼 */
public class PulseView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private float level = 0f;
    private float slow = 0f;
    private boolean running = false;

    public PulseView(Context c) { super(c); }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float base = dp(46);
        float target = RecordService.level();
        level += (target - level) * (target > level ? 0.45f : 0.12f);
        slow += (level - slow) * 0.06f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x1FFF5A4E);
        c.drawCircle(cx, cy, base * (1.30f + 0.55f * slow), p);
        p.setColor(0x33FF5A4E);
        c.drawCircle(cx, cy, base * (1.12f + 0.45f * level), p);

        float ringR = base * 2.05f;
        arc.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(3));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0x26FFFFFF);
        c.drawCircle(cx, cy, ringR, p);
        if (RecordService.recording) {
            float frac = Math.min(1f, (System.currentTimeMillis() - RecordService.startedAt) / (float) RecordService.maxMs());
            p.setColor(0xCCFFFFFF);
            c.drawArc(arc, -90, 360 * frac, false, p);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF5A4E);
        c.drawCircle(cx, cy, base, p);
        p.setColor(0xFFFFFFFF);
        float s = dp(13);
        c.drawRoundRect(cx - s, cy - s, cx + s, cy + s, dp(4), dp(4), p);

        if (running) postInvalidateOnAnimation();
    }
}
