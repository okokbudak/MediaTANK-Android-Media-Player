package com.orhan.iptv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.orhan.iptv.R
import com.orhan.iptv.data.Engine
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Repository
import com.orhan.iptv.ui.settings.Languages
import java.util.concurrent.TimeUnit

/**
 * Oynatma ekranı.
 *
 * Motor seçimi:
 *  1. Öğe bir motoru açıkça istiyorsa (örn. SMB dosyaları -> VLC) o kullanılır.
 *  2. Canlı HLS/DASH yayınlar ExoPlayer'a gider (uyarlanabilir bit hızı, hızlı açılış).
 *  3. Diğer her şey ve daha önce ExoPlayer'da patlamış içerikler VLC'ye gider.
 *  4. ExoPlayer ölümcül hata verirse aynı konumdan VLC'ye otomatik düşülür.
 *
 * Kumanda: Sony kumandasının medya, kanal, ses/altyazı ve renkli tuşları
 * doğrudan destekleniyor — bkz. [onKeyDown].
 */
@UnstableApi
class PlaybackActivity : AppCompatActivity(), PlayerEngine.Listener {

    private lateinit var item: MediaItem
    private lateinit var videoContainer: FrameLayout
    private lateinit var controls: View
    private lateinit var buffering: View
    private lateinit var status: TextView
    private lateinit var nowTitle: TextView
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var seek: SeekBar
    private lateinit var btnPlayPause: ImageView
    private lateinit var engineLabel: TextView

    private var engine: PlayerEngine? = null
    private var currentEngineKind: Engine = Engine.EXOPLAYER
    private var triedFallback = false
    private var userIsSeeking = false

    /** Önceki/sonraki için sıra ve içindeki konum. */
    private var queue: List<MediaItem> = emptyList()
    private var queueIndex = 0

    /** Tercih edilen diller yalnız bir kez uygulanmalı. */
    private var languagesApplied = false

    /** Dokunmatik jestler: tek dokunuş paneli açıp kapatır, çift dokunuş sarar. */
    private lateinit var gestureDetector: GestureDetector

    /** Başlayan dokunuşun jest algılayıcıya gidip gitmeyeceği (panel üstündekiler gitmez). */
    private var gestureArmed = false

    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { controls.visibility = View.GONE }

    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)

        queue = Repository.queue
        queueIndex = intent.getIntExtra(EXTRA_INDEX, -1)

        @Suppress("DEPRECATION")
        val direct = intent.getSerializableExtra(EXTRA_ITEM) as? MediaItem
        item = direct
            ?: queue.getOrNull(queueIndex)
            ?: run {
                Toast.makeText(this, "Oynatılacak içerik bulunamadı", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        // Tek öğe verildiyse sıra da o öğeden ibaret olsun.
        if (queue.isEmpty()) {
            queue = listOf(item)
            queueIndex = 0
        }

        bindViews()
        startEngine(chooseEngine(), Repository.position(item.id))
    }

    private fun bindViews() {
        videoContainer = findViewById(R.id.video_container)
        controls = findViewById(R.id.controls)
        buffering = findViewById(R.id.buffering)
        status = findViewById(R.id.status)
        nowTitle = findViewById(R.id.now_title)
        timeCurrent = findViewById(R.id.time_current)
        timeTotal = findViewById(R.id.time_total)
        seek = findViewById(R.id.seek)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        engineLabel = findViewById(R.id.engine_label)

        nowTitle.text = item.title

        btnPlayPause.setOnClickListener { togglePlayPause() }
        findViewById<View>(R.id.btn_prev).setOnClickListener { skip(-1) }
        findViewById<View>(R.id.btn_next).setOnClickListener { skip(1) }
        findViewById<View>(R.id.btn_rewind).setOnClickListener { seekBy(-SEEK_STEP_MS) }
        findViewById<View>(R.id.btn_forward).setOnClickListener { seekBy(SEEK_STEP_MS) }
        findViewById<View>(R.id.btn_audio).setOnClickListener { showAudioMenu() }
        findViewById<View>(R.id.btn_subtitle).setOnClickListener { showSubtitleMenu() }
        findViewById<View>(R.id.btn_engine).setOnClickListener { switchEngine() }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                // Çubuk sürüklenirken panel 5 sn dolunca kapanmasın.
                if (fromUser) keepControlsAlive()
            }
            override fun onStartTrackingTouch(bar: SeekBar) {
                userIsSeeking = true
                keepControlsAlive()
            }
            override fun onStopTrackingTouch(bar: SeekBar) {
                userIsSeeking = false
                val duration = engine?.durationMs() ?: -1L
                if (duration > 0) engine?.seekTo(duration * bar.progress / 1000)
                keepControlsAlive()
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls()
                return true
            }

            // Çift dokunuş: sol üçte bir geri, sağ üçte bir ileri, orta oynat/duraklat.
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val third = videoContainer.width / 3f
                when {
                    e.x < third -> seekBy(-SEEK_STEP_MS)
                    e.x > third * 2 -> seekBy(SEEK_STEP_MS)
                    else -> togglePlayPause()
                }
                return true
            }
        })
    }

    /**
     * Dokunuşları panelin dışında jest algılayıcıya da iletir.
     *
     * Etkinlik düzeyinde dinliyoruz: video yüzeyi (VLC/ExoPlayer) dokunuşu
     * kendisi tüketebiliyor. Panel görünürken onun üstüne düşen dokunuşlar
     * atlanıyor, yoksa bir düğmeye basmak paneli de kapatırdı. Olay hiçbir
     * zaman tüketilmiyor; düğmeler ve arama çubuğu normal çalışmaya devam eder.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureArmed = !(controls.visibility == View.VISIBLE && isOnScreenInside(controls, ev))
        }
        if (gestureArmed) gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun isOnScreenInside(view: View, ev: MotionEvent): Boolean {
        val where = IntArray(2)
        view.getLocationOnScreen(where)
        return ev.rawX >= where[0] && ev.rawX <= where[0] + view.width &&
            ev.rawY >= where[1] && ev.rawY <= where[1] + view.height
    }

    private fun toggleControls() {
        if (controls.visibility == View.VISIBLE) {
            handler.removeCallbacks(hideControls)
            controls.visibility = View.GONE
        } else {
            showControls()
        }
    }

    /** Paneli açık tutar; odağı düğmeye taşımadan yalnızca kapanma sayacını yeniler. */
    private fun keepControlsAlive() {
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, CONTROLS_TIMEOUT_MS)
    }

    // --- Motor yönetimi ---

    private fun chooseEngine(): Engine {
        item.preferredEngine?.let { return it }
        if (Repository.exoFailedBefore(item.playUrl)) return Engine.VLC

        val url = item.playUrl.lowercase(java.util.Locale.ROOT)
        if (url.startsWith("smb://") || url.startsWith("nfs://") || url.startsWith("ftp://")) {
            return Engine.VLC
        }
        if (item.kind == MediaKind.LIVE && (url.contains(".m3u8") || url.contains(".mpd"))) {
            return Engine.EXOPLAYER
        }
        return if (url.endsWith(".mp4") || url.contains(".m3u8")) Engine.EXOPLAYER else Engine.VLC
    }

    private fun startEngine(kind: Engine, positionMs: Long) {
        engine?.let {
            it.listener = null
            it.release()
        }

        currentEngineKind = kind
        languagesApplied = false
        engineLabel.text = getString(
            if (kind == Engine.VLC) R.string.player_engine_vlc else R.string.player_engine_exo
        )

        val created = if (kind == Engine.VLC) VlcEngine(this) else ExoEngine(this)
        created.listener = this
        engine = created
        buffering.visibility = View.VISIBLE

        created.prepare(videoContainer, item, positionMs)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun switchEngine() {
        val position = engine?.positionMs() ?: 0L
        val next = if (currentEngineKind == Engine.VLC) Engine.EXOPLAYER else Engine.VLC
        showStatus("${if (next == Engine.VLC) "VLC" else "ExoPlayer"} motoruna geçiliyor…")
        triedFallback = true      // kullanıcı bilinçli seçti, otomatik geri düşme
        startEngine(next, position)
        showControls()
    }

    // --- Sıra ---

    /** Sırada [delta] kadar ilerler; canlı kanallarda kanal değiştirmeye karşılık gelir. */
    private fun skip(delta: Int) {
        if (queue.size < 2) {
            showStatus("Sırada başka içerik yok")
            return
        }
        savePosition()
        queueIndex = (queueIndex + delta + queue.size) % queue.size
        item = queue[queueIndex]
        nowTitle.text = item.title
        triedFallback = false
        showStatus(item.title)
        startEngine(chooseEngine(), Repository.position(item.id))
        showControls()
    }

    // --- PlayerEngine.Listener ---

    override fun onReady(durationMs: Long) = runOnUiThread {
        buffering.visibility = View.GONE
        val hasDuration = durationMs > 0
        seek.isEnabled = hasDuration
        timeTotal.text = if (hasDuration) formatTime(durationMs) else "CANLI"
    }

    override fun onPlayingChanged(playing: Boolean) = runOnUiThread {
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        if (playing) buffering.visibility = View.GONE
    }

    override fun onBuffering(percent: Int) = runOnUiThread {
        buffering.visibility = if (percent >= 100) View.GONE else View.VISIBLE
    }

    override fun onEnded() = runOnUiThread {
        val duration = engine?.durationMs() ?: 0L
        Repository.savePosition(item.id, duration, duration)
        // Sıradaki bölüme/filme kendiliğinden geç; tek öğe varsa kapat.
        if (queue.size > 1) skip(1) else finish()
    }

    override fun onError(message: String, fatal: Boolean) = runOnUiThread {
        if (!fatal) {
            showStatus(message)
            return@runOnUiThread
        }

        if (currentEngineKind == Engine.EXOPLAYER && !triedFallback) {
            triedFallback = true
            Repository.markExoFailed(item.playUrl)
            showStatus("Bu format için VLC motoruna geçiliyor…")
            startEngine(Engine.VLC, engine?.positionMs() ?: 0L)
            return@runOnUiThread
        }

        buffering.visibility = View.GONE
        AlertDialog.Builder(this)
            .setTitle("Oynatılamadı")
            .setMessage("$message\n\n${item.playUrl}")
            .setPositiveButton("Kapat") { _, _ -> finish() }
            .setOnDismissListener { finish() }
            .show()
    }

    override fun onTracksChanged() = runOnUiThread { applyPreferredLanguages() }

    /**
     * Ayarlardaki dil tercihlerini içerikteki kanallara uygular.
     *
     * Yalnız ilk kanal listesi hazır olduğunda çalışır; kullanıcı sonradan
     * elle başka bir kanal seçerse tercih onu ezmemeli.
     */
    private fun applyPreferredLanguages() {
        val player = engine ?: return
        if (languagesApplied) return

        val audioTracks = player.audioTracks()
        val subtitleTracks = player.subtitleTracks()
        if (audioTracks.isEmpty() && subtitleTracks.isEmpty()) return
        languagesApplied = true

        Repository.preferredAudioLanguage().takeIf { it.isNotBlank() }?.let { code ->
            audioTracks.firstOrNull { Languages.matches(code, it.language, it.label) }
                ?.let { player.selectAudio(it) }
        }

        val subtitleCode = Repository.preferredSubtitleLanguage()
        if (subtitleCode.isBlank()) {
            player.selectSubtitle(null)
        } else {
            subtitleTracks.firstOrNull { Languages.matches(subtitleCode, it.language, it.label) }
                ?.let { player.selectSubtitle(it) }
        }
    }

    // --- Ses / altyazı menüleri ---

    private fun showAudioMenu() {
        val tracks = engine?.audioTracks().orEmpty()
        if (tracks.isEmpty()) {
            Toast.makeText(this, "Ses kanalı bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = tracks.map { it.label }.toTypedArray()
        val checked = tracks.indexOfFirst { it.selected }

        AlertDialog.Builder(this)
            .setTitle(R.string.track_audio)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                engine?.selectAudio(tracks[which])
                dialog.dismiss()
                showControls()
            }
            .show()
    }

    private fun showSubtitleMenu() {
        val tracks = engine?.subtitleTracks().orEmpty()
        val labels = (listOf(getString(R.string.track_off)) + tracks.map { it.label }).toTypedArray()
        val checked = tracks.indexOfFirst { it.selected }.let { if (it < 0) 0 else it + 1 }

        AlertDialog.Builder(this)
            .setTitle(R.string.track_subtitle)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                engine?.selectSubtitle(if (which == 0) null else tracks[which - 1])
                dialog.dismiss()
                showControls()
            }
            .show()
    }

    // --- Kumanda ---

    private fun togglePlayPause() {
        val e = engine ?: return
        if (e.isPlaying()) e.pause() else e.play()
        showControls()
    }

    /** [deltaMs] kadar sarar; canlı yayında sarılacak bir şey yoktur. */
    private fun seekBy(deltaMs: Long) {
        val e = engine ?: return
        val duration = e.durationMs()
        if (duration <= 0) {
            showStatus("Canlı yayında sarma yapılamaz")
            return
        }
        val target = (e.positionMs() + deltaMs).coerceIn(0, duration)
        e.seekTo(target)
        showStatus(formatTime(target))
        showControls()
    }

    private fun showControls() {
        controls.visibility = View.VISIBLE
        if (!controls.hasFocus()) btnPlayPause.requestFocus()
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, CONTROLS_TIMEOUT_MS)
    }

    private fun updateProgress() {
        val e = engine ?: return
        val duration = e.durationMs()
        val position = e.positionMs()

        timeCurrent.text = formatTime(position)
        if (duration > 0 && !userIsSeeking) {
            seek.progress = (position * 1000 / duration).toInt()
        }

        // Son bir dakikaya girildiğinde izlendi işaretle. Çıkışı beklemiyoruz:
        // kullanıcı jenerikte uygulamayı kapatırsa da işaret düşmüş olmalı.
        if (item.kind != MediaKind.LIVE &&
            duration > 0 &&
            position > duration - Repository.WATCHED_THRESHOLD_MS &&
            !Repository.isWatched(item.id)
        ) {
            Repository.setWatched(item.id, true)
            showStatus("İzlendi olarak işaretlendi")
        }
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
        handler.removeCallbacks(hideStatus)
        handler.postDelayed(hideStatus, 2500)
    }

    private val hideStatus = Runnable { status.visibility = View.GONE }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    // --- Kumanda tuşları ---

    /**
     * Sony Bravia kumandasının tuş eşlemeleri.
     *
     * Kumanda paneli görünürken yön tuşları düğmeler arası gezinmeye ayrılır;
     * panel kapalıyken sol/sağ sarma yapar. Medya, kanal, AUDIO, SUBTITLE ve
     * renkli tuşlar panelden bağımsız her zaman çalışır.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val controlsVisible = controls.visibility == View.VISIBLE

        when (keyCode) {
            // --- Medya tuşları: her zaman ---
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayPause(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { engine?.play(); showControls(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { engine?.pause(); showControls(); return true }

            KeyEvent.KEYCODE_MEDIA_STOP -> { finish(); return true }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(SEEK_STEP_LONG_MS); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-SEEK_STEP_LONG_MS); return true }

            KeyEvent.KEYCODE_MEDIA_NEXT -> { skip(1); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { skip(-1); return true }

            // --- Kanal tuşları: sırada gezinme ---
            KeyEvent.KEYCODE_CHANNEL_UP -> { skip(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { skip(-1); return true }

            // --- Ses / altyazı tuşları ---
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> { showAudioMenu(); return true }
            KeyEvent.KEYCODE_CAPTIONS -> { showSubtitleMenu(); return true }

            // --- Bilgi ve menü ---
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE -> { showControls(); return true }
            KeyEvent.KEYCODE_MENU -> { switchEngine(); return true }

            // --- Renkli tuşlar ---
            KeyEvent.KEYCODE_PROG_RED -> { showSubtitleMenu(); return true }
            KeyEvent.KEYCODE_PROG_GREEN -> { showAudioMenu(); return true }
            KeyEvent.KEYCODE_PROG_YELLOW -> { switchEngine(); return true }
            KeyEvent.KEYCODE_PROG_BLUE -> { showControls(); return true }

            // --- Yön tuşları ---
            // Panel kapalıyken herhangi bir yön tuşu yalnızca paneli açar.
            // Kör sarma/kanal değiştirme kullanıcıyı şaşırtıyordu: ekranda
            // hiçbir gösterge yokken basılan tuş içeriği kaydırıyordu.
            // Sarma ve gezinme artık yalnız paneldeki düğmelerle ve
            // kumandanın kendi medya tuşlarıyla yapılıyor.
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER ->
                if (!controlsVisible) { showControls(); return true }

            KeyEvent.KEYCODE_BACK ->
                if (controlsVisible) { controls.visibility = View.GONE; return true }
        }

        // Rakam tuşları: filmin yüzdesine atla (0 = başı, 9 = %90).
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            val e = engine
            val duration = e?.durationMs() ?: -1L
            if (e != null && duration > 0) {
                val percent = keyCode - KeyEvent.KEYCODE_0
                e.seekTo(duration * percent / 10)
                showStatus("%${percent * 10}")
                showControls()
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    // --- Yaşam döngüsü ---

    override fun onPause() {
        super.onPause()
        savePosition()
        engine?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        savePosition()
        engine?.listener = null
        engine?.release()
        engine = null
    }

    private fun savePosition() {
        val e = engine ?: return
        if (item.kind == MediaKind.LIVE) return      // canlıda kaldığı yer anlamsız
        Repository.savePosition(item.id, e.positionMs(), e.durationMs())
    }

    companion object {
        const val EXTRA_ITEM = "item"
        private const val EXTRA_INDEX = "index"
        private const val CONTROLS_TIMEOUT_MS = 5_000L
        private const val SEEK_STEP_MS = 10_000L
        private const val SEEK_STEP_LONG_MS = 60_000L

        /** Tek bir öğeyi oynatır; önceki/sonraki devre dışı kalır. */
        fun start(context: Context, item: MediaItem) {
            Repository.queue = emptyList()
            context.startActivity(
                Intent(context, PlaybackActivity::class.java).putExtra(EXTRA_ITEM, item)
            )
        }

        /**
         * [items] sırasını oynatır ve [index]'ten başlar; önceki/sonraki ile
         * liste içinde gezinilebilir.
         */
        fun start(context: Context, items: List<MediaItem>, index: Int) {
            Repository.queue = items
            context.startActivity(
                Intent(context, PlaybackActivity::class.java).putExtra(EXTRA_INDEX, index)
            )
        }
    }
}
