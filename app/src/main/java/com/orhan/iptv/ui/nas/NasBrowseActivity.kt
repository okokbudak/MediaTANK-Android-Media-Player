package com.orhan.iptv.ui.nas

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Source
import com.orhan.iptv.data.smb.SmbBrowser
import com.orhan.iptv.player.PlaybackActivity
import com.orhan.iptv.ui.photo.PhotoViewerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NAS gezgini.
 *
 * İki seviyeli: en üstte sunucunun paylaşılan klasörleri, içine girilince o
 * paylaşımın klasör ağacı. Kaynağa sabit bir paylaşım adı verilmişse doğrudan
 * onun içinde başlar ve paylaşım listesi atlanır.
 *
 * Tek Activity kendi yol yığınıyla çalışıyor: her klasör için yeni Activity
 * açmak TV'de geri tuşunu yavaşlatır ve bellek tüketir.
 */
class NasBrowseActivity : AppCompatActivity() {

    private lateinit var source: Source
    private lateinit var list: RecyclerView
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var progress: View
    private lateinit var empty: TextView

    private lateinit var adapter: NasAdapter
    private var viewMode = NasViewMode.LIST

    /** İçinde bulunulan paylaşım; null ise paylaşım listesi gösteriliyor. */
    private var share: String? = null

    /** Kaynak sabit bir paylaşıma bağlıysa geri tuşu paylaşım listesine dönmemeli. */
    private var shareIsFixed = false

    /** Paylaşım kökünden itibaren gezinilen klasörler. */
    private val pathStack = ArrayList<String>()

    /** Geçerli klasördeki fotoğraflar; görüntüleyiciye topluca verilir. */
    private var currentPhotos: List<MediaItem> = emptyList()

    /** Geçerli klasördeki videolar; oynatıcıya sıra olarak verilir. */
    private var currentVideos: List<MediaItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        com.orhan.iptv.ui.TouchNav.install(this)

        @Suppress("DEPRECATION")
        source = (intent.getSerializableExtra(EXTRA_SOURCE) as? Source) ?: run {
            Toast.makeText(this, "NAS kaynağı bulunamadı", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        share = source.share?.takeIf { it.isNotBlank() }
        shareIsFixed = share != null

        titleView = findViewById(R.id.list_title)
        subtitleView = findViewById(R.id.list_subtitle)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)

        list = findViewById(R.id.list)
        adapter = NasAdapter(lifecycleScope, viewMode) { item -> onItemClick(item) }
        applyViewMode()

        val modeButton = findViewById<TextView>(R.id.btn_list_view_mode)
        modeButton.visibility = View.VISIBLE
        modeButton.text = viewMode.label
        modeButton.setOnClickListener {
            viewMode = NasViewMode.values()[(viewMode.ordinal + 1) % NasViewMode.values().size]
            modeButton.text = viewMode.label
            adapter.setMode(viewMode)
            applyViewMode()
        }

        // Tanılama yalnız paylaşım listesindeyken anlamlı.
        val diagnose = findViewById<TextView>(R.id.btn_diagnose)
        diagnose.visibility = if (shareIsFixed) View.GONE else View.VISIBLE
        diagnose.setOnClickListener { runDiagnostics() }

        load()
    }

    /** Seçili görünüme göre yerleşimi kurar ve adaptörü yeniden bağlar. */
    private fun applyViewMode() {
        list.layoutManager = when (viewMode) {
            NasViewMode.LIST -> LinearLayoutManager(this)
            NasViewMode.GRID -> GridLayoutManager(this, spanCount(cardWidthDp = 200))
            NasViewMode.TILE -> GridLayoutManager(this, spanCount(cardWidthDp = 150))
        }
        // Odaklanan kart büyürken komşularına taşabilsin.
        list.clipChildren = false
        list.adapter = adapter
    }

    private fun spanCount(cardWidthDp: Int): Int {
        val density = resources.displayMetrics.density
        val available = list.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return (available / ((cardWidthDp + 16) * density)).toInt().coerceAtLeast(1)
    }

    private fun currentPath(): String = pathStack.joinToString("/")

    private fun load() {
        val activeShare = share
        val path = currentPath()

        titleView.text = when {
            activeShare == null -> source.name
            pathStack.isEmpty() -> activeShare
            else -> pathStack.last()
        }
        subtitleView.text = when {
            activeShare == null -> "${source.url} · tüm paylaşımlar"
            else -> "${source.url} / $activeShare/$path"
        }

        progress.visibility = View.VISIBLE
        empty.visibility = View.GONE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (activeShare == null) SmbBrowser.listShares(source)
                    else SmbBrowser.list(source, activeShare, path)
                }
            }
            progress.visibility = View.GONE

            result.fold(
                onSuccess = { items ->
                    currentPhotos = items.filter { it.kind == MediaKind.PHOTO }
                    currentVideos = items.filter { it.kind == MediaKind.FILE }
                    adapter.submit(items)
                    if (items.isEmpty()) {
                        empty.visibility = View.VISIBLE
                        empty.text = if (activeShare == null) {
                            "Bu sunucuda paylaşılan klasör görünmüyor"
                        } else {
                            "Bu klasörde video veya fotoğraf yok"
                        }
                    } else {
                        list.requestFocus()
                    }
                },
                onFailure = { error ->
                    empty.visibility = View.VISIBLE
                    empty.text =
                        "Bağlanılamadı: ${error.message ?: error.javaClass.simpleName}"
                }
            )
        }
    }

    /**
     * Her paylaşımı tek tek açmayı deneyip sonucu ekrana döker.
     *
     * Hangi paylaşımın hangi NT durum koduyla reddedildiğini görmek, "izinler
     * açık ama açılmıyor" gibi durumlarda tahmin yürütmeyi bitiriyor.
     */
    private fun runDiagnostics() {
        progress.visibility = View.VISIBLE
        empty.visibility = View.GONE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { SmbBrowser.probeShares(source) }
            }
            progress.visibility = View.GONE

            result.fold(
                onSuccess = { probes ->
                    val report = buildString {
                        val ok = probes.count { it.ok }
                        appendLine("${probes.size} paylaşım · $ok açıldı · ${probes.size - ok} reddedildi")
                        appendLine()
                        for (p in probes) {
                            if (p.ok) {
                                appendLine("✓ ${p.share} — ${p.entries} öğe")
                            } else {
                                appendLine("✗ ${p.share} — ${p.ntStatus ?: ""} ${p.error}")
                            }
                        }
                        probes.firstOrNull { it.ok }?.attempt?.let {
                            appendLine()
                            appendLine("Çalışan kip: $it")
                        }
                    }
                    android.util.Log.i("SmbProbe", report)
                    AlertDialog.Builder(this@NasBrowseActivity)
                        .setTitle("Paylaşım sınaması")
                        .setMessage(report)
                        .setPositiveButton("Tamam", null)
                        .show()
                },
                onFailure = { error ->
                    AlertDialog.Builder(this@NasBrowseActivity)
                        .setTitle("Sınama yapılamadı")
                        .setMessage(error.message ?: error.javaClass.simpleName)
                        .setPositiveButton("Tamam", null)
                        .show()
                }
            )
        }
    }

    private fun onItemClick(item: MediaItem) {
        when (item.kind) {
            MediaKind.FOLDER -> {
                if (share == null) {
                    // Paylaşım listesindeyiz: seçilen paylaşımın içine gir.
                    share = item.title
                    pathStack.clear()
                } else {
                    // playUrl klasörlerde göreli yolu taşıyor.
                    pathStack += item.playUrl.substringAfterLast('/')
                }
                load()
            }

            MediaKind.PHOTO -> PhotoViewerActivity.start(
                this,
                currentPhotos,
                currentPhotos.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            )

            // Klasördeki videolar sıra olsun: oynatıcıda önceki/sonraki ile
            // dosyalar arasında gezinilebilsin.
            else -> PlaybackActivity.start(
                this,
                currentVideos.ifEmpty { listOf(item) },
                currentVideos.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            // Klasör ağacında yukarı çık.
            pathStack.isNotEmpty() -> {
                pathStack.removeAt(pathStack.lastIndex)
                load()
            }
            // Paylaşım kökündeyiz: paylaşım listesine dön.
            share != null && !shareIsFixed -> {
                share = null
                load()
            }
            else -> super.onBackPressed()
        }
    }

    companion object {
        const val EXTRA_SOURCE = "source"
    }
}
