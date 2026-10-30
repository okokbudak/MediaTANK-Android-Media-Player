package com.orhan.iptv.ui.music

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Repository
import com.orhan.iptv.data.Source
import com.orhan.iptv.data.SourceType
import com.orhan.iptv.data.local.LocalBrowser
import com.orhan.iptv.data.local.SafBrowser
import com.orhan.iptv.data.smb.SmbBrowser
import com.orhan.iptv.ui.RowAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Müzik kaynağı gezgini.
 *
 * En üstte kaynaklar (kayıtlı NAS'lar ve USB/dahili depolama), içine girilince
 * klasör ağacı. Bir parça seçilince o klasördeki tüm parçalar çalma sırası
 * oluyor — kullanıcı tek tek eklemek zorunda kalmasın.
 */
class MusicBrowseActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var progress: View
    private lateinit var empty: TextView
    private lateinit var playAll: TextView
    private lateinit var addFolderButton: TextView

    private val adapter = RowAdapter { item -> onItemClick(item) }

    /** Gezinme durumu: hangi kaynakta, hangi paylaşımda, hangi yolda. */
    private var nasSource: Source? = null
    private var share: String? = null
    private val pathStack = ArrayList<String>()
    private var localPath: String? = null

    /** Geçerli klasördeki parçalar. */
    private var currentTracks: List<MediaItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        com.orhan.iptv.ui.TouchNav.install(this)

        titleView = findViewById(R.id.list_title)
        subtitleView = findViewById(R.id.list_subtitle)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)

        list = findViewById(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        playAll = findViewById(R.id.btn_diagnose)
        playAll.text = getString(R.string.music_play_all)
        playAll.visibility = View.GONE
        playAll.setOnClickListener {
            if (currentTracks.isNotEmpty()) MusicPlayerActivity.start(this, currentTracks, 0)
        }

        addFolderButton = findViewById(R.id.btn_list_view_mode)
        addFolderButton.text = getString(R.string.music_add_folder)
        addFolderButton.visibility = View.GONE
        addFolderButton.setOnClickListener {
            runCatching { folderPicker.launch(null) }
                .onFailure {
                    Toast.makeText(this, "Klasör seçici açılamadı", Toast.LENGTH_SHORT).show()
                }
        }

        showSources()
    }

    /**
     * Kullanıcının seçtiği klasörün iznini kalıcı alır.
     *
     * Android 11+ ham dosya erişimini engellediği için USB ve dahili
     * depolamaya ancak böyle ulaşılabiliyor; izin bir kez veriliyor ve
     * saklanıyor.
     */
    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        SafBrowser.rememberFolder(this, uri)
        loadLocal(null)
    }

    // --- Kaynak listesi ---

    private fun showSources() {
        nasSource = null
        share = null
        localPath = null
        pathStack.clear()

        titleView.text = getString(R.string.row_music)
        subtitleView.text = getString(R.string.music_sources)
        playAll.visibility = View.GONE
        empty.visibility = View.GONE

        val entries = ArrayList<MediaItem>()
        // Kayıtlı NAS kaynakları
        Repository.sources().filter { it.type == SourceType.SMB }.forEach { source ->
            entries += MediaItem(
                id = "music_src_${source.id}",
                title = source.name,
                kind = MediaKind.FOLDER,
                playUrl = SOURCE_PREFIX + source.id,
                group = source.url
            )
        }
        // USB ve dahili depolama
        entries += MediaItem(
            id = "music_local",
            title = getString(R.string.music_local),
            kind = MediaKind.FOLDER,
            playUrl = LOCAL_PREFIX,
            group = "Yerel"
        )

        adapter.submit(entries)
        list.requestFocus()
    }

    // --- Listeleme ---

    private fun loadNas(source: Source) {
        val activeShare = share
        val path = pathStack.joinToString("/")

        titleView.text = when {
            activeShare == null -> source.name
            pathStack.isEmpty() -> activeShare
            else -> pathStack.last()
        }
        subtitleView.text =
            if (activeShare == null) "${source.url} · tüm paylaşımlar"
            else "${source.url} / $activeShare/$path"

        loadWith {
            if (activeShare == null) SmbBrowser.listShares(source)
            else SmbBrowser.list(source, activeShare, path)
        }
    }

    private fun loadLocal(path: String?) {
        val saf = SafBrowser.useSaf()

        titleView.text = when {
            path == null -> getString(R.string.music_local)
            saf -> path.substringAfterLast("%2F").substringAfterLast('/')
            else -> path.substringAfterLast('/')
        }
        subtitleView.text = when {
            path != null -> if (saf) "Cihaz klasörü" else path
            saf -> "Yetki verilen klasörler"
            else -> "USB ve dahili depolama"
        }

        // Android 11+ ham dosya erişimini engelliyor; orada klasör seçtiriyoruz.
        addFolderButton.visibility = if (saf && path == null) View.VISIBLE else View.GONE

        loadWith {
            when {
                path != null && saf -> SafBrowser.list(this@MusicBrowseActivity, path)
                path != null -> LocalBrowser.list(path)
                saf -> SafBrowser.roots(this@MusicBrowseActivity)
                else -> LocalBrowser.roots(this@MusicBrowseActivity)
            }
        }
    }

    private fun loadWith(fetch: suspend () -> List<MediaItem>) {
        progress.visibility = View.VISIBLE
        empty.visibility = View.GONE
        playAll.visibility = View.GONE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetch() } }
            progress.visibility = View.GONE

            result.fold(
                onSuccess = { items ->
                    // Müzik gezgininde video ve fotoğraflar gürültü; yalnız
                    // klasörler ve ses dosyaları gösteriliyor.
                    val visible = items.filter {
                        it.kind == MediaKind.FOLDER || it.kind == MediaKind.AUDIO
                    }
                    currentTracks = visible.filter { it.kind == MediaKind.AUDIO }
                    adapter.submit(visible)

                    playAll.visibility =
                        if (currentTracks.isNotEmpty()) View.VISIBLE else View.GONE

                    if (visible.isEmpty()) {
                        empty.visibility = View.VISIBLE
                        empty.text = getString(R.string.music_empty)
                    } else {
                        list.requestFocus()
                    }
                },
                onFailure = { error ->
                    empty.visibility = View.VISIBLE
                    empty.text = "Açılamadı: ${error.message ?: error.javaClass.simpleName}"
                }
            )
        }
    }

    // --- Gezinme ---

    private fun onItemClick(item: MediaItem) {
        when {
            item.playUrl.startsWith(SOURCE_PREFIX) -> {
                val id = item.playUrl.removePrefix(SOURCE_PREFIX)
                val source = Repository.sources().firstOrNull { it.id == id } ?: return
                nasSource = source
                share = source.share?.takeIf { it.isNotBlank() }
                pathStack.clear()
                loadNas(source)
            }

            item.playUrl == LOCAL_PREFIX -> {
                localPath = null
                loadLocal(null)
            }

            item.kind == MediaKind.FOLDER -> {
                val source = nasSource
                if (source != null) {
                    if (share == null) {
                        share = item.title
                        pathStack.clear()
                    } else {
                        pathStack += item.playUrl.substringAfterLast('/')
                    }
                    loadNas(source)
                } else {
                    localPath = item.playUrl
                    loadLocal(localPath)
                }
            }

            item.kind == MediaKind.AUDIO -> {
                // Klasördeki tüm parçalar sıra olsun, seçilen parçadan başlasın.
                val index = currentTracks.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                MusicPlayerActivity.start(this, currentTracks, index)
            }

            else -> Toast.makeText(this, "Bu öğe çalınamıyor", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val source = nasSource
        when {
            // NAS: klasör ağacında yukarı, sonra paylaşım listesine, sonra kaynaklara.
            source != null && pathStack.isNotEmpty() -> {
                pathStack.removeAt(pathStack.lastIndex)
                loadNas(source)
            }
            source != null && share != null && source.share.isNullOrBlank() -> {
                share = null
                loadNas(source)
            }
            source != null -> showSources()

            // Yerel: bir üst seviyeye, sonra kök listesine.
            //
            // SAF'ta klasör ağacında geriye yürüyemiyoruz — her klasör
            // bağımsız bir belge URI'si ve üst klasörü taşımıyor. Bu yüzden
            // orada doğrudan yetkili klasörler listesine dönülüyor.
            localPath != null -> {
                localPath = if (SafBrowser.useSaf()) {
                    null
                } else {
                    val parent = java.io.File(localPath!!).parentFile
                    val roots = LocalBrowser.roots(this).map { it.playUrl }
                    if (parent != null && localPath !in roots) parent.absolutePath else null
                }
                loadLocal(localPath)
            }

            else -> super.onBackPressed()
        }
    }

    private companion object {
        const val SOURCE_PREFIX = "music-source:"
        const val LOCAL_PREFIX = "music-local:"
    }
}
