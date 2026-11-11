package com.gguard.tv;

import android.content.Intent;
import android.os.Bundle;

import com.gguard.tv.Models.Profile;

import java.util.List;

/** Entry point: opens the last used playlist, the playlist list, or the add-playlist screen. */
public class MainActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        List<Profile> profiles = Store.profiles();
        String last = Store.lastId();
        Intent intent;
        if (profiles.isEmpty()) {
            // No subscription yet: open login pre-filled with the default account and auto-sign-in.
            intent = new Intent(this, LoginActivity.class).putExtra(LoginActivity.EXTRA_AUTO, true);
        } else {
            Class<?> next = ProfilesActivity.class;
            for (Profile p : profiles) {
                if (p.id.equals(last)) { Session.open(p); next = HomeActivity.class; break; }
            }
            intent = new Intent(this, next);
        }
        startActivity(intent);
        finish();
    }
}
