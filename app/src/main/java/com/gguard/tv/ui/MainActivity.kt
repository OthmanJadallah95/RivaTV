package com.gguard.tv.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gguard.tv.data.ProfileStore
import com.gguard.tv.data.Session

/** Entry point: opens the last used playlist, the playlist list, or the add-playlist screen. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profiles = ProfileStore.all()
        val last = profiles.firstOrNull { it.id == ProfileStore.lastId }
        val next = when {
            profiles.isEmpty() -> LoginActivity::class.java
            last != null -> { Session.open(last); HomeActivity::class.java }
            else -> ProfilesActivity::class.java
        }
        startActivity(Intent(this, next))
        finish()
    }
}

/** Sends the user back through [MainActivity] if Android restarted the process and the session is gone. */
fun AppCompatActivity.ensureSession(): Boolean {
    if (Session.profile != null) return true
    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
    finish()
    return false
}
