package com.gguard.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small async image loader with an in-memory cache, enough for logos and posters. */
public final class ImageLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)) {
                @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
            };

    private ImageLoader() {}

    public static void load(final ImageView view, final String url, final int targetPx) {
        view.setTag(R.id.tag_url, url);
        if (url == null || url.isEmpty()) { view.setImageResource(R.drawable.placeholder); return; }
        Bitmap cached = CACHE.get(url);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageResource(R.drawable.placeholder);
        POOL.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = fetch(url, targetPx);
                if (bmp != null) CACHE.put(url, bmp);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (url.equals(view.getTag(R.id.tag_url)) && bmp != null) view.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String url, int targetPx) {
        HttpURLConnection c = null;
        try {
            c = Http.open(url);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > 8 * 1024 * 1024) return null;
            }
            byte[] data = bos.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= targetPx) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
