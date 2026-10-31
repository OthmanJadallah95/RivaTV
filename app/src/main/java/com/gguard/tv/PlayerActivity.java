package com.gguard.tv;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.gguard.tv.Models.ContentType;
import com.gguard.tv.Models.EpgEntry;
import com.gguard.tv.Models.StreamItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Full-screen player on the platform MediaPlayer. Live TV: Up/Down or CH+/CH- switch channel.
 * Movies/episodes: OK shows controls, Left/Right seek 10 s, and the position is remembered in
 * ResumeStore so Continue Watching can pick it up again. Menu/Info cycles Fit / Stretch / Zoom.
 */
public class PlayerActivity extends BaseActivity implements SurfaceHolder.Callback, MediaController.MediaPlayerControl {
    private static final String EXTRA_INDEX = "index", EXTRA_LIVE = "live";
    private static final int SEEK_MS = 10000;

    public static void start(Context ctx, int index, boolean live) {
        if ("vlc".equals(Store.player())) VlcPlayerActivity.start(ctx, index, live);
        else startSystem(ctx, index, live);
    }

    /** The Android built-in player. */
    public static void startSystem(Context ctx, int index, boolean live) {
        ctx.startActivity(new Intent(ctx, PlayerActivity.class).putExtra(EXTRA_INDEX, index).putExtra(EXTRA_LIVE, live));
    }

    private FrameLayout root;
    private SurfaceView surface;
    private View overlay, loading;
    private TextView tvChannel, tvEpg, tvError;
    private PlayerControls controls;
    private SpeedMeter speedMeter;

    private MediaPlayer mp;
    private boolean prepared, surfaceReady;
    private List<StreamItem> queue;
    private int index;
    private boolean live;
    private String currentUrl = "";
    private int retries;
    private int resumePosition;
    private int videoW, videoH;
    private int resizeIdx;
    private static final int[] RESIZE_LABELS = {R.string.resize_fit, R.string.resize_fill, R.string.resize_zoom};

    private final Runnable hideOverlay = new Runnable() {
        @Override public void run() { overlay.setVisibility(View.GONE); }
    };
    private final Runnable saveProgress = new Runnable() {
        @Override public void run() {
            saveResume();
            main.postDelayed(this, 5000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        queue = Session.playQueue;
        if (queue == null || queue.isEmpty()) { finish(); return; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_player);

        root = findViewById(R.id.root);
        surface = findViewById(R.id.surface);
        overlay = findViewById(R.id.overlay);
        loading = findViewById(R.id.loading);
        tvChannel = findViewById(R.id.tvChannel);
        tvEpg = findViewById(R.id.tvEpg);
        tvError = findViewById(R.id.tvError);
        speedMeter = new SpeedMeter(main, (TextView) findViewById(R.id.tvSpeed));
        surface.getHolder().addCallback(this);

        index = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_INDEX)
                : getIntent().getIntExtra(EXTRA_INDEX, 0);
        index = Math.max(0, Math.min(index, queue.size() - 1));
        live = getIntent().getBooleanExtra(EXTRA_LIVE, false);
        resumePosition = savedInstanceState != null ? savedInstanceState.getInt("pos") : 0;

        controls = new PlayerControls(findViewById(R.id.controls), (TextView) findViewById(R.id.ctrlPos),
                (SeekBar) findViewById(R.id.ctrlSeek), (TextView) findViewById(R.id.ctrlDur), this, main);
        controls.setEnabled(!live);
    }

    @Override protected void onStart() {
        super.onStart();
        if (speedMeter != null) speedMeter.start();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_INDEX, index);
        out.putInt("pos", live ? 0 : getCurrentPosition());
    }

    @Override protected void onStop() {
        super.onStop();
        if (speedMeter != null) speedMeter.stop();
        main.removeCallbacks(saveProgress);
        saveResume();
        if (mp != null && !live) resumePosition = getCurrentPosition();
        releasePlayer();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(saveProgress);
        releasePlayer();
        super.onDestroy();
    }

    // ---- surface ----

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (mp == null) play(index, resumePosition);
        else mp.setDisplay(holder);
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        if (mp != null && !live) resumePosition = getCurrentPosition();
        releasePlayer();
    }

    // ---- playback ----

    private void releasePlayer() {
        if (mp != null) {
            try { mp.reset(); } catch (Exception ignored) { }
            mp.release();
            mp = null;
        }
        prepared = false;
    }

    private void play(int i, int positionMs) {
        index = i;
        StreamItem item = queue.get(i);
        String url = Session.streamUrl(item);
        if (url == null || url.isEmpty()) { showError(getString(R.string.empty)); return; }
        currentUrl = url;
        retries = 0;
        // When no explicit position is given, continue where this title was last left off.
        int start = positionMs;
        if (start <= 0 && !live) start = (int) ResumeStore.position(item);
        startUrl(url, start);
        showOverlay(item);
        main.removeCallbacks(saveProgress);
        if (!live) main.postDelayed(saveProgress, 5000);
    }

    private void startUrl(String url, final int positionMs) {
        if (!surfaceReady) return;
        releasePlayer();
        tvError.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        final MediaPlayer p = new MediaPlayer();
        mp = p;
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", Http.USER_AGENT);
            p.setDataSource(this, Uri.parse(url), headers);
            p.setDisplay(surface.getHolder());
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            p.setScreenOnWhilePlaying(true);
            p.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer m) {
                    if (m != mp) return;
                    prepared = true;
                    loading.setVisibility(View.GONE);
                    if (positionMs > 0 && !live) m.seekTo(positionMs);
                    m.start();
                    retries = 0;
                }
            });
            p.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override public void onVideoSizeChanged(MediaPlayer m, int w, int h) {
                    videoW = w; videoH = h;
                    applySize();
                }
            });
            p.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override public boolean onInfo(MediaPlayer m, int what, int extra) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) loading.setVisibility(View.VISIBLE);
                    else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                            || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) loading.setVisibility(View.GONE);
                    return false;
                }
            });
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int what, int extra) {
                    if (m == mp) handleError(what + "/" + extra);
                    return true;
                }
            });
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) {
                    if (m != mp) return;
                    if (live) { handleError("ended"); return; }
                    ResumeStore.remove(queue.get(index));   // finished
                    if (index < queue.size() - 1) play(index + 1, 0);
                    else finish();
                }
            });
            p.prepareAsync();
        } catch (Exception e) {
            handleError(e.getClass().getSimpleName());
        }
    }

    private void handleError(String code) {
        loading.setVisibility(View.GONE);
        if (live && retries == 0 && (currentUrl.endsWith(".ts") || currentUrl.endsWith(".m3u8"))) {
            retries++;
            currentUrl = currentUrl.endsWith(".ts")
                    ? currentUrl.substring(0, currentUrl.length() - 3) + ".m3u8"
                    : currentUrl.substring(0, currentUrl.length() - 5) + ".ts";
            startUrl(currentUrl, 0);
            return;
        }
        if (retries < 4) {
            retries++;
            showError(getString(R.string.reconnecting));
            final int pos = live ? 0 : getCurrentPosition();
            final String url = currentUrl;
            main.postDelayed(new Runnable() {
                @Override public void run() { if (url.equals(currentUrl)) startUrl(url, pos); }
            }, 2500);
            return;
        }
        showError(getString(R.string.playback_error, code));
    }

    private void saveResume() {
        if (live || mp == null || !prepared) return;
        try { ResumeStore.save(queue.get(index), getCurrentPosition(), getDuration()); } catch (Exception ignored) { }
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void applySize() {
        int pw = root.getWidth(), ph = root.getHeight();
        if (pw == 0 || ph == 0) return;
        int w = FrameLayout.LayoutParams.MATCH_PARENT, h = FrameLayout.LayoutParams.MATCH_PARENT;
        if (resizeIdx != 1 && videoW > 0 && videoH > 0) {
            float scale = resizeIdx == 0
                    ? Math.min((float) pw / videoW, (float) ph / videoH)
                    : Math.max((float) pw / videoW, (float) ph / videoH);
            w = Math.round(videoW * scale);
            h = Math.round(videoH * scale);
        }
        surface.setLayoutParams(new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
    }

    private void showOverlay(final StreamItem item) {
        tvChannel.setText(live ? (index + 1) + "   " + item.name : item.name);
        tvEpg.setVisibility(View.GONE);
        overlay.setVisibility(View.VISIBLE);
        main.removeCallbacks(hideOverlay);
        main.postDelayed(hideOverlay, 5000);

        if (live && Session.isXtream() && item.url == null) {
            final int requested = index;
            async(new Callable<List<EpgEntry>>() {
                @Override public List<EpgEntry> call() throws Exception { return Session.shortEpg(item.id); }
            }, new Done<List<EpgEntry>>() {
                @Override public void run(List<EpgEntry> epg, Exception error) {
                    if (epg == null || epg.isEmpty() || requested != index) return;
                    StringBuilder b = new StringBuilder(getString(R.string.now_fmt, epg.get(0).title));
                    if (epg.size() > 1) b.append('\n').append(getString(R.string.next_fmt, epg.get(1).title));
                    tvEpg.setText(b.toString());
                    tvEpg.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void zap(int delta) {
        if (queue.size() < 2) return;
        int n = queue.size();
        play(((index + delta) % n + n) % n, 0);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int k = event.getKeyCode();
            boolean repeat = event.getRepeatCount() > 0;
            if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO) {
                if (!repeat) {
                    resizeIdx = (resizeIdx + 1) % RESIZE_LABELS.length;
                    applySize();
                    Toast.makeText(this, RESIZE_LABELS[resizeIdx], Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            if (live) {
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_CHANNEL_UP: if (!repeat) zap(1); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_CHANNEL_DOWN: if (!repeat) zap(-1); return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                        showOverlay(queue.get(index)); return true;
                    default: break;
                }
            } else {
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                        controls.show(); showOverlay(queue.get(index)); return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_MEDIA_REWIND:
                        seekBy(-SEEK_MS); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                        seekBy(SEEK_MS); return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_SPACE:
                        if (isPlaying()) pause(); else start();
                        controls.show(); return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY: start(); controls.show(); return true;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE: pause(); controls.show(); return true;
                    default: break;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void seekBy(int deltaMs) {
        if (!prepared || mp == null) return;
        int d = getDuration();
        int target = Math.max(0, getCurrentPosition() + deltaMs);
        if (d > 0) target = Math.min(target, d - 1000);
        seekTo(target);
        controls.show();
    }

    // ---- MediaController.MediaPlayerControl ----

    @Override public void start() { if (prepared && mp != null) mp.start(); }
    @Override public void pause() { if (prepared && mp != null && mp.isPlaying()) mp.pause(); }
    @Override public int getDuration() { return prepared && mp != null ? mp.getDuration() : 0; }
    @Override public int getCurrentPosition() {
        try { return prepared && mp != null ? mp.getCurrentPosition() : 0; } catch (IllegalStateException e) { return 0; }
    }
    @Override public void seekTo(int pos) { if (prepared && mp != null) mp.seekTo(pos); }
    @Override public boolean isPlaying() {
        try { return prepared && mp != null && mp.isPlaying(); } catch (IllegalStateException e) { return false; }
    }
    @Override public int getBufferPercentage() { return 0; }
    @Override public boolean canPause() { return true; }
    @Override public boolean canSeekBackward() { return !live; }
    @Override public boolean canSeekForward() { return !live; }
    @Override public int getAudioSessionId() { return mp != null ? mp.getAudioSessionId() : 0; }
}
