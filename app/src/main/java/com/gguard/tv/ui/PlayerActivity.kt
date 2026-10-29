package com.gguard.tv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gguard.tv.R
import com.gguard.tv.data.Http
import com.gguard.tv.data.Session
import com.gguard.tv.data.StreamItem
import kotlinx.coroutines.launch

/**
 * Full-screen player. For live TV, Up/Down (or Channel +/-) zaps through the
 * channel list while the controls are hidden; Menu cycles the picture size.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_LIVE = "live"
        fun start(ctx: Context, index: Int, live: Boolean) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_LIVE, live))
        }
    }

    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var tvChannel: TextView
    private lateinit var tvEpg: TextView
    private lateinit var tvError: TextView

    private var player: ExoPlayer? = null
    private var queue: List<StreamItem> = emptyList()
    private var index = 0
    private var live = false
    private var currentUrl = ""
    private var retries = 0
    private var resumePosition = 0L
    private var resizeIdx = 0

    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }
    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    )
    private val resizeLabels = intArrayOf(R.string.resize_fit, R.string.resize_fill, R.string.resize_zoom)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ensureSession()) return
        queue = Session.playQueue
        if (queue.isEmpty()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        overlay = findViewById(R.id.overlay)
        tvChannel = findViewById(R.id.tvChannel)
        tvEpg = findViewById(R.id.tvEpg)
        tvError = findViewById(R.id.tvError)

        index = (savedInstanceState?.getInt(EXTRA_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0)).coerceIn(0, queue.size - 1)
        live = intent.getBooleanExtra(EXTRA_LIVE, false)
        resumePosition = savedInstanceState?.getLong("pos") ?: 0L
        if (live) playerView.controllerShowTimeoutMs = 3000
    }

    override fun onStart() {
        super.onStart()
        if (queue.isEmpty()) return
        initPlayer()
        play(index, resumePosition)
    }

    override fun onStop() {
        super.onStop()
        resumePosition = player?.currentPosition ?: 0L
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INDEX, index)
        outState.putLong("pos", if (live) 0L else (player?.currentPosition ?: resumePosition))
    }

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .build()
        p.addListener(listener)
        p.playWhenReady = true
        playerView.player = p
        playerView.resizeMode = resizeModes[resizeIdx]
        player = p
    }

    private fun releasePlayer() {
        handler.removeCallbacksAndMessages(null)
        player?.removeListener(listener)
        player?.release()
        player = null
        if (::playerView.isInitialized) playerView.player = null
    }

    private fun play(i: Int, startAt: Long = 0L) {
        index = i
        val item = queue[i]
        val url = Session.streamUrl(item)
        if (url.isNullOrBlank()) { showError(getString(R.string.empty)); return }
        currentUrl = url
        retries = 0
        startUrl(url, startAt)
        showOverlay(item)
    }

    private fun startUrl(url: String, startAt: Long = 0L) {
        val p = player ?: return
        val builder = MediaItem.Builder().setUri(url)
        if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        p.setMediaItem(builder.build(), startAt)
        p.prepare()
        tvError.visibility = View.GONE
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                tvError.visibility = View.GONE
                retries = 0
            }
            if (state == Player.STATE_ENDED && !live && index < queue.size - 1) play(index + 1)
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                p.seekToDefaultPosition(); p.prepare(); return
            }
            // Some providers only serve HLS for live channels: switch .ts -> .m3u8 once.
            if (live && retries == 0 && currentUrl.endsWith(".ts")) {
                retries++
                currentUrl = currentUrl.removeSuffix(".ts") + ".m3u8"
                startUrl(currentUrl)
                return
            }
            if (retries < 4) {
                retries++
                showError(getString(R.string.reconnecting))
                val pos = if (live) 0L else p.currentPosition
                handler.postDelayed({ startUrl(currentUrl, pos) }, 2500)
                return
            }
            showError(getString(R.string.playback_error, error.errorCodeName))
        }
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun showOverlay(item: StreamItem) {
        tvChannel.text = if (live) "${index + 1}   ${item.name}" else item.name
        tvEpg.visibility = View.GONE
        overlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideOverlay)
        handler.postDelayed(hideOverlay, 5000)

        if (live && Session.isXtream && item.url == null) {
            val requested = index
            lifecycleScope.launch {
                val epg = runCatching { Session.shortEpg(item.id) }.getOrNull().orEmpty()
                if (requested != index || epg.isEmpty()) return@launch
                val lines = buildList {
                    add(getString(R.string.now_fmt, epg[0].title))
                    epg.getOrNull(1)?.let { add(getString(R.string.next_fmt, it.title)) }
                }
                tvEpg.text = lines.joinToString("\n")
                tvEpg.visibility = View.VISIBLE
            }
        }
    }

    private fun zap(delta: Int) {
        if (queue.size < 2) return
        play(Math.floorMod(index + delta, queue.size))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (live && !playerView.isControllerFullyVisible) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(1); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); return true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        queue.getOrNull(index)?.let(::showOverlay); return true
                    }
                }
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                    resizeIdx = (resizeIdx + 1) % resizeModes.size
                    playerView.resizeMode = resizeModes[resizeIdx]
                    Toast.makeText(this, resizeLabels[resizeIdx], Toast.LENGTH_SHORT).show()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
