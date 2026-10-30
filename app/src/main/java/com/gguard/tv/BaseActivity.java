package com.gguard.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseActivity extends Activity {
    protected static final ExecutorService IO = Executors.newFixedThreadPool(3);
    protected final Handler main = new Handler(Looper.getMainLooper());
    private boolean destroyed;

    public interface Done<T> { void run(T result, Exception error); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Store.init(this);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    protected boolean alive() { return !destroyed && !isFinishing(); }

    /** Runs [work] off the UI thread and delivers the result back on it, if the screen is still open. */
    protected <T> void async(final Callable<T> work, final Done<T> done) {
        IO.execute(new Runnable() {
            @Override public void run() {
                T r = null;
                Exception err = null;
                try { r = work.call(); } catch (Exception e) { err = e; }
                final T result = r;
                final Exception error = err;
                main.post(new Runnable() {
                    @Override public void run() { if (alive()) done.run(result, error); }
                });
            }
        });
    }

    /** If Android restarted the process, the open playlist is gone: go back through MainActivity. */
    protected boolean ensureSession() {
        if (Session.profile != null) return true;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        return false;
    }

    protected String errorText(Exception e) {
        String m = e.getMessage();
        return getString(R.string.err_generic, m != null ? m : e.getClass().getSimpleName());
    }

    /** Slight zoom on the focused view so the remote's position is easy to see from the sofa. */
    public static void focusZoom(View v, final float scale) {
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean has) {
                view.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f).setDuration(120).start();
            }
        });
    }
}
