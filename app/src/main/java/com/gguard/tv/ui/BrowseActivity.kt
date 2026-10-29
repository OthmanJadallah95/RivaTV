package com.gguard.tv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gguard.tv.R
import com.gguard.tv.data.Category
import com.gguard.tv.data.ContentType
import com.gguard.tv.data.Favorites
import com.gguard.tv.data.Session
import com.gguard.tv.data.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Category list on the side, channels (list) or posters (grid) on the right, with search. */
class BrowseActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TYPE = "type"
        fun start(ctx: Context, type: ContentType) {
            ctx.startActivity(Intent(ctx, BrowseActivity::class.java).putExtra(EXTRA_TYPE, type.name))
        }
    }

    private lateinit var type: ContentType
    private lateinit var rvCategories: RecyclerView
    private lateinit var rvItems: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var catAdapter: CategoryAdapter
    private lateinit var itemAdapter: StreamAdapter

    private var allItems: List<StreamItem> = emptyList()
    private var shown: List<StreamItem> = emptyList()
    private var currentCat = Session.ALL

    private val handler = Handler(Looper.getMainLooper())
    private var pendingCat: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSession()) return
        setContentView(R.layout.activity_browse)
        type = ContentType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: ContentType.LIVE.name)

        rvCategories = findViewById(R.id.rvCategories)
        rvItems = findViewById(R.id.rvItems)
        etSearch = findViewById(R.id.etSearch)
        progress = findViewById(R.id.progress)
        tvEmpty = findViewById(R.id.tvEmpty)

        findViewById<TextView>(R.id.tvTitle).setText(
            when (type) {
                ContentType.LIVE -> R.string.live_tv
                ContentType.MOVIE -> R.string.movies
                ContentType.SERIES -> R.string.series
            }
        )

        catAdapter = CategoryAdapter(R.layout.item_category,
            onFocus = { c -> scheduleCategory(c.id) },
            onClick = { c ->
                if (etSearch.text.isNotEmpty()) etSearch.setText("")
                selectCategory(c.id)
                rvItems.requestFocus()
            })
        rvCategories.layoutManager = LinearLayoutManager(this)
        rvCategories.adapter = catAdapter

        val poster = type != ContentType.LIVE
        itemAdapter = StreamAdapter(poster, Favorites::isFav, ::openItem, ::toggleFavorite)
        rvItems.layoutManager = if (poster) GridLayoutManager(this, 5) else LinearLayoutManager(this)
        rvItems.adapter = itemAdapter
        rvItems.setHasFixedSize(true)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            try {
                val cats = Session.categories(type)
                allItems = Session.items(type)
                catAdapter.counts = allItems.groupingBy { it.categoryId }.eachCount() + (Session.ALL to allItems.size)
                catAdapter.items = listOf(
                    Category(Session.ALL, getString(R.string.all)),
                    Category(Session.FAV, getString(R.string.favorites)),
                ) + cats
                selectCategory(Session.ALL)
                rvCategories.post { rvCategories.getChildAt(0)?.requestFocus() }
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

    /** Focusing a category previews it after a short pause, so scrolling quickly through the list stays smooth. */
    private fun scheduleCategory(id: String) {
        pendingCat?.let { handler.removeCallbacks(it) }
        val r = Runnable { if (etSearch.text.isEmpty()) selectCategory(id) }
        pendingCat = r
        handler.postDelayed(r, 350)
    }

    private fun selectCategory(id: String) {
        currentCat = id
        catAdapter.markSelected(rvCategories, id)
        applyFilter()
    }

    private fun applyFilter() {
        val q = etSearch.text.toString().trim()
        shown = when {
            q.isNotEmpty() -> allItems.filter { it.name.contains(q, ignoreCase = true) }
            currentCat == Session.ALL -> allItems
            currentCat == Session.FAV -> allItems.filter { Favorites.isFav(it) }
            else -> allItems.filter { it.categoryId == currentCat }
        }
        itemAdapter.items = shown
        rvItems.scrollToPosition(0)
        tvEmpty.text = getString(if (currentCat == Session.FAV && q.isEmpty()) R.string.fav_hint else R.string.empty)
        tvEmpty.visibility = if (shown.isEmpty() && progress.visibility != View.VISIBLE) View.VISIBLE else View.GONE
    }

    private fun openItem(pos: Int) {
        val item = shown.getOrNull(pos) ?: return
        when {
            item.type == ContentType.LIVE -> {
                Session.playQueue = shown
                PlayerActivity.start(this, pos, live = true)
            }
            item.type == ContentType.SERIES && item.url == null -> SeriesActivity.start(this, item)
            else -> {
                Session.playQueue = listOf(item)
                PlayerActivity.start(this, 0, live = false)
            }
        }
    }

    private fun toggleFavorite(pos: Int) {
        val item = shown.getOrNull(pos) ?: return
        val added = Favorites.toggle(item)
        Toast.makeText(this, if (added) R.string.added_fav else R.string.removed_fav, Toast.LENGTH_SHORT).show()
        if (currentCat == Session.FAV && !added) applyFilter() else itemAdapter.notifyItemChanged(pos)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val focused = rvItems.focusedChild
            if (focused != null) {
                toggleFavorite(rvItems.getChildAdapterPosition(focused))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
