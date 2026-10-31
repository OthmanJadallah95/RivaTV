package com.gguard.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;

import com.gguard.tv.Models.StreamItem;

import java.util.Collections;
import java.util.List;

/** The Continue Watching grid: each movie/episode resumes exactly where it stopped. */
public class ContinueActivity extends BaseActivity {

    public static void start(Context ctx) { ctx.startActivity(new Intent(ctx, ContinueActivity.class)); }

    private GridView grid;
    private TextView tvEmpty;
    private Adapters.ResumeAdapter adapter;
    private List<ResumeStore.Entry> items = Collections.emptyList();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        setContentView(R.layout.activity_continue);
        grid = findViewById(R.id.gvItems);
        tvEmpty = findViewById(R.id.tvEmpty);
        ((TextView) findViewById(R.id.tvTitle)).setText(R.string.continue_watching);
        adapter = new Adapters.ResumeAdapter(getLayoutInflater());
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) { play(pos); }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { remove(pos); return true; }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        items = ResumeStore.list();
        adapter.items = items;
        adapter.notifyDataSetChanged();
        boolean empty = items.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        grid.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) finish();           // nothing left to resume
    }

    private void play(int pos) {
        if (pos < 0 || pos >= items.size()) return;
        StreamItem item = items.get(pos).item;
        Session.playQueue = Collections.singletonList(item);
        PlayerActivity.start(this, 0, false);   // the player auto-resumes from ResumeStore
    }

    private void remove(int pos) {
        if (pos < 0 || pos >= items.size()) return;
        ResumeStore.remove(items.get(pos).item);
        refresh();
    }
}
