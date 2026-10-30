package com.gguard.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.gguard.tv.Models.AuthException;
import com.gguard.tv.Models.ContentType;
import com.gguard.tv.Models.Profile;

import java.util.UUID;
import java.util.concurrent.Callable;

public class LoginActivity extends BaseActivity {
    private boolean xtream = true;
    private Button btnX, btnM, btnSave;
    private EditText etName, etServer, etUser, etPass, etM3u;
    private View groupX, groupM, progress;
    private TextView tvError;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        btnX = findViewById(R.id.btnTypeXtream);
        btnM = findViewById(R.id.btnTypeM3u);
        btnSave = findViewById(R.id.btnSave);
        etName = findViewById(R.id.etName);
        etServer = findViewById(R.id.etServer);
        etUser = findViewById(R.id.etUser);
        etPass = findViewById(R.id.etPass);
        etM3u = findViewById(R.id.etM3u);
        groupX = findViewById(R.id.groupXtream);
        groupM = findViewById(R.id.groupM3u);
        progress = findViewById(R.id.progress);
        tvError = findViewById(R.id.tvError);

        btnX.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setMode(true); }
        });
        btnM.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setMode(false); }
        });
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        setMode(true);
        btnX.requestFocus();
    }

    private void setMode(boolean x) {
        xtream = x;
        groupX.setVisibility(x ? View.VISIBLE : View.GONE);
        groupM.setVisibility(x ? View.GONE : View.VISIBLE);
        btnX.setActivated(x);
        btnM.setActivated(!x);
        tvError.setText("");
    }

    private void busy(boolean b) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!b);
        if (b) tvError.setText("");
    }

    private static String text(EditText e) { return e.getText().toString().trim(); }

    private void save() {
        String name = text(etName);
        final Profile p = new Profile();
        p.id = UUID.randomUUID().toString();
        if (xtream) {
            String server = text(etServer), user = text(etUser), pass = text(etPass);
            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) { tvError.setText(R.string.err_required); return; }
            p.type = Profile.XTREAM;
            p.server = XtreamApi.normalize(server);
            p.username = user;
            p.password = pass;
            p.name = name.isEmpty() ? user : name;
        } else {
            String url = text(etM3u);
            if (url.isEmpty()) { tvError.setText(R.string.err_required); return; }
            p.type = Profile.M3U;
            p.m3uUrl = url;
            p.name = name.isEmpty() ? "M3U" : name;
        }

        busy(true);
        Session.open(p);
        async(new Callable<Object>() {
            @Override public Object call() throws Exception {
                if (p.isXtream()) Session.account = Session.api.login();
                else Session.items(ContentType.LIVE); // downloads and parses the playlist once to validate it
                return Boolean.TRUE;
            }
        }, new Done<Object>() {
            @Override public void run(Object result, Exception error) {
                busy(false);
                if (error instanceof AuthException) { tvError.setText(R.string.err_auth); return; }
                if (error != null) { tvError.setText(errorText(error)); return; }
                Store.addProfile(p);
                Store.setLastId(p.id);
                startActivity(new Intent(LoginActivity.this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            }
        });
    }
}
