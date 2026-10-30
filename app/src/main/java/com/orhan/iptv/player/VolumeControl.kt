package com.orhan.iptv.player

import android.content.Context
import android.media.AudioManager
import android.widget.ImageView
import android.widget.SeekBar
import com.orhan.iptv.R

/**
 * Ekrandaki ses çubuğu ve sessiz düğmesi.
 *
 * Tabletlerde donanım ses tuşu her zaman elin altında olmuyor ve oynatıcıda
 * ses ayarı için ekranda hiçbir şey yoktu. Bu sınıf medya sesini
 * ([AudioManager.STREAM_MUSIC]) doğrudan ayarlar; ExoPlayer ve libVLC ikisi de
 * bu akıştan çaldığı için motora dokunmaya gerek kalmıyor.
 */
class VolumeControl(
    context: Context,
    private val bar: SeekBar,
    private val muteButton: ImageView,
    /** Kullanıcı çubuğa ya da düğmeye dokununca çağrılır (örn. paneli açık tut). */
    private val onInteraction: () -> Unit = {}
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxLevel = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    /** Sessize alınmadan önceki seviye; 0 ise sessizde değil. */
    private var levelBeforeMute = 0

    /** Çubuğun yeniden çizilmesi dinleyiciyi tetiklemesin. */
    private var updating = false

    init {
        bar.max = maxLevel
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(view: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && !updating) {
                    set(progress)
                    onInteraction()
                }
            }
            override fun onStartTrackingTouch(view: SeekBar) = Unit
            override fun onStopTrackingTouch(view: SeekBar) = Unit
        })
        muteButton.setOnClickListener { toggleMute() }
        refresh()
    }

    /** Geçerli seviyeyi okuyup çubuğu ve simgeyi günceller (donanım tuşları da değiştirebilir). */
    fun refresh() {
        val level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        updating = true
        bar.progress = level
        updating = false
        muteButton.setImageResource(
            if (level == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        )
    }

    /** Geçerli seviye yüzde olarak (0-100). */
    fun percent(): Int =
        audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxLevel

    /** 0..maxLevel arasında bir seviye ayarlar. */
    fun set(level: Int) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, maxLevel), 0)
        refresh()
    }

    fun level(): Int = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun max(): Int = maxLevel

    private fun toggleMute() {
        onInteraction()
        val current = level()
        if (current > 0) {
            levelBeforeMute = current
            set(0)
        } else {
            set(if (levelBeforeMute > 0) levelBeforeMute else maxLevel / 2)
        }
    }
}
