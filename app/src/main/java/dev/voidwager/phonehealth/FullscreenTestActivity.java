package dev.voidwager.phonehealth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

/** Tests that need the whole screen: pixels, touch, keys. Returns PASS/FAIL + detail. */
public class FullscreenTestActivity extends Activity {
    static final String MODE = "mode", DETAIL = "detail";

    private String mode;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getStringExtra(MODE);
        if ("pixels".equals(mode)) setContentView(new PixelView(this));
        else if ("touch".equals(mode)) setContentView(new TouchGrid(this));
        else setContentView(keysView());
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        if (Build.VERSION.SDK_INT >= 33) // targetSdk 36 stops routing back through onBackPressed
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::confirmStop);
    }

    void finishWith(boolean pass, String detail) {
        setResult(pass ? RESULT_OK : RESULT_CANCELED, new Intent().putExtra(DETAIL, detail));
        finish();
    }

    void askPassFail(String question, String detail) {
        new AlertDialog.Builder(this)
                .setMessage(question)
                .setCancelable(false)
                .setPositiveButton("Pass", (d, w) -> finishWith(true, detail))
                .setNegativeButton("Fail", (d, w) -> finishWith(false, detail))
                .show();
    }

    private void confirmStop() {
        askPassFail("Stop the test. Did it pass?", "stopped early");
    }

    /** Only reached below Android 13; newer versions use the callback registered in onCreate. */
    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        confirmStop();
    }

    // ------------------------------------------------------------- dead / stuck pixels

    private class PixelView extends View {
        final int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK, 0xFF808080};
        final String[] names = {"red", "green", "blue", "white", "black", "grey"};
        int i = 0;
        final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PixelView(Context c) {
            super(c);
            hint.setTextSize(getResources().getDisplayMetrics().density * 14);
            hint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(colors[i]);
            if (i == 0) {
                hint.setColor(Color.WHITE);
                c.drawText("Look for dots that don't match. Tap for the next colour.", getWidth() / 2f, getHeight() / 2f, hint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            if (++i < colors.length) invalidate();
            else askPassFail("Any stuck, dead or discoloured pixels, or burn-in on any colour?\n\nPass = screen is clean.",
                    "checked " + String.join("/", names));
            return true;
        }
    }

    // ------------------------------------------------------------- touch grid

    private class TouchGrid extends View {
        static final int COLS = 8, ROWS = 14;
        final boolean[][] hit = new boolean[COLS][ROWS];
        int count = 0, maxPointers = 0;
        boolean done = false;
        final Paint fill = new Paint(), stroke = new Paint(), txt = new Paint(Paint.ANTI_ALIAS_FLAG);

        TouchGrid(Context c) {
            super(c);
            fill.setColor(0xFF2E9E62);
            stroke.setColor(0xFF444444);
            stroke.setStyle(Paint.Style.STROKE);
            txt.setColor(Color.WHITE);
            txt.setTextSize(getResources().getDisplayMetrics().density * 14);
            txt.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(Color.BLACK);
            float cw = getWidth() / (float) COLS, ch = getHeight() / (float) ROWS;
            for (int x = 0; x < COLS; x++)
                for (int y = 0; y < ROWS; y++) {
                    if (hit[x][y]) c.drawRect(x * cw, y * ch, (x + 1) * cw, (y + 1) * ch, fill);
                    c.drawRect(x * cw, y * ch, (x + 1) * cw, (y + 1) * ch, stroke);
                }
            c.drawText("Paint every cell  ·  " + count + "/" + COLS * ROWS + "  ·  max fingers " + maxPointers,
                    getWidth() / 2f, getHeight() / 2f, txt);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            maxPointers = Math.max(maxPointers, e.getPointerCount());
            float cw = getWidth() / (float) COLS, ch = getHeight() / (float) ROWS;
            for (int h = 0; h <= e.getHistorySize(); h++)
                for (int p = 0; p < e.getPointerCount(); p++) {
                    float fx = h < e.getHistorySize() ? e.getHistoricalX(p, h) : e.getX(p);
                    float fy = h < e.getHistorySize() ? e.getHistoricalY(p, h) : e.getY(p);
                    int x = Math.min(COLS - 1, Math.max(0, (int) (fx / cw)));
                    int y = Math.min(ROWS - 1, Math.max(0, (int) (fy / ch)));
                    if (!hit[x][y]) { hit[x][y] = true; count++; }
                }
            invalidate();
            if (!done && count == COLS * ROWS) {
                done = true;
                finishWith(true, "all " + count + " cells, max " + maxPointers + " fingers");
            }
            return true;
        }
    }

    // ------------------------------------------------------------- hardware keys

    private int keyStep = 0;
    private TextView keyPrompt;

    private View keysView() {
        keyPrompt = new TextView(this);
        keyPrompt.setGravity(Gravity.CENTER);
        keyPrompt.setTextSize(24);
        keyPrompt.setTextColor(Color.WHITE);
        keyPrompt.setBackgroundColor(Color.BLACK);
        keyPrompt.setText("Press VOLUME UP");
        return keyPrompt;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (!"keys".equals(mode)) return super.onKeyDown(code, e);
        if (keyStep == 0 && code == KeyEvent.KEYCODE_VOLUME_UP) {
            keyStep = 1;
            keyPrompt.setText("Press VOLUME DOWN");
            return true;
        }
        if (keyStep == 1 && code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            finishWith(true, "vol up + vol down registered");
            return true;
        }
        return code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || super.onKeyDown(code, e);
    }
}
