package com.gguard.tv;

import android.net.TrafficStats;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

/**
 * Small on-screen readout of the download speed the app is getting right now (what the stream
 * actually receives), shown always or for a few seconds every N seconds depending on Settings.
 * It measures traffic instead of running a separate speed test, so it costs no extra data
 * and never interrupts playback.
 */
final class SpeedMeter {
    /** Settings values: 0 = off, -1 = always, otherwise the interval in seconds. */
    static final int[] MODES = {0, -1, 30, 60, 300};
    private static final long SHOW_MS = 6000;

    private final Handler handler;
    private final TextView view;
    private final int mode;
    private final int colorOk, colorWarn, colorBad;
    private long lastBytes = -1, lastTime;
    private double smoothed = -1;
    private long cycleStart;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            sample();
            handler.postDelayed(this, 1000);
        }
    };

    SpeedMeter(Handler handler, TextView view) {
        this.handler = handler;
        this.view = view;
        this.mode = Store.speedMode();
        colorOk = view.getResources().getColor(R.color.text);
        colorWarn = view.getResources().getColor(R.color.accent);
        colorBad = view.getResources().getColor(R.color.danger);
    }

    void start() {
        if (mode == 0 || running) { view.setVisibility(View.GONE); return; }
        running = true;
        lastBytes = -1;
        smoothed = -1;
        cycleStart = SystemClock.elapsedRealtime();
        handler.post(tick);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
        view.setVisibility(View.GONE);
    }

    private static long rxBytes() {
        long b = TrafficStats.getUidRxBytes(Process.myUid());
        return b != TrafficStats.UNSUPPORTED ? b : TrafficStats.getTotalRxBytes();
    }

    private void sample() {
        long now = SystemClock.elapsedRealtime();
        long bytes = rxBytes();
        if (lastBytes >= 0 && now > lastTime && bytes >= lastBytes) {
            double mbps = (bytes - lastBytes) * 8.0 / ((now - lastTime) / 1000.0) / 1_000_000.0;
            smoothed = smoothed < 0 ? mbps : smoothed * 0.6 + mbps * 0.4;
        }
        lastBytes = bytes;
        lastTime = now;

        boolean visible = mode == -1 || ((now - cycleStart) % (mode * 1000L)) < SHOW_MS;
        if (smoothed < 0 || !visible) { view.setVisibility(View.GONE); return; }
        view.setText(String.format(Locale.US, "↓ %.1f Mbps", smoothed));
        view.setTextColor(smoothed < 2 ? colorBad : smoothed < 5 ? colorWarn : colorOk);
        view.setVisibility(View.VISIBLE);
    }
}
