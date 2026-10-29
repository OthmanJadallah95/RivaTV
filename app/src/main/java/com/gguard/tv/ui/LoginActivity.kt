package com.gguard.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gguard.tv.R
import com.gguard.tv.data.AuthException
import com.gguard.tv.data.ContentType
import com.gguard.tv.data.Profile
import com.gguard.tv.data.ProfileStore
import com.gguard.tv.data.ProfileType
import com.gguard.tv.data.Session
import com.gguard.tv.data.XtreamApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

class LoginActivity : AppCompatActivity() {
    private var mode = ProfileType.XTREAM

    private lateinit var btnX: Button
    private lateinit var btnM: Button
    private lateinit var btnSave: Button
    private lateinit var etName: EditText
    private lateinit var etServer: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etM3u: EditText
    private lateinit var groupX: View
    private lateinit var groupM: View
    private lateinit var progress: ProgressBar
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        btnX = findViewById(R.id.btnTypeXtream)
        btnM = findViewById(R.id.btnTypeM3u)
        btnSave = findViewById(R.id.btnSave)
        etName = findViewById(R.id.etName)
        etServer = findViewById(R.id.etServer)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etM3u = findViewById(R.id.etM3u)
        groupX = findViewById(R.id.groupXtream)
        groupM = findViewById(R.id.groupM3u)
        progress = findViewById(R.id.progress)
        tvError = findViewById(R.id.tvError)

        btnX.setOnClickListener { setMode(ProfileType.XTREAM) }
        btnM.setOnClickListener { setMode(ProfileType.M3U) }
        btnSave.setOnClickListener { save() }
        setMode(ProfileType.XTREAM)
        btnX.requestFocus()
    }

    private fun setMode(m: ProfileType) {
        mode = m
        groupX.visibility = if (m == ProfileType.XTREAM) View.VISIBLE else View.GONE
        groupM.visibility = if (m == ProfileType.M3U) View.VISIBLE else View.GONE
        btnX.isActivated = m == ProfileType.XTREAM
        btnM.isActivated = m == ProfileType.M3U
        tvError.text = ""
    }

    private fun busy(b: Boolean) {
        progress.visibility = if (b) View.VISIBLE else View.GONE
        btnSave.isEnabled = !b
        if (b) tvError.text = ""
    }

    private fun save() {
        val name = etName.text.toString().trim()
        val profile = if (mode == ProfileType.XTREAM) {
            val server = etServer.text.toString().trim()
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                tvError.setText(R.string.err_required); return
            }
            val base = XtreamApi.normalize(server)
            Profile(UUID.randomUUID().toString(), name.ifEmpty { user }, ProfileType.XTREAM, server = base, username = user, password = pass)
        } else {
            val url = etM3u.text.toString().trim()
            if (url.isEmpty()) { tvError.setText(R.string.err_required); return }
            Profile(UUID.randomUUID().toString(), name.ifEmpty { "M3U" }, ProfileType.M3U, m3uUrl = url)
        }

        lifecycleScope.launch {
            busy(true)
            try {
                Session.open(profile)
                if (profile.type == ProfileType.XTREAM) {
                    Session.account = Session.api!!.login()
                } else {
                    Session.items(ContentType.LIVE) // downloads and parses the playlist once to validate it
                }
                ProfileStore.add(profile)
                ProfileStore.lastId = profile.id
                startActivity(Intent(this@LoginActivity, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                tvError.setText(R.string.err_auth)
            } catch (e: Exception) {
                tvError.text = getString(R.string.err_generic, e.message ?: e.javaClass.simpleName)
            } finally {
                busy(false)
            }
        }
    }
}
