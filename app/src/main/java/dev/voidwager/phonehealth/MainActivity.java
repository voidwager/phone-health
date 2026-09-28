package dev.voidwager.phonehealth;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String PREFS = "ph";
    private static final String[] TABS = {"Overview", "Battery", "System", "Network", "Host", "Tests"};
    private static final int OVERVIEW = 0, BATTERY = 1, SYSTEM = 2, NETWORK = 3, HOST = 4, TESTS = 5;
    private static final long DAY = 24 * 3600 * 1000L;

    private static final String[] TEST_IDS = {"pixels", "touch", "keys", "speaker", "earpiece", "mic",
            "vibrate", "torch", "sensors"};
    private static final String[] TEST_NAMES = {"Screen pixels", "Touch grid", "Volume buttons", "Loudspeaker",
            "Earpiece", "Microphone", "Vibration motor", "Flashlight", "Motion & light sensors"};
    private static final String[] TEST_HINTS = {
            "Six solid colours: spot dead or stuck pixels and burn-in",
            "Paint every cell to find dead zones in the digitiser",
            "Press volume up, then volume down",
            "2 s sweep, 300 Hz to 3 kHz. Listen for rattle or dropouts",
            "Same sweep through the call speaker. Hold the phone to your ear",
            "Clap or talk for 3 s. Passes on its own above -40 dBFS",
            "Two firm pulses",
            "Torch on for 1.5 s",
            "Listens for 3 s. Tilt the phone and wave a hand over the top edge while it runs"};
    private static final int REQ_MIC = 100;

    private Ui ui;
    private SharedPreferences prefs;
    private LinearLayout tabBar, content;
    private int tab = OVERVIEW;
    private EditText portsEdit;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean[] portsUp;
    private volatile long latencyMs = -2, asyncAt;
    private volatile boolean benchRunning;
    private List<History.Sample> history = new ArrayList<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            boolean typing = portsEdit != null && portsEdit.hasFocus();
            if (tab != TESTS && !typing) render();
            main.postDelayed(this, 2000);
        }
    };

    // ================================================================ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        prefs = getSharedPreferences(PREFS, 0);
        if (b != null) tab = b.getInt("tab", OVERVIEW);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.bg);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(ui.px(20), ui.px(14), ui.px(20), ui.px(6));
        head.addView(ui.text("Phone Health", 26, ui.text, true));
        head.addView(ui.text(Build.MANUFACTURER + " " + Build.MODEL + "  ·  Android " + Build.VERSION.RELEASE,
                13, ui.muted, false));
        root.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabBar = new LinearLayout(this);
        tabBar.setPadding(ui.px(14), ui.px(6), ui.px(14), ui.px(8));
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            TextView chip = ui.text(TABS[i], 14, ui.text, true);
            chip.setPadding(ui.px(14), ui.px(8), ui.px(14), ui.px(8));
            chip.setOnClickListener(v -> { tab = t; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = ui.px(6);
            tabBar.addView(chip, lp);
        }
        hs.addView(tabBar);
        root.addView(hs);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.px(14), ui.px(4), ui.px(14), ui.px(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.setOnApplyWindowInsetsListener((v, in) -> {
            Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(root);
        WindowInsetsController wic = getWindow().getInsetsController();
        if (wic != null && ui.bg != 0xFF0F1115) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            wic.setSystemBarsAppearance(light, light);
        }
        SampleJob.schedule(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        io.execute(() -> {
            List<History.Sample> h = History.load(this);
            // Seed the log on open so the charts aren't empty for the first 15 minutes.
            if (h.isEmpty() || System.currentTimeMillis() - h.get(h.size() - 1).t > 10 * 60 * 1000L) {
                History.append(this, History.capture(this));
                h = History.load(this);
            }
            List<History.Sample> fh = h;
            main.post(() -> { history = fh; render(); });
        });
        render();
        main.postDelayed(tick, 2000);
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
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            TextView chip = (TextView) tabBar.getChildAt(i);
            boolean on = i == tab;
            chip.setBackground(ui.rounded(on ? ui.accent : ui.card, 18));
            chip.setTextColor(on ? 0xFFFFFFFF : ui.text);
        }
        content.removeAllViews();
        portsEdit = null;
        refreshAsync();
        switch (tab) {
            case OVERVIEW: overview(); break;
            case BATTERY: batteryTab(); break;
            case SYSTEM: systemTab(); break;
            case NETWORK: networkTab(); break;
            case HOST: hostTab(); break;
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
                if ((tab == OVERVIEW || tab == NETWORK || tab == HOST) && !typing) render();
            });
        });
    }

    private List<Probes.Port> ports() { return Probes.parsePorts(prefs.getString("ports", Probes.DEFAULT_PORTS)); }

    // ---------------------------------------------------------------- overview

    private void overview() {
        Probes.Battery b = Probes.battery(this);
        int thermal = Probes.thermalStatus(this);
        long[] st = Probes.storage();
        ActivityManager.MemoryInfo mi = Probes.memory(this);
        Probes.Signal sig = Probes.signal(this);

        List<Object[]> rows = new ArrayList<>();

        double[] cap = trustedCapacity(b);
        double hp = cap == null ? -1 : cap[0];
        Ui.S bh = b.health != android.os.BatteryManager.BATTERY_HEALTH_GOOD ? Ui.S.BAD
                : hp < 0 ? Ui.S.GOOD : hp >= 80 ? Ui.S.GOOD : hp >= 70 ? Ui.S.WARN : Ui.S.BAD;
        rows.add(new Object[]{"Battery", Probes.healthName(b.health)
                + (hp > 0 ? String.format("  ·  ~%.0f%% capacity", hp) : "  ·  charge to 80% to estimate"), bh});
        rows.add(new Object[]{"Battery temperature", fmtTemp(b.tempC), Probes.tempStatus(b.tempC)});
        rows.add(new Object[]{"Thermal throttling", Probes.thermalName(thermal), Probes.thermalS(thermal)});

        double freePct = st[1] * 100.0 / st[0];
        rows.add(new Object[]{"Storage free", String.format("%s  (%.0f%%)", Probes.gb(st[1]), freePct),
                freePct > 15 ? Ui.S.GOOD : freePct > 5 ? Ui.S.WARN : Ui.S.BAD});
        double ramPct = mi.availMem * 100.0 / mi.totalMem;
        rows.add(new Object[]{"Memory available", String.format("%.1f GB  (%.0f%%)", mi.availMem / 1e9, ramPct),
                mi.lowMemory ? Ui.S.BAD : ramPct > 15 ? Ui.S.GOOD : Ui.S.WARN});
        rows.add(new Object[]{"Signal", signalSummary(sig), Probes.signalS(sig)});

        Object[] host = hostVerdict(b);
        rows.add(new Object[]{"Hosting", host[1], host[0]});

        int pass = 0, fail = 0;
        for (String id : TEST_IDS) {
            String r = prefs.getString("t_" + id, null);
            if (r == null) continue;
            if (r.startsWith("P")) pass++; else fail++;
        }
        rows.add(new Object[]{"Hardware tests", fail > 0 ? fail + " failed · " + pass + " passed"
                : pass + " of " + TEST_IDS.length + " passed",
                fail > 0 ? Ui.S.BAD : pass == TEST_IDS.length ? Ui.S.GOOD : Ui.S.NA});

        Object[] logger = loggerVerdict();
        rows.add(new Object[]{"Background logger", logger[1], logger[0]});

        int warn = 0, bad = 0;
        for (Object[] r : rows) {
            if (r[2] == Ui.S.BAD) bad++;
            else if (r[2] == Ui.S.WARN) warn++;
        }
        LinearLayout v = ui.card(content, null);
        String verdict = bad > 0 ? "Problem found" : warn > 0 ? "Mostly healthy" : "Healthy";
        v.addView(ui.text(verdict, 28, bad > 0 ? ui.bad : warn > 0 ? ui.warn : ui.good, true));
        v.addView(ui.text(bad + " problem" + (bad == 1 ? "" : "s") + " · " + warn + " to watch · "
                + (rows.size() - bad - warn) + " fine", 14, ui.muted, false));

        // Worst first: the thing to act on should be the first thing read.
        LinearLayout list = ui.card(content, "Checks");
        for (Ui.S s : new Ui.S[]{Ui.S.BAD, Ui.S.WARN, Ui.S.GOOD, Ui.S.NA})
            for (Object[] r : rows) if (r[2] == s) ui.row(list, (String) r[0], (String) r[1], s);
    }

    // ---------------------------------------------------------------- battery

    private void batteryTab() {
        Probes.Battery b = Probes.battery(this);
        LinearLayout now = ui.card(content, "Right now");
        ui.row(now, "Level", b.level + "%", null);
        ui.row(now, "Status", Probes.statusName(b.status), null);
        ui.row(now, "Power source", Probes.plugName(b.plugged), null);
        ui.row(now, "Temperature", fmtTemp(b.tempC), Probes.tempStatus(b.tempC));
        ui.row(now, "Voltage", String.format("%.2f V", b.voltageMv / 1000.0), null);
        if (b.currentMa > 0) {
            double w = b.currentMa * b.voltageMv / 1e6;
            ui.row(now, b.charging() ? "Charging current" : "Current draw",
                    String.format("%d mA  ·  %.1f W", b.currentMa, w), b.charging() ? chargeS(b) : null);
        }

        LinearLayout wear = ui.card(content, "Wear");
        ui.row(wear, "Android health flag", Probes.healthName(b.health),
                b.health == android.os.BatteryManager.BATTERY_HEALTH_GOOD ? Ui.S.GOOD : Ui.S.BAD);
        ui.row(wear, "Charge cycles", b.cycles >= 0 ? String.valueOf(b.cycles) : "not reported",
                b.cycles < 0 ? Ui.S.NA : b.cycles < 500 ? Ui.S.GOOD : b.cycles < 800 ? Ui.S.WARN : Ui.S.BAD);
        double full = b.estFullMah();
        double[] cap = trustedCapacity(b);
        ui.row(wear, "Live capacity estimate", full > 0 ? String.format("%.0f mAh", full) : "needs ≥15% charge", null);
        ui.row(wear, "Design capacity", b.designMah > 0 ? String.format("%.0f mAh", b.designMah) : "unknown", null);
        ui.row(wear, "Capacity vs design", cap == null ? "charge to 80% once"
                        : String.format("~%.0f%%  (%s)", cap[0], day((long) cap[1])),
                cap == null ? Ui.S.NA : cap[0] >= 80 ? Ui.S.GOOD : cap[0] >= 70 ? Ui.S.WARN : Ui.S.BAD);
        ui.row(wear, "Chemistry", b.tech.isEmpty() ? "unknown" : b.tech, null);
        ui.note(wear, "Capacity = the fuel gauge's charge counter divided by the level. The graded figure comes only "
                + "from readings taken at 80% charge or more, where the counter's error is smallest. A figure that "
                + "stays at exactly 100% for months suggests the gauge is echoing the design value. On One UI 6.1 and "
                + "later, Settings › About phone › Battery information shows Samsung's own health figure. Use it as "
                + "the tie-breaker.");
        if (b.charging() && b.currentMa > 0 && b.currentMa < 500 && b.level < 90)
            ui.note(wear, "Charging under 500 mA below 90% usually means a weak cable or USB port, not the battery.");

        long now2 = System.currentTimeMillis();
        List<History.Sample> day = History.since(history, now2 - DAY);
        LinearLayout ch = ui.card(content, "Last 24 h");
        ch.addView(ui.text("Temperature", 13, ui.muted, false));
        ChartView t = new ChartView(this, ui);
        t.set(day, s -> s.temp, DAY, ui.warn, "°", 25, 45, 40);
        ch.addView(t);
        ch.addView(ui.text("Level", 13, ui.muted, false));
        ChartView l = new ChartView(this, ui);
        l.set(day, s -> s.level, DAY, ui.good, "%", 0, 100, Double.NaN);
        ch.addView(l);
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

    // ---------------------------------------------------------------- system

    private void systemTab() {
        int thermal = Probes.thermalStatus(this);
        float head = Probes.thermalHeadroom(this);
        LinearLayout th = ui.card(content, "Thermal");
        ui.row(th, "Throttling state", Probes.thermalName(thermal), Probes.thermalS(thermal));
        if (!Float.isNaN(head))
            ui.row(th, "Headroom (10 s ahead)", String.format("%.0f%% of limit", head * 100),
                    head < 0.7 ? Ui.S.GOOD : head < 0.95 ? Ui.S.WARN : Ui.S.BAD);
        List<String[]> zones = Probes.thermalZones();
        for (String[] z : zones) ui.row(th, z[0], z[1] + " °C", null);
        if (zones.isEmpty()) ui.note(th, "Per-sensor temperatures are hidden from apps on this phone. "
                + "Battery temperature (Battery tab) is the best proxy for heat.");

        LinearLayout cpu = ui.card(content, "CPU (" + Runtime.getRuntime().availableProcessors() + " cores)");
        List<long[]> f = Probes.cpuFreqs();
        boolean any = false;
        for (int i = 0; i < f.size(); i++) {
            long[] c = f.get(i);
            if (c[0] < 0) continue;
            any = true;
            ui.row(cpu, "cpu" + i, c[0] < 0 ? "offline"
                    : String.format("%.2f / %.2f GHz", c[0] / 1e6, c[1] / 1e6), null);
        }
        if (!any) ui.note(cpu, "The kernel won't show core clocks to apps here.");

        ActivityManager.MemoryInfo mi = Probes.memory(this);
        LinearLayout mem = ui.card(content, "Memory");
        ui.row(mem, "Total", String.format("%.1f GB", mi.totalMem / 1e9), null);
        double ramPct = mi.availMem * 100.0 / mi.totalMem;
        ui.row(mem, "Available", String.format("%.1f GB  (%.0f%%)", mi.availMem / 1e9, ramPct),
                ramPct > 15 ? Ui.S.GOOD : Ui.S.WARN);
        ui.row(mem, "Low-memory state", mi.lowMemory ? "YES, apps being killed" : "no",
                mi.lowMemory ? Ui.S.BAD : Ui.S.GOOD);

        long[] st = Probes.storage();
        double freePct = st[1] * 100.0 / st[0];
        LinearLayout sto = ui.card(content, "Storage");
        ui.row(sto, "Capacity", Probes.gb(st[0]), null);
        ui.row(sto, "Free", String.format("%s  (%.0f%%)", Probes.gb(st[1]), freePct),
                freePct > 15 ? Ui.S.GOOD : freePct > 5 ? Ui.S.WARN : Ui.S.BAD);
        List<String[]> runs = benchRuns();
        for (int i = Math.max(0, runs.size() - 4); i < runs.size(); i++) {
            String[] r = runs.get(i);
            ui.row(sto, "Speed · " + day(Long.parseLong(r[0])),
                    String.format("%.0f MB/s  ·  %.0f IOPS", Double.parseDouble(r[1]), Double.parseDouble(r[2])),
                    i == runs.size() - 1 ? benchS(runs) : null);
        }
        ui.note(sto, "Writes 256 MB with fsync, then 4 KB synced random writes for 3 s. Run it every few months. "
                + "A drop of more than 30% from your first run means the flash is wearing.");
        Button bb = ui.button(sto, benchRunning ? "Testing… (≈10 s)" : "Run storage speed test", v -> runBench());
        bb.setEnabled(!benchRunning);

        LinearLayout dev = ui.card(content, "Device");
        ui.row(dev, "Up since boot", duration(SystemClock.elapsedRealtime()), null);
        ui.row(dev, "Security patch", Build.VERSION.SECURITY_PATCH, null);
        ui.row(dev, "Build", Build.DISPLAY, null);
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
                if (fe != null) Toast.makeText(this, "Speed test failed: " + fe, Toast.LENGTH_LONG).show();
                render();
            });
        });
    }

    // ---------------------------------------------------------------- network

    private void networkTab() {
        Probes.Signal s = Probes.signal(this);
        LinearLayout c = ui.card(content, "Connection");
        ui.row(c, "Active network", !s.online ? "offline" : s.wifi ? "Wi-Fi" : s.cellular ? "Mobile data" : "other",
                s.online ? Ui.S.GOOD : Ui.S.BAD);
        ui.row(c, "Internet reachable", s.validated ? "yes" : "no", s.validated ? Ui.S.GOOD : Ui.S.WARN);
        ui.row(c, "Latency to 1.1.1.1", latencyMs == -2 ? "measuring…" : latencyMs < 0 ? "no reply"
                : latencyMs + " ms", latencyMs < 0 ? Ui.S.NA : latencyMs < 80 ? Ui.S.GOOD
                : latencyMs < 200 ? Ui.S.WARN : Ui.S.BAD);
        if (s.wifi && s.wifiRssi != Integer.MIN_VALUE)
            ui.row(c, "Wi-Fi signal", s.wifiRssi + " dBm", Probes.signalS(s));
        if (s.downKbps > 0)
            ui.row(c, "Link estimate", String.format("↓ %.1f  ↑ %.1f Mbps", s.downKbps / 1000.0, s.upKbps / 1000.0), null);

        LinearLayout cell = ui.card(content, "Mobile" + (s.operator.isEmpty() ? "" : " · " + s.operator));
        if (s.cells.isEmpty()) ui.note(cell, "No cell signal reported (no SIM, or airplane mode).");
        for (String x : s.cells) ui.row(cell, x.split(" {2}")[0], x.substring(x.indexOf("  ") + 2),
                s.level < 0 ? Ui.S.NA : s.level >= 3 ? Ui.S.GOOD : s.level == 2 ? Ui.S.WARN : Ui.S.BAD);
        ui.note(cell, "Rule of thumb: above -95 dBm is solid on LTE/5G. Below -110 dBm, calls drop and the radio "
                + "burns battery hunting for a tower.");
    }

    private String signalSummary(Probes.Signal s) {
        if (!s.online) return "offline";
        if (s.wifi && s.wifiRssi != Integer.MIN_VALUE) return "Wi-Fi " + s.wifiRssi + " dBm";
        if (s.bestDbm != Integer.MIN_VALUE) return s.bestDbm + " dBm";
        return s.wifi ? "Wi-Fi" : "mobile";
    }

    // ---------------------------------------------------------------- host fitness

    /** {S, text}: is this phone fit to keep hosting right now? */
    private Object[] hostVerdict(Probes.Battery b) {
        List<Probes.Port> ps = ports();
        boolean[] up = portsUp;
        if (up == null || up.length != ps.size()) return new Object[]{Ui.S.NA, "checking…"};
        int n = 0;
        for (boolean u : up) if (u) n++;
        Ui.S s = n == up.length ? Ui.S.GOOD : n == 0 ? Ui.S.BAD : Ui.S.WARN;
        if (b.plugged == 0) s = Ui.worst(s, Ui.S.WARN);
        s = Ui.worst(s, Probes.tempStatus(b.tempC));
        return new Object[]{s, n + "/" + up.length + " services" + (b.plugged == 0 ? " · on battery" : "")};
    }

    /** {S, text}: has the background sampler actually been running? Samsung likes to put it to sleep. */
    private Object[] loggerVerdict() {
        if (history.isEmpty()) return new Object[]{Ui.S.NA, "starting"};
        long age = System.currentTimeMillis() - history.get(history.size() - 1).t;
        return new Object[]{age < 45 * 60_000L ? Ui.S.GOOD : Ui.S.WARN, "last sample " + duration(age) + " ago"};
    }

    private void hostTab() {
        Probes.Battery b = Probes.battery(this);
        Object[] v = hostVerdict(b);
        LinearLayout top = ui.card(content, "Fitness for 24/7 hosting");
        ui.row(top, "Verdict", (String) v[1], (Ui.S) v[0]);
        ui.row(top, "Power", Probes.plugName(b.plugged) + " · " + b.level + "%", b.plugged != 0 ? Ui.S.GOOD : Ui.S.WARN);
        ui.row(top, "Battery temperature", fmtTemp(b.tempC), Probes.tempStatus(b.tempC));
        int th = Probes.thermalStatus(this);
        ui.row(top, "Throttling", Probes.thermalName(th), Probes.thermalS(th));
        ui.row(top, "Up since boot", duration(SystemClock.elapsedRealtime()), null);

        LinearLayout svc = ui.card(content, "Services on this phone");
        List<Probes.Port> ps = ports();
        boolean[] up = portsUp;
        for (int i = 0; i < ps.size(); i++) {
            Probes.Port p = ps.get(i);
            boolean known = up != null && up.length == ps.size();
            ui.row(svc, p.name + "  :" + p.port, !known ? "checking…" : up[i] ? "listening" : "DOWN",
                    !known ? Ui.S.NA : up[i] ? Ui.S.GOOD : Ui.S.BAD);
        }
        ui.note(svc, "A service shows DOWN when nothing accepts connections on 127.0.0.1 at that port. "
                + "If every service is down, Android probably killed Termux.");
        portsEdit = new EditText(this);
        portsEdit.setText(prefs.getString("ports", Probes.DEFAULT_PORTS));
        portsEdit.setTextColor(ui.text);
        portsEdit.setTextSize(14);
        portsEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        portsEdit.setSingleLine(true);
        svc.addView(portsEdit);
        ui.button(svc, "Save ports", x -> {
            prefs.edit().putString("ports", portsEdit.getText().toString()).apply();
            portsEdit.clearFocus();
            portsUp = null;
            asyncAt = 0;
            render();
        });

        boolean termux = installed("com.termux");
        LinearLayout keep = ui.card(content, "Keep it alive");
        ui.row(keep, "Termux", termux ? "installed" : "not installed", termux ? Ui.S.GOOD : Ui.S.NA);
        Object[] lg = loggerVerdict();
        ui.row(keep, "This app's logger", (String) lg[1], (Ui.S) lg[0]);
        ui.note(keep, "One UI puts idle apps to sleep, and that kills both Termux and this logger. Set both to "
                + "Unrestricted battery use, and add them to Never sleeping apps (Settings › Battery › Background "
                + "usage limits).");
        if (termux) ui.button(keep, "Open Termux app settings", x -> openAppSettings("com.termux"));
        ui.button(keep, "Open Phone Health app settings", x -> openAppSettings(getPackageName()));

        long now = System.currentTimeMillis();
        List<History.Sample> week = History.since(history, now - History.KEEP_MS);
        List<History.Sample> day = History.since(week, now - DAY);
        LinearLayout ch = ui.card(content, "Last 7 days");
        int hot = 0;
        for (History.Sample s : day) if (s.temp >= 40) hot++;
        // A fresh install hasn't had 24 h to collect 96 samples; expect only what time allows.
        long logging = week.isEmpty() ? 0 : now - week.get(0).t;
        int expected = (int) Math.max(1, Math.min(96, logging / (15 * 60_000L)));
        ui.row(ch, "Samples in last 24 h", day.size() + " of ~" + expected,
                day.size() >= expected * 0.8 ? Ui.S.GOOD : day.size() >= expected * 0.4 ? Ui.S.WARN : Ui.S.BAD);
        ui.row(ch, "Time at ≥40 °C (24 h)", day.isEmpty() ? "—" : String.format("≈%.1f h", hot * 0.25),
                hot == 0 ? Ui.S.GOOD : hot <= 8 ? Ui.S.WARN : Ui.S.BAD);
        ch.addView(ui.text("Battery temperature", 13, ui.muted, false));
        ChartView t = new ChartView(this, ui);
        t.set(week, s -> s.temp, History.KEEP_MS, ui.warn, "°", 25, 45, 40);
        ch.addView(t);
        ch.addView(ui.text("Services up (%)", 13, ui.muted, false));
        ChartView sv = new ChartView(this, ui);
        sv.set(week, s -> s.portsTotal == 0 ? Double.NaN : s.portsUp * 100.0 / s.portsTotal,
                History.KEEP_MS, ui.good, "%", 0, 100, Double.NaN);
        ch.addView(sv);
        ui.note(ch, "Breaks in a line mean the logger was asleep, so the phone could have been anything then.");
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

    // ---------------------------------------------------------------- tests

    private void testsTab() {
        LinearLayout c = ui.card(content, "Hardware self-test");
        ui.note(c, "Run each test once now for a baseline. Rerun one when something feels off.");
        for (int i = 0; i < TEST_IDS.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, ui.px(10), 0, ui.px(10));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(ui.text(TEST_NAMES[i], 16, ui.text, true));
            col.addView(ui.text(TEST_HINTS[i], 13, ui.muted, false));
            String r = prefs.getString("t_" + TEST_IDS[i], null);
            if (r != null) {
                String[] p = r.split("\\|", 3);
                boolean pass = "P".equals(p[0]);
                TextView res = ui.text((pass ? "PASS" : "FAIL") + " · " + day(Long.parseLong(p[1]))
                        + (p.length > 2 && !p[2].isEmpty() ? " · " + p[2] : ""), 13, pass ? ui.good : ui.bad, true);
                col.addView(res);
            }
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            Button run = new Button(this);
            run.setText(r == null ? "Run" : "Rerun");
            run.setAllCaps(false);
            run.setTextColor(0xFFFFFFFF);
            run.setBackground(ui.rounded(ui.accent, 10));
            run.setOnClickListener(v -> runTest(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.px(84), ui.px(40));
            lp.leftMargin = ui.px(10);
            row.addView(run, lp);
            c.addView(row);
            if (i < TEST_IDS.length - 1) {
                View div = new View(this);
                div.setBackgroundColor(ui.line);
                c.addView(div, new LinearLayout.LayoutParams(-1, ui.px(1)));
            }
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
                Toast.makeText(this, "Hold the phone to your ear", Toast.LENGTH_SHORT).show();
                main.postDelayed(() -> HwTests.tone(this, true,
                        r -> resolve(i, r, "Did you hear the tone from the earpiece at the top?")), 1500);
                break;
            case "mic":
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    return;
                }
                Toast.makeText(this, "Recording 3 s. Clap or talk", Toast.LENGTH_SHORT).show();
                HwTests.mic(r -> resolve(i, r, null));
                break;
            case "vibrate":
                HwTests.vibrate(this, r -> resolve(i, r, "Did you feel two firm pulses?"));
                break;
            case "torch":
                HwTests.torch(this, r -> resolve(i, r, "Did the flashlight come on?"));
                break;
            case "sensors":
                Toast.makeText(this, "Tilt the phone and wave over the top edge…", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, TEST_NAMES[i] + ": " + (pass ? "PASS" : "FAIL") + "\n" + detail,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void saveTest(int i, boolean pass, String detail) {
        prefs.edit().putString("t_" + TEST_IDS[i], (pass ? "P" : "F") + "|" + System.currentTimeMillis()
                + "|" + detail.replace("|", "/")).apply();
        tab = TESTS;
        render();
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
        else Toast.makeText(this, "Microphone test needs the mic permission. Nothing was recorded as failed.",
                Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------- formatting

    private static String fmtTemp(float t) { return Float.isNaN(t) ? "unknown" : String.format("%.1f °C", t); }

    private static String day(long ms) { return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(ms)); }

    private static String duration(long ms) {
        long m = ms / 60_000, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h";
        if (h > 0) return h + "h " + (m % 60) + "m";
        return m + " min";
    }
}
