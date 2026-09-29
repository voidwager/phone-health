package dev.voidwager.phonehealth;

/*
 * THESIS: The phone is a server, so the app is its front bezel. The verdict lives on a two-line
 *   character LCD, the way rack servers report health, not in a score ring or a stack of cards.
 * OWN-WORLD: Powder-coated bezel (silver on a lit shelf, graphite in the dark). Inset bays with
 *   sheet-metal radii. Round LED lenses, hatched when not OK. A backlit 5x7 dot-matrix LCD, blue
 *   when healthy and amber on a fault. Hex vent perforation. Silkscreen condensed caps. Blue touch
 *   points for everything you press.
 * STORY: Glance: blue LCD means SYSTEM OK. If amber, the LCD names the fault. Tap the amber bay to
 *   read its cause and remedy. The 7-day LED matrix shows what happened while nobody was looking.
 * FIRST VIEWPORT: vent strip, then a full-width LCD (line 1 verdict, line 2 stepping readings),
 *   then a 2x4 grid of component bays (LED, label, tabular reading, state word). Bottom navigation:
 *   Status / Readings / History / Tests.
 * FORM: Rack Bezel, grounded candidate #3 of 7 (re-roll 1), seed key 48bdb416.
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the
 *   verdict, and DESIGN.md
 */

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String PREFS = "ph";
    private static final int STATUS = 0, READINGS = 1, HISTORY = 2, TESTS = 3;
    private static final String[] TABS = {"Status", "Readings", "History", "Tests"};
    private static final long DAY = 24 * 3600 * 1000L;

    private static final String[] TEST_IDS = {"pixels", "touch", "keys", "speaker", "earpiece", "mic",
            "vibrate", "torch", "sensors"};
    private static final String[] TEST_NAMES = {"Screen pixels", "Touch grid", "Volume buttons", "Loudspeaker",
            "Earpiece", "Microphone", "Vibration motor", "Flashlight", "Motion & light sensors"};
    private static final String[] TEST_HINTS = {
            "Six solid colours. Look for dead or stuck pixels and burn-in.",
            "Paint every cell to find dead zones in the digitiser.",
            "Press volume up, then volume down.",
            "2 s sweep, 300 Hz to 3 kHz. Listen for rattle or dropouts.",
            "Same sweep through the call speaker. Hold the phone to your ear.",
            "Clap or talk for 3 s. Passes on its own above -40 dBFS.",
            "Two firm pulses.",
            "Torch on for 1.5 s.",
            "Listens for 3 s. Tilt the phone and wave a hand over the top edge while it runs."};
    private static final int REQ_MIC = 100;

    private Ui ui;
    private SharedPreferences prefs;
    private LinearLayout content;
    private ScrollView scroll;
    private final List<View[]> navItems = new ArrayList<>();
    private TextView snack;
    private int tab = STATUS;
    private EditText portsEdit;
    private String openBay;
    private int lcdPhase;
    private OnBackInvokedCallback backToStatus;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean[] portsUp;
    private volatile long latencyMs = -2, asyncAt;
    private volatile boolean benchRunning;
    private List<History.Sample> history = new ArrayList<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            lcdPhase++;
            boolean typing = portsEdit != null && portsEdit.hasFocus();
            if (tab != TESTS && !typing) render();
            main.postDelayed(this, 2500);
        }
    };

    // ================================================================ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        prefs = getSharedPreferences(PREFS, 0);
        ui.onToggle = key -> { if (!ui.openRows.remove(key)) ui.openRows.add(key); render(); };
        if (b != null) {
            java.util.ArrayList<String> rows = b.getStringArrayList("rows");
            if (rows != null) ui.openRows.addAll(rows);
            tab = b.getInt("tab", STATUS);
            openBay = b.getString("bay");
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ui.bezel);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(ui.px(20), ui.px(12), ui.px(20), ui.px(10));
        TextView name = ui.silk("Phone Health", 17, ui.ink);
        name.setSingleLine(true);
        head.addView(name);
        TextView model = ui.silk(Build.MODEL + " · Android " + Build.VERSION.RELEASE, 11, ui.muted);
        model.setSingleLine(true);
        model.setEllipsize(android.text.TextUtils.TruncateAt.START);
        model.setGravity(Gravity.END);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, -2, 1f);
        ml.leftMargin = ui.px(12);
        head.addView(model, ml);
        column.addView(head);

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.px(16), 0, ui.px(16), ui.px(28));
        scroll.addView(content);
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        View navRule = new View(this);
        navRule.setBackgroundColor(ui.rule);
        column.addView(navRule, new LinearLayout.LayoutParams(-1, Math.max(1, ui.px(1))));
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(ui.bezel);
        for (int i = 0; i < TABS.length; i++) nav.addView(navItem(i), new LinearLayout.LayoutParams(0, ui.px(80), 1f));
        column.addView(nav);

        snack = ui.text("", 14, ui.bay, false);
        snack.setBackground(ui.rounded(ui.ink, 6));
        snack.setPadding(ui.px(16), ui.px(14), ui.px(16), ui.px(14));
        snack.setVisibility(View.GONE);
        snack.setElevation(ui.px(6));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        sl.setMargins(ui.px(16), 0, ui.px(16), ui.px(96));
        root.addView(snack, sl);

        column.setOnApplyWindowInsetsListener((v, in) -> {
            Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            head.setPadding(ui.px(20) + i.left, ui.px(12) + i.top, ui.px(20) + i.right, ui.px(10));
            nav.setPadding(i.left, 0, i.right, i.bottom);
            ((LinearLayout.LayoutParams) nav.getChildAt(0).getLayoutParams()).height = ui.px(80);
            content.setPadding(ui.px(16) + i.left, 0, ui.px(16) + i.right, ui.px(28));
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) snack.getLayoutParams();
            p.bottomMargin = ui.px(92) + i.bottom;
            snack.setLayoutParams(p);
            return WindowInsets.CONSUMED;
        });
        setContentView(root);
        WindowInsetsController wic = getWindow().getInsetsController();
        if (wic != null && !ui.night) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            wic.setSystemBarsAppearance(light, light);
        }
        if (Build.VERSION.SDK_INT >= 33) backToStatus = () -> switchTab(STATUS);
        SampleJob.schedule(this);
    }

    private View navItem(int i) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setBackground(ui.pressable(ui.rounded(0, 0)));
        FrameLayout pill = new FrameLayout(this);
        BezelParts.NavIcon icon = new BezelParts.NavIcon(this, ui, i);
        pill.addView(icon, new FrameLayout.LayoutParams(ui.px(24), ui.px(24), Gravity.CENTER));
        item.addView(pill, new LinearLayout.LayoutParams(ui.px(64), ui.px(32)));
        TextView label = ui.text(TABS[i], 12, ui.muted, true);
        label.setPadding(0, ui.px(4), 0, 0);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        item.addView(label, new LinearLayout.LayoutParams(-2, -2));
        item.setContentDescription(TABS[i]);
        item.setOnClickListener(v -> switchTab(i));
        navItems.add(new View[]{pill, icon, label, item});
        return item;
    }

    private void switchTab(int t) {
        if (t == tab) {
            scroll.smoothScrollTo(0, 0);
            return;
        }
        tab = t;
        scroll.scrollTo(0, 0);
        render();
        if (ValueAnimator.areAnimatorsEnabled()) { // fade-through between destinations
            content.setAlpha(0f);
            content.setTranslationY(ui.px(8));
            content.animate().alpha(1f).translationY(0).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putString("bay", openBay);
        out.putStringArrayList("rows", new java.util.ArrayList<>(ui.openRows));
    }

    @Override
    protected void onResume() {
        super.onResume();
        io.execute(() -> {
            List<History.Sample> h = History.load(this);
            // Seed the log on open so the panels aren't empty for the first 15 minutes.
            if (h.isEmpty() || System.currentTimeMillis() - h.get(h.size() - 1).t > 10 * 60 * 1000L) {
                History.append(this, History.capture(this));
                h = History.load(this);
            }
            List<History.Sample> fh = h;
            main.post(() -> { history = fh; render(); });
        });
        render();
        main.postDelayed(tick, 2500);
        if (Updater.due(prefs)) io.execute(() -> { Updater.check(prefs); main.post(this::render); });
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ================================================================ rendering

    private void render() {
        for (int i = 0; i < navItems.size(); i++) {
            View[] v = navItems.get(i);
            boolean on = i == tab;
            v[0].setBackground(on ? ui.rounded(ui.touchTonal, 16) : null);
            ((BezelParts.NavIcon) v[1]).setColor(on ? ui.ink : ui.muted);
            ((TextView) v[2]).setTextColor(on ? ui.ink : ui.muted);
            v[3].setSelected(on);
        }
        if (Build.VERSION.SDK_INT >= 33 && backToStatus != null) {
            OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
            d.unregisterOnBackInvokedCallback(backToStatus);
            if (tab != STATUS) d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backToStatus);
        }
        content.removeAllViews();
        portsEdit = null;
        refreshAsync();
        switch (tab) {
            case STATUS: statusTab(); break;
            case READINGS: readingsTab(); break;
            case HISTORY: historyTab(); break;
            case TESTS: testsTab(); break;
        }
    }

    /** Port and latency probes are network calls, so they run on io and repaint when done. */
    private void refreshAsync() {
        long now = SystemClock.elapsedRealtime();
        if (now - asyncAt < 8000) return;
        asyncAt = now;
        io.execute(() -> {
            portsUp = Probes.probePorts(ports());
            latencyMs = Probes.tcpConnectMs("1.1.1.1", 443, 1500);
            main.post(() -> {
                boolean typing = portsEdit != null && portsEdit.hasFocus();
                if (tab != TESTS && !typing) render();
            });
        });
    }

    private List<Probes.Port> ports() { return Probes.parsePorts(prefs.getString("ports", Probes.DEFAULT_PORTS)); }

    private void showSnack(String msg) {
        snack.setText(msg);
        snack.setVisibility(View.VISIBLE);
        snack.setAlpha(0f);
        snack.animate().alpha(1f).setDuration(ValueAnimator.areAnimatorsEnabled() ? 150 : 0).start();
        main.removeCallbacks(hideSnack);
        main.postDelayed(hideSnack, 4000);
    }

    private final Runnable hideSnack = () -> snack.setVisibility(View.GONE);

    // ---------------------------------------------------------------- the checks

    /** One component bay: what the LCD, the grid and the service note all read from. */
    private static final class Check {
        final String id, label, reading, detail, cause, remedy;
        final Ui.S s;
        final int target;

        Check(String id, String label, String reading, String detail, Ui.S s, String cause, String remedy, int target) {
            this.id = id; this.label = label; this.reading = reading; this.detail = detail;
            this.s = s; this.cause = cause; this.remedy = remedy; this.target = target;
        }
    }

    private static String band(float t) {
        if (Float.isNaN(t)) return "no reading";
        return t < 35 ? "cool" : t < 40 ? "warm" : t < 45 ? "hot" : "too hot";
    }

    private List<Check> checks() {
        List<Check> out = new ArrayList<>();
        Probes.Battery b = Probes.battery(this);
        double[] cap = trustedCapacity(b);

        Ui.S bs = Probes.tempStatus(b.tempC);
        if (b.health != BatteryManager.BATTERY_HEALTH_GOOD) bs = Ui.S.BAD;
        else if (cap != null && cap[0] < 70) bs = Ui.worst(bs, Ui.S.BAD);
        else if (cap != null && cap[0] < 80) bs = Ui.worst(bs, Ui.S.WARN);
        out.add(new Check("battery", "Battery", fmtTemp(b.tempC),
                band(b.tempC) + (cap != null ? String.format(Locale.getDefault(), " · %.0f%% capacity", cap[0]) : ""), bs,
                b.health != BatteryManager.BATTERY_HEALTH_GOOD
                        ? "Android flags the battery as " + Probes.healthName(b.health) + "."
                        : "Battery at " + fmtTemp(b.tempC) + (cap != null ? String.format(Locale.getDefault(),
                        ", about %.0f%% of its design capacity.", cap[0]) : "."),
                "Heat ages a battery that never leaves the charger faster than anything else. Keep it off soft "
                        + "surfaces and out of sun, take the case off, and on One UI turn on Battery › Protect battery "
                        + "to stop charging at 80–85%.", READINGS));

        Ui.S ps = b.plugged != 0 ? Ui.S.GOOD : Ui.S.WARN;
        if (b.charging() && b.currentMa > 0 && b.currentMa < 500 && b.level < 90) ps = Ui.S.WARN;
        out.add(new Check("power", "Power", b.level + "%", Probes.plugName(b.plugged).toLowerCase(Locale.getDefault()), ps,
                b.plugged == 0 ? "Running on battery at " + b.level + "%." : "On " + Probes.plugName(b.plugged)
                        + (b.currentMa > 0 ? ", " + b.currentMa + " mA." : "."),
                "A server phone should stay plugged in. If the charge current sits under 500 mA below 90%, "
                        + "try another cable or USB port before blaming the battery.", READINGS));

        int th = Probes.thermalStatus(this);
        out.add(new Check("thermal", "Thermal", Probes.thermalName(th), "throttling state", Probes.thermalS(th),
                "Android reports thermal state " + Probes.thermalName(th).toLowerCase(Locale.getDefault()) + ".",
                "Moderate or worse means the CPU is being slowed to shed heat, so your services slow with it. "
                        + "Give the phone airflow and check what is using the CPU.", READINGS));

        long[] st = Probes.storage();
        double freePct = st[1] * 100.0 / st[0];
        out.add(new Check("storage", "Storage", String.format(Locale.getDefault(), "%.1f GB", st[1] / 1e9),
                String.format(Locale.getDefault(), "%.0f%% free", freePct),
                freePct > 15 ? Ui.S.GOOD : freePct > 5 ? Ui.S.WARN : Ui.S.BAD,
                String.format(Locale.getDefault(), "%.0f%% of internal storage is free.", freePct),
                "Flash slows down and wears faster when nearly full. Rotate server logs and world backups off "
                        + "the phone.", READINGS));

        ActivityManager.MemoryInfo mi = Probes.memory(this);
        double ramPct = mi.availMem * 100.0 / mi.totalMem;
        out.add(new Check("memory", "Memory", String.format(Locale.getDefault(), "%.1f GB", mi.availMem / 1e9),
                String.format(Locale.getDefault(), "%.0f%% available", ramPct),
                mi.lowMemory ? Ui.S.BAD : ramPct > 15 ? Ui.S.GOOD : Ui.S.WARN,
                mi.lowMemory ? "Android is in a low-memory state and killing background apps."
                        : String.format(Locale.getDefault(), "%.0f%% of memory is available.", ramPct),
                "Low memory is how Termux gets killed. Lower the game server's heap or stop apps you don't need.",
                READINGS));

        Probes.Signal sig = Probes.signal(this);
        out.add(new Check("network", "Network", signalSummary(sig),
                !sig.online ? "offline" : sig.validated ? "internet reachable" : "no internet",
                Ui.worst(Probes.signalS(sig), sig.online && !sig.validated ? Ui.S.WARN : Ui.S.GOOD),
                !sig.online ? "The phone has no active network." : "Connected over " + (sig.wifi ? "Wi-Fi" : "mobile data")
                        + (sig.validated ? "." : ", but Android can't reach the internet."),
                "Weak Wi-Fi (below -75 dBm) drops tunnels and game connections. Move the phone closer to the "
                        + "router or give it a wired USB-Ethernet link.", READINGS));

        List<Probes.Port> ps2 = ports();
        boolean[] up = portsUp;
        boolean known = up != null && up.length == ps2.size();
        int n = 0;
        if (known) for (boolean u : up) if (u) n++;
        out.add(new Check("services", "Services", known ? n + "/" + up.length : "…",
                known ? "ports up" : "checking",
                !known || up.length == 0 ? Ui.S.NA : n == up.length ? Ui.S.GOOD : n == 0 ? Ui.S.BAD : Ui.S.WARN,
                known ? (n == up.length ? "Every watched port accepts connections." : (up.length - n)
                        + " watched port" + (up.length - n == 1 ? " is" : "s are") + " not answering.") : "Probing ports…",
                "If every service is down, Android probably killed Termux: set it to Unrestricted battery use "
                        + "and add it to Never sleeping apps. Edit the watched ports under History.", HISTORY));

        Object[] lg = loggerVerdict();
        out.add(new Check("logger", "Logger", (String) lg[2], "last sample", (Ui.S) lg[0],
                "The background logger last wrote " + lg[1] + ".",
                "One UI puts idle apps to sleep, and then nothing is recorded. Set Phone Health to Unrestricted "
                        + "battery use so the 7-day panel stays complete.", HISTORY));
        return out;
    }

    // ---------------------------------------------------------------- status: the front bezel

    private void statusTab() {
        List<Check> cs = checks();
        Check worst = null;
        for (Ui.S s : new Ui.S[]{Ui.S.BAD, Ui.S.WARN})
            for (Check c : cs) if (worst == null && c.s == s) worst = c;

        content.addView(new BezelParts.Vent(this, ui), new LinearLayout.LayoutParams(-1, -2));

        LcdView lcd = new LcdView(this, ui);
        // Line 1 names the fault once. A FAULT flashes its prefix in inverse video on alternate ticks,
        // so it can't be mistaken for a steady CHECK, and the word itself never leaves the glass.
        boolean flash = worst != null && worst.s == Ui.S.BAD && lcdPhase % 2 == 1 && ValueAnimator.areAnimatorsEnabled();
        String line1 = worst == null ? "SYSTEM OK" : (worst.s == Ui.S.BAD ? "FAULT: " : "CHECK: ") + worst.label;
        lcd.invertPrefix(flash ? 6 : 0);
        // Line 2 steps through values only, never repeating a label line 1 already shows.
        List<String> msgs = new ArrayList<>();
        if (worst != null) msgs.add(worst.reading + " " + worst.detail);
        for (Check c : cs) if (c != worst && (c.s == Ui.S.BAD || c.s == Ui.S.WARN)) msgs.add(c.label + " " + c.reading);
        if (worst == null) {
            Probes.Battery b = Probes.battery(this);
            for (Check c : cs) if (c.id.equals("services") && c.s != Ui.S.NA) msgs.add("SERVING " + c.reading + " PORTS");
            msgs.add("BATT " + fmtTempShort(b.tempC) + "C  " + b.level + "%");
            msgs.add("UP " + duration(SystemClock.elapsedRealtime()));
        }
        Updater.Release upd = Updater.available(this, prefs);
        if (upd != null) msgs.add(worst == null ? 0 : msgs.size(), "UPDATE " + upd.version + " READY");
        lcd.set(line1, msgs.get(lcdPhase % msgs.size()), worst != null);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
        ll.topMargin = ui.px(6);
        content.addView(lcd, ll);

        // component bays, 2 across
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = ui.px(14);
        content.addView(grid, gl);
        LinearLayout rowL = null;
        Check open = null;
        for (int i = 0; i < cs.size(); i++) {
            if (i % 2 == 0) {
                rowL = new LinearLayout(this);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) rl.topMargin = ui.px(10);
                grid.addView(rowL, rl);
            }
            Check c = cs.get(i);
            if (c.id.equals(openBay)) open = c;
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 2 == 1) bl.leftMargin = ui.px(10);
            rowL.addView(bay(c, c.id.equals(openBay)), bl);
        }

        if (open != null) serviceNote(open);
        if (upd != null) updateBay(upd);

        LinearLayout act = ui.panel(content, "Last 7 days · hourly");
        LedMatrixView m = new LedMatrixView(this, ui);
        m.set(history);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = ui.px(10);
        act.addView(m, ml);
        act.addView(legend());

        LinearLayout tag = ui.panel(content, "Service tag");
        ui.row(tag, "Model", Build.MANUFACTURER + " " + Build.MODEL, null);
        ui.row(tag, "Android", Build.VERSION.RELEASE + " · patch " + Build.VERSION.SECURITY_PATCH, null);
        ui.row(tag, "Up since boot", duration(SystemClock.elapsedRealtime()), null);
    }

    /** A drive-bay caddy: LED + printed label on top, the reading, then the state word and detail. */
    private View bay(Check c, boolean open) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setBackground(ui.pressable(ui.bayShape(open ? ui.touch : 0)));
        b.setPadding(ui.px(12), ui.px(12), ui.px(8), ui.px(12));
        b.setMinimumHeight(ui.px(100));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LedView led = new LedView(this, ui);
        led.set(c.s);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ui.px(12), ui.px(12));
        ll.rightMargin = ui.px(8);
        top.addView(led, ll);
        TextView label = ui.silk(c.label, 14, ui.muted);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(new BezelParts.Chevron(this, ui, open), new LinearLayout.LayoutParams(ui.px(20), ui.px(20)));
        b.addView(top);

        TextView v = ui.reading(c.reading, 22, c.s == Ui.S.NA ? ui.muted : ui.ink);
        v.setPadding(0, ui.px(10), ui.px(4), ui.px(4));
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.addView(v);

        // detail on the left, the state word on the right: status never rides on colour alone
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView d = ui.text(c.detail, 13, ui.muted, false);
        d.setSingleLine(true);
        d.setEllipsize(android.text.TextUtils.TruncateAt.END);
        foot.addView(d, new LinearLayout.LayoutParams(0, -2, 1f));
        if (c.s == Ui.S.WARN || c.s == Ui.S.BAD) {
            TextView w = ui.silk(Ui.word(c.s), 13, c.s == Ui.S.BAD ? ui.badText : ui.ink);
            w.setPadding(ui.px(6), 0, ui.px(4), 0);
            foot.addView(w);
        }
        b.addView(foot);

        b.setContentDescription(c.label + ", " + c.reading + ", " + c.detail + ", " + Ui.word(c.s)
                + (open ? ". Expanded" : ". Double-tap for details"));
        b.setOnClickListener(x -> {
            openBay = c.id.equals(openBay) ? null : c.id;
            render();
        });
        return b;
    }

    // ---------------------------------------------------------------- in-place updates

    /** Full-width bay that appears only while a newer release exists; opens to its notes + install. */
    private void updateBay(Updater.Release r) {
        boolean open = "update".equals(openBay);
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setBackground(ui.pressable(ui.bayShape(open ? ui.touch : 0)));
        b.setPadding(ui.px(12), ui.px(12), ui.px(8), ui.px(12));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        BezelParts.NavIcon icon = new BezelParts.NavIcon(this, ui, BezelParts.NavIcon.UPDATE);
        icon.setColor(ui.touch);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ui.px(18), ui.px(18));
        il.rightMargin = ui.px(6);
        top.addView(icon, il);
        top.addView(ui.silk("Update", 14, ui.muted), new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(new BezelParts.Chevron(this, ui, open), new LinearLayout.LayoutParams(ui.px(20), ui.px(20)));
        b.addView(top);
        TextView v = ui.reading(Updater.installedVersion(this) + "  →  " + r.version, 22, ui.ink);
        v.setPadding(0, ui.px(10), 0, ui.px(4));
        b.addView(v);
        b.addView(ui.text(String.format(Locale.getDefault(), "%.1f MB · from GitHub · keeps your data", r.size / 1e6),
                13, ui.muted, false));
        b.setContentDescription("Update available, version " + r.version + (open ? ". Expanded" : ". Double-tap for details"));
        b.setOnClickListener(x -> { openBay = open ? null : "update"; render(); });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = ui.px(10);
        content.addView(b, bl);
        if (!open) return;

        LinearLayout p = ui.panel(content, null);
        ((LinearLayout.LayoutParams) p.getLayoutParams()).topMargin = ui.px(12);
        TextView h = ui.text("Version " + r.version + " is ready to install.", 17, ui.ink, true);
        h.setPadding(0, ui.px(10), 0, ui.px(2));
        p.addView(h);
        if (!r.notes.isEmpty()) ui.note(p, r.notes);
        String err = prefs.getString("upd_err", null);
        if (err != null && dlPct < 0) {
            TextView e = ui.text("Last attempt: " + err + ".", 14, ui.badText, false);
            e.setPadding(0, ui.px(4), 0, ui.px(4));
            p.addView(e);
        }
        Button go = ui.button(p, dlPct >= 0 ? "Downloading… " + dlPct + "%" : "Install update", x -> installUpdate(r));
        go.setEnabled(dlPct < 0);
        go.setAlpha(dlPct < 0 ? 1f : 0.6f);
        ui.note(p, "Android asks you to confirm, then replaces the app in place. History, test results and settings stay.");
    }

    private volatile int dlPct = -1;

    private void installUpdate(Updater.Release r) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            showSnack("Allow Phone Health to install updates, then tap Install update again.");
            return;
        }
        dlPct = 0;
        prefs.edit().remove("upd_err").apply();
        render();
        io.execute(() -> {
            String err = null;
            try {
                java.io.File apk = Updater.download(this, r, pct -> {
                    if (pct - dlPct >= 5 || pct == 100) { dlPct = pct; main.post(() -> { if (tab == STATUS) render(); }); }
                });
                err = Updater.verify(this, apk);
                if (err == null) Updater.install(this, apk);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            String fe = err;
            main.post(() -> {
                dlPct = -1;
                if (fe != null) {
                    SharedPreferences.Editor ed = prefs.edit().putString("upd_err", fe);
                    // a release that turns out not to be newer would otherwise be offered forever
                    if (fe.startsWith("the download isn't newer")) ed.remove("upd_ver").remove("upd_url");
                    ed.apply();
                    showSnack("Update not installed: " + fe + ".");
                }
                render();
            });
        });
    }

    private void softwarePanel() {
        LinearLayout s = ui.panel(content, "Software");
        String mine = Updater.installedVersion(this);
        ui.row(s, "Installed version", mine, null);
        Updater.Release r = Updater.available(this, prefs);
        long at = prefs.getLong("upd_at", 0);
        boolean on = Updater.enabled(prefs);
        ui.row(s, "Latest on GitHub", !on ? "checks off" : r != null ? r.version + " available"
                : prefs.getString("upd_ver", null) == null ? "not checked yet" : "up to date",
                // an available update is news, not a health problem: no CHECK light for it
                !on || r != null ? null : prefs.getString("upd_ver", null) == null ? Ui.S.NA : Ui.S.GOOD);
        if (at > 0) ui.row(s, "Last checked", duration(Math.max(0, System.currentTimeMillis() - at)) + " ago", null);
        ui.note(s, "Update checks ask api.github.com for the latest release of " + Updater.REPO + ", at most every "
                + "6 hours and only while the app is open. Nothing about this phone is sent.");
        if (on) ui.button(s, "Check now", x -> {
            showSnack("Checking GitHub…");
            io.execute(() -> {
                String err = Updater.check(prefs);
                main.post(() -> {
                    Updater.Release nr = Updater.available(this, prefs);
                    showSnack(err != null ? "Couldn't check: " + err + "." : nr != null
                            ? "Version " + nr.version + " is available on the Status screen." : "You're on the latest version.");
                    render();
                });
            });
        });
        ui.button(s, on ? "Turn update checks off" : "Turn update checks on", x -> {
            prefs.edit().putBoolean("upd_on", !on).apply();
            if (on) prefs.edit().remove("upd_ver").remove("upd_url").apply(); // hide any pending update bay
            render();
        });
    }

    private void serviceNote(Check c) {
        LinearLayout gap = new LinearLayout(this);
        content.addView(gap, new LinearLayout.LayoutParams(-1, ui.px(12)));
        LinearLayout p = ui.panel(content, null);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.TOP);
        head.setPadding(0, ui.px(10), 0, ui.px(4));
        LedView led = new LedView(this, ui);
        led.set(c.s);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ui.px(12), ui.px(12));
        ll.rightMargin = ui.px(10);
        ll.topMargin = ui.px(5);
        head.addView(led, ll);
        TextView cause = ui.text(c.cause, 17, ui.ink, true);
        cause.setLineSpacing(0, 1.2f);
        head.addView(cause, new LinearLayout.LayoutParams(0, -2, 1f));
        p.addView(head);
        Explain.E e = Explain.of("bay:" + c.id);
        if (e != null) {
            LinearLayout ex = ui.explainBody(e, c.s, c.reading);
            ex.setPadding(0, ui.px(8), 0, ui.px(4));
            p.addView(ex);
        }
        TextView fix = ui.silk("What to do", 12, ui.muted);
        fix.setPadding(0, ui.px(12), 0, 0);
        p.addView(fix);
        ui.note(p, c.remedy);
        ui.button(p, c.target == HISTORY ? "Open history" : "Open readings", x -> switchTab(c.target));
    }

    private View legend() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, ui.px(12), 0, ui.px(8));
        Object[][] keys = {{Ui.S.GOOD, "Serving, all services up"}, {Ui.S.WARN, "Hot (≥40 °C) or some services down"},
                {Ui.S.BAD, "Every service down"}, {Ui.S.NA, "No sample: logger asleep"}};
        for (Object[] k : keys) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, ui.px(3), 0, ui.px(3));
            LedView led = new LedView(this, ui);
            led.set((Ui.S) k[0]);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ui.px(10), ui.px(10));
            ll.rightMargin = ui.px(10);
            r.addView(led, ll);
            r.addView(ui.text((String) k[1], 13, ui.muted, false));
            l.addView(r);
        }
        return l;
    }

    // ---------------------------------------------------------------- readings: bays pulled out

    private void readingsTab() {
        Probes.Battery b = Probes.battery(this);
        LinearLayout now = ui.panel(content, "Battery");
        ui.row(now, "Level", b.level + "%", null);
        ui.row(now, "Status", Probes.statusName(b.status), null);
        ui.row(now, "Power source", Probes.plugName(b.plugged), b.plugged != 0 ? Ui.S.GOOD : Ui.S.WARN);
        ui.row(now, "Temperature", fmtTemp(b.tempC) + " · " + band(b.tempC), Probes.tempStatus(b.tempC));
        ui.row(now, "Voltage", String.format(Locale.getDefault(), "%.2f V", b.voltageMv / 1000.0), null);
        if (b.currentMa > 0) {
            double w = b.currentMa * b.voltageMv / 1e6;
            ui.row(now, b.charging() ? "Charging current" : "Current draw",
                    String.format(Locale.getDefault(), "%d mA · %.1f W", b.currentMa, w), b.charging() ? chargeS(b) : null);
        }

        LinearLayout wear = ui.panel(content, "Battery wear");
        ui.row(wear, "Android health flag", Probes.healthName(b.health),
                b.health == BatteryManager.BATTERY_HEALTH_GOOD ? Ui.S.GOOD : Ui.S.BAD);
        ui.row(wear, "Charge cycles", b.cycles >= 0 ? String.valueOf(b.cycles) : "not reported",
                b.cycles < 0 ? Ui.S.NA : b.cycles < 500 ? Ui.S.GOOD : b.cycles < 800 ? Ui.S.WARN : Ui.S.BAD);
        double full = b.estFullMah();
        double[] cap = trustedCapacity(b);
        ui.row(wear, "Live capacity estimate", full > 0 ? String.format(Locale.getDefault(), "%.0f mAh", full) : "needs ≥15% charge", null);
        ui.row(wear, "Design capacity", b.designMah > 0 ? String.format(Locale.getDefault(), "%.0f mAh", b.designMah) : "unknown", null);
        ui.row(wear, "Capacity vs design", cap == null ? "charge to 80% once"
                        : String.format(Locale.getDefault(), "~%.0f%% (%s)", cap[0], day((long) cap[1])),
                cap == null ? Ui.S.NA : cap[0] >= 80 ? Ui.S.GOOD : cap[0] >= 70 ? Ui.S.WARN : Ui.S.BAD);
        ui.row(wear, "Chemistry", b.tech.isEmpty() ? "unknown" : b.tech, null);
        ui.note(wear, "Capacity is the fuel gauge's charge counter divided by the level, graded only from readings "
                + "taken at 80% or more, where the counter's error is smallest. On One UI 6.1 and later, Settings › "
                + "About phone › Battery information shows Samsung's own figure; use it as the tie-breaker.");

        int thermal = Probes.thermalStatus(this);
        float head = Probes.thermalHeadroom(this);
        LinearLayout th = ui.panel(content, "Thermal");
        ui.row(th, "Throttling state", Probes.thermalName(thermal), Probes.thermalS(thermal));
        if (!Float.isNaN(head))
            ui.row(th, "Headroom (10 s ahead)", String.format(Locale.getDefault(), "%.0f%% of limit", head * 100),
                    head < 0.7 ? Ui.S.GOOD : head < 0.95 ? Ui.S.WARN : Ui.S.BAD);
        List<String[]> zones = Probes.thermalZones();
        for (String[] z : zones) ui.row(th, z[0], z[1] + " °C", null);
        if (zones.isEmpty()) ui.note(th, "Per-sensor temperatures are hidden from apps on this phone. Battery "
                + "temperature is the best proxy for heat.");

        List<long[]> f = Probes.cpuFreqs();
        LinearLayout cpu = ui.panel(content, "CPU · " + Runtime.getRuntime().availableProcessors() + " cores");
        boolean any = false;
        for (int i = 0; i < f.size(); i++) {
            long[] c = f.get(i);
            if (c[0] < 0) continue;
            any = true;
            ui.row(cpu, "cpu" + i, String.format(Locale.getDefault(), "%.2f / %.2f GHz", c[0] / 1e6, c[1] / 1e6), null);
        }
        if (!any) ui.note(cpu, "The kernel doesn't show core clocks to apps on this phone.");

        ActivityManager.MemoryInfo mi = Probes.memory(this);
        LinearLayout mem = ui.panel(content, "Memory");
        double ramPct = mi.availMem * 100.0 / mi.totalMem;
        ui.row(mem, "Total", String.format(Locale.getDefault(), "%.1f GB", mi.totalMem / 1e9), null);
        ui.row(mem, "Available", String.format(Locale.getDefault(), "%.1f GB · %.0f%%", mi.availMem / 1e9, ramPct),
                ramPct > 15 ? Ui.S.GOOD : Ui.S.WARN);
        ui.row(mem, "Low-memory state", mi.lowMemory ? "yes, apps being killed" : "no", mi.lowMemory ? Ui.S.BAD : Ui.S.GOOD);

        long[] st = Probes.storage();
        double freePct = st[1] * 100.0 / st[0];
        LinearLayout sto = ui.panel(content, "Storage");
        ui.row(sto, "Capacity", Probes.gb(st[0]), null);
        ui.row(sto, "Free", String.format(Locale.getDefault(), "%s · %.0f%%", Probes.gb(st[1]), freePct),
                freePct > 15 ? Ui.S.GOOD : freePct > 5 ? Ui.S.WARN : Ui.S.BAD);
        List<String[]> runs = benchRuns();
        for (int i = Math.max(0, runs.size() - 4); i < runs.size(); i++) {
            String[] r = runs.get(i);
            ui.row(sto, "Speed · " + day(Long.parseLong(r[0])) + (i == 0 ? " (baseline)" : ""),
                    String.format(Locale.getDefault(), "%.0f MB/s · %.0f IOPS", Double.parseDouble(r[1]), Double.parseDouble(r[2])),
                    i == runs.size() - 1 ? benchS(runs) : null);
        }
        ui.note(sto, "Writes 256 MB with fsync, then synced 4 KB random writes for 3 s. The first run is kept as "
                + "the baseline; a drop of more than 30% means the flash is wearing.");
        Button bb = ui.button(sto, benchRunning ? "Testing… about 10 s" : "Run storage speed test", v -> runBench());
        bb.setEnabled(!benchRunning);
        bb.setAlpha(benchRunning ? 0.6f : 1f);

        Probes.Signal s = Probes.signal(this);
        LinearLayout c = ui.panel(content, "Network");
        ui.row(c, "Active network", !s.online ? "offline" : s.wifi ? "Wi-Fi" : s.cellular ? "Mobile data" : "other",
                s.online ? Ui.S.GOOD : Ui.S.BAD);
        ui.row(c, "Internet reachable", s.validated ? "yes" : "no", s.validated ? Ui.S.GOOD : Ui.S.WARN);
        ui.row(c, "Latency to 1.1.1.1", latencyMs == -2 ? "measuring…" : latencyMs < 0 ? "no reply" : latencyMs + " ms",
                latencyMs < 0 ? Ui.S.NA : latencyMs < 80 ? Ui.S.GOOD : latencyMs < 200 ? Ui.S.WARN : Ui.S.BAD);
        if (s.wifi && s.wifiRssi != Integer.MIN_VALUE) ui.row(c, "Wi-Fi signal", s.wifiRssi + " dBm", Probes.signalS(s));
        if (s.downKbps > 0)
            ui.row(c, "Link estimate", String.format(Locale.getDefault(), "↓ %.1f  ↑ %.1f Mbps", s.downKbps / 1000.0, s.upKbps / 1000.0), null);

        LinearLayout cell = ui.panel(content, "Mobile" + (s.operator.isEmpty() ? "" : " · " + s.operator));
        if (s.cells.isEmpty()) ui.note(cell, "No cell signal reported (no SIM, or airplane mode).");
        for (String x : s.cells) ui.row(cell, x.split(" {2}")[0], x.substring(x.indexOf("  ") + 2),
                s.level < 0 ? Ui.S.NA : s.level >= 3 ? Ui.S.GOOD : s.level == 2 ? Ui.S.WARN : Ui.S.BAD);
        ui.note(cell, "Above -95 dBm is solid on LTE/5G. Below -110 dBm, the radio burns battery hunting for a tower.");

        softwarePanel();
    }

    /**
     * Capacity % from the latest reading taken at ≥80% charge, as {pct, epochMs}, or null.
     * Low-charge estimates swing several points on counter offset alone, so they never grade the battery.
     */
    private double[] trustedCapacity(Probes.Battery b) {
        double hp = b.estHealthPct();
        String saved = prefs.getString("cap", null);
        long now = System.currentTimeMillis();
        if (hp > 0 && b.level >= 80
                && (saved == null || now - Long.parseLong(saved.split("\\|")[1]) > 3600_000L)) {
            saved = hp + "|" + now;
            prefs.edit().putString("cap", saved).apply();
        }
        if (saved == null) return null;
        String[] p = saved.split("\\|");
        return new double[]{Double.parseDouble(p[0]), Long.parseLong(p[1])};
    }

    private Ui.S chargeS(Probes.Battery b) {
        if (b.level >= 90) return Ui.S.NA; // taper near full is normal
        return b.currentMa >= 1000 ? Ui.S.GOOD : b.currentMa >= 500 ? Ui.S.WARN : Ui.S.BAD;
    }

    private List<String[]> benchRuns() {
        List<String[]> out = new ArrayList<>();
        for (String line : prefs.getString("bench", "").split("\n"))
            if (line.split("\\|").length == 3) out.add(line.split("\\|"));
        return out;
    }

    private Ui.S benchS(List<String[]> runs) {
        if (runs.size() < 2) return Ui.S.NA;
        double first = Double.parseDouble(runs.get(0)[2]), last = Double.parseDouble(runs.get(runs.size() - 1)[2]);
        return last >= first * 0.7 ? Ui.S.GOOD : Ui.S.WARN;
    }

    private void runBench() {
        benchRunning = true;
        render();
        io.execute(() -> {
            String err = null;
            try {
                double[] r = Probes.storageBench(getCacheDir());
                List<String> lines = new ArrayList<>();
                for (String[] x : benchRuns()) lines.add(String.join("|", x));
                lines.add(System.currentTimeMillis() + "|" + r[0] + "|" + r[1]);
                // Keep the first run as the baseline plus the latest seven.
                while (lines.size() > 8) lines.remove(1);
                prefs.edit().putString("bench", String.join("\n", lines)).apply();
            } catch (Exception e) {
                err = e.getMessage();
            }
            String fe = err;
            main.post(() -> {
                benchRunning = false;
                showSnack(fe != null ? "Speed test failed: " + fe : "Speed test recorded.");
                render();
            });
        });
    }

    private String signalSummary(Probes.Signal s) {
        if (!s.online) return "offline";
        if (s.wifi && s.wifiRssi != Integer.MIN_VALUE) return s.wifiRssi + " dBm";
        if (s.bestDbm != Integer.MIN_VALUE) return s.bestDbm + " dBm";
        return s.wifi ? "Wi-Fi" : "mobile";
    }

    // ---------------------------------------------------------------- history: while nobody watched

    /** {S, sentence, short reading}: has the background sampler actually been running? */
    private Object[] loggerVerdict() {
        if (history.isEmpty()) return new Object[]{Ui.S.NA, "nothing yet", "…"};
        long age = System.currentTimeMillis() - history.get(history.size() - 1).t;
        return new Object[]{age < 45 * 60_000L ? Ui.S.GOOD : Ui.S.WARN, duration(age) + " ago", shortAge(age)};
    }

    private void historyTab() {
        long now = System.currentTimeMillis();
        List<History.Sample> week = History.since(history, now - History.KEEP_MS);
        List<History.Sample> day = History.since(week, now - DAY);

        LinearLayout svc = ui.panel(content, "Watched services");
        List<Probes.Port> ps = ports();
        boolean[] up = portsUp;
        for (int i = 0; i < ps.size(); i++) {
            Probes.Port p = ps.get(i);
            boolean known = up != null && up.length == ps.size();
            ui.row(svc, p.name + "  :" + p.port, !known ? "checking…" : up[i] ? "listening" : "down",
                    !known ? Ui.S.NA : up[i] ? Ui.S.GOOD : Ui.S.BAD);
        }
        ui.note(svc, "Each port is probed on 127.0.0.1. Edit the list as port name, comma-separated.");
        portsEdit = new EditText(this);
        portsEdit.setText(prefs.getString("ports", Probes.DEFAULT_PORTS));
        portsEdit.setTextColor(ui.ink);
        portsEdit.setTextSize(15);
        portsEdit.setFontFeatureSettings("tnum");
        portsEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        portsEdit.setSingleLine(true);
        portsEdit.setBackground(ui.rounded(ui.bezel, 6));
        portsEdit.setPadding(ui.px(12), 0, ui.px(12), 0);
        portsEdit.setMinHeight(ui.px(48));
        svc.addView(portsEdit, new LinearLayout.LayoutParams(-1, ui.px(48)));
        ui.button(svc, "Save ports", x -> {
            prefs.edit().putString("ports", portsEdit.getText().toString()).apply();
            portsEdit.clearFocus();
            portsUp = null;
            asyncAt = 0;
            showSnack("Ports saved. Probing now.");
            render();
        });

        LinearLayout ch = ui.panel(content, "Last 7 days");
        int hot = 0;
        for (History.Sample s : day) if (s.temp >= 40) hot++;
        // A fresh install hasn't had 24 h to collect 96 samples; expect only what time allows.
        long logging = week.isEmpty() ? 0 : now - week.get(0).t;
        int expected = (int) Math.max(1, Math.min(96, logging / (15 * 60_000L)));
        // Coverage counts quarter-hour slots with at least one sample, so extra samples from
        // opening the app can't push it past 100%.
        java.util.Set<Long> slots = new java.util.HashSet<>();
        for (History.Sample s : day) slots.add(s.t / (15 * 60_000L));
        int covered = Math.min(slots.size(), expected);
        ui.row(ch, "Logged in last 24 h", covered + " of " + expected + " quarter-hours",
                covered >= expected * 0.8 ? Ui.S.GOOD : covered >= expected * 0.4 ? Ui.S.WARN : Ui.S.BAD);
        ui.row(ch, "Time at 40 °C or more", day.isEmpty() ? "—" : String.format(Locale.getDefault(), "≈%.1f h in 24 h", hot * 0.25),
                hot == 0 ? Ui.S.GOOD : hot <= 8 ? Ui.S.WARN : Ui.S.BAD);
        // Data lines are ink; only the dashed 40 °C limit carries a state colour.
        chart(ch, "Battery temperature, °C", week, s -> s.temp, History.KEEP_MS, ui.data(), "°", 25, 45, 40);
        chart(ch, "Services up, %", week, s -> s.portsTotal == 0 ? Double.NaN : s.portsUp * 100.0 / s.portsTotal,
                History.KEEP_MS, ui.data(), "%", 0, 100, Double.NaN);
        chart(ch, "Charge level (24 h), %", day, s -> s.level, DAY, ui.data(), "%", 0, 100, Double.NaN);
        ui.note(ch, "Breaks in a line mean the logger was asleep; the phone could have been anything then.");

        boolean termux = installed("com.termux");
        LinearLayout keep = ui.panel(content, "Keep it alive");
        ui.row(keep, "Termux", termux ? "installed" : "not installed", termux ? Ui.S.GOOD : Ui.S.NA);
        Object[] lg = loggerVerdict();
        ui.row(keep, "Phone Health logger", "last sample " + lg[1], (Ui.S) lg[0]);
        ui.note(keep, "One UI puts idle apps to sleep, which kills both your server and this logger. Set both to "
                + "Unrestricted battery use and add them to Never sleeping apps (Settings › Battery › Background "
                + "usage limits).");
        if (termux) ui.button(keep, "Termux app settings", x -> openAppSettings("com.termux"));
        ui.button(keep, "Phone Health app settings", x -> openAppSettings(getPackageName()));
    }

    private void chart(LinearLayout parent, String title, List<History.Sample> data,
                       java.util.function.ToDoubleFunction<History.Sample> f, long window, int color,
                       String unit, double min, double max, double warnAt) {
        TextView t = ui.silk(title, 12, ui.muted);
        t.setPadding(0, ui.px(16), 0, ui.px(6));
        parent.addView(t);
        ChartView cv = new ChartView(this, ui);
        cv.set(data, f, window, color, unit, min, max, warnAt);
        parent.addView(cv);
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openAppSettings(String pkg) {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    // ---------------------------------------------------------------- tests: a service procedure

    private void testsTab() {
        int pass = 0, fail = 0;
        for (String id : TEST_IDS) {
            String r = prefs.getString("t_" + id, null);
            if (r == null) continue;
            if (r.startsWith("P")) pass++; else fail++;
        }
        LinearLayout c = ui.panel(content, "Hardware procedure · " + pass + " passed · " + fail + " failed");
        ui.note(c, "Run every step once for a baseline, then rerun a step when something feels off.");
        for (int i = 0; i < TEST_IDS.length; i++) {
            final int idx = i;
            if (i > 0) {
                View div = new View(this);
                div.setBackgroundColor(ui.rule);
                c.addView(div, new LinearLayout.LayoutParams(-1, Math.max(1, ui.px(1) / 2 + 1)));
            }
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, ui.px(14), 0, ui.px(14));
            String r = prefs.getString("t_" + TEST_IDS[i], null);
            String[] p = r == null ? null : r.split("\\|", 3);
            boolean ok = p != null && "P".equals(p[0]);
            Ui.S st = p == null ? Ui.S.NA : ok ? Ui.S.GOOD : Ui.S.BAD;

            // margin column: step number over its LED and state word
            LinearLayout margin = new LinearLayout(this);
            margin.setOrientation(LinearLayout.VERTICAL);
            TextView num = ui.silk(String.format(Locale.ROOT, "%02d", i + 1), 22, ui.muted);
            num.setFontFeatureSettings("tnum");
            margin.addView(num);
            LinearLayout state = new LinearLayout(this);
            state.setGravity(Gravity.CENTER_VERTICAL);
            state.setPadding(0, ui.px(6), 0, 0);
            LedView led = new LedView(this, ui);
            led.set(st);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ui.px(10), ui.px(10));
            ll.rightMargin = ui.px(5);
            state.addView(led, ll);
            state.addView(ui.silk(p == null ? "untested" : ok ? "pass" : "fail", 11, st == Ui.S.BAD ? ui.badText : ui.muted));
            margin.addView(state);
            row.addView(margin, new LinearLayout.LayoutParams(ui.px(72), -2));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(ui.text(TEST_NAMES[i], 16, ui.ink, true));
            TextView hint = ui.text(TEST_HINTS[i], 13, ui.muted, false);
            hint.setPadding(0, ui.px(4), 0, 0);
            hint.setLineSpacing(0, 1.2f);
            col.addView(hint);
            if (p != null) {
                TextView rt = ui.reading(day(Long.parseLong(p[1]))
                        + (p.length > 2 && !p[2].isEmpty() ? " · " + p[2] : ""), 13, ok ? ui.ink : ui.badText);
                rt.setPadding(0, ui.px(6), 0, 0);
                col.addView(rt);
            }
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            Button run = ui.touchButton(r == null ? "Run" : "Rerun", v -> runTest(idx));
            run.setContentDescription((r == null ? "Run " : "Rerun ") + TEST_NAMES[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, ui.px(48));
            lp.leftMargin = ui.px(12);
            lp.gravity = Gravity.TOP;
            row.addView(run, lp);
            c.addView(row);
        }
    }

    private void runTest(int i) {
        switch (TEST_IDS[i]) {
            case "pixels": case "touch": case "keys":
                startActivityForResult(new Intent(this, FullscreenTestActivity.class)
                        .putExtra(FullscreenTestActivity.MODE, TEST_IDS[i]), i);
                break;
            case "speaker":
                HwTests.tone(this, false, r -> resolve(i, r, "Did you hear a clean rising tone from the loudspeaker?"));
                break;
            case "earpiece":
                showSnack("Hold the phone to your ear.");
                main.postDelayed(() -> HwTests.tone(this, true,
                        r -> resolve(i, r, "Did you hear the tone from the earpiece at the top?")), 1500);
                break;
            case "mic":
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    return;
                }
                showSnack("Recording for 3 s. Clap or talk.");
                HwTests.mic(r -> resolve(i, r, null));
                break;
            case "vibrate":
                HwTests.vibrate(this, r -> resolve(i, r, "Did you feel two firm pulses?"));
                break;
            case "torch":
                HwTests.torch(this, r -> resolve(i, r, "Did the flashlight come on?"));
                break;
            case "sensors":
                showSnack("Tilt the phone and wave a hand over the top edge…");
                HwTests.sensors(this, r -> resolve(i, r, null));
                break;
        }
    }

    /** r == null → ask the human; otherwise r is "P|detail" or "F|detail". */
    private void resolve(int i, String r, String question) {
        if (isFinishing()) return;
        if (r == null) {
            new AlertDialog.Builder(this)
                    .setTitle(TEST_NAMES[i])
                    .setMessage(question)
                    .setPositiveButton("Pass", (d, w) -> saveTest(i, true, ""))
                    .setNegativeButton("Fail", (d, w) -> saveTest(i, false, ""))
                    .setNeutralButton("Replay", (d, w) -> runTest(i))
                    .show();
        } else {
            boolean pass = r.startsWith("P");
            String detail = r.length() > 2 ? r.substring(2) : "";
            saveTest(i, pass, detail);
            showSnack(TEST_NAMES[i] + ": " + (pass ? "pass" : "fail") + (detail.isEmpty() ? "" : " · " + detail));
        }
    }

    private void saveTest(int i, boolean pass, String detail) {
        prefs.edit().putString("t_" + TEST_IDS[i], (pass ? "P" : "F") + "|" + System.currentTimeMillis()
                + "|" + detail.replace("|", "/")).apply();
        if (tab != TESTS) switchTab(TESTS); else render();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req < 0 || req >= TEST_IDS.length || data == null) return;
        String d = data.getStringExtra(FullscreenTestActivity.DETAIL);
        saveTest(req, res == RESULT_OK, d == null ? "" : d);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req != REQ_MIC) return;
        int mic = java.util.Arrays.asList(TEST_IDS).indexOf("mic");
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) runTest(mic);
        else showSnack("The microphone step needs the mic permission. Nothing was recorded as failed.");
    }

    // ---------------------------------------------------------------- formatting

    private static String fmtTemp(float t) {
        return Float.isNaN(t) ? "unknown" : String.format(Locale.getDefault(), "%.1f °C", t);
    }

    private static String fmtTempShort(float t) {
        return Float.isNaN(t) ? "—" : String.format(Locale.getDefault(), "%.1f°", t);
    }

    private static String day(long ms) { return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(ms)); }

    private static String shortAge(long ms) {
        long m = ms / 60_000;
        if (m < 60) return m + " min";
        if (m < 60 * 24) return (m / 60) + " h";
        return (m / 60 / 24) + " d";
    }

    private static String duration(long ms) {
        long m = ms / 60_000, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        return m + " min";
    }
}
