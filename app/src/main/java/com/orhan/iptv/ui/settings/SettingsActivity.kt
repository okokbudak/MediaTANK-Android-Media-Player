package com.orhan.iptv.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.orhan.iptv.R
import com.orhan.iptv.data.Engine
import com.orhan.iptv.data.Repository
import com.orhan.iptv.data.meta.MetadataCache
import com.orhan.iptv.ui.profile.ProfilesActivity
import com.orhan.iptv.ui.setup.AddSourceActivity

/**
 * Ayarlar: dil tercihleri, varsayılan oynatıcı motoru, kaynak ve profil yönetimi.
 *
 * Dil tercihleri profil başına saklanıyor; aynı televizyonda İngilizce izleyen
 * biriyle Türkçe izleyen biri çakışmasın.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var audioButton: TextView
    private lateinit var subtitleButton: TextView
    private lateinit var engineButton: TextView
    private lateinit var tmdbKeyButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        com.orhan.iptv.ui.TouchNav.install(this)

        audioButton = findViewById(R.id.btn_audio_language)
        subtitleButton = findViewById(R.id.btn_subtitle_language)
        engineButton = findViewById(R.id.btn_engine)

        findViewById<TextView>(R.id.profile_line).text =
            Repository.activeProfile()?.let { "Profil: ${it.name}" }.orEmpty()

        audioButton.setOnClickListener {
            pickLanguage(
                title = "Ses dili",
                entries = Languages.AUDIO,
                current = Repository.preferredAudioLanguage()
            ) { code ->
                Repository.setPreferredAudioLanguage(code)
                renderLanguages()
            }
        }

        subtitleButton.setOnClickListener {
            pickLanguage(
                title = "Altyazı dili",
                entries = Languages.SUBTITLE,
                current = Repository.preferredSubtitleLanguage()
            ) { code ->
                Repository.setPreferredSubtitleLanguage(code)
                renderLanguages()
            }
        }

        engineButton.setOnClickListener {
            val options = arrayOf("ExoPlayer (hızlı, donanım)", "VLC (geniş codec desteği)")
            val checked = if (Repository.defaultEngine() == Engine.VLC) 1 else 0
            AlertDialog.Builder(this)
                .setTitle("Varsayılan oynatıcı motoru")
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    Repository.setDefaultEngine(if (which == 1) Engine.VLC else Engine.EXOPLAYER)
                    renderLanguages()
                    dialog.dismiss()
                }
                .show()
        }

        tmdbKeyButton = findViewById(R.id.btn_tmdb_key)
        tmdbKeyButton.setOnClickListener { promptTmdbKey() }

        findViewById<TextView>(R.id.btn_clear_meta).setOnClickListener {
            MetadataCache.clear()
            Toast.makeText(this, "Önbellek temizlendi", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btn_sources).setOnClickListener {
            startActivity(Intent(this, AddSourceActivity::class.java))
        }
        findViewById<TextView>(R.id.btn_profiles).setOnClickListener {
            startActivity(Intent(this, ProfilesActivity::class.java))
            finish()
        }

        renderLanguages()
    }

    /**
     * TMDB anahtarını sorar.
     *
     * Anahtar bir parola değil, kullanıcının kendi hesabına ait genel bir
     * erişim anahtarı; ekranda kısaltılmış olarak gösteriliyor ki doğru
     * girilip girilmediği anlaşılsın.
     */
    private fun promptTmdbKey() {
        val input = EditText(this).apply {
            setText(Repository.tmdbApiKey())
            hint = "TMDB API Key (v3 auth)"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("TMDB anahtarı")
            .setMessage("themoviedb.org hesabınızda Ayarlar → API bölümünden ücretsiz alınır.")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                val key = input.text.toString().trim()
                Repository.setTmdbApiKey(key)
                // Anahtar değişince eski "bulunamadı" kayıtları anlamsız.
                MetadataCache.clear()
                renderTmdbKey()
                Toast.makeText(
                    this,
                    if (key.isEmpty()) "Anahtar silindi" else "Anahtar kaydedildi",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun renderTmdbKey() {
        val key = Repository.tmdbApiKey()
        tmdbKeyButton.text = if (key.isBlank()) {
            getString(R.string.settings_tmdb_key_none)
        } else {
            getString(R.string.settings_tmdb_key, key.take(6) + "…" + key.takeLast(4))
        }
    }

    private fun renderLanguages() {
        renderTmdbKey()
        audioButton.text = getString(
            R.string.settings_audio_language,
            Languages.labelFor(Languages.AUDIO, Repository.preferredAudioLanguage())
        )
        subtitleButton.text = getString(
            R.string.settings_subtitle_language,
            Languages.labelFor(Languages.SUBTITLE, Repository.preferredSubtitleLanguage())
        )
        engineButton.text = getString(
            R.string.settings_engine,
            if (Repository.defaultEngine() == Engine.VLC) "VLC" else "ExoPlayer"
        )
    }

    private fun pickLanguage(
        title: String,
        entries: List<Languages.Entry>,
        current: String,
        onPicked: (String) -> Unit
    ) {
        val labels = entries.map { it.label }.toTypedArray()
        val checked = entries.indexOfFirst { it.code == current }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                onPicked(entries[which].code)
                dialog.dismiss()
            }
            .show()
    }
}
