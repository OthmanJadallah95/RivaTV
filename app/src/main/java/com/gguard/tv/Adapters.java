package com.gguard.tv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.gguard.tv.Models.Category;
import com.gguard.tv.Models.Episode;
import com.gguard.tv.Models.Profile;
import com.gguard.tv.Models.StreamItem;
import com.gguard.tv.ResumeStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Adapters {
    private Adapters() {}

    public static final class CategoryAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        public List<Category> items = Collections.emptyList();
        public Map<String, Integer> counts = Collections.emptyMap();

        public CategoryAdapter(LayoutInflater inflater) { this.inflater = inflater; }

        @Override public int getCount() { return items.size(); }
        @Override public Category getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            View v = convert != null ? convert : inflater.inflate(R.layout.item_category, parent, false);
            Category c = items.get(pos);
            ((TextView) v.findViewById(R.id.tvName)).setText(c.name);
            Integer n = counts.get(c.id);
            ((TextView) v.findViewById(R.id.tvCount)).setText(n != null ? String.valueOf(n) : "");
            return v;
        }
    }

    public static final class StreamAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final boolean poster;
        private final int imagePx;
        public List<StreamItem> items = Collections.emptyList();

        public StreamAdapter(LayoutInflater inflater, boolean poster) {
            this.inflater = inflater;
            this.poster = poster;
            float d = inflater.getContext().getResources().getDisplayMetrics().density;
            this.imagePx = (int) ((poster ? 180 : 72) * d);
        }

        @Override public int getCount() { return items.size(); }
        @Override public StreamItem getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : inflater.inflate(poster ? R.layout.item_poster : R.layout.item_channel, parent, false);
            StreamItem item = items.get(pos);
            ((TextView) v.findViewById(R.id.tvName)).setText(item.name);
            TextView num = v.findViewById(R.id.tvNum);
            if (num != null) num.setText(String.valueOf(pos + 1));
            TextView fav = v.findViewById(R.id.tvFav);
            boolean isFav = Store.isFav(item);
            fav.setVisibility(View.VISIBLE);
            fav.setText(isFav ? "★" : "☆");
            fav.setAlpha(isFav ? 1f : 0.6f);
            ImageView iv = v.findViewById(poster ? R.id.ivPoster : R.id.ivLogo);
            ImageLoader.load(iv, item.logo, imagePx);
            return v;
        }
    }

    public static final class EpisodeAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final int imagePx;
        public List<Episode> items = Collections.emptyList();

        public EpisodeAdapter(LayoutInflater inflater) {
            this.inflater = inflater;
            this.imagePx = (int) (160 * inflater.getContext().getResources().getDisplayMetrics().density);
        }

        @Override public int getCount() { return items.size(); }
        @Override public Episode getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            View v = convert != null ? convert : inflater.inflate(R.layout.item_episode, parent, false);
            Episode e = items.get(pos);
            String title = e.title.trim().isEmpty() ? "E" + e.episodeNum : e.title;
            ((TextView) v.findViewById(R.id.tvTitle)).setText(title);
            TextView plot = v.findViewById(R.id.tvPlot);
            plot.setText(e.plot != null ? e.plot : "");
            plot.setVisibility(e.plot == null ? View.GONE : View.VISIBLE);
            ImageLoader.load((ImageView) v.findViewById(R.id.ivThumb), e.image, imagePx);
            return v;
        }
    }

    public static final class ProfileAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        public List<Profile> items = Collections.emptyList();

        public ProfileAdapter(LayoutInflater inflater) { this.inflater = inflater; }

        @Override public int getCount() { return items.size(); }
        @Override public Profile getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            View v = convert != null ? convert : inflater.inflate(R.layout.item_profile, parent, false);
            Profile p = items.get(pos);
            ((TextView) v.findViewById(R.id.tvName)).setText(p.name);
            ((TextView) v.findViewById(R.id.tvSub)).setText(p.isXtream()
                    ? "Xtream • " + p.server + " • " + p.username : "M3U • " + p.m3uUrl);
            return v;
        }
    }

    public static final class ResumeAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final int imagePx;
        public List<ResumeStore.Entry> items = Collections.emptyList();

        public ResumeAdapter(LayoutInflater inflater) {
            this.inflater = inflater;
            this.imagePx = (int) (160 * inflater.getContext().getResources().getDisplayMetrics().density);
        }

        @Override public int getCount() { return items.size(); }
        @Override public ResumeStore.Entry getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            View v = convert != null ? convert : inflater.inflate(R.layout.item_resume, parent, false);
            ResumeStore.Entry e = items.get(pos);
            ((TextView) v.findViewById(R.id.tvName)).setText(e.item.name);
            ((android.widget.ProgressBar) v.findViewById(R.id.pbProgress)).setProgress(Math.max(3, e.percent()));
            ImageLoader.load((ImageView) v.findViewById(R.id.ivPoster), e.item.logo, imagePx);
            return v;
        }
    }
}
