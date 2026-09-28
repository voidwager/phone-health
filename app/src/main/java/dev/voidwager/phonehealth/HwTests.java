package dev.voidwager.phonehealth;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.util.function.Consumer;

/**
 * Stimulus-only tests. Each calls back on the main thread with null when a human has to judge
 * ("did you hear it?"), or with a finished verdict string "P|detail" / "F|detail" when the
 * test can decide by itself.
 */
final class HwTests {
    private HwTests() {}

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 2 s sweep 300 Hz -> 3 kHz; low end reveals a blown speaker, high end a clogged grille. */
    static void tone(Context c, boolean earpiece, Consumer<String> done) {
        final int sr = 44100, n = sr * 2;
        short[] buf = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double f = 300 * Math.pow(10, (double) i / n); // 300 Hz -> 3 kHz, log sweep
            phase += 2 * Math.PI * f / sr;
            double env = Math.min(1, Math.min(i, n - i) / (sr * 0.05));
            buf[i] = (short) (Math.sin(phase) * env * 0.7 * Short.MAX_VALUE);
        }
        AudioManager am = c.getSystemService(AudioManager.class);
        int oldMode = am.getMode();
        if (earpiece) {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (Build.VERSION.SDK_INT >= 31)
                for (AudioDeviceInfo d : am.getAvailableCommunicationDevices())
                    if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) am.setCommunicationDevice(d);
        }
        AudioTrack at = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(earpiece ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(sr)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        at.write(buf, 0, n);
        at.play();
        MAIN.postDelayed(() -> {
            at.release();
            if (earpiece) {
                if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice();
                am.setMode(oldMode);
            }
            done.accept(null);
        }, 2300);
    }

    /** 3 s of mic input; passes itself if the peak clears -40 dBFS (a clap or speech does). */
    static void mic(Consumer<String> done) {
        new Thread(() -> {
            String verdict;
            final int sr = 44100;
            int min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            try {
                @SuppressWarnings("MissingPermission")
                AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, sr,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192));
                short[] b = new short[4096];
                int peak = 0;
                ar.startRecording();
                long end = System.currentTimeMillis() + 3000;
                while (System.currentTimeMillis() < end) {
                    int r = ar.read(b, 0, b.length);
                    for (int i = 0; i < r; i++) peak = Math.max(peak, Math.abs((int) b[i]));
                }
                ar.stop();
                ar.release();
                double db = peak == 0 ? -120 : 20 * Math.log10(peak / 32767.0);
                verdict = (db > -40 ? "P|" : "F|") + String.format("peak %.0f dBFS", db);
            } catch (Throwable t) {
                verdict = "F|" + t.getClass().getSimpleName();
            }
            String v = verdict;
            MAIN.post(() -> done.accept(v));
        }).start();
    }

    static void vibrate(Context c, Consumer<String> done) {
        Vibrator v = Build.VERSION.SDK_INT >= 31
                ? c.getSystemService(VibratorManager.class).getDefaultVibrator()
                : c.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) { done.accept("F|no vibrator reported"); return; }
        v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 250, 400}, -1));
        MAIN.postDelayed(() -> done.accept(null), 1200);
    }

    static void torch(Context c, Consumer<String> done) {
        CameraManager cm = c.getSystemService(CameraManager.class);
        try {
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) {
                    cm.setTorchMode(id, true);
                    MAIN.postDelayed(() -> {
                        try { cm.setTorchMode(id, false); } catch (Exception ignored) {}
                        done.accept(null);
                    }, 1500);
                    return;
                }
            }
            done.accept("F|no camera with a flash");
        } catch (Exception e) {
            done.accept("F|" + e.getClass().getSimpleName());
        }
    }

    private static final int[] SENSORS = {Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY, Sensor.TYPE_PRESSURE,
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ROTATION_VECTOR};
    private static final String[] SENSOR_NAMES = {"accel", "gyro", "magnet", "light", "proximity",
            "barometer", "gravity", "rotation"};

    /** Listens 3 s; a sensor that exists but sends nothing is a failure. Absent ones are just listed. */
    static void sensors(Context c, Consumer<String> done) {
        SensorManager sm = c.getSystemService(SensorManager.class);
        final int[] events = new int[SENSORS.length];
        final boolean[] present = new boolean[SENSORS.length];
        SensorEventListener l = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent e) {
                for (int i = 0; i < SENSORS.length; i++) if (SENSORS[i] == e.sensor.getType()) events[i]++;
            }

            @Override
            public void onAccuracyChanged(Sensor s, int a) {}
        };
        for (int i = 0; i < SENSORS.length; i++) {
            Sensor s = sm.getDefaultSensor(SENSORS[i]);
            present[i] = s != null;
            if (s != null) sm.registerListener(l, s, SensorManager.SENSOR_DELAY_UI);
        }
        MAIN.postDelayed(() -> {
            sm.unregisterListener(l);
            StringBuilder ok = new StringBuilder(), silent = new StringBuilder(), absent = new StringBuilder();
            for (int i = 0; i < SENSORS.length; i++) {
                StringBuilder t = !present[i] ? absent : events[i] > 0 ? ok : silent;
                if (t.length() > 0) t.append(", ");
                t.append(SENSOR_NAMES[i]);
            }
            String detail = "ok: " + ok + (silent.length() > 0 ? " · SILENT: " + silent : "")
                    + (absent.length() > 0 ? " · not fitted: " + absent : "");
            done.accept((silent.length() == 0 ? "P|" : "F|") + detail);
        }, 3000);
    }
}
