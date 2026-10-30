package com.orhan.iptv.ui.detail

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.orhan.iptv.R
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Repository
import com.orhan.iptv.data.SourceType
import com.orhan.iptv.data.meta.MetadataCache
import com.orhan.iptv.data.meta.TmdbClient
import com.orhan.iptv.data.xtream.ContentDetail
import com.orhan.iptv.data.xtream.XtreamClient
import com.orhan.iptv.player.PlaybackActivity
import com.orhan.iptv.ui.RowAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Film ve dizi detay ekranı.
 *
 * Özet, puan, tür ve oyuncular sağlayıcının kataloğundan (Xtream
 * get_vod_info / get_series_info) geliyor. IMDb'nin anahtarsız herkese açık
 * bir arayüzü olmadığı için doğrudan IMDb'ye bağlanılmıyor; sağlayıcılar bu
 * alanları zaten IMDb/TMDB'den doldurduğu için gösterilen bilgi pratikte
 * aynı. M3U kaynaklarında bu uç noktalar olmadığından ekran playlist'teki
 * bilgiyle yetiniyor.
 */
class DetailsActivity : AppCompatActivity() {

    private lateinit var item: MediaItem

    private lateinit var poster: ImageView
    private lateinit var titleView: TextView
    private lateinit var starsView: TextView
    private lateinit var ratingValue: TextView
    private lateinit var metaView: TextView
    private lateinit var plotView: TextView
    private lateinit var creditsView: TextView
    private lateinit var favoriteButton: TextView
    private lateinit var playButton: TextView
    private lateinit var resumeButton: TextView
    private lateinit var watchedButton: TextView
    private lateinit var seasonBar: View
    private lateinit var seasonList: RecyclerView
    private lateinit var episodes: RecyclerView
    private lateinit var progress: View
    private lateinit var empty: TextView

    /** Sezon numarası → bölümler. Dizilerde dolu, filmlerde boş. */
    private var seasons: Map<Int, List<MediaItem>> = emptyMap()
    private var currentSeason: Int? = null

    private val episodeAdapter = RowAdapter { episode -> playEpisode(episode) }
    private lateinit var seasonAdapter: SeasonAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        com.orhan.iptv.ui.TouchNav.install(this)

        @Suppress("DEPRECATION")
        item = (intent.getSerializableExtra(EXTRA_ITEM) as? MediaItem) ?: run {
            Toast.makeText(this, "İçerik bulunamadı", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bindViews()
        renderFromItem()
        loadDetail()
    }

    private fun bindViews() {
        poster = findViewById(R.id.poster)
        titleView = findViewById(R.id.title)
        starsView = findViewById(R.id.stars)
        ratingValue = findViewById(R.id.rating_value)
        metaView = findViewById(R.id.meta)
        plotView = findViewById(R.id.plot)
        creditsView = findViewById(R.id.credits)
        favoriteButton = findViewById(R.id.btn_favorite)
        seasonBar = findViewById(R.id.season_bar)
        seasonList = findViewById(R.id.season_list)
        episodes = findViewById(R.id.episodes)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)

        episodes.layoutManager = LinearLayoutManager(this)
        episodes.adapter = episodeAdapter

        seasonAdapter = SeasonAdapter { number -> showSeason(number) }
        seasonList.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        seasonList.adapter = seasonAdapter

        playButton = findViewById(R.id.btn_play)
        resumeButton = findViewById(R.id.btn_resume)
        watchedButton = findViewById(R.id.btn_watched)

        playButton.setOnClickListener { playPrimary(fromStart = true) }
        resumeButton.setOnClickListener { playPrimary(fromStart = false) }

        favoriteButton.setOnClickListener {
            val added = Repository.toggleFavorite(item)
            renderFavorite()
            Toast.makeText(
                this,
                if (added) "Favorilere eklendi" else "Favorilerden çıkarıldı",
                Toast.LENGTH_SHORT
            ).show()
        }

        watchedButton.setOnClickListener {
            val watched = Repository.toggleWatched(item.id)
            renderWatched()
            renderResume()
            Toast.makeText(
                this,
                if (watched) "İzlendi olarak işaretlendi" else "İşaret kaldırıldı",
                Toast.LENGTH_SHORT
            ).show()
        }

        playButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        // Oynatıcıdan dönüldüğünde kaldığı yer ve izlendi durumu değişmiş olabilir.
        renderResume()
        renderWatched()
    }

    private fun renderWatched() {
        watchedButton.setText(
            if (Repository.isWatched(item.id)) R.string.detail_unmark_watched
            else R.string.detail_mark_watched
        )
    }

    /**
     * "Kaldığı yerden" düğmesi yalnız kayıtlı bir konum varsa görünür.
     * Dizilerde konum bölüm başına tutulduğu için bu düğme sezon seçimiyle
     * değil, doğrudan açılan öğeyle ilgili.
     */
    private fun renderResume() {
        val position = Repository.position(item.id)
        if (position > 0) {
            resumeButton.visibility = View.VISIBLE
            resumeButton.text = getString(R.string.detail_resume, formatTime(position))
            playButton.setText(R.string.detail_play_from_start)
        } else {
            resumeButton.visibility = View.GONE
            playButton.setText(R.string.detail_play)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    /** Ağ beklemeden, elimizdeki bilgiyle ekranı doldur. */
    private fun renderFromItem() {
        titleView.text = item.title
        metaView.text = item.group.orEmpty()
        plotView.text = item.description.orEmpty()
        plotView.visibility = if (item.description.isNullOrBlank()) View.GONE else View.VISIBLE
        creditsView.visibility = View.GONE
        starsView.visibility = View.GONE
        ratingValue.visibility = View.GONE

        if (!item.logoUrl.isNullOrBlank()) {
            Glide.with(this).load(item.logoUrl).into(poster)
        }
        renderFavorite()
    }

    private fun renderFavorite() {
        favoriteButton.setText(
            if (Repository.isFavorite(item.id)) R.string.detail_remove_favorite
            else R.string.detail_add_favorite
        )
    }

    // --- Ayrıntı yükleme ---

    private fun loadDetail() {
        val source = Repository.sources().firstOrNull { it.type == SourceType.XTREAM }
        val seriesId = item.seriesId
        val streamId = item.id.substringAfterLast('_').takeIf { it.isNotBlank() }

        // M3U kaynağında sağlayıcı uç noktası yok; bilgi doğrudan TMDB'den.
        if (source == null) {
            enrichFromTmdb(null)
            if (seriesId == null) showNoEpisodes(getString(R.string.detail_no_info))
            return
        }

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val client = XtreamClient(source)
                    if (seriesId != null) {
                        client.seriesDetail(seriesId, item.title)
                    } else {
                        client.vodDetail(streamId.orEmpty(), item.title) to emptyMap()
                    }
                }
            }
            progress.visibility = View.GONE

            result.fold(
                onSuccess = { (detail, bySeason) ->
                    detail?.let { applyDetail(it) }
                    enrichFromTmdb(detail)
                    seasons = bySeason
                    if (bySeason.isNotEmpty()) {
                        seasonBar.visibility = View.VISIBLE
                        seasonAdapter.submit(bySeason.keys.toList())
                        showSeason(bySeason.keys.first())
                    } else if (seriesId != null) {
                        showNoEpisodes("Bölüm bulunamadı")
                    }
                },
                onFailure = { error ->
                    if (seriesId != null) {
                        showNoEpisodes("Bilgi alınamadı: ${error.message ?: "bilinmeyen hata"}")
                    }
                }
            )
        }
    }

    /**
     * Sağlayıcının vermediği alanları TMDB'den tamamlar.
     *
     * Sağlayıcı bilgisi önceliklidir — oynatılan kopyanın gerçek süresi ve
     * adı orada doğru. TMDB yalnız boş kalan alanları dolduruyor: özet, tür,
     * oyuncular, afiş ve puan.
     *
     * Sonuç kalıcı önbelleğe yazılıyor; aynı film ikinci kez açıldığında ağa
     * gidilmiyor ve TMDB kotası korunuyor.
     */
    private fun enrichFromTmdb(providerDetail: ContentDetail?) {
        val apiKey = Repository.tmdbApiKey()
        if (apiKey.isBlank()) return

        MetadataCache.get(item.id)?.let { entry ->
            if (entry is MetadataCache.Entry.Hit) mergeTmdb(entry.detail, providerDetail)
            return
        }

        lifecycleScope.launch {
            val fetched = withContext(Dispatchers.IO) {
                runCatching {
                    TmdbClient(apiKey).lookup(item.title, isSeries = item.seriesId != null)
                }.getOrNull()
            }
            MetadataCache.put(item.id, fetched)
            fetched?.let { mergeTmdb(it, providerDetail) }
        }
    }

    private fun mergeTmdb(tmdb: ContentDetail, providerDetail: ContentDetail?) {
        if (plotView.text.isNullOrBlank() && !tmdb.plot.isNullOrBlank()) {
            plotView.text = tmdb.plot
            plotView.visibility = View.VISIBLE
        }

        if (creditsView.visibility != View.VISIBLE) {
            val credits = listOfNotNull(
                tmdb.director?.let { "Yönetmen: $it" },
                tmdb.cast?.let { "Oyuncular: $it" }
            )
            if (credits.isNotEmpty()) {
                creditsView.text = credits.joinToString("\n")
                creditsView.visibility = View.VISIBLE
            }
        }

        // Meta satırı sağlayıcıda yoksa TMDB'nin yıl/tür/süresiyle kurulur.
        if (providerDetail?.genre.isNullOrBlank()) {
            val parts = listOfNotNull(tmdb.year, tmdb.genre, tmdb.durationText)
            if (parts.isNotEmpty()) metaView.text = parts.joinToString("  ·  ")
        }

        if (starsView.visibility != View.VISIBLE) {
            tmdb.rating?.let { rating ->
                starsView.text = stars(rating)
                starsView.visibility = View.VISIBLE
                // Puanın kaynağı belirtiliyor: bu IMDb değil, TMDB oylaması.
                ratingValue.text = "%.1f / 10  ·  TMDB".format(rating)
                ratingValue.visibility = View.VISIBLE
            }
        }

        if (item.logoUrl.isNullOrBlank() && providerDetail?.posterUrl.isNullOrBlank()) {
            tmdb.posterUrl?.let { Glide.with(this).load(it).into(poster) }
        }
    }

    private fun applyDetail(detail: ContentDetail) {
        detail.plot?.let {
            plotView.text = it
            plotView.visibility = View.VISIBLE
        }

        val metaParts = listOfNotNull(
            detail.year,
            detail.genre,
            detail.durationText?.let { if (it.contains(':')) it else "$it dk" }
        )
        if (metaParts.isNotEmpty()) metaView.text = metaParts.joinToString("  ·  ")

        val credits = listOfNotNull(
            detail.director?.let { "Yönetmen: $it" },
            detail.cast?.let { "Oyuncular: $it" }
        )
        if (credits.isNotEmpty()) {
            creditsView.text = credits.joinToString("\n")
            creditsView.visibility = View.VISIBLE
        }

        detail.rating?.let { rating ->
            starsView.text = stars(rating)
            starsView.visibility = View.VISIBLE
            ratingValue.text = "%.1f / 10".format(rating)
            ratingValue.visibility = View.VISIBLE
        }

        detail.posterUrl?.takeIf { it.isNotBlank() }?.let {
            Glide.with(this).load(it).into(poster)
        }
    }

    /** 10 üzerinden puanı 5 yıldıza çevirir; yarım yıldız gösterilmiyor. */
    private fun stars(rating: Double): String {
        val filled = (rating / 2).roundToInt().coerceIn(0, 5)
        return "★".repeat(filled) + "☆".repeat(5 - filled)
    }

    // --- Sezon / bölüm ---

    private fun showSeason(number: Int) {
        currentSeason = number
        seasonAdapter.setSelected(number)
        val list = seasons[number].orEmpty()
        episodeAdapter.submit(list)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        if (list.isEmpty()) empty.text = "Bu sezonda bölüm yok"
    }

    private fun showNoEpisodes(message: String) {
        empty.visibility = View.VISIBLE
        empty.text = message
    }

    // --- Oynatma ---

    /**
     * Ana oynatma düğmesi.
     *
     * [fromStart] true ise kayıtlı konum yok sayılıp baştan başlar; oynatıcı
     * konumu depodan okuduğu için kaydı geçici olarak siliyoruz.
     */
    private fun playPrimary(fromStart: Boolean) {
        if (fromStart) Repository.clearPosition(item.id)

        val episodesOfSeason = currentSeason?.let { seasons[it] }.orEmpty()
        when {
            episodesOfSeason.isNotEmpty() ->
                PlaybackActivity.start(this, episodesOfSeason, 0)

            item.playUrl.isNotBlank() ->
                PlaybackActivity.start(this, item)

            else ->
                Toast.makeText(this, "Oynatılacak içerik yok", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playEpisode(episode: MediaItem) {
        val list = currentSeason?.let { seasons[it] }.orEmpty().ifEmpty { listOf(episode) }
        PlaybackActivity.start(this, list, list.indexOfFirst { it.id == episode.id }.coerceAtLeast(0))
    }

    companion object {
        const val EXTRA_ITEM = "item"
    }
}
