package com.orhan.iptv.player

import android.view.ViewGroup
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.TrackOption

/**
 * ExoPlayer ve libVLC'yi tek bir arayüz arkasında toplar.
 *
 * PlaybackActivity yalnızca bu arayüzü tanır; motorlar arası geçiş, aynı
 * konumdan yeni bir motor örneği açmak demektir.
 */
interface PlayerEngine {

    /** Motoru [container] içine bağlar ve [item]'ı [startPositionMs]'ten oynatır. */
    fun prepare(container: ViewGroup, item: MediaItem, startPositionMs: Long)

    fun play()
    fun pause()
    fun isPlaying(): Boolean

    /** Canlı yayında -1 dönebilir. */
    fun durationMs(): Long
    fun positionMs(): Long
    fun seekTo(positionMs: Long)

    fun audioTracks(): List<TrackOption>
    fun subtitleTracks(): List<TrackOption>
    fun selectAudio(track: TrackOption)
    /** [track] null ise altyazı kapatılır. */
    fun selectSubtitle(track: TrackOption?)

    /** Harici altyazı dosyası ekler (.srt/.ass/.sub). */
    fun addExternalSubtitle(uri: String): Boolean

    /** Ses/görüntü senkron kaymasını düzeltir (mikrosaniye). */
    fun setAudioDelay(delayUs: Long)

    fun release()

    var listener: Listener?

    interface Listener {
        fun onReady(durationMs: Long)
        fun onPlayingChanged(playing: Boolean)
        fun onBuffering(percent: Int)
        fun onEnded()
        /** [fatal] true ise motor bu içeriği açamadı; çağıran diğer motoru deneyebilir. */
        fun onError(message: String, fatal: Boolean)
        /** Ses/altyazı listesi hazır olduğunda veya değiştiğinde. */
        fun onTracksChanged()
    }
}
