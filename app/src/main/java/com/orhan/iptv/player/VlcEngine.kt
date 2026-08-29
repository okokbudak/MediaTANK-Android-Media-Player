package com.orhan.iptv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.TrackOption
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * libVLC tabanlı motor.
 *
 * Neden var: Bravia'nın donanım kod çözücüsünün reddettiği her şeyi
 * (VC-1, eski DivX/Xvid, DTS/DTS-HD, TrueHD, PGS/VobSub altyazılar, ISO/VOB,
 * SMB üzerinden ham MKV) yazılımsal olarak çözebiliyor. Ayrıca smb:// ve
 * nfs:// şemalarını doğrudan destekliyor.
 */
class VlcEngine(private val context: Context) : PlayerEngine {

    override var listener: PlayerEngine.Listener? = null

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var reportedReady = false

    private fun options(item: MediaItem): ArrayList<String> = arrayListOf(
        "--no-drop-late-frames",
        "--no-skip-frames",
        "--rtsp-tcp",                       // RTSP'yi TCP üzerinden; UDP paket kaybına karşı
        "--http-reconnect",
        "--network-caching=2000",           // canlı yayında takılmayı azaltır
        "--file-caching=1500",
        "--live-caching=2000",
        "--clock-jitter=0",
        "--clock-synchro=0",
        "--audio-time-stretch",             // hız değişiminde sesi bozmadan
        "--sub-autodetect-file",            // yanındaki .srt dosyasını kendiliğinden bul
        "--freetype-rel-fontsize=16",
        "--avcodec-skiploopfilter=0",
        "--avcodec-fast",
        // Playlist bir UA dayatıyorsa ona uy: sağlayıcıların çoğu başka bir
        // UA gördüğünde 403 dönüyor.
        "--http-user-agent=${item.userAgent ?: com.orhan.iptv.net.Http.USER_AGENT}"
    ).apply {
        item.referer?.let { add("--http-referrer=$it") }
    }

    override fun prepare(container: ViewGroup, item: MediaItem, startPositionMs: Long) {
        release()

        val vlc = LibVLC(context, options(item))
        libVlc = vlc

        val layout = VLCVideoLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        videoLayout = layout
        container.removeAllViews()
        container.addView(layout)

        val mp = MediaPlayer(vlc)
        player = mp
        // useTextureView=false -> SurfaceView, TV'de daha az GPU yükü ve daha az tearing
        mp.attachViews(layout, null, /* enableSubtitles = */ true, /* useTextureView = */ false)

        mp.setEventListener { event -> handleEvent(event) }

        val media = openMedia(vlc, item).apply {
            // Donanım hızlandırmayı dene, çözemezse VLC yazılıma düşer.
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=2000")
            if (startPositionMs > 0) {
                addOption(":start-time=${startPositionMs / 1000}")
            }
        }
        mp.media = media
        media.release()

        mp.play()
    }

    /**
     * Medyayı kaynağına göre açar.
     *
     * `content://` adresleri libVLC'nin doğrudan çözemediği Android'e özgü
     * belge URI'leri; Storage Access Framework ile seçilen dosyalar böyle
     * geliyor. Bunlar için dosya tanımlayıcı açılıp VLC'ye o veriliyor.
     */
    private fun openMedia(vlc: LibVLC, item: MediaItem): Media {
        if (!item.playUrl.startsWith("content://")) {
            return Media(vlc, Uri.parse(item.playUrl))
        }

        val descriptor = context.contentResolver
            .openFileDescriptor(Uri.parse(item.playUrl), "r")
            ?: throw java.io.IOException("Dosya açılamadı: ${item.title}")

        // VLC tanımlayıcıyı kendisi kapatmıyor; medya bırakılırken kapatılmalı.
        openDescriptor = descriptor
        return Media(vlc, descriptor.fileDescriptor)
    }

    private var openDescriptor: android.os.ParcelFileDescriptor? = null

    private fun handleEvent(event: MediaPlayer.Event) {
        val mp = player ?: return
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                if (!reportedReady) {
                    reportedReady = true
                    listener?.onReady(mp.length)
                    // Kanal listesi ancak oynatma başladıktan sonra dolar.
                    listener?.onTracksChanged()
                }
                listener?.onPlayingChanged(true)
            }

            MediaPlayer.Event.Paused,
            MediaPlayer.Event.Stopped -> listener?.onPlayingChanged(false)

            MediaPlayer.Event.Buffering ->
                listener?.onBuffering(event.buffering.toInt())

            MediaPlayer.Event.EndReached -> listener?.onEnded()

            MediaPlayer.Event.EncounteredError ->
                listener?.onError("VLC içeriği açamadı", fatal = true)

            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted -> listener?.onTracksChanged()
        }
    }

    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun isPlaying(): Boolean = player?.isPlaying == true

    override fun durationMs(): Long = player?.length ?: -1L
    override fun positionMs(): Long = player?.time ?: 0L

    override fun seekTo(positionMs: Long) {
        val mp = player ?: return
        if (mp.isSeekable) mp.time = positionMs
    }

    override fun audioTracks(): List<TrackOption> {
        val mp = player ?: return emptyList()
        val current = mp.audioTrack
        return mp.audioTracks.orEmpty()
            .filter { it.id >= 0 }          // -1 = "Devre dışı" girdisi
            .map { d ->
                TrackOption(
                    id = "vlc_a_${d.id}",
                    label = d.name ?: "Ses ${d.id}",
                    language = null,
                    selected = d.id == current,
                    engineIndex = d.id
                )
            }
    }

    override fun subtitleTracks(): List<TrackOption> {
        val mp = player ?: return emptyList()
        val current = mp.spuTrack
        return mp.spuTracks.orEmpty()
            .filter { it.id >= 0 }
            .map { d ->
                TrackOption(
                    id = "vlc_s_${d.id}",
                    label = d.name ?: "Altyazı ${d.id}",
                    language = null,
                    selected = d.id == current,
                    engineIndex = d.id
                )
            }
    }

    override fun selectAudio(track: TrackOption) {
        player?.audioTrack = track.engineIndex
    }

    override fun selectSubtitle(track: TrackOption?) {
        // VLC'de -1 altyazıyı kapatır.
        player?.spuTrack = track?.engineIndex ?: -1
    }

    override fun addExternalSubtitle(uri: String): Boolean =
        player?.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle, Uri.parse(uri), true)
            ?: false

    override fun setAudioDelay(delayUs: Long) {
        player?.audioDelay = delayUs
    }

    override fun release() {
        reportedReady = false
        runCatching {
            player?.let {
                it.setEventListener(null)
                if (it.isPlaying) it.stop()
                it.detachViews()
                it.release()
            }
        }.onFailure { Log.w(TAG, "VLC player release", it) }
        player = null

        runCatching { libVlc?.release() }
            .onFailure { Log.w(TAG, "LibVLC release", it) }
        libVlc = null
        videoLayout = null

        // content:// için açılan tanımlayıcıyı biz kapatıyoruz; VLC kapatmıyor.
        runCatching { openDescriptor?.close() }
            .onFailure { Log.w(TAG, "Dosya tanımlayıcı kapatılamadı", it) }
        openDescriptor = null
    }

    private companion object {
        const val TAG = "VlcEngine"
    }
}
