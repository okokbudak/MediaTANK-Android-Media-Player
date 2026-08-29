package com.orhan.iptv.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R
import com.orhan.iptv.data.Engine
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.Repository
import com.orhan.iptv.player.ExoEngine
import com.orhan.iptv.player.PlayerEngine
import com.orhan.iptv.player.VlcEngine
import java.util.concurrent.TimeUnit

/** Sıranın sonuna gelince ne olacağı. */
private enum class RepeatMode(val label: String) {
    OFF("kapalı"),
    ALL("liste"),
    ONE("parça")
}

/**
 * Müzik çalar: "şu an çalıyor" ekranı ve çalma sırası.
 *
 * Görüntü katmanı olmadığı için oynatıcı motoru gizli bir kapsayıcıya
 * bağlanıyor; ses aynı [PlayerEngine] soyutlamasından geçiyor. NAS parçaları
 * VLC ile (smb:// desteği), yerel dosyalar ExoPlayer ile çalınıyor.
 */
@UnstableApi
class MusicPlayerActivity : AppCompatActivity(), PlayerEngine.Listener {

    private lateinit var cover: ImageView
    private lateinit var trackTitle: TextView
    private lateinit var trackMeta: TextView
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var seek: SeekBar
    private lateinit var btnPlayPause: ImageView
    private lateinit var modeLabel: TextView
    private lateinit var queueList: RecyclerView

    private lateinit var adapter: MusicQueueAdapter

    private var engine: PlayerEngine? = null

    /** Motorun bağlandığı görünüm; düzende gerçekten yer alıyor. */
    private lateinit var engineContainer: android.widget.FrameLayout

    private var currentEngineKind: Engine = Engine.VLC

    /** Geri düşüşte kullanılan motor; parça değişince sıfırlanır. */
    private var engineOverride: Engine? = null

    /** Kullanıcının seçtiği sıra ve karıştırılmış çalma düzeni. */
    private var tracks: List<MediaItem> = emptyList()
    private var order: List<Int> = emptyList()
    private var orderPosition = 0

    private var shuffle = false
    private var repeat = RepeatMode.ALL
    private var userIsSeeking = false

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_player)

        tracks = Repository.musicQueue
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0)
        if (tracks.isEmpty()) {
            finish()
            return
        }

        bindViews()
        buildOrder(startIndex)
        playCurrent()
    }

    private fun bindViews() {
        engineContainer = findViewById(R.id.engine_container)
        cover = findViewById(R.id.cover)
        trackTitle = findViewById(R.id.track_title)
        trackMeta = findViewById(R.id.track_meta)
        timeCurrent = findViewById(R.id.time_current)
        timeTotal = findViewById(R.id.time_total)
        seek = findViewById(R.id.seek)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        modeLabel = findViewById(R.id.mode_label)

        queueList = findViewById(R.id.queue)
        queueList.layoutManager = LinearLayoutManager(this)
        adapter = MusicQueueAdapter { index -> jumpTo(index) }
        queueList.adapter = adapter
        adapter.submit(tracks, currentTrackIndex())

        btnPlayPause.setOnClickListener { togglePlayPause() }
        findViewById<View>(R.id.btn_prev).setOnClickListener { skip(-1) }
        findViewById<View>(R.id.btn_next).setOnClickListener { skip(1) }
        findViewById<View>(R.id.btn_shuffle).setOnClickListener { toggleShuffle() }
        findViewById<View>(R.id.btn_repeat).setOnClickListener { cycleRepeat() }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(bar: SeekBar) { userIsSeeking = true }
            override fun onStopTrackingTouch(bar: SeekBar) {
                userIsSeeking = false
                val duration = engine?.durationMs() ?: -1L
                if (duration > 0) engine?.seekTo(duration * bar.progress / 1000)
            }
        })

        renderModes()
        btnPlayPause.requestFocus()
    }

    // --- Sıra düzeni ---

    /**
     * Çalma düzenini kurar.
     *
     * Karışık modda sıra bir kez karıştırılıp saklanıyor; her parçada yeniden
     * rastgele seçmek "önceki" tuşunu anlamsız kılardı.
     */
    private fun buildOrder(startIndex: Int) {
        val indices = tracks.indices.toMutableList()
        if (shuffle) {
            indices.remove(startIndex)
            indices.shuffle()
            indices.add(0, startIndex)
        }
        order = indices
        orderPosition = order.indexOf(startIndex).coerceAtLeast(0)
    }

    private fun currentTrackIndex(): Int = order.getOrElse(orderPosition) { 0 }

    private fun currentTrack(): MediaItem = tracks[currentTrackIndex()]

    // --- Oynatma ---

    private fun playCurrent() {
        val track = currentTrack()

        engine?.let {
            it.listener = null
            it.release()
        }

        // Ses varsayılan olarak VLC ile çalınıyor: ExoPlayer WMA, APE, DSD,
        // WavPack, Musepack gibi biçimleri açamıyor, VLC hepsini açıyor.
        // Yüksek çözünürlüklü FLAC ve ALAC de bu yüzden VLC'ye gidiyor.
        val kind = if (engineOverride != null) engineOverride!! else Engine.VLC

        val created = if (kind == Engine.VLC) VlcEngine(this) else ExoEngine(this)
        created.listener = this
        engine = created
        currentEngineKind = kind
        // Kapsayıcı düzende gerçekten var; pencereye eklenmemiş bir görünüme
        // bağlanınca libVLC başlamıyor.
        created.prepare(engineContainer, track, 0L)

        trackTitle.text = track.title
        trackMeta.text = buildString {
            track.group?.takeIf { it.isNotBlank() }?.let { append(it).append("  ·  ") }
            append("${orderPosition + 1} / ${tracks.size}")
        }
        adapter.setPlaying(currentTrackIndex())
        queueList.scrollToPosition(currentTrackIndex())

        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun jumpTo(trackIndex: Int) {
        engineOverride = null
        orderPosition = order.indexOf(trackIndex).coerceAtLeast(0)
        playCurrent()
    }

    private fun skip(delta: Int) {
        if (tracks.isEmpty()) return
        engineOverride = null
        orderPosition = (orderPosition + delta + order.size) % order.size
        playCurrent()
    }

    private fun togglePlayPause() {
        val e = engine ?: return
        if (e.isPlaying()) e.pause() else e.play()
    }

    // --- Modlar ---

    private fun toggleShuffle() {
        shuffle = !shuffle
        // Düzeni yeniden kur ama çalan parçayı bozma.
        buildOrder(currentTrackIndex())
        renderModes()
    }

    private fun cycleRepeat() {
        repeat = RepeatMode.values()[(repeat.ordinal + 1) % RepeatMode.values().size]
        renderModes()
    }

    private fun renderModes() {
        modeLabel.text = "Karışık: ${if (shuffle) "açık" else "kapalı"}  ·  Tekrar: ${repeat.label}"
        findViewById<ImageView>(R.id.btn_shuffle).alpha = if (shuffle) 1f else 0.4f
        findViewById<ImageView>(R.id.btn_repeat).alpha =
            if (repeat == RepeatMode.OFF) 0.4f else 1f
    }

    // --- PlayerEngine.Listener ---

    override fun onReady(durationMs: Long) = runOnUiThread {
        val hasDuration = durationMs > 0
        seek.isEnabled = hasDuration
        timeTotal.text = if (hasDuration) formatTime(durationMs) else "--:--"
    }

    override fun onPlayingChanged(playing: Boolean) = runOnUiThread {
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    override fun onBuffering(percent: Int) = Unit

    override fun onEnded() = runOnUiThread {
        when (repeat) {
            RepeatMode.ONE -> playCurrent()
            RepeatMode.ALL -> skip(1)
            RepeatMode.OFF ->
                // Listenin sonundaysak dur, değilse devam et.
                if (orderPosition < order.lastIndex) skip(1) else engine?.pause()
        }
    }

    override fun onError(message: String, fatal: Boolean) = runOnUiThread {
        if (!fatal) return@runOnUiThread

        // Önce diğer motoru dene: VLC biçim kapsamında geniş ama bazı
        // konteynerlerde ExoPlayer daha başarılı olabiliyor.
        if (currentEngineKind == Engine.VLC && engineOverride == null) {
            engineOverride = Engine.EXOPLAYER
            playCurrent()
            return@runOnUiThread
        }

        // İki motor da açamadıysa bozuk dosya listeyi durdurmasın.
        trackMeta.text = "Çalınamadı: ${currentTrack().title}"
        engineOverride = null
        if (order.size > 1) skip(1)
    }

    override fun onTracksChanged() = Unit

    // --- Yardımcılar ---

    private fun updateProgress() {
        val e = engine ?: return
        val duration = e.durationMs()
        val position = e.positionMs()

        timeCurrent.text = formatTime(position)
        if (duration > 0 && !userIsSeeking) {
            seek.progress = (position * 1000 / duration).toInt()
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayPause(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { engine?.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { engine?.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> { skip(1); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> { skip(-1); return true }
            KeyEvent.KEYCODE_MEDIA_STOP -> { finish(); return true }
            KeyEvent.KEYCODE_PROG_GREEN -> { toggleShuffle(); return true }
            KeyEvent.KEYCODE_PROG_YELLOW -> { cycleRepeat(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        engine?.listener = null
        engine?.release()
        engine = null
    }

    companion object {
        private const val EXTRA_INDEX = "index"

        fun start(context: Context, tracks: List<MediaItem>, index: Int) {
            Repository.musicQueue = tracks
            context.startActivity(
                Intent(context, MusicPlayerActivity::class.java).putExtra(EXTRA_INDEX, index)
            )
        }
    }
}
