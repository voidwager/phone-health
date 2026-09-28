package dev.voidwager.phonehealth;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.telephony.CellSignalStrength;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Every reading the app shows. Each probe degrades to "unknown" rather than throwing. */
final class Probes {
    private Probes() {}

    // ---------------------------------------------------------------- battery

    static final class Battery {
        int level = -1, health, status, plugged, voltageMv, cycles = -1;
        float tempC = Float.NaN;
        String tech = "";
        long currentMa = -1;   // magnitude only; direction comes from status
        long chargeMah = -1;
        double designMah = -1;

        boolean charging() { return status == BatteryManager.BATTERY_STATUS_CHARGING; }

        /** Full capacity implied by the fuel gauge's charge counter. Noisy below ~15%. */
        double estFullMah() { return chargeMah > 0 && level >= 15 ? chargeMah * 100.0 / level : -1; }

        double estHealthPct() {
            double f = estFullMah();
            return f > 0 && designMah > 0 ? Math.min(100.0, f * 100.0 / designMah) : -1;
        }
    }

    static Battery battery(Context c) {
        Battery b = new Battery();
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i != null) {
            int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            b.level = lvl < 0 ? -1 : Math.round(lvl * 100f / scale);
            b.health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            b.status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            b.plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            b.voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            int t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (t != Integer.MIN_VALUE) b.tempC = t / 10f;
            String tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            b.tech = tech == null ? "" : tech;
            if (Build.VERSION.SDK_INT >= 34) b.cycles = i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
        }
        BatteryManager bm = c.getSystemService(BatteryManager.class);
        if (bm != null) {
            // Units are supposed to be µA / µAh, but Samsung kernels often report mA / mAh.
            // Nothing a running phone does draws under 10 mA, so small values are already mA.
            int cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (cur != Integer.MIN_VALUE && cur != 0) {
                long a = Math.abs((long) cur);
                b.currentMa = a < 10_000 ? a : a / 1000;
            }
            int cc = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (cc > 0 && cc != Integer.MIN_VALUE) b.chargeMah = cc < 100_000 ? cc : cc / 1000;
        }
        b.designMah = designCapacity(c);
        return b;
    }

    private static double designCapacity(Context c) {
        try {
            Class<?> pp = Class.forName("com.android.internal.os.PowerProfile");
            Object o = pp.getConstructor(Context.class).newInstance(c);
            double cap = (double) pp.getMethod("getBatteryCapacity").invoke(o);
            if (cap >= 1500) return cap;   // AOSP's placeholder profile says 1000
        } catch (Throwable ignored) {
        }
        if (Build.MODEL != null && Build.MODEL.startsWith("SM-A166")) return 5000; // Galaxy A16 5G
        return -1;
    }

    static String healthName(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over-voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Failure";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }

    static String plugName(int p) {
        switch (p) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "Charger";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            case 8: return "Dock";
            default: return "Unplugged";
        }
    }

    static String statusName(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
            default: return "Unknown";
        }
    }

    static Ui.S tempStatus(float t) {
        if (Float.isNaN(t)) return Ui.S.NA;
        return t < 40 ? Ui.S.GOOD : t < 45 ? Ui.S.WARN : Ui.S.BAD;
    }

    // ---------------------------------------------------------------- thermal / cpu

    static int thermalStatus(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        return pm == null ? -1 : pm.getCurrentThermalStatus();
    }

    /** 1.0 = the point where the OS starts severe throttling. NaN if unsupported. */
    static float thermalHeadroom(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        try {
            return pm == null ? Float.NaN : pm.getThermalHeadroom(10);
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    static final String[] THERMAL = {"None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown"};

    static String thermalName(int s) { return s >= 0 && s < THERMAL.length ? THERMAL[s] : "Unknown"; }

    static Ui.S thermalS(int s) {
        if (s < 0) return Ui.S.NA;
        return s <= 1 ? Ui.S.GOOD : s == 2 ? Ui.S.WARN : Ui.S.BAD;
    }

    /** {type, °C} for each readable thermal zone. Usually empty: SELinux hides them from apps. */
    static List<String[]> thermalZones() {
        List<String[]> out = new ArrayList<>();
        File[] zs = new File("/sys/class/thermal").listFiles();
        if (zs == null) return out;
        for (File z : zs) {
            if (!z.getName().startsWith("thermal_zone")) continue;
            String type = readLine(new File(z, "type"));
            long t = readLong(new File(z, "temp"));
            if (type == null || t == Long.MIN_VALUE) continue;
            double tc = t > 1000 ? t / 1000.0 : t;
            if (tc > 0 && tc < 130) out.add(new String[]{type, String.format("%.1f", tc)});
        }
        return out;
    }

    /** Per core {current kHz, max kHz}; -1 where the kernel won't say. */
    static List<long[]> cpuFreqs() {
        List<long[]> out = new ArrayList<>();
        for (int n = 0; n < 16; n++) {
            File d = new File("/sys/devices/system/cpu/cpu" + n);
            if (!d.exists()) break;
            long cur = readLong(new File(d, "cpufreq/scaling_cur_freq"));
            long max = readLong(new File(d, "cpufreq/cpuinfo_max_freq"));
            out.add(new long[]{cur == Long.MIN_VALUE ? -1 : cur, max == Long.MIN_VALUE ? -1 : max});
        }
        return out;
    }

    // ---------------------------------------------------------------- memory / storage

    static ActivityManager.MemoryInfo memory(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = c.getSystemService(ActivityManager.class);
        if (am != null) am.getMemoryInfo(mi);
        return mi;
    }

    /** {total, available} bytes for /data. */
    static long[] storage() {
        StatFs s = new StatFs(Environment.getDataDirectory().getPath());
        return new long[]{s.getTotalBytes(), s.getAvailableBytes()};
    }

    static String gb(long bytes) { return String.format("%.1f GB", bytes / 1e9); }

    /**
     * Sequential write (fsync'd, so it measures flash rather than the page cache) and
     * 4 KB synchronous random writes — the number that falls first as flash wears.
     * Returns {MB/s, IOPS}.
     */
    static double[] storageBench(File dir) throws Exception {
        File f = new File(dir, "bench.bin");
        byte[] chunk = new byte[4 << 20];
        new Random(1).nextBytes(chunk);
        final int chunks = 64; // 256 MB
        long t0 = System.nanoTime();
        try (FileOutputStream o = new FileOutputStream(f)) {
            for (int i = 0; i < chunks; i++) o.write(chunk);
            o.getFD().sync();
        }
        double seq = chunks * 4.0 / ((System.nanoTime() - t0) / 1e9);

        ByteBuffer small = ByteBuffer.allocate(4096);
        Random r = new Random(2);
        long blocks = f.length() / 4096, ops = 0;
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw"); FileChannel ch = raf.getChannel()) {
            long end = System.nanoTime() + 3_000_000_000L;
            t0 = System.nanoTime();
            while (System.nanoTime() < end) {
                small.clear();
                ch.write(small, (long) (r.nextDouble() * blocks) * 4096);
                ch.force(false);
                ops++;
            }
        }
        double iops = ops / ((System.nanoTime() - t0) / 1e9);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        return new double[]{seq, iops};
    }

    // ---------------------------------------------------------------- network

    static final class Signal {
        String operator = "";
        final List<String> cells = new ArrayList<>();
        int level = -1, bestDbm = Integer.MIN_VALUE, wifiRssi = Integer.MIN_VALUE;
        int downKbps = -1, upKbps = -1;
        boolean wifi, cellular, validated, online;
    }

    static Signal signal(Context c) {
        Signal s = new Signal();
        TelephonyManager tm = c.getSystemService(TelephonyManager.class);
        if (tm != null) {
            try {
                s.operator = tm.getNetworkOperatorName();
                SignalStrength ss = tm.getSignalStrength();
                if (ss != null) {
                    s.level = ss.getLevel();
                    for (CellSignalStrength cs : ss.getCellSignalStrengths()) {
                        int d = cs.getDbm();
                        String name = cs.getClass().getSimpleName().replace("CellSignalStrength", "");
                        switch (name) {
                            case "Nr": name = "5G NR"; break;
                            case "Lte": name = "4G LTE"; break;
                            case "Wcdma": case "Tdscdma": name = "3G"; break;
                            case "Gsm": name = "2G"; break;
                        }
                        boolean known = d != Integer.MAX_VALUE && d != Integer.MIN_VALUE;
                        s.cells.add(name + "  " + (known ? d + " dBm" : "?") + "  (" + cs.getLevel() + "/4)");
                        if (known && d > s.bestDbm) s.bestDbm = d;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        if (cm != null) {
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc != null) {
                s.online = true;
                s.wifi = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                s.cellular = nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                s.validated = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                s.downKbps = nc.getLinkDownstreamBandwidthKbps();
                s.upKbps = nc.getLinkUpstreamBandwidthKbps();
                if (s.wifi) s.wifiRssi = nc.getSignalStrength();
            }
        }
        return s;
    }

    static Ui.S signalS(Signal s) {
        if (!s.online) return Ui.S.BAD;
        if (s.wifi && s.wifiRssi != Integer.MIN_VALUE)
            return s.wifiRssi >= -67 ? Ui.S.GOOD : s.wifiRssi >= -75 ? Ui.S.WARN : Ui.S.BAD;
        if (s.level < 0) return Ui.S.NA;
        return s.level >= 3 ? Ui.S.GOOD : s.level == 2 ? Ui.S.WARN : Ui.S.BAD;
    }

    /** TCP connect time in ms, or -1. Blocking — call off the main thread. */
    static long tcpConnectMs(String host, int port, int timeoutMs) {
        long t0 = System.nanoTime();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return (System.nanoTime() - t0) / 1_000_000;
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- host ports

    static final class Port {
        final int port;
        final String name;
        Port(int port, String name) { this.port = port; this.name = name; }
    }

    static final String DEFAULT_PORTS = "8080 dashboard, 25565 minecraft";

    /** "8080 dashboard, 25565 minecraft" -> ports. Bad entries are skipped. */
    static List<Port> parsePorts(String spec) {
        List<Port> out = new ArrayList<>();
        for (String part : spec.split(",")) {
            String[] w = part.trim().split("\\s+", 2);
            try {
                int p = Integer.parseInt(w[0]);
                if (p > 0 && p < 65536) out.add(new Port(p, w.length > 1 ? w[1] : "port " + p));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** Which of the ports accept a connection on this phone. Blocking. */
    static boolean[] probePorts(List<Port> ports) {
        boolean[] up = new boolean[ports.size()];
        for (int i = 0; i < up.length; i++) up[i] = tcpConnectMs("127.0.0.1", ports.get(i).port, 400) >= 0;
        return up;
    }

    // ---------------------------------------------------------------- sysfs helpers

    private static String readLine(File f) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String s = r.readLine();
            return s == null ? null : s.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static long readLong(File f) {
        String s = readLine(f);
        if (s == null) return Long.MIN_VALUE;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }
}
