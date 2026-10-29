package com.gguard.tv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.gguard.tv.R
import com.gguard.tv.data.Category
import com.gguard.tv.data.ContentType
import com.gguard.tv.data.Episode
import com.gguard.tv.data.Session
import com.gguard.tv.data.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SeriesActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_COVER = "cover"
        fun start(ctx: Context, item: StreamItem) {
            ctx.startActivity(Intent(ctx, SeriesActivity::class.java)
                .putExtra(EXTRA_ID, item.id)
                .putExtra(EXTRA_NAME, item.name)
                .putExtra(EXTRA_COVER, item.logo))
        }
    }

    private lateinit var rvSeasons: RecyclerView
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var seasonAdapter: CategoryAdapter
    private lateinit var episodeAdapter: EpisodeAdapter
    private var all: List<Episode> = emptyList()
    private var current: List<Episode> = emptyList()
    private var seriesName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSession()) return
        setContentView(R.layout.activity_series)
        val id = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        seriesName = intent.getStringExtra(EXTRA_NAME) ?: ""
        findViewById<TextView>(R.id.tvName).text = seriesName
        findViewById<ImageView>(R.id.ivCover).load(intent.getStringExtra(EXTRA_COVER)) {
            placeholder(R.drawable.placeholder); error(R.drawable.placeholder)
        }

        rvSeasons = findViewById(R.id.rvSeasons)
        rvEpisodes = findViewById(R.id.rvEpisodes)
        progress = findViewById(R.id.progress)
        tvEmpty = findViewById(R.id.tvEmpty)

        seasonAdapter = CategoryAdapter(R.layout.item_season, onFocus = null, onClick = { c ->
            showSeason(c.id.toInt())
            rvEpisodes.requestFocus()
        })
        rvSeasons.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSeasons.adapter = seasonAdapter

        episodeAdapter = EpisodeAdapter(::play)
        rvEpisodes.layoutManager = LinearLayoutManager(this)
        rvEpisodes.adapter = episodeAdapter

        lifecycleScope.launch {
            try {
                all = Session.episodes(id)
                val seasons = all.map { it.season }.distinct().sorted()
                seasonAdapter.items = seasons.map { Category(it.toString(), getString(R.string.season_n, it)) }
                if (seasons.isEmpty()) {
                    tvEmpty.setText(R.string.empty); tvEmpty.visibility = View.VISIBLE
                } else {
                    showSeason(seasons.first())
                    rvSeasons.post { rvSeasons.getChildAt(0)?.requestFocus() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tvEmpty.text = getString(R.string.err_generic, e.message ?: e.javaClass.simpleName)
                tvEmpty.visibility = View.VISIBLE
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun showSeason(season: Int) {
        seasonAdapter.markSelected(rvSeasons, season.toString())
        current = all.filter { it.season == season }.sortedBy { it.episodeNum }
        episodeAdapter.items = current
    }

    private fun play(pos: Int) {
        if (pos !in current.indices) return
        Session.playQueue = current.map { e ->
            val label = "S%02dE%02d".format(e.season, e.episodeNum)
            StreamItem(
                id = e.id,
                name = listOf(seriesName, label, e.title).filter { it.isNotBlank() }.joinToString("  •  "),
                logo = e.image,
                categoryId = "",
                type = ContentType.MOVIE,
                url = Session.episodeUrl(e),
            )
        }
        PlayerActivity.start(this, pos, live = false)
    }
}
