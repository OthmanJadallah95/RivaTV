package com.gguard.tv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gguard.tv.R
import com.gguard.tv.data.Profile
import com.gguard.tv.data.ProfileStore
import com.gguard.tv.data.Session

class ProfilesActivity : AppCompatActivity() {
    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)
        val rv = findViewById<RecyclerView>(R.id.rvProfiles)
        adapter = ProfileAdapter(::open, ::confirmDelete)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.items = ProfileStore.all()
        if (adapter.items.isEmpty()) findViewById<Button>(R.id.btnAdd).requestFocus()
    }

    private fun open(p: Profile) {
        Session.open(p)
        ProfileStore.lastId = p.id
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun confirmDelete(p: Profile) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_profile_q, p.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                ProfileStore.remove(p.id)
                adapter.items = ProfileStore.all()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
