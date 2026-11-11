package com.gguard.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;

import com.gguard.tv.Models.ContentType;
import com.gguard.tv.Models.Episode;
import com.gguard.tv.Models.StreamItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

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
        final StreamItem item = items.get(pos).item;
        if (item.seriesId != null && !item.seriesId.isEmpty()) {
            // Rebuild the season queue so next/previous episode works after resuming.
            final String sid = item.seriesId;
            async(new Callable<List<Episode>>() {
                @Override public List<Episode> call() throws Exception { return Session.episodes(sid); }
            }, new Done<List<Episode>>() {
                @Override public void run(List<Episode> eps, Exception error) {
                    if (error != null || eps == null || eps.isEmpty()) { playSingle(item); return; }
                    List<Episode> inSeason = new ArrayList<>();
                    for (Episode e : eps) if (e.season == item.season) inSeason.add(e);
                    if (inSeason.isEmpty()) inSeason = eps;
                    Collections.sort(inSeason, new Comparator<Episode>() {
                        @Override public int compare(Episode a, Episode b) { return Integer.compare(a.episodeNum, b.episodeNum); }
                    });
                    List<StreamItem> queue = new ArrayList<>();
                    int idx = 0;
                    for (int i = 0; i < inSeason.size(); i++) {
                        Episode e = inSeason.get(i);
                        String label = String.format(Locale.ROOT, "S%02dE%02d", e.season, e.episodeNum);
                        StringBuilder name = new StringBuilder();
                        if (item.seriesName != null && !item.seriesName.isEmpty()) name.append(item.seriesName).append("  •  ");
                        name.append(label);
                        if (e.title != null && !e.title.trim().isEmpty()) name.append("  •  ").append(e.title);
                        StreamItem si = new StreamItem(e.id, name.toString(), e.image, "", ContentType.MOVIE,
                                Session.episodeUrl(e), e.extension);
                        si.seriesId = item.seriesId; si.seriesName = item.seriesName; si.seriesLogo = item.seriesLogo;
                        si.season = e.season; si.episodeNum = e.episodeNum;
                        queue.add(si);
                        if (e.id.equals(item.id)) idx = i;
                    }
                    Session.playQueue = queue;
                    PlayerActivity.start(ContinueActivity.this, idx, false);
                }
            });
        } else {
            playSingle(item);
        }
    }

    private void playSingle(StreamItem item) {
        Session.playQueue = Collections.singletonList(item);
        PlayerActivity.start(this, 0, false);   // the player auto-resumes from ResumeStore
    }

    private void remove(int pos) {
        if (pos < 0 || pos >= items.size()) return;
        ResumeStore.remove(items.get(pos).item);
        refresh();
    }
}
