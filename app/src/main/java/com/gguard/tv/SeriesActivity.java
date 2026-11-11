package com.gguard.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.gguard.tv.Models.ContentType;
import com.gguard.tv.Models.Episode;
import com.gguard.tv.Models.StreamItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.Callable;

public class SeriesActivity extends BaseActivity {
    private static final String EXTRA_ID = "id", EXTRA_NAME = "name", EXTRA_COVER = "cover";

    public static void start(Context ctx, StreamItem item) {
        ctx.startActivity(new Intent(ctx, SeriesActivity.class)
                .putExtra(EXTRA_ID, item.id).putExtra(EXTRA_NAME, item.name).putExtra(EXTRA_COVER, item.logo));
    }

    private ViewGroup llSeasons;
    private ListView lvEpisodes;
    private View progress;
    private TextView tvEmpty;
    private Adapters.EpisodeAdapter episodeAdapter;
    private List<Episode> all = Collections.emptyList();
    private List<Episode> current = Collections.emptyList();
    private String seriesName = "";
    private String seriesId = "", seriesCover = null;
    private StreamItem seriesItem;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        setContentView(R.layout.activity_series);
        final String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) { finish(); return; }
        seriesId = id;
        seriesCover = getIntent().getStringExtra(EXTRA_COVER);
        seriesName = getIntent().getStringExtra(EXTRA_NAME);
        if (seriesName == null) seriesName = "";
        seriesItem = new StreamItem(seriesId, seriesName, seriesCover, "", ContentType.SERIES, null, null);
        renderName();
        ImageLoader.load((ImageView) findViewById(R.id.ivCover), getIntent().getStringExtra(EXTRA_COVER),
                (int) (340 * getResources().getDisplayMetrics().density));

        llSeasons = findViewById(R.id.llSeasons);
        lvEpisodes = findViewById(R.id.lvEpisodes);
        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);
        episodeAdapter = new Adapters.EpisodeAdapter(getLayoutInflater());
        lvEpisodes.setAdapter(episodeAdapter);
        lvEpisodes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long rowId) { play(position); }
        });

        async(new Callable<List<Episode>>() {
            @Override public List<Episode> call() throws Exception { return Session.episodes(id); }
        }, new Done<List<Episode>>() {
            @Override public void run(List<Episode> result, Exception error) {
                progress.setVisibility(View.GONE);
                if (error != null) { tvEmpty.setText(errorText(error)); tvEmpty.setVisibility(View.VISIBLE); return; }
                all = result;
                TreeSet<Integer> seasons = new TreeSet<>();
                for (Episode e : all) seasons.add(e.season);
                if (seasons.isEmpty()) { tvEmpty.setText(R.string.empty); tvEmpty.setVisibility(View.VISIBLE); return; }
                buildSeasonButtons(seasons);
                showSeason(seasons.first());
                llSeasons.getChildAt(0).requestFocus();
            }
        });
    }

    private void buildSeasonButtons(TreeSet<Integer> seasons) {
        LayoutInflater inf = getLayoutInflater();
        for (final Integer s : seasons) {
            View v = inf.inflate(R.layout.item_season, llSeasons, false);
            ((TextView) v.findViewById(R.id.tvName)).setText(getString(R.string.season_n, s));
            v.setTag(s);
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { showSeason(s); lvEpisodes.requestFocus(); }
            });
            focusZoom(v, 1.05f);
            llSeasons.addView(v);
        }
    }

    private void showSeason(int season) {
        for (int i = 0; i < llSeasons.getChildCount(); i++) {
            View v = llSeasons.getChildAt(i);
            v.setActivated(Integer.valueOf(season).equals(v.getTag()));
        }
        List<Episode> list = new ArrayList<>();
        for (Episode e : all) if (e.season == season) list.add(e);
        Collections.sort(list, new Comparator<Episode>() {
            @Override public int compare(Episode a, Episode b) { return Integer.compare(a.episodeNum, b.episodeNum); }
        });
        current = list;
        episodeAdapter.items = current;
        episodeAdapter.notifyDataSetChanged();
    }

    private void play(int pos) {
        if (pos < 0 || pos >= current.size()) return;
        List<StreamItem> queue = new ArrayList<>();
        for (Episode e : current) {
            String label = String.format(Locale.ROOT, "S%02dE%02d", e.season, e.episodeNum);
            StringBuilder name = new StringBuilder();
            if (!seriesName.isEmpty()) name.append(seriesName).append("  •  ");
            name.append(label);
            if (!e.title.trim().isEmpty()) name.append("  •  ").append(e.title);
            StreamItem si = new StreamItem(e.id, name.toString(), e.image, "", ContentType.MOVIE, Session.episodeUrl(e), e.extension);
            si.seriesId = seriesId; si.seriesName = seriesName; si.seriesLogo = seriesCover;
            si.season = e.season; si.episodeNum = e.episodeNum;
            queue.add(si);
        }
        Session.playQueue = queue;
        PlayerActivity.start(this, pos, false);
    }

    private void renderName() {
        boolean fav = seriesItem != null && Store.isFav(seriesItem);
        ((TextView) findViewById(R.id.tvName)).setText(seriesName + (fav ? "  ★" : "  ☆"));
    }

    private void toggleSeriesFav() {
        if (seriesItem == null) return;
        boolean added = Store.toggleFav(seriesItem);
        Toast.makeText(this, added ? R.string.added_fav : R.string.removed_fav, Toast.LENGTH_SHORT).show();
        renderName();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) { toggleSeriesFav(); return true; }
        return super.onKeyDown(keyCode, event);
    }
}
