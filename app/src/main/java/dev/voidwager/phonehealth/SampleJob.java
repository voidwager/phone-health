package dev.voidwager.phonehealth;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Background sampler: every ~15 min (Android's floor), survives reboot. */
public class SampleJob extends JobService {
    private static final int ID = 41;

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(ID) != null) return;
        js.schedule(new JobInfo.Builder(ID, new ComponentName(c, SampleJob.class))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build());
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        new Thread(() -> {
            try {
                History.append(this, History.capture(this));
            } catch (Throwable ignored) {
            }
            jobFinished(p, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return true; }
}
