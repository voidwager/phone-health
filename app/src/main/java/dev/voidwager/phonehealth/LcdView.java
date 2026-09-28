package dev.voidwager.phonehealth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Two-line, 20-column character LCD like the front panel of a rack server. Drawn dot by dot from
 * an HD44780-style 5x7 font, so the glyphs are the real thing rather than a font imitating them.
 * Blue backlight = healthy; amber backlight with dark pixels = something needs you.
 */
final class LcdView extends View {
    static final int COLS = 20;

    private final Ui ui;
    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG), frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private String l1 = "", l2 = "";
    private boolean amber;

    LcdView(Context c, Ui ui) {
        super(c);
        this.ui = ui;
        frame.setColor(ui.lcdFrame);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(ui.px(1));
        rim.setColor(ui.highlight);
    }

    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int invert;

    /** Draw the first n cells of line 1 in inverse video (lit field, dark glyph). */
    void invertPrefix(int n) {
        invert = n;
        invalidate();
    }

    void set(String line1, String line2, boolean amber) {
        this.l1 = fit(line1);
        this.l2 = fit(line2);
        this.amber = amber;
        setContentDescription(line1 + ". " + line2);
        invalidate();
    }

    private static String fit(String s) {
        s = s.toUpperCase(java.util.Locale.ROOT);
        if (s.length() <= COLS) return s;
        int cut = s.lastIndexOf(' ', COLS); // break at a word, like a panel message would
        return (cut > COLS / 2 ? s.substring(0, cut) : s.substring(0, COLS)).replaceAll("[ ·]+$", "");
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        float pitch = pitch(width);
        int height = Math.round(pitch * (2 * 7 + 3) + 2 * inset() + 2 * ui.px(10));
        setMeasuredDimension(width, height);
    }

    private float inset() { return ui.px(6); }

    /** Dot pitch so 20 cells of 5 dots + 1 gap fill the glass. */
    private float pitch(int width) { return (width - 2 * inset() - 2 * ui.px(12)) / (COLS * 6f - 1); }

    @Override
    protected void onDraw(Canvas c) {
        // bezel window, then backlit glass
        r.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(r, ui.px(6), ui.px(6), frame);
        if (ui.night) { // lift the black window off the graphite bezel
            float h = ui.px(1) / 2f;
            r.set(h, h, getWidth() - h, getHeight() - h);
            c.drawRoundRect(r, ui.px(6), ui.px(6), rim);
        }
        float in = inset();
        r.set(in, in, getWidth() - in, getHeight() - in);
        glass.setColor(amber ? ui.lcdAmber : ui.lcdBlue);
        c.drawRoundRect(r, ui.px(3), ui.px(3), glass);
        on.setColor(amber ? ui.lcdAmberOn : ui.lcdBlueOn);
        off.setColor(amber ? ui.lcdAmberOff : ui.lcdBlueOff);

        float p = pitch(getWidth()), dot = p * 0.82f;
        float x0 = in + ui.px(12), y0 = in + ui.px(10);
        drawLine(c, l1, x0, y0, p, dot, invert);
        drawLine(c, l2, x0, y0 + p * 10, p, dot, 0);
    }

    private void drawLine(Canvas c, String s, float x0, float y0, float p, float dot, int inverted) {
        if (inverted > 0) // inverse video: one solid lit band, glyphs knocked out of it
            c.drawRect(x0 - p, y0 - p, x0 + inverted * 6 * p - p * 0.2f, y0 + 8 * p, on);
        for (int col = 0; col < COLS; col++) {
            int[] g = glyph(col < s.length() ? s.charAt(col) : ' ');
            float cx = x0 + col * 6 * p;
            boolean inv = col < inverted;
            for (int row = 0; row < 7; row++)
                for (int bit = 0; bit < 5; bit++) {
                    boolean lit = (g[row] & (0x10 >> bit)) != 0;
                    float x = cx + bit * p, y = y0 + row * p;
                    if (inv) { if (lit) c.drawRect(x, y, x + dot, y + dot, glass); }
                    else c.drawRect(x, y, x + dot, y + dot, lit ? on : off);
                }
        }
    }

    // ------------------------------------------------------------------ 5x7 character ROM

    private static final int[] BLANK = new int[7];
    private static final int[][] ROM = new int[128][];

    private static void g(char ch, int... rows) { ROM[ch] = rows; }

    static {
        g('0', 0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E);
        g('1', 0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E);
        g('2', 0x0E, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1F);
        g('3', 0x1F, 0x02, 0x04, 0x02, 0x01, 0x11, 0x0E);
        g('4', 0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02);
        g('5', 0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E);
        g('6', 0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E);
        g('7', 0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08);
        g('8', 0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E);
        g('9', 0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C);
        g('A', 0x0E, 0x11, 0x11, 0x11, 0x1F, 0x11, 0x11);
        g('B', 0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E);
        g('C', 0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E);
        g('D', 0x1C, 0x12, 0x11, 0x11, 0x11, 0x12, 0x1C);
        g('E', 0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F);
        g('F', 0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10);
        g('G', 0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0F);
        g('H', 0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11);
        g('I', 0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E);
        g('J', 0x07, 0x02, 0x02, 0x02, 0x02, 0x12, 0x0C);
        g('K', 0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11);
        g('L', 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F);
        g('M', 0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11);
        g('N', 0x11, 0x11, 0x19, 0x15, 0x13, 0x11, 0x11);
        g('O', 0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E);
        g('P', 0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10);
        g('Q', 0x0E, 0x11, 0x11, 0x11, 0x15, 0x12, 0x0D);
        g('R', 0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11);
        g('S', 0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E);
        g('T', 0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04);
        g('U', 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E);
        g('V', 0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04);
        g('W', 0x11, 0x11, 0x11, 0x15, 0x15, 0x15, 0x0A);
        g('X', 0x11, 0x11, 0x0A, 0x04, 0x0A, 0x11, 0x11);
        g('Y', 0x11, 0x11, 0x11, 0x0A, 0x04, 0x04, 0x04);
        g('Z', 0x1F, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1F);
        g('!', 0x04, 0x04, 0x04, 0x04, 0x00, 0x00, 0x04);
        g('%', 0x18, 0x19, 0x02, 0x04, 0x08, 0x13, 0x03);
        g('\'', 0x0C, 0x04, 0x08, 0x00, 0x00, 0x00, 0x00);
        g('(', 0x02, 0x04, 0x08, 0x08, 0x08, 0x04, 0x02);
        g(')', 0x08, 0x04, 0x02, 0x02, 0x02, 0x04, 0x08);
        g('+', 0x00, 0x04, 0x04, 0x1F, 0x04, 0x04, 0x00);
        g(',', 0x00, 0x00, 0x00, 0x00, 0x0C, 0x04, 0x08);
        g('-', 0x00, 0x00, 0x00, 0x1F, 0x00, 0x00, 0x00);
        g('.', 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C);
        g('/', 0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x00);
        g(':', 0x00, 0x0C, 0x0C, 0x00, 0x0C, 0x0C, 0x00);
        g('<', 0x02, 0x04, 0x08, 0x10, 0x08, 0x04, 0x02);
        g('=', 0x00, 0x00, 0x1F, 0x00, 0x1F, 0x00, 0x00);
        g('>', 0x08, 0x04, 0x02, 0x01, 0x02, 0x04, 0x08);
        g('?', 0x0E, 0x11, 0x01, 0x02, 0x04, 0x00, 0x04);
    }

    private static final int[] DEGREE = {0x1C, 0x14, 0x1C, 0x00, 0x00, 0x00, 0x00};
    private static final int[] MIDDOT = {0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00};

    private static int[] glyph(char ch) {
        if (ch == '°') return DEGREE;
        if (ch == '·') return MIDDOT;
        if (ch < 128 && ROM[ch] != null) return ROM[ch];
        return BLANK;
    }
}
