package com.gguard.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gguard.tv.R
import com.gguard.tv.data.AccountInfo
import com.gguard.tv.data.ContentType
import com.gguard.tv.data.Session
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class HomeActivity : AppCompatActivity() {
    private lateinit var tvAccount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSession()) return
        setContentView(R.layout.activity_home)
        tvAccount = findViewById(R.id.tvAccount)
        findViewById<TextView>(R.id.tvProfile).text = Session.profile?.name

        tile(R.id.tileLive) { BrowseActivity.start(this, ContentType.LIVE) }
        tile(R.id.tileMovies) { BrowseActivity.start(this, ContentType.MOVIE) }
        tile(R.id.tileSeries) { BrowseActivity.start(this, ContentType.SERIES) }
        tile(R.id.tileSwitch) {
            startActivity(Intent(this, ProfilesActivity::class.java))
            finish()
        }
        tile(R.id.tileReload) {
            Session.clearCache()
            Toast.makeText(this, R.string.reloaded, Toast.LENGTH_SHORT).show()
        }
        tile(R.id.tileSettings) { showSettings() }
        findViewById<View>(R.id.tileLive).requestFocus()

        loadAccount()
    }

    private fun tile(id: Int, action: () -> Unit) {
        val v = findViewById<View>(id)
        v.setOnClickListener { action() }
        v.focusZoom(1.05f)
    }

    private fun loadAccount() {
        if (!Session.isXtream) {
            tvAccount.setText(R.string.m3u_label)
            return
        }
        Session.account?.let { showAccount(it); return }
        lifecycleScope.launch {
            val acc = runCatching { Session.api?.login() }.getOrNull() ?: return@launch
            Session.account = acc
            showAccount(acc)
        }
    }

    private fun showAccount(a: AccountInfo) {
        val exp = a.expDate?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1000)) }
            ?: getString(R.string.unlimited)
        tvAccount.text = getString(R.string.account_fmt, a.status, exp, a.activeCons, a.maxConnections)
    }

    private fun showSettings() {
        val options = arrayOf("MPEG-TS (.ts)", "HLS (.m3u8)")
        val current = if (Session.liveExt == "m3u8") 1 else 0
        AlertDialog.Builder(this)
            .setTitle(R.string.live_format_title)
            .setSingleChoiceItems(options, current) { d, which ->
                Session.liveExt = if (which == 1) "m3u8" else "ts"
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
