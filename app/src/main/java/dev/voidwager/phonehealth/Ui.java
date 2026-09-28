package dev.voidwager.phonehealth;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Palette + tiny view factory. Everything is built in code so the app has no dependencies. */
final class Ui {
    enum S { GOOD, WARN, BAD, NA }

    final Context c;
    final int bg, card, text, muted, good, warn, bad, accent, line;
    private final float dp;

    Ui(Context c) {
        this.c = c;
        dp = c.getResources().getDisplayMetrics().density;
        boolean night = (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            bg = 0xFF0F1115; card = 0xFF181B21; text = 0xFFE8EAED; muted = 0xFF9AA0A6;
            good = 0xFF4CC38A; warn = 0xFFE5B143; bad = 0xFFEF6B6B; accent = 0xFF7AB0FF; line = 0xFF2A2E36;
        } else {
            bg = 0xFFF3F4F6; card = 0xFFFFFFFF; text = 0xFF1B1D21; muted = 0xFF5F6368;
            good = 0xFF1E8E57; warn = 0xFFB27A00; bad = 0xFFC62828; accent = 0xFF1F6FEB; line = 0xFFE1E4E8;
        }
    }

    int px(float d) { return Math.round(d * dp); }

    int color(S s) {
        switch (s) {
            case GOOD: return good;
            case WARN: return warn;
            case BAD: return bad;
            default: return muted;
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
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    GradientDrawable rounded(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radiusDp));
        return g;
    }

    /** Adds a titled card to parent and returns its body. */
    LinearLayout card(LinearLayout parent, String title) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(card, 14));
        box.setPadding(px(16), px(14), px(16), px(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(12);
        parent.addView(box, lp);
        if (title != null) {
            TextView h = text(title.toUpperCase(), 12, muted, true);
            h.setLetterSpacing(0.08f);
            h.setPadding(0, 0, 0, px(6));
            box.addView(h);
        }
        return box;
    }

    /** label ........ value, with a status dot when s != null. */
    void row(LinearLayout card, String label, CharSequence value, S s) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, px(5), 0, px(5));
        if (s != null) {
            View dot = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color(s));
            dot.setBackground(g);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(9), px(9));
            dl.rightMargin = px(10);
            r.addView(dot, dl);
        }
        TextView l = text(label, 15, text, false);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = text(value, 15, s == null || s == S.NA ? muted : text, true);
        v.setGravity(Gravity.END);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.leftMargin = px(12);
        r.addView(v, vl);
        card.addView(r);
    }

    void note(LinearLayout card, CharSequence s) {
        TextView t = text(s, 13, muted, false);
        t.setPadding(0, px(4), 0, px(4));
        t.setLineSpacing(0, 1.15f);
        card.addView(t);
    }

    Button button(LinearLayout parent, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(accent, 10));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(44));
        lp.topMargin = px(8);
        parent.addView(b, lp);
        return b;
    }
}
