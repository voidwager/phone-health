package dev.voidwager.phonehealth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity panel: one LED row per day for the last 7 days, one LED per hour.
 * Green = sampled and every service up. Amber = hot (≥40 °C) or some services down.
 * Red = every service down. Dark = no sample that hour — the logger was asleep.
 * Hours that haven't happened yet show no lens at all.
 */
final class LedMatrixView extends View {
    private static final int DAYS = 7, HOURS = 24;

    private final Ui ui;
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG), hatch = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Ui.S[][] cells = new Ui.S[DAYS][HOURS];
    private final boolean[][] future = new boolean[DAYS][HOURS];
    private final String[] dayNames = new String[DAYS];

    LedMatrixView(Context c, Ui ui) {
        super(c);
        this.ui = ui;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ui.px(1));
        ring.setColor(ui.bayEdge);
        label.setTypeface(ui.silk);
        label.setLetterSpacing(0.08f);
        label.setTextSize(ui.px(10) * c.getResources().getConfiguration().fontScale);
        label.setColor(ui.muted);
        hatch.setColor(0x66000000);
        hatch.setStrokeWidth(ui.px(1));
    }

    void set(List<History.Sample> samples) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int nowHour = cal.get(Calendar.HOUR_OF_DAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        long today0 = cal.getTimeInMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("EEE", Locale.getDefault());
        int seen = 0, ok = 0;
        for (int d = 0; d < DAYS; d++) {
            long day0 = today0 - (long) (DAYS - 1 - d) * 24 * 3600_000L;
            dayNames[d] = d == DAYS - 1 ? "TODAY" : fmt.format(new Date(day0)).toUpperCase(Locale.getDefault());
            for (int h = 0; h < HOURS; h++) {
                cells[d][h] = null;
                future[d][h] = d == DAYS - 1 && h > nowHour;
            }
        }
        for (History.Sample s : samples) {
            long rel = s.t - (today0 - (long) (DAYS - 1) * 24 * 3600_000L);
            if (rel < 0) continue;
            int d = (int) (rel / (24 * 3600_000L)), h = (int) (rel % (24 * 3600_000L) / 3600_000L);
            if (d >= DAYS) continue;
            Ui.S st;
            if (s.portsTotal > 0 && s.portsUp == 0) st = Ui.S.BAD;
            else if (s.temp >= 40 || s.portsUp < s.portsTotal) st = Ui.S.WARN;
            else st = Ui.S.GOOD;
            cells[d][h] = cells[d][h] == null ? st : Ui.worst(cells[d][h], st);
        }
        for (int d = 0; d < DAYS; d++)
            for (int h = 0; h < HOURS; h++) {
                if (future[d][h]) continue;
                seen++;
                if (cells[d][h] == Ui.S.GOOD) ok++;
            }
        setContentDescription("Last 7 days: " + ok + " of " + seen + " hours sampled with every service up.");
        invalidate();
    }

    private float labelW() { return label.measureText("TODAY") + ui.px(10); }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        float pitch = (width - labelW()) / HOURS;
        setMeasuredDimension(width, Math.round(ui.px(18) + pitch * DAYS));
    }

    @Override
    protected void onDraw(Canvas c) {
        float lw = labelW(), pitch = (getWidth() - lw) / HOURS, r = pitch * 0.34f, top = ui.px(18);
        for (int h = 0; h < HOURS; h += 6) {
            String t = String.format(Locale.ROOT, "%02d", h);
            c.drawText(t, lw + h * pitch + pitch / 2 - label.measureText(t) / 2, ui.px(11), label);
        }
        for (int d = 0; d < DAYS; d++) {
            float cy = top + d * pitch + pitch / 2;
            label.setColor(d == DAYS - 1 ? ui.ink : ui.muted);
            c.drawText(dayNames[d], 0, cy + label.getTextSize() * 0.35f, label);
            for (int h = 0; h < HOURS; h++) {
                if (future[d][h]) continue;
                float cx = lw + h * pitch + pitch / 2;
                Ui.S s = cells[d][h];
                lens.setColor(s == null ? ui.ledOff : ui.color(s));
                lens.setAlpha(s == null ? 110 : 255);
                c.drawCircle(cx, cy, r, lens);
                if (s == Ui.S.WARN || s == Ui.S.BAD) { // hatched like the bay LEDs: state is never colour alone
                    c.drawLine(cx - r * 0.7f, cy + r * 0.7f, cx + r * 0.7f, cy - r * 0.7f, hatch);
                    if (s == Ui.S.BAD) c.drawLine(cx - r * 0.2f, cy + r * 0.95f, cx + r * 0.95f, cy - r * 0.2f, hatch);
                }
                if (s == null) c.drawCircle(cx, cy, r, ring);
            }
        }
        label.setColor(ui.muted);
    }
}
