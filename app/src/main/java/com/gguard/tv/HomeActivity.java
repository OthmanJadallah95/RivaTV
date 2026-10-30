package com.gguard.tv;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.gguard.tv.Models.AccountInfo;
import com.gguard.tv.Models.ContentType;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

public class HomeActivity extends BaseActivity {
    private TextView tvAccount;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        setContentView(R.layout.activity_home);
        tvAccount = findViewById(R.id.tvAccount);
        ((TextView) findViewById(R.id.tvProfile)).setText(Session.profile.name);

        tile(R.id.tileLive, new Runnable() { @Override public void run() { BrowseActivity.start(HomeActivity.this, ContentType.LIVE); } });
        tile(R.id.tileMovies, new Runnable() { @Override public void run() { BrowseActivity.start(HomeActivity.this, ContentType.MOVIE); } });
        tile(R.id.tileSeries, new Runnable() { @Override public void run() { BrowseActivity.start(HomeActivity.this, ContentType.SERIES); } });
        tile(R.id.tileSwitch, new Runnable() {
            @Override public void run() {
                startActivity(new Intent(HomeActivity.this, ProfilesActivity.class));
                finish();
            }
        });
        tile(R.id.tileReload, new Runnable() {
            @Override public void run() {
                Session.clearCache();
                Toast.makeText(HomeActivity.this, R.string.reloaded, Toast.LENGTH_SHORT).show();
            }
        });
        tile(R.id.tileSettings, new Runnable() { @Override public void run() { showSettings(); } });
        findViewById(R.id.tileLive).requestFocus();
        loadAccount();
    }

    private void tile(int id, final Runnable action) {
        View v = findViewById(id);
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { action.run(); }
        });
        focusZoom(v, 1.05f);
    }

    private void loadAccount() {
        if (!Session.isXtream()) { tvAccount.setText(R.string.m3u_label); return; }
        if (Session.account != null) { showAccount(Session.account); return; }
        async(new Callable<AccountInfo>() {
            @Override public AccountInfo call() throws Exception { return Session.api.login(); }
        }, new Done<AccountInfo>() {
            @Override public void run(AccountInfo acc, Exception error) {
                if (acc == null) return;
                Session.account = acc;
                showAccount(acc);
            }
        });
    }

    private void showAccount(AccountInfo a) {
        String exp = a.expDate != null
                ? DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(a.expDate * 1000L))
                : getString(R.string.unlimited);
        tvAccount.setText(getString(R.string.account_fmt, a.status, exp, a.activeCons, a.maxConnections));
    }

    private void showSettings() {
        final String[] items = {
                getString(R.string.player_title) + ":  " + getString("vlc".equals(Store.player()) ? R.string.player_vlc : R.string.player_system),
                getString(R.string.buffer_title) + ":  " + getString(R.string.seconds_fmt, Store.bufferMs() / 1000),
                getString(R.string.live_format_title) + ":  " + ("ts".equals(Store.liveExt()) ? "MPEG-TS" : "HLS"),
                getString(R.string.speed_title) + ":  " + speedLabel(Store.speedMode()),
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) choosePlayer();
                        else if (which == 1) chooseBuffer();
                        else if (which == 2) chooseLiveFormat();
                        else chooseSpeed();
                    }
                })
                .show();
    }

    private void choosePlayer() {
        String[] options = {getString(R.string.player_vlc), getString(R.string.player_system)};
        int current = "vlc".equals(Store.player()) ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.player_title)
                .setSingleChoiceItems(options, current, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Store.setPlayer(which == 0 ? "vlc" : "system");
                        d.dismiss();
                        showSettings();
                    }
                })
                .show();
    }

    private void chooseBuffer() {
        final int[] values = {1500, 3000, 5000, 8000, 12000};
        String[] options = new String[values.length];
        int current = 2;
        for (int i = 0; i < values.length; i++) {
            options[i] = getString(R.string.seconds_fmt, Math.round(values[i] / 1000f));
            if (values[i] == Store.bufferMs()) current = i;
        }
        options[0] = getString(R.string.seconds_dec_fmt, "1.5");
        new AlertDialog.Builder(this)
                .setTitle(R.string.buffer_title)
                .setSingleChoiceItems(options, current, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Store.setBufferMs(values[which]);
                        d.dismiss();
                        showSettings();
                    }
                })
                .show();
    }

    private String speedLabel(int mode) {
        if (mode == 0) return getString(R.string.speed_off);
        if (mode == -1) return getString(R.string.speed_always);
        return mode < 60 ? getString(R.string.speed_every_s, mode) : getString(R.string.speed_every_m, mode / 60);
    }

    private void chooseSpeed() {
        final int[] modes = SpeedMeter.MODES;
        String[] options = new String[modes.length];
        int current = 0;
        for (int i = 0; i < modes.length; i++) {
            options[i] = speedLabel(modes[i]);
            if (modes[i] == Store.speedMode()) current = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.speed_title)
                .setSingleChoiceItems(options, current, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Store.setSpeedMode(modes[which]);
                        d.dismiss();
                        showSettings();
                    }
                })
                .show();
    }

    private void chooseLiveFormat() {
        String[] options = {"HLS (.m3u8)", "MPEG-TS (.ts)"};
        int current = "ts".equals(Store.liveExt()) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.live_format_title)
                .setSingleChoiceItems(options, current, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Store.setLiveExt(which == 1 ? "ts" : "m3u8");
                        d.dismiss();
                        showSettings();
                    }
                })
                .show();
    }
}
