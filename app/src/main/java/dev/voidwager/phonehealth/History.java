package dev.voidwager.phonehealth;

import android.app.ActivityManager;
import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rolling 7-day log of samples in filesDir/history.csv, written by SampleJob and on app open. */
final class History {
    static final long KEEP_MS = 7L * 24 * 3600 * 1000;

    static final class Sample {
        long t;
        int level, currentMa, thermal, ramFreeMb, portsUp, portsTotal;
        float temp;
        boolean plugged;
    }

    private History() {}

    /** Blocking (probes ports) — never call on the main thread. */
    static Sample capture(Context c) {
        Sample s = new Sample();
        s.t = System.currentTimeMillis();
        Probes.Battery b = Probes.battery(c);
        s.level = b.level;
        s.temp = b.tempC;
        s.plugged = b.plugged != 0;
        s.currentMa = (int) b.currentMa;
        s.thermal = Probes.thermalStatus(c);
        ActivityManager.MemoryInfo mi = Probes.memory(c);
        s.ramFreeMb = (int) (mi.availMem >> 20);
        List<Probes.Port> ports = Probes.parsePorts(
                c.getSharedPreferences(MainActivity.PREFS, 0).getString("ports", Probes.DEFAULT_PORTS));
        boolean[] up = Probes.probePorts(ports);
        s.portsTotal = up.length;
        for (boolean u : up) if (u) s.portsUp++;
        return s;
    }

    static synchronized void append(Context c, Sample s) {
        List<Sample> all = load(c);
        all.add(s);
        long cutoff = s.t - KEEP_MS;
        File f = file(c);
        try (FileWriter w = new FileWriter(f, false)) {
            for (Sample x : all) {
                if (x.t < cutoff) continue;
                w.write(x.t + "," + x.level + "," + x.temp + "," + (x.plugged ? 1 : 0) + "," + x.currentMa
                        + "," + x.thermal + "," + x.ramFreeMb + "," + x.portsUp + "," + x.portsTotal + "\n");
            }
        } catch (IOException ignored) {
        }
    }

    static synchronized List<Sample> load(Context c) {
        List<Sample> out = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 9) continue;
                try {
                    Sample s = new Sample();
                    s.t = Long.parseLong(p[0]);
                    s.level = Integer.parseInt(p[1]);
                    s.temp = Float.parseFloat(p[2]);
                    s.plugged = "1".equals(p[3]);
                    s.currentMa = Integer.parseInt(p[4]);
                    s.thermal = Integer.parseInt(p[5]);
                    s.ramFreeMb = Integer.parseInt(p[6]);
                    s.portsUp = Integer.parseInt(p[7]);
                    s.portsTotal = Integer.parseInt(p[8]);
                    out.add(s);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    static List<Sample> since(List<Sample> all, long fromMs) {
        List<Sample> out = new ArrayList<>();
        for (Sample s : all) if (s.t >= fromMs) out.add(s);
        return out;
    }

    private static File file(Context c) { return new File(c.getFilesDir(), "history.csv"); }
}
