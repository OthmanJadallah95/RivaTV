package com.gguard.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.gguard.tv.Models.Category;
import com.gguard.tv.Models.ContentType;
import com.gguard.tv.Models.StreamItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/** Categories on the side; channels (list) or posters (grid) on the right; search on top. */
public class BrowseActivity extends BaseActivity {
    private static final String EXTRA_TYPE = "type";

    public static void start(Context ctx, ContentType type) {
        ctx.startActivity(new Intent(ctx, BrowseActivity.class).putExtra(EXTRA_TYPE, type.name()));
    }

    private ContentType type;
    private ListView lvCategories;
    private AbsListView itemsView;
    private EditText etSearch;
    private View progress;
    private TextView tvEmpty;
    private Adapters.CategoryAdapter catAdapter;
    private Adapters.StreamAdapter itemAdapter;

    private List<StreamItem> allItems = Collections.emptyList();
    private List<StreamItem> shown = Collections.emptyList();
    private String currentCat = Session.ALL;
    private Runnable pendingCat;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        setContentView(R.layout.activity_browse);
        String t = getIntent().getStringExtra(EXTRA_TYPE);
        type = t != null ? ContentType.valueOf(t) : ContentType.LIVE;

        lvCategories = findViewById(R.id.lvCategories);
        etSearch = findViewById(R.id.etSearch);
        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);
        ListView lvItems = findViewById(R.id.lvItems);
        GridView gvItems = findViewById(R.id.gvItems);

        ((TextView) findViewById(R.id.tvTitle)).setText(
                type == ContentType.LIVE ? R.string.live_tv : type == ContentType.MOVIE ? R.string.movies : R.string.series);

        boolean poster = type != ContentType.LIVE;
        itemsView = poster ? gvItems : lvItems;
        (poster ? lvItems : gvItems).setVisibility(View.GONE);
        itemsView.setVisibility(View.VISIBLE);

        catAdapter = new Adapters.CategoryAdapter(getLayoutInflater());
        lvCategories.setAdapter(catAdapter);
        lvCategories.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (lvCategories.hasFocus()) scheduleCategory(catAdapter.getItem(position).id);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        lvCategories.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (etSearch.getText().length() > 0) etSearch.setText("");
                selectCategory(catAdapter.getItem(position).id);
                itemsView.requestFocus();
            }
        });

        itemAdapter = new Adapters.StreamAdapter(getLayoutInflater(), poster);
        itemsView.setAdapter(itemAdapter);
        itemsView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) { openItem(position); }
        });
        itemsView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                toggleFavorite(position);
                return true;
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        async(new Callable<List<Category>>() {
            @Override public List<Category> call() throws Exception {
                List<Category> cats = Session.categories(type);
                allItems = Session.items(type);
                return cats;
            }
        }, new Done<List<Category>>() {
            @Override public void run(List<Category> cats, Exception error) {
                progress.setVisibility(View.GONE);
                if (error != null) {
                    tvEmpty.setText(errorText(error));
                    tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                Map<String, Integer> counts = new HashMap<>();
                for (StreamItem i : allItems) {
                    Integer n = counts.get(i.categoryId);
                    counts.put(i.categoryId, n == null ? 1 : n + 1);
                }
                counts.put(Session.ALL, allItems.size());
                List<Category> list = new ArrayList<>();
                list.add(new Category(Session.ALL, getString(R.string.all)));
                list.add(new Category(Session.FAV, getString(R.string.favorites)));
                list.addAll(cats);
                catAdapter.items = list;
                catAdapter.counts = counts;
                catAdapter.notifyDataSetChanged();
                selectCategory(Session.ALL);
                lvCategories.requestFocus();
                lvCategories.setSelection(0);
            }
        });
    }

    /** Moving through categories previews them after a short pause, so fast scrolling stays smooth. */
    private void scheduleCategory(final String id) {
        if (pendingCat != null) main.removeCallbacks(pendingCat);
        pendingCat = new Runnable() {
            @Override public void run() { if (etSearch.getText().length() == 0) selectCategory(id); }
        };
        main.postDelayed(pendingCat, 350);
    }

    private void selectCategory(String id) {
        currentCat = id;
        for (int i = 0; i < catAdapter.items.size(); i++) {
            if (catAdapter.items.get(i).id.equals(id)) {
                lvCategories.setItemChecked(i, true);
                break;
            }
        }
        applyFilter();
    }

    private void applyFilter() {
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<StreamItem> out = new ArrayList<>();
        for (StreamItem i : allItems) {
            boolean keep;
            if (!q.isEmpty()) keep = i.name.toLowerCase(Locale.ROOT).contains(q);
            else if (Session.ALL.equals(currentCat)) keep = true;
            else if (Session.FAV.equals(currentCat)) keep = Store.isFav(i);
            else keep = currentCat.equals(i.categoryId);
            if (keep) out.add(i);
        }
        shown = out;
        itemAdapter.items = shown;
        itemAdapter.notifyDataSetChanged();
        itemsView.setSelection(0);
        tvEmpty.setText(Session.FAV.equals(currentCat) && q.isEmpty() ? R.string.fav_hint : R.string.empty);
        tvEmpty.setVisibility(shown.isEmpty() && progress.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private void openItem(int pos) {
        if (pos < 0 || pos >= shown.size()) return;
        StreamItem item = shown.get(pos);
        if (item.type == ContentType.LIVE) {
            Session.playQueue = shown;
            PlayerActivity.start(this, pos, true);
        } else if (item.type == ContentType.SERIES && item.url == null) {
            SeriesActivity.start(this, item);
        } else {
            Session.playQueue = Collections.singletonList(item);
            PlayerActivity.start(this, 0, false);
        }
    }

    private void toggleFavorite(int pos) {
        if (pos < 0 || pos >= shown.size()) return;
        boolean added = Store.toggleFav(shown.get(pos));
        Toast.makeText(this, added ? R.string.added_fav : R.string.removed_fav, Toast.LENGTH_SHORT).show();
        if (Session.FAV.equals(currentCat) && !added) applyFilter();
        else itemAdapter.notifyDataSetChanged();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && itemsView.hasFocus()) {
            toggleFavorite(itemsView.getSelectedItemPosition());
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onResume() {
        super.onResume();
        if (itemAdapter != null) itemAdapter.notifyDataSetChanged();
    }
}
