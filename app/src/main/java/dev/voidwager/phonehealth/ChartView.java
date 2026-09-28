package dev.voidwager.phonehealth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** Single-series time chart. Gaps longer than 45 min break the line — a gap means the logger was asleep. */
final class ChartView extends View {
    private static final long GAP_MS = 45 * 60 * 1000L;

    private final Ui ui;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint limit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long[] ts = new long[0];
    private double[] vs = new double[0];
    private long from, to;
    private double lo, hi, warnAt = Double.NaN;
    private String unit = "";

    ChartView(Context c, Ui ui) {
        super(c);
        this.ui = ui;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(ui.px(2));
        line.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(ui.line);
        grid.setStrokeWidth(ui.px(1));
        limit.setStyle(Paint.Style.STROKE);
        limit.setStrokeWidth(ui.px(1));
        limit.setColor(ui.warn);
        limit.setPathEffect(new DashPathEffect(new float[]{ui.px(4), ui.px(4)}, 0));
        label.setColor(ui.muted);
        label.setTextSize(ui.px(11));
        label.setTypeface(ui.silk);
        label.setFontFeatureSettings("tnum");
    }

    void set(List<History.Sample> samples, ToDoubleFunction<History.Sample> f, long windowMs,
             int color, String unit, double min, double max, double warnAt) {
        to = System.currentTimeMillis();
        from = to - windowMs;
        ts = new long[samples.size()];
        vs = new double[samples.size()];
        lo = min;
        hi = max;
        for (int i = 0; i < ts.length; i++) {
            ts[i] = samples.get(i).t;
            vs[i] = f.applyAsDouble(samples.get(i));
            if (!Double.isNaN(vs[i])) {
                lo = Math.min(lo, vs[i]);
                hi = Math.max(hi, vs[i]);
            }
        }
        this.unit = unit;
        this.warnAt = warnAt;
        line.setColor(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), ui.px(120));
    }

    @Override
    protected void onDraw(Canvas c) {
        float left = ui.px(36), top = ui.px(6), right = getWidth() - ui.px(4), bottom = getHeight() - ui.px(18);
        float h = bottom - top, w = right - left;
        double span = hi - lo == 0 ? 1 : hi - lo;
        for (int i = 0; i <= 2; i++) {
            float y = top + h * i / 2f;
            c.drawLine(left, y, right, y, grid);
            c.drawText(String.format("%.0f%s", hi - span * i / 2, unit), 0, y + ui.px(4), label);
        }
        long hours = (to - from) / 3_600_000L;
        c.drawText(hours >= 48 ? (hours / 24) + " d ago" : hours + " h ago", left, getHeight() - ui.px(3), label);
        c.drawText("now", right - label.measureText("now"), getHeight() - ui.px(3), label);
        if (!Double.isNaN(warnAt) && warnAt > lo && warnAt < hi) {
            float y = (float) (bottom - (warnAt - lo) / span * h);
            c.drawLine(left, y, right, y, limit);
        }
        if (ts.length == 0) {
            c.drawText("No samples yet — the logger fills this in every 15 min", left + ui.px(6), top + h / 2, label);
            return;
        }
        Path p = new Path();
        boolean pen = false;
        long prev = 0;
        for (int i = 0; i < ts.length; i++) {
            if (Double.isNaN(vs[i]) || ts[i] < from) { pen = false; continue; }
            float x = left + (float) (ts[i] - from) / (to - from) * w;
            float y = (float) (bottom - (vs[i] - lo) / span * h);
            if (!pen || ts[i] - prev > GAP_MS) p.moveTo(x, y); else p.lineTo(x, y);
            if (ts.length == 1) c.drawCircle(x, y, ui.px(3), line);
            pen = true;
            prev = ts[i];
        }
        c.drawPath(p, line);
    }
}
