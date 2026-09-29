package dev.voidwager.phonehealth;

import java.util.HashMap;
import java.util.Map;

/**
 * What each reading means and where good / average / bad start. The ranges mirror the thresholds
 * the app grades against (Probes, MainActivity), so the explanation can never disagree with the LED.
 * A null range means that band doesn't exist for this reading; all-null means it's informational.
 */
final class Explain {
    static final class E {
        final String what, good, avg, bad;

        E(String what, String good, String avg, String bad) {
            this.what = what; this.good = good; this.avg = avg; this.bad = bad;
        }

        boolean graded() { return good != null || avg != null || bad != null; }
    }

    private static final Map<String, E> BY_LABEL = new HashMap<>();

    private static void put(String label, String what, String good, String avg, String bad) {
        BY_LABEL.put(label, new E(what, good, avg, bad));
    }

    private static final E TEMP = new E(
            "Battery temperature. Heat is the biggest cause of wear on a phone that stays on a charger.",
            "under 40 °C (under 35 °C is ideal)", "40–45 °C", "45 °C or more");
    private static final E THROTTLE = new E(
            "How hard Android is slowing the processor to shed heat. Your services slow down with it.",
            "None or Light", "Moderate", "Severe, Critical or worse");
    private static final E FREE = new E(
            "Free space on internal storage. Nearly-full flash gets slower and wears faster.",
            "more than 15% free", "5–15% free", "under 5% free");
    private static final E RAM = new E(
            "Memory apps can still use right now. When it runs out, Android starts killing background apps like Termux.",
            "more than 15% available", "15% or less available", "Android is in its low-memory state");
    private static final E WIFI = new E(
            "Strength of the Wi-Fi signal from your router. Closer to 0 dBm is stronger.",
            "-67 dBm or stronger", "-67 to -75 dBm", "weaker than -75 dBm (tunnels and game connections drop)");
    private static final E CELL = new E(
            "Strength of the signal from the cell tower on this radio. Closer to 0 dBm is stronger.",
            "3–4 of 4 bars (about -95 dBm or stronger)", "2 of 4 bars", "0–1 bars (about -110 dBm or weaker)");
    private static final E SERVICE = new E(
            "One of your watched ports on this phone (127.0.0.1): a program such as a web or game server should be answering on it.",
            "listening", null, "down: the program isn't running, or Android killed it");
    private static final E LOGGER = new E(
            "When Phone Health's background logger last saved a sample. It should run every 15 minutes.",
            "within the last 45 minutes", "older than 45 minutes: Android probably put the app to sleep", null);

    static {
        // Status bays
        BY_LABEL.put("bay:battery", new E(
                "Battery temperature, plus wear once a capacity reading exists. Heat is what ages a phone that lives on a charger.",
                "under 40 °C and 80%+ of original capacity", "40–45 °C, or 70–80% capacity",
                "45 °C or more, under 70% capacity, or Android flags the battery"));
        put("bay:power", "Whether the phone is on a charger and charging properly.",
                "plugged in and charging normally", "on battery, or charging under 500 mA below 90%", null);
        BY_LABEL.put("bay:thermal", THROTTLE);
        BY_LABEL.put("bay:storage", FREE);
        BY_LABEL.put("bay:memory", RAM);
        put("bay:network", "The phone's connection: Wi-Fi strength, or cell strength on mobile data.",
                "strong signal and internet reachable", "weaker signal, or connected but no internet", "weak signal or offline");
        put("bay:services", "Your watched ports on this phone, such as a dashboard or a game server.",
                "every watched port answering", "some ports down", "every port down");
        BY_LABEL.put("bay:logger", LOGGER);

        // Readings › Battery
        put("Level", "How full the battery is right now. On a phone that never unplugs, sitting between 40% and 80% "
                + "wears it least; this reading itself isn't graded.", null, null, null);
        put("Status", "Charging, discharging, full or not charging, as the battery reports it.", null, null, null);
        put("Power source", "What's powering the phone: a charger, USB, wireless, or nothing.",
                "charger, USB or wireless", "unplugged", null);
        BY_LABEL.put("Temperature", TEMP);
        put("Voltage", "Battery voltage. It rises as the battery fills: about 3.5 V when empty, about 4.4 V when full.",
                null, null, null);
        put("Charging current", "How fast current is flowing into the battery. Near 90% and above it slows on purpose, "
                + "so it isn't graded there.", "1000 mA or more", "500–1000 mA", "under 500 mA: try another cable or port");
        put("Current draw", "How much current the phone is pulling from the battery. Idle is usually 100–300 mA; "
                + "heavy use can pass 800 mA.", null, null, null);
        put("Android health flag", "Android's own verdict on the battery hardware.",
                "Good", null, "Overheat, Dead, Over-voltage, Cold or Failure");
        put("Charge cycles", "How many full charges' worth the battery has been through. Most phone batteries are "
                + "rated for 500–800 before they fade noticeably.", "under 500", "500–800", "over 800");
        put("Live capacity estimate", "How much charge the battery would hold if full, estimated right now from the "
                + "fuel gauge. It's rough; the graded figure is Capacity vs design.", null, null, null);
        put("Design capacity", "How much charge the battery held when it was new.", null, null, null);
        put("Capacity vs design", "How much of its original capacity the battery still has, measured only when "
                + "it's 80% charged or more, where the estimate is most accurate.", "80% or more", "70–80%", "under 70%");
        put("Chemistry", "The battery's type. Almost always Li-ion.", null, null, null);

        // Readings › Thermal / CPU / Memory / Storage
        BY_LABEL.put("Throttling state", THROTTLE);
        put("Headroom (10 s ahead)", "How close Android expects the phone to be to its throttling limit 10 seconds "
                + "from now. 100% means throttling starts.", "under 70%", "70–95%", "95% or more");
        put("Total", "Total memory (RAM) in the phone.", null, null, null);
        BY_LABEL.put("Available", RAM);
        put("Low-memory state", "Whether Android has declared memory critically low and is killing background apps.",
                "no", null, "yes");
        put("Capacity", "Total internal storage.", null, null, null);
        BY_LABEL.put("Free", FREE);

        // Readings › Network
        put("Active network", "The connection the phone is using right now.", "Wi-Fi or mobile data", null, "offline");
        put("Internet reachable", "Whether Android can actually reach the internet over that connection.",
                "yes", "no: connected, but the internet isn't reachable", null);
        put("Latency to 1.1.1.1", "Time for a round trip to a public server. Lower feels snappier for players and "
                + "visitors.", "under 80 ms", "80–200 ms", "over 200 ms");
        BY_LABEL.put("Wi-Fi signal", WIFI);
        put("Link estimate", "Android's rough estimate of the connection's download and upload speed.", null, null, null);

        // Readings › Software
        put("Installed version", "The version of Phone Health on this phone.", null, null, null);
        put("Latest on GitHub", "The newest published release. When it's newer than yours, an Update bay appears "
                + "on Status.", null, null, null);
        put("Last checked", "When the app last asked GitHub for a newer release (at most every 6 hours).", null, null, null);

        // History
        put("Logged in last 24 h", "How many 15-minute slots the background logger actually recorded. Gaps mean "
                + "Android put the app to sleep, so nobody saw what the phone did then.",
                "80% or more of slots", "40–80%", "under 40%");
        put("Time at 40 °C or more", "How long the battery spent at 40 °C or hotter in the last day.",
                "none", "up to 2 hours", "over 2 hours");
        put("Termux", "Whether Termux, the usual way to run servers on Android, is installed.", null, null, null);
        BY_LABEL.put("Phone Health logger", LOGGER);

        // Status › Service tag
        put("Model", "The phone's model name.", null, null, null);
        put("Android", "Android version and the date of its latest security patch.", null, null, null);
        put("Up since boot", "Time since the phone last restarted. A surprise restart shows up as a short time here.",
                null, null, null);
    }

    /** The explanation for a row label, including the families of rows whose labels vary. */
    static E of(String label) {
        E e = BY_LABEL.get(label);
        if (e != null) return e;
        if (label.startsWith("Speed ·")) return new E("Storage speed test: fsync'd sequential writes (MB/s) and "
                + "small synced random writes (IOPS). Flash that's wearing out gets slower over time.",
                "within 30% of your first run", "more than 30% slower than your first run", null);
        if (label.matches("cpu\\d+")) return new E("One processor core's current clock speed against its maximum. "
                + "Lower when idle or when the phone is throttling.", null, null, null);
        if (label.contains("  :")) return SERVICE;
        if (label.matches("(5G NR|4G LTE|3G|2G|Cdma).*")) return CELL;
        return null;
    }

    /** The scale word for a state. */
    static String band(Ui.S s) {
        switch (s) {
            case GOOD: return "good";
            case WARN: return "average";
            case BAD: return "bad";
            default: return null;
        }
    }
}
