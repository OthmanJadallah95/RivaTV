package com.gguard.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.gguard.tv.R
import com.gguard.tv.data.Category
import com.gguard.tv.data.Episode
import com.gguard.tv.data.Profile
import com.gguard.tv.data.ProfileType
import com.gguard.tv.data.StreamItem

/** Slight zoom on the focused card so the remote's position is easy to see across the room. */
fun View.focusZoom(scale: Float = 1.04f, extra: ((Boolean) -> Unit)? = null) {
    setOnFocusChangeListener { v, has ->
        v.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).setDuration(120).start()
        extra?.invoke(has)
    }
}

class CategoryAdapter(
    private val layout: Int,
    private val onFocus: ((Category) -> Unit)?,
    private val onClick: (Category) -> Unit,
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    var items: List<Category> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }
    var counts: Map<String, Int> = emptyMap()
    var selectedId: String? = null

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val count: TextView = v.findViewById(R.id.tvCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = items[position]
        h.name.text = c.name
        h.count.text = counts[c.id]?.toString() ?: ""
        h.itemView.isActivated = c.id == selectedId
        h.itemView.setOnClickListener { onClick(c) }
        h.itemView.focusZoom(1.02f) { has -> if (has) onFocus?.invoke(c) }
    }

    /** Updates the highlighted row without rebinding, so D-pad focus is not lost. */
    fun markSelected(rv: RecyclerView, id: String) {
        selectedId = id
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val pos = rv.getChildAdapterPosition(child)
            if (pos in items.indices) child.isActivated = items[pos].id == id
        }
    }
}

class StreamAdapter(
    private val poster: Boolean,
    private val isFav: (StreamItem) -> Boolean,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<StreamAdapter.VH>() {

    var items: List<StreamItem> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val fav: TextView = v.findViewById(R.id.tvFav)
        val num: TextView? = v.findViewById<TextView>(R.id.tvNum)
        val image: ImageView = v.findViewById<ImageView?>(R.id.ivLogo) ?: v.findViewById<ImageView>(R.id.ivPoster)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (poster) R.layout.item_poster else R.layout.item_channel
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.name.text = item.name
        h.num?.text = (position + 1).toString()
        h.fav.visibility = if (isFav(item)) View.VISIBLE else View.GONE
        h.image.load(item.logo) {
            crossfade(true)
            placeholder(R.drawable.placeholder)
            error(R.drawable.placeholder)
        }
        h.itemView.setOnClickListener { onClick(h.bindingAdapterPosition) }
        h.itemView.setOnLongClickListener { onLongClick(h.bindingAdapterPosition); true }
        h.itemView.focusZoom(if (poster) 1.06f else 1.02f)
    }
}

class EpisodeAdapter(private val onClick: (Int) -> Unit) : RecyclerView.Adapter<EpisodeAdapter.VH>() {
    var items: List<Episode> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.ivThumb)
        val title: TextView = v.findViewById(R.id.tvTitle)
        val plot: TextView = v.findViewById(R.id.tvPlot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = items[position]
        h.title.text = if (e.title.isNotBlank()) e.title else "E${e.episodeNum}"
        h.plot.text = e.plot ?: ""
        h.plot.visibility = if (e.plot.isNullOrBlank()) View.GONE else View.VISIBLE
        h.thumb.load(e.image) {
            crossfade(true)
            placeholder(R.drawable.placeholder)
            error(R.drawable.placeholder)
        }
        h.itemView.setOnClickListener { onClick(h.bindingAdapterPosition) }
        h.itemView.focusZoom(1.02f)
    }
}

class ProfileAdapter(
    private val onClick: (Profile) -> Unit,
    private val onLongClick: (Profile) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.VH>() {
    var items: List<Profile> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val sub: TextView = v.findViewById(R.id.tvSub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = items[position]
        h.name.text = p.name
        h.sub.text = if (p.type == ProfileType.XTREAM) "Xtream • ${p.server} • ${p.username}" else "M3U • ${p.m3uUrl}"
        h.itemView.setOnClickListener { onClick(p) }
        h.itemView.setOnLongClickListener { onLongClick(p); true }
        h.itemView.focusZoom(1.02f)
    }
}
