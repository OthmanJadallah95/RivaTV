package com.gguard.tv;

import android.os.Handler;
import android.view.View;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * A small transport bar (position / progress / duration) shown at the bottom for VOD.
 * It replaces android.widget.MediaController, whose pop-up window stole the D-pad and made
 * Left/Right jump ~5% of the film. Seeking stays in the Activity's key handler at a fixed 10 s.
 */
final class PlayerControls {
    private static final long HIDE_MS = 4000;

    private final View bar;
    private final TextView position, duration;
    private final SeekBar seek;
    private final MediaController.MediaPlayerControl control;
    private final Handler handler;
    private boolean enabled = true;

    private final Runnable hide = new Runnable() {
        @Override public void run() { bar.setVisibility(View.GONE); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (bar.getVisibility() != View.VISIBLE) return;
            update();
            handler.postDelayed(this, 500);
        }
    };

    PlayerControls(View bar, TextView position, SeekBar seek, TextView duration,
                   MediaController.MediaPlayerControl control, Handler handler) {
        this.bar = bar;
        this.position = position;
        this.seek = seek;
        this.duration = duration;
        this.control = control;
        this.handler = handler;
        seek.setMax(1000);
        seek.setFocusable(false);
        seek.setEnabled(false);
    }

    /** Live has no timeline, so the bar is disabled for it. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) hide();
    }

    void show() {
        if (!enabled) return;
        update();
        bar.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, HIDE_MS);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    void hide() {
        handler.removeCallbacks(hide);
        handler.removeCallbacks(tick);
        bar.setVisibility(View.GONE);
    }

    boolean isShowing() { return bar.getVisibility() == View.VISIBLE; }

    private void update() {
        int dur = control.getDuration();
        int pos = control.getCurrentPosition();
        position.setText(fmt(pos));
        duration.setText(dur > 0 ? fmt(dur) : "--:--");
        seek.setProgress(dur > 0 ? (int) (1000L * pos / dur) : 0);
    }

    private static String fmt(int ms) {
        int s = Math.max(0, ms) / 1000;
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                     : String.format(Locale.US, "%02d:%02d", m, sec);
    }
}
