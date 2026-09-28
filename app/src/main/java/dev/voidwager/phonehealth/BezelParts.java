package dev.voidwager.phonehealth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small drawn parts of the bezel: the vent perforation strip and the navigation icons. */
final class BezelParts {
    private BezelParts() {}

    /** Hex-perforated vent strip, the bezel's one pure material band. */
    static final class Vent extends View {
        private final Ui ui;
        private final Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path hex = new Path();

        Vent(Context c, Ui ui) {
            super(c);
            this.ui = ui;
            hole.setColor(ui.vent);
            lip.setStyle(Paint.Style.STROKE);
            lip.setStrokeWidth(ui.px(1));
            lip.setColor(ui.highlight);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private final Paint lip = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), ui.px(22));
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = ui.px(3.2f), dx = r * 2.1f, dy = r * 1.85f;
            int row = 0;
            for (float y = r + ui.px(1); y < getHeight() - r / 2; y += dy, row++) {
                for (float x = (row % 2 == 0 ? r : r + dx / 2); x < getWidth() - r; x += dx) {
                    hex.reset();
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI / 3 * i + Math.PI / 6;
                        float px = x + (float) (Math.cos(a) * r), py = y + (float) (Math.sin(a) * r);
                        if (i == 0) hex.moveTo(px, py); else hex.lineTo(px, py);
                    }
                    hex.close();
                    c.drawPath(hex, hole);
                    if (ui.night) c.drawPath(hex, lip); // punched edge catches light on graphite
                }
            }
        }
    }

    /** Chevron: the bay opens to its service note. Same stroke family as the nav icons. */
    static final class Chevron extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private boolean open;

        Chevron(Context c, Ui ui, boolean open) {
            super(c);
            this.open = open;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(open ? ui.touch : ui.muted);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = getWidth() / 24f;
            c.save();
            c.scale(u, u);
            p.setStrokeWidth(2);
            path.reset();
            if (open) { path.moveTo(7, 14.5f); path.lineTo(12, 9.5f); path.lineTo(17, 14.5f); }
            else { path.moveTo(7, 9.5f); path.lineTo(12, 14.5f); path.lineTo(17, 9.5f); }
            c.drawPath(path, p);
            c.restore();
        }
    }

    /** 24 dp line icons, 2 dp stroke, round caps — one drawn family for the navigation bar. */
    static final class NavIcon extends View {
        static final int STATUS = 0, READINGS = 1, HISTORY = 2, TESTS = 3;
        private final Ui ui;
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();

        NavIcon(Context c, Ui ui, int kind) {
            super(c);
            this.ui = ui;
            this.kind = kind;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ui.px(2));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setColor(int color) {
            p.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = getWidth() / 24f;
            c.save();
            c.scale(u, u);
            p.setStrokeWidth(2);
            dot.set(p);
            switch (kind) {
                case STATUS: // the LCD: a window with two text lines
                    r.set(3, 6, 21, 18);
                    c.drawRoundRect(r, 2, 2, p);
                    c.drawLine(7, 10.5f, 17, 10.5f, p);
                    c.drawLine(7, 14, 13, 14, p);
                    break;
                case READINGS: // a gauge: arc + needle
                    r.set(3, 5, 21, 23);
                    c.drawArc(r, 200, 140, false, p);
                    c.drawLine(12, 14, 16.5f, 9, p);
                    dot.setStyle(Paint.Style.FILL);
                    c.drawCircle(12, 14, 1.6f, dot);
                    break;
                case HISTORY: // the LED matrix: 3 x 3 lamps
                    dot.setStyle(Paint.Style.FILL);
                    for (int y = 0; y < 3; y++)
                        for (int x = 0; x < 3; x++) c.drawCircle(6 + x * 6, 6 + y * 6, 1.9f, dot);
                    break;
                case TESTS: // a service procedure: clipboard with a tick
                    r.set(5, 4, 19, 21);
                    c.drawRoundRect(r, 2, 2, p);
                    c.drawLine(9, 4, 15, 4, p);
                    path.reset();
                    path.moveTo(8.5f, 12.5f);
                    path.lineTo(11, 15);
                    path.lineTo(15.5f, 10);
                    c.drawPath(path, p);
                    break;
            }
            c.restore();
        }
    }
}
