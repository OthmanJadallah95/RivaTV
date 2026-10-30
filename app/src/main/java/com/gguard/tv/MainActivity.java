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
        Class<?> next = ProfilesActivity.class;
        if (profiles.isEmpty()) {
            next = LoginActivity.class;
        } else {
            for (Profile p : profiles) {
                if (p.id.equals(last)) { Session.open(p); next = HomeActivity.class; break; }
            }
        }
        startActivity(new Intent(this, next));
        finish();
    }
}
