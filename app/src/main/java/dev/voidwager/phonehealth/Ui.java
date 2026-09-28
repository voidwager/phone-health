package dev.voidwager.phonehealth;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Rack-bezel tokens + view factory. Every surface is a powder-coated bezel, every group an
 * inset bay, every reading an LED + printed label + tabular value + state word.
 */
final class Ui {
    enum S { GOOD, WARN, BAD, NA }

    final Context c;
    final boolean night;
    // bezel world
    final int bezel, bay, bayEdge, ink, muted, rule, vent, touch, onTouch, touchTonal, highlight;
    /** Data traces are ink-coloured, never a state colour. */
    int data() { return ink; }
    final int ledOk, ledWarn, ledBad, ledOff;
    /** Red for FAULT words: darker than the LED red so small text keeps 4.5:1 on the bay well. */
    final int badText;
    final int lcdFrame, lcdBlue, lcdBlueOn, lcdBlueOff, lcdAmber, lcdAmberOn, lcdAmberOff;
    // aliases the charts and older code read
    final int bg, card, text, good, warn, bad, accent, line;

    /** Barlow Condensed SemiBold (SIL OFL, bundled): the DIN-like lettering printed on server bezels. */
    final Typeface silk;
    final Typeface body = Typeface.create("sans-serif", Typeface.NORMAL);
    final Typeface bodyMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final float dp;

    Ui(Context c) {
        this.c = c;
        dp = c.getResources().getDisplayMetrics().density;
        night = (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Typeface f;
        try {
            f = c.getResources().getFont(R.font.barlow_condensed_semibold);
        } catch (Exception e) {
            f = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        }
        silk = f;
        if (night) {   // graphite bezel in a dark room; bays are wells pressed into it
            bezel = 0xFF23282D; bay = 0xFF181B1F; bayEdge = 0xFF0C0E10; ink = 0xFFE4E7EA; muted = 0xFFA4ACB4;
            rule = 0xFF30363C; vent = 0xFF0A0C0E; touch = 0xFF6F9BF0; onTouch = 0xFFFFFFFF; touchTonal = 0xFF2A3E63;
            ledOk = 0xFF34C46A; ledWarn = 0xFFF0B429; ledBad = 0xFFF0584A; ledOff = 0xFF4A5057;
            highlight = 0xFF3A4148; badText = 0xFFF0584A;
        } else {       // silver bezel on a lit shelf; bays are wells pressed into it
            bezel = 0xFFD7DCE0; bay = 0xFFC9CFD4; bayEdge = 0xFF9FA7AE; ink = 0xFF1B1F24; muted = 0xFF454E57;
            rule = 0xFFAEB5BC; vent = 0xFF8E979F; touch = 0xFF2159C4; onTouch = 0xFFFFFFFF; touchTonal = 0xFFB9CCF2;
            ledOk = 0xFF1C9A45; ledWarn = 0xFFD9930C; ledBad = 0xFFCC3326; ledOff = 0xFF7F878E;
            highlight = 0xFFEEF1F3; badText = 0xFF9E2218;
        }
        lcdFrame = 0xFF101214;
        lcdBlue = 0xFF2F63C9; lcdBlueOn = 0xFFEAF2FF; lcdBlueOff = 0xFF3A6ED4;
        lcdAmber = 0xFFE9A21C; lcdAmberOn = 0xFF2B1B02; lcdAmberOff = 0xFFEDAB33;
        bg = bezel; card = bay; text = ink; good = ledOk; warn = ledWarn; bad = ledBad; accent = touch; line = rule;
    }

    int px(float d) { return Math.round(d * dp); }

    int color(S s) {
        switch (s) {
            case GOOD: return ledOk;
            case WARN: return ledWarn;
            case BAD: return ledBad;
            default: return ledOff;
        }
    }

    /** The word printed beside every LED: status is never colour alone. */
    static String word(S s) {
        switch (s) {
            case GOOD: return "OK";
            case WARN: return "CHECK";
            case BAD: return "FAULT";
            default: return "—";
        }
    }

    static S worst(S a, S b) {
        if (a == S.BAD || b == S.BAD) return S.BAD;
        if (a == S.WARN || b == S.WARN) return S.WARN;
        if (a == S.GOOD || b == S.GOOD) return S.GOOD;
        return S.NA;
    }

    TextView text(CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? bodyMedium : body);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Silkscreen: condensed caps printed on the bezel. */
    TextView silk(String s, float sp, int color) {
        TextView t = text(s.toUpperCase(java.util.Locale.ROOT), sp, color, false);
        t.setTypeface(silk);
        t.setLetterSpacing(0.07f);
        return t;
    }

    /** A reading: tabular digits so values never shift their row as they change. */
    TextView reading(CharSequence s, float sp, int color) {
        TextView t = text(s, sp, color, true);
        t.setFontFeatureSettings("tnum");
        return t;
    }

    GradientDrawable rounded(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radiusDp));
        return g;
    }

    /**
     * Inset bay: a well pressed into the bezel. Darker than the bezel, a shadowed top edge and a
     * lit bottom lip, no drop shadow.
     */
    android.graphics.drawable.Drawable bayShape() { return bayShape(0); }

    android.graphics.drawable.Drawable bayShape(int ringColor) {
        GradientDrawable lip = rounded(highlight, 7);
        GradientDrawable shade = rounded(bayEdge, 6);
        GradientDrawable well = rounded(bay, 6);
        if (ringColor != 0) well.setStroke(px(2), ringColor);
        android.graphics.drawable.LayerDrawable l = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{lip, shade, well});
        l.setLayerInset(1, 0, 0, 0, px(1));          // lit lip shows 1 dp along the bottom
        l.setLayerInset(2, 0, px(2), 0, px(1));       // shadow shows 2 dp along the top
        return l;
    }

    /** Printed section label on the bezel, then the inset panel it names. Returns the panel. */
    LinearLayout panel(LinearLayout parent, String title) {
        if (title != null) {
            TextView h = silk(title, 13, muted);
            h.setPadding(px(4), px(18), 0, px(8));
            parent.addView(h);
        }
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(bayShape());
        box.setPadding(px(14), px(6), px(14), px(6));
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    /** Backwards-compatible name used by older call sites. */
    LinearLayout card(LinearLayout parent, String title) { return panel(parent, title); }

    /** LED · label ........ value · STATE */
    void row(LinearLayout panel, String label, CharSequence value, S s) {
        if (panel.getChildCount() > 0 && panel.getChildAt(panel.getChildCount() - 1).getTag() == ROW) {
            View div = new View(c);
            div.setBackgroundColor(rule);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, px(1) / 2 + 1));
            panel.addView(div, dl);
        }
        LinearLayout r = new LinearLayout(c);
        r.setTag(ROW);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(px(44));
        r.setPadding(0, px(8), 0, px(8));
        if (s != null) {
            LedView led = new LedView(c, this);
            led.set(s);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(px(12), px(12));
            ll.rightMargin = px(12);
            r.addView(led, ll);
        }
        TextView l = text(label, 15, ink, false);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView v = reading(value, 15, s == S.NA ? muted : ink);
        v.setGravity(Gravity.END);
        right.addView(v);
        if (s != null && s != S.NA && s != S.GOOD) {
            TextView w = silk(word(s), 11, s == S.BAD ? badText : ink);
            w.setGravity(Gravity.END);
            w.setPadding(0, px(3), 0, 0);
            right.addView(w);
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.leftMargin = px(12);
        r.addView(right, rl);
        panel.addView(r);
    }

    private static final Object ROW = new Object();

    void note(LinearLayout panel, CharSequence s) {
        TextView t = text(s, 14, muted, false);
        t.setPadding(0, px(8), 0, px(10));
        t.setLineSpacing(0, 1.25f);
        panel.addView(t);
    }

    /** Blue touch point: on server hardware, blue marks the parts that are always safe to handle. */
    Button button(LinearLayout parent, String label, View.OnClickListener l) {
        Button b = touchButton(label, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(48));
        lp.topMargin = px(8);
        lp.bottomMargin = px(8);
        parent.addView(b, lp);
        return b;
    }

    /** Outlined blue touch point: the blue marks what's safe to press without it shouting. */
    Button touchButton(String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label.toUpperCase(java.util.Locale.ROOT));
        b.setTypeface(silk);
        b.setLetterSpacing(0.07f);
        b.setTextSize(15);
        b.setTextColor(touch);
        b.setStateListAnimator(null);
        GradientDrawable outline = rounded(0x00000000, 6);
        outline.setStroke(px(1.5f), touch);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(touch, 0.22f)), outline, rounded(0xFF000000, 6)));
        b.setMinHeight(px(48));
        b.setMinimumHeight(px(48));
        b.setPadding(px(16), 0, px(16), 0);
        b.setOnClickListener(l);
        return b;
    }

    /** Ripple for tappable bays and rows, bounded to their shape. */
    RippleDrawable pressable(android.graphics.drawable.Drawable shape) {
        return new RippleDrawable(ColorStateList.valueOf(night ? 0x33FFFFFF : 0x22000000), shape, null);
    }

    static int alpha(int color, float a) {
        return Color.argb(Math.round(a * 255), Color.red(color), Color.green(color), Color.blue(color));
    }
}
