package com.orhan.iptv.player

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.TrackOption
import com.orhan.iptv.net.Http

/**
 * ExoPlayer (Media3) tabanlı motor.
 *
 * Neden var: HLS/DASH canlı yayınlarda VLC'den belirgin biçimde daha iyi.
 * Uyarlanabilir bit hızı, hızlı kanal geçişi ve düşük gecikme burada.
 * Bravia'nın donanım kod çözücülerini doğrudan kullanır.
 *
 * Sınırı: Bravia'nın çözücüsünün tanımadığı bir codec'te (VC-1, DTS-HD,
 * eski DivX) hata verir. O durumda PlaybackActivity VLC'ye düşer.
 */
@UnstableApi
class ExoEngine(private val context: Context) : PlayerEngine {

    override var listener: PlayerEngine.Listener? = null

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var reportedReady = false

    override fun prepare(container: ViewGroup, item: MediaItem, startPositionMs: Long) {
        release()

        val selector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                // Cihazın çözemediği formatlar için yazılımsal çözücüye izin ver.
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setPreferredTextLanguage("tr")
                .setPreferredAudioLanguage("tr")
                .build()
        }
        trackSelector = selector

        val httpFactory = DefaultHttpDataSource.Factory()
            // Playlist bir UA dayatıyorsa ona uy; aksi halde sağlayıcı 403 döner.
            .setUserAgent(item.userAgent ?: Http.USER_AGENT)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                item.referer?.let { setDefaultRequestProperties(mapOf("Referer" to it)) }
            }

        val loadControl = DefaultLoadControl.Builder()
            // Canlı IPTV'de ağ dalgalanmasını yutmak için geniş tampon.
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .build()

        val renderers = DefaultRenderersFactory(context)
            // Donanım çözücü başarısız olursa yazılımsal olana devret.
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val exo = ExoPlayer.Builder(context, renderers)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
            )
            .build()
        player = exo

        val view = PlayerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = false          // kendi kumandamızı çiziyoruz
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            player = exo
        }
        playerView = view
        container.removeAllViews()
        container.addView(view)

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        if (!reportedReady) {
                            reportedReady = true
                            listener?.onReady(exo.duration)
                        }
                        listener?.onBuffering(100)
                    }
                    Player.STATE_BUFFERING -> listener?.onBuffering(exo.bufferedPercentage)
                    Player.STATE_ENDED -> listener?.onEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onPlayingChanged(isPlaying)
            }

            override fun onTracksChanged(tracks: Tracks) {
                listener?.onTracksChanged()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Kod çözme/kaynak hataları ölümcül: bu içerik ExoPlayer'da açılmıyor.
                listener?.onError(error.errorCodeName, fatal = true)
            }
        })

        exo.setMediaItem(
            androidx.media3.common.MediaItem.Builder()
                .setUri(Uri.parse(item.playUrl))
                .build()
        )
        if (startPositionMs > 0) exo.seekTo(startPositionMs)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun play() { player?.play() }
    override fun pause() { player?.pause() }
    override fun isPlaying(): Boolean = player?.isPlaying == true

    override fun durationMs(): Long {
        val d = player?.duration ?: C.TIME_UNSET
        return if (d == C.TIME_UNSET) -1L else d
    }

    override fun positionMs(): Long = player?.currentPosition ?: 0L

    override fun seekTo(positionMs: Long) { player?.seekTo(positionMs) }

    override fun audioTracks(): List<TrackOption> = tracksOfType(C.TRACK_TYPE_AUDIO)

    override fun subtitleTracks(): List<TrackOption> = tracksOfType(C.TRACK_TYPE_TEXT)

    private fun tracksOfType(type: Int): List<TrackOption> {
        val exo = player ?: return emptyList()
        val result = ArrayList<TrackOption>()
        exo.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != type) return@forEachIndexed
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                result += TrackOption(
                    id = "exo_${type}_${groupIndex}_$i",
                    label = format.label
                        ?: format.language?.let { languageName(it) }
                        ?: "${if (type == C.TRACK_TYPE_AUDIO) "Ses" else "Altyazı"} ${result.size + 1}",
                    language = format.language,
                    selected = group.isTrackSelected(i),
                    groupIndex = groupIndex,
                    trackIndexInGroup = i
                )
            }
        }
        return result
    }

    private fun languageName(code: String): String =
        runCatching { java.util.Locale(code).getDisplayLanguage(java.util.Locale("tr")) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: code

    override fun selectAudio(track: TrackOption) = select(C.TRACK_TYPE_AUDIO, track)

    override fun selectSubtitle(track: TrackOption?) {
        val exo = player ?: return
        if (track == null) {
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            select(C.TRACK_TYPE_TEXT, track)
        }
    }

    private fun select(type: Int, track: TrackOption) {
        val exo = player ?: return
        val group = exo.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(track.trackIndexInGroup))
            )
            .build()
    }

    /**
     * ExoPlayer harici altyazıyı ancak medya hazırlanırken kabul eder; akış
     * ortasında ekleme desteklenmiyor. Bu durumda çağıran VLC'ye geçmeli.
     */
    override fun addExternalSubtitle(uri: String): Boolean = false

    /** ExoPlayer'da ses gecikmesi ayarı yok; bu özellik VLC motoruna özgü. */
    override fun setAudioDelay(delayUs: Long) = Unit

    override fun release() {
        reportedReady = false
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
        trackSelector = null
    }
}
