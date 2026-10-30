package com.gguard.tv;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import com.gguard.tv.Models.Profile;

public class ProfilesActivity extends BaseActivity {
    private Adapters.ProfileAdapter adapter;
    private ListView list;
    private Button btnAdd;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiles);
        list = findViewById(R.id.lvProfiles);
        btnAdd = findViewById(R.id.btnAdd);
        adapter = new Adapters.ProfileAdapter(getLayoutInflater());
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                open(adapter.getItem(position));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(adapter.getItem(position));
                return true;
            }
        });
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(ProfilesActivity.this, LoginActivity.class));
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.items = Store.profiles();
        adapter.notifyDataSetChanged();
        if (adapter.items.isEmpty()) btnAdd.requestFocus(); else list.requestFocus();
    }

    private void open(Profile p) {
        Session.open(p);
        Store.setLastId(p.id);
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void confirmDelete(final Profile p) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.delete_profile_q, p.name))
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Store.removeProfile(p.id);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
