package com.gguard.tv;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

import com.gguard.tv.Models.EpgEntry;
import com.gguard.tv.Models.StreamItem;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Player built on libVLC: large network buffer, automatic reconnect and its own decoders,
 * which copes with unstable IPTV streams better than the TV's built-in player.
 * If VLC can't open a stream at all, it hands over to {@link PlayerActivity}.
 */
public class VlcPlayerActivity extends BaseActivity
        implements IVLCVout.OnNewVideoLayoutListener, MediaController.MediaPlayerControl {
    private static final String EXTRA_INDEX = "index", EXTRA_LIVE = "live";
    private static final int[] RESIZE_LABELS = {R.string.resize_fit, R.string.resize_fill, R.string.resize_zoom};

    public static void start(Context ctx, int index, boolean live) {
        ctx.startActivity(new Intent(ctx, VlcPlayerActivity.class).putExtra(EXTRA_INDEX, index).putExtra(EXTRA_LIVE, live));
    }

    private FrameLayout root;
    private SurfaceView surface;
    private View overlay, loading;
    private TextView tvChannel, tvEpg, tvError;
    private MediaController controller;
    private SpeedMeter speedMeter;

    private LibVLC libVLC;
    private MediaPlayer mp;
    private List<StreamItem> queue;
    private int index;
    private boolean live;
    private String currentUrl = "";
    private int retries;
    private boolean everPlayed;
    private long startAt;
    private int resizeIdx;
    private int videoW, videoH;
    private boolean attached;

    private final Runnable hideOverlay = new Runnable() {
        @Override public void run() { overlay.setVisibility(View.GONE); }
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

        index = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_INDEX) : getIntent().getIntExtra(EXTRA_INDEX, 0);
        index = Math.max(0, Math.min(index, queue.size() - 1));
        live = getIntent().getBooleanExtra(EXTRA_LIVE, false);
        startAt = savedInstanceState != null ? savedInstanceState.getLong("pos") : 0;

        try {
            ArrayList<String> opts = new ArrayList<>();
            opts.add("--network-caching=" + Store.bufferMs());
            opts.add("--http-reconnect");
            opts.add("--audio-time-stretch");
            opts.add("--android-display-chroma");
            opts.add("RV32");
            libVLC = new LibVLC(this, opts);
            libVLC.setUserAgent(getString(R.string.app_name), Http.USER_AGENT);
            mp = new MediaPlayer(libVLC);
        } catch (Throwable t) {
            fallbackToSystem();
            return;
        }
        mp.setEventListener(new MediaPlayer.EventListener() {
            @Override public void onEvent(MediaPlayer.Event e) { onVlcEvent(e); }
        });

        controller = new MediaController(this);
        controller.setMediaPlayer(this);
        controller.setAnchorView(root);
        if (queue.size() > 1 && !live) {
            controller.setPrevNextListeners(new View.OnClickListener() {
                @Override public void onClick(View v) { if (index < queue.size() - 1) play(index + 1, 0); }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { if (index > 0) play(index - 1, 0); }
            });
        }

        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (mp != null && (r - l != or - ol || b - t != ob - ot)) {
                    mp.getVLCVout().setWindowSize(r - l, b - t);
                    applySize();
                }
            }
        });
    }

    private void fallbackToSystem() {
        Toast.makeText(this, R.string.vlc_fallback, Toast.LENGTH_LONG).show();
        PlayerActivity.startSystem(this, index, live);
        finish();
    }

    @Override protected void onStart() {
        super.onStart();
        if (mp == null) return;
        IVLCVout vout = mp.getVLCVout();
        vout.setVideoView(surface);
        if (root.getWidth() > 0) vout.setWindowSize(root.getWidth(), root.getHeight());
        vout.attachViews(this);
        attached = true;
        play(index, startAt);
        speedMeter.start();
    }

    @Override protected void onStop() {
        super.onStop();
        if (speedMeter != null) speedMeter.stop();
        if (mp == null) return;
        if (!live) startAt = mp.getTime();
        if (controller != null) controller.hide();
        mp.stop();
        if (attached) { mp.getVLCVout().detachViews(); attached = false; }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_INDEX, index);
        out.putLong("pos", live || mp == null ? 0 : mp.getTime());
    }

    @Override protected void onDestroy() {
        if (mp != null) { mp.setEventListener(null); mp.release(); mp = null; }
        if (libVLC != null) { libVLC.release(); libVLC = null; }
        super.onDestroy();
    }

    // ---- playback ----

    private void play(int i, long positionMs) {
        index = i;
        StreamItem item = queue.get(i);
        String url = Session.streamUrl(item);
        if (url == null || url.isEmpty()) { showError(getString(R.string.empty)); return; }
        currentUrl = url;
        retries = 0;
        everPlayed = false;
        startUrl(url, positionMs);
        showOverlay(item);
    }

    private void startUrl(String url, long positionMs) {
        if (mp == null) return;
        tvError.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        Media m = new Media(libVLC, Uri.parse(url));
        m.setHWDecoderEnabled(true, false);
        m.addOption(":network-caching=" + Store.bufferMs());
        m.addOption(":http-user-agent=" + Http.USER_AGENT);
        if (positionMs > 0 && !live) m.addOption(":start-time=" + (positionMs / 1000.0));
        mp.setMedia(m);
        m.release();
        mp.play();
    }

    private void onVlcEvent(MediaPlayer.Event e) {
        switch (e.type) {
            case MediaPlayer.Event.Buffering:
                loading.setVisibility(e.getBuffering() < 100f ? View.VISIBLE : View.GONE);
                break;
            case MediaPlayer.Event.Playing:
                everPlayed = true;
                retries = 0;
                loading.setVisibility(View.GONE);
                tvError.setVisibility(View.GONE);
                break;
            case MediaPlayer.Event.EncounteredError:
                handleError();
                break;
            case MediaPlayer.Event.EndReached:
                if (live) handleError();                       // live streams shouldn't end: reconnect
                else if (index < queue.size() - 1) play(index + 1, 0);
                else finish();
                break;
            default:
                break;
        }
    }

    private void handleError() {
        loading.setVisibility(View.GONE);
        // Some providers serve only one live format: try the other one once.
        if (live && retries == 0 && (currentUrl.endsWith(".ts") || currentUrl.endsWith(".m3u8"))) {
            retries++;
            currentUrl = currentUrl.endsWith(".ts")
                    ? currentUrl.substring(0, currentUrl.length() - 3) + ".m3u8"
                    : currentUrl.substring(0, currentUrl.length() - 5) + ".ts";
            startUrl(currentUrl, 0);
            return;
        }
        // VLC never managed to open this stream (e.g. the provider refuses VLC): use the built-in player.
        if (!everPlayed && retries >= 1) { fallbackToSystem(); return; }
        if (retries < 5) {
            retries++;
            showError(getString(R.string.reconnecting));
            final long pos = live || mp == null ? 0 : mp.getTime();
            final String url = currentUrl;
            main.postDelayed(new Runnable() {
                @Override public void run() { if (url.equals(currentUrl)) startUrl(url, pos); }
            }, 2000);
            return;
        }
        showError(getString(R.string.playback_error, "VLC"));
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    @Override public void onNewVideoLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight,
                                           int sarNum, int sarDen) {
        if (visibleWidth <= 0 || visibleHeight <= 0) return;
        double sar = (sarNum > 0 && sarDen > 0) ? (double) sarNum / sarDen : 1.0;
        videoW = (int) Math.round(visibleWidth * sar);
        videoH = visibleHeight;
        applySize();
    }

    /** Fit (letterbox), Stretch (fill the screen) or Zoom (fill and crop). */
    private void applySize() {
        if (mp == null) return;
        int sw = root.getWidth(), sh = root.getHeight();
        if (sw == 0 || sh == 0) return;
        if (resizeIdx == 1) {
            mp.setScale(0);
            mp.setAspectRatio(sw + ":" + sh);
        } else if (resizeIdx == 2 && videoW > 0 && videoH > 0) {
            mp.setAspectRatio(null);
            mp.setScale((float) Math.max((double) sw / videoW, (double) sh / videoH));
        } else {
            mp.setAspectRatio(null);
            mp.setScale(0);
        }
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
        if (event.getAction() == KeyEvent.ACTION_DOWN && mp != null) {
            int k = event.getKeyCode();
            if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO) {
                resizeIdx = (resizeIdx + 1) % RESIZE_LABELS.length;
                applySize();
                Toast.makeText(this, RESIZE_LABELS[resizeIdx], Toast.LENGTH_SHORT).show();
                return true;
            }
            if (live) {
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_CHANNEL_UP: zap(1); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_CHANNEL_DOWN: zap(-1); return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                        showOverlay(queue.get(index)); return true;
                    default: break;
                }
            } else {
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                        controller.show(4000); showOverlay(queue.get(index)); return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_MEDIA_REWIND:
                        seekBy(-10000); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                        seekBy(10000); return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_SPACE:
                        if (isPlaying()) pause(); else start();
                        controller.show(3000); return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY: start(); return true;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE: pause(); controller.show(0); return true;
                    default: break;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void seekBy(int deltaMs) {
        if (mp == null || !mp.isSeekable()) return;
        long len = mp.getLength();
        long target = Math.max(0, mp.getTime() + deltaMs);
        if (len > 0) target = Math.min(target, len - 1000);
        mp.setTime(target);
        controller.show(3000);
    }

    // ---- MediaController.MediaPlayerControl ----

    @Override public void start() { if (mp != null && !mp.isPlaying()) mp.play(); }
    @Override public void pause() { if (mp != null && mp.isPlaying()) mp.pause(); }
    @Override public int getDuration() { return mp != null ? (int) mp.getLength() : 0; }
    @Override public int getCurrentPosition() { return mp != null ? (int) mp.getTime() : 0; }
    @Override public void seekTo(int pos) { if (mp != null) mp.setTime(pos); }
    @Override public boolean isPlaying() { return mp != null && mp.isPlaying(); }
    @Override public int getBufferPercentage() { return 0; }
    @Override public boolean canPause() { return !live; }
    @Override public boolean canSeekBackward() { return !live; }
    @Override public boolean canSeekForward() { return !live; }
    @Override public int getAudioSessionId() { return 0; }
}
