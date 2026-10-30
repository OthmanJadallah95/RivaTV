package com.gguard.tv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

public final class Http {
    public static final String USER_AGENT = "GGuardTV/1.0 (Linux; Android)";

    private Http() {}

    /** Opens a GET connection, following redirects across http/https (HttpURLConnection won't by itself). */
    public static HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(90000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", USER_AGENT);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("HTTP " + code);
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c;
        }
        throw new IOException("Too many redirects");
    }

    public static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (BufferedReader r = reader(c)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[16384];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    public static BufferedReader reader(HttpURLConnection c) throws IOException {
        InputStream in = c.getInputStream();
        return new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")), 65536);
    }
}
