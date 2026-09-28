package dev.voidwager.phonehealth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * A panel LED lens. CHECK and FAULT also carry a diagonal hatch across the lens, so the state
 * survives colour-blindness and direct sunlight; an unlit LED is a dark lens with a ring.
 */
final class LedView extends View {
    private final Ui ui;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hatch = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private Ui.S s = Ui.S.NA;

    LedView(Context c, Ui ui) {
        super(c);
        this.ui = ui;
        hatch.setStrokeWidth(ui.px(1.4f));
        hatch.setColor(0x73000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ui.px(1));
        glint.setColor(0x66FFFFFF);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // the state word beside it speaks
    }

    void set(Ui.S s) {
        this.s = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = Math.min(getWidth(), getHeight()) / 2f, cx = getWidth() / 2f, cy = getHeight() / 2f;
        boolean lit = s != Ui.S.NA;
        fill.setColor(ui.color(s));
        c.drawCircle(cx, cy, r, fill);
        if (s == Ui.S.WARN || s == Ui.S.BAD) {
            clip.reset();
            clip.addCircle(cx, cy, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            float step = r * (s == Ui.S.BAD ? 0.55f : 0.8f);
            for (float x = -2 * r; x < 2 * r; x += step)
                c.drawLine(cx + x - r, cy + r, cx + x + r, cy - r, hatch);
            c.restore();
        }
        ring.setColor(lit ? 0x40000000 : ui.bayEdge);
        c.drawCircle(cx, cy, r - ring.getStrokeWidth() / 2, ring);
        if (lit) c.drawCircle(cx - r * 0.32f, cy - r * 0.32f, r * 0.22f, glint);
    }
}
