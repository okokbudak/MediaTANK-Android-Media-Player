package com.orhan.iptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R
import com.orhan.iptv.data.Catalog
import com.orhan.iptv.data.CatalogLoader
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Repository
import com.orhan.iptv.data.SourceType
import com.orhan.iptv.player.PlaybackActivity
import com.orhan.iptv.ui.detail.DetailsActivity
import com.orhan.iptv.ui.music.MusicBrowseActivity
import com.orhan.iptv.ui.nas.NasBrowseActivity
import com.orhan.iptv.ui.photo.PhotoViewerActivity
import com.orhan.iptv.ui.profile.ProfilesActivity
import com.orhan.iptv.ui.search.SearchActivity
import com.orhan.iptv.ui.settings.SettingsActivity
import com.orhan.iptv.ui.setup.AddSourceActivity
import kotlinx.coroutines.launch

/** Kenar çubuğundaki bölümler. */
private enum class Section { HOME, LIVE, MOVIES, SERIES }

/** İçerik listesinin çizim biçimi. */
private enum class ViewMode(val label: String) {
    GRID("Kılavuz"),
    LIST("Liste"),
    TILE("Karo")
}

/** Sıralama ölçütü. */
private enum class SortMode(val label: String) {
    ORIGINAL("Sıra: Listedeki"),
    NAME("Sıra: Ada göre")
}

/**
 * Ana ekran.
 *
 * Ana Sayfa bölüm kutuları ve raylar; diğer bölümler solda kategori sütunu,
 * sağda içerik ızgarası. Katalog bir kez yüklenip bellekte tutuluyor, bölüm
 * ve kategori değişimi ağ beklemeden çiziliyor.
 */
class MainActivity : AppCompatActivity() {

    // Ana Sayfa
    private lateinit var homeScroll: NestedScrollView
    private lateinit var railsContainer: LinearLayout
    private lateinit var emptyHint: TextView

    // Gözat
    private lateinit var browsePane: View
    private lateinit var sectionTitle: TextView
    private lateinit var categoryList: RecyclerView
    private lateinit var contentGrid: RecyclerView
    private lateinit var contentTitle: TextView
    private lateinit var contentEmpty: TextView
    private lateinit var viewModeButton: TextView
    private lateinit var sortButton: TextView

    private lateinit var loading: View

    private val navIcons = HashMap<Section, TextView>()

    private var catalog = Catalog()
    private var section = Section.HOME

    private var viewMode = ViewMode.GRID
    private var sortMode = SortMode.ORIGINAL

    /** Geçerli bölümün kategori adı → öğeler eşlemesi, ekrandaki sırayla. */
    private var groups: List<Pair<String, List<MediaItem>>> = emptyList()

    private lateinit var categoryAdapter: CategoryAdapter
    private var contentAdapter: MediaAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Profil seçilmeden buraya gelinirse profil ekranına dön.
        if (Repository.activeProfile() == null) {
            startActivity(Intent(this, ProfilesActivity::class.java))
            finish()
            return
        }

        bindViews()
        setupSidebar()
        setupBrowse()
        load()
    }

    override fun onResume() {
        super.onResume()
        if (catalog.isEmpty && Repository.sources().isNotEmpty()) load()
    }

    private fun bindViews() {
        homeScroll = findViewById(R.id.home_scroll)
        railsContainer = findViewById(R.id.rails_container)
        emptyHint = findViewById(R.id.empty_hint)

        browsePane = findViewById(R.id.browse_pane)
        sectionTitle = findViewById(R.id.section_title)
        categoryList = findViewById(R.id.category_list)
        contentGrid = findViewById(R.id.content_grid)
        contentTitle = findViewById(R.id.content_title)
        contentEmpty = findViewById(R.id.content_empty)
        viewModeButton = findViewById(R.id.btn_view_mode)
        sortButton = findViewById(R.id.btn_sort)

        loading = findViewById(R.id.loading)

        // Ana sayfadan arama tüm bölümleri kapsar.
        findViewById<View>(R.id.hero_search).setOnClickListener {
            SearchActivity.start(this, SearchActivity.Scope.ALL)
        }
        findViewById<View>(R.id.btn_search).setOnClickListener { openSearchForSection() }
        // Yenile: Xtream/M3U kataloğunu sunucudan baştan indirir; sağlayıcıda
        // kategori eklenip çıkarıldığında uygulamanın haberi olsun.
        findViewById<View>(R.id.home_refresh).setOnClickListener { refresh() }
    }

    /** Gözat ekranındaki arama yalnız o bölümde arar. */
    private fun openSearchForSection() = SearchActivity.start(
        this,
        when (section) {
            Section.LIVE -> SearchActivity.Scope.LIVE
            Section.MOVIES -> SearchActivity.Scope.VOD
            Section.SERIES -> SearchActivity.Scope.SERIES
            Section.HOME -> SearchActivity.Scope.ALL
        }
    )

    // --- Kenar çubuğu ---

    private fun setupSidebar() {
        navIcons[Section.HOME] = findViewById(R.id.nav_home)
        navIcons[Section.LIVE] = findViewById(R.id.nav_live)
        navIcons[Section.MOVIES] = findViewById(R.id.nav_movies)
        navIcons[Section.SERIES] = findViewById(R.id.nav_series)

        applySidebarLabels()

        navIcons.forEach { (target, icon) -> icon.setOnClickListener { show(target) } }
        findViewById<View>(R.id.nav_music).setOnClickListener {
            startActivity(Intent(this, MusicBrowseActivity::class.java))
        }
        findViewById<View>(R.id.nav_nas).setOnClickListener { openNas() }
        findViewById<View>(R.id.nav_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        markActiveNav()
    }

    /**
     * Dar ekranlarda kenar çubuğunu ikon-only yapar.
     *
     * Etiketler XML'de duruyor; burada yalnız gizleniyor. Böylece erişilebilirlik
     * için `contentDescription` yerine gerçek metin korunuyor ve büyük ekranda
     * aynı düzen etiketli çalışıyor.
     */
    private fun applySidebarLabels() {
        if (resources.getBoolean(R.bool.sidebar_show_labels)) return

        val allItems = navIcons.values + listOf<TextView>(
            findViewById(R.id.nav_music),
            findViewById(R.id.nav_nas),
            findViewById(R.id.nav_settings)
        )
        allItems.forEach { item ->
            item.contentDescription = item.text
            item.text = ""
            // İkonu ortala: metin gidince drawableStart sola yapışık kalıyor.
            item.gravity = android.view.Gravity.CENTER
        }
    }

    private fun markActiveNav() {
        navIcons.forEach { (target, icon) -> icon.isSelected = target == section }
    }

    private fun show(target: Section) {
        section = target
        markActiveNav()
        render()
    }

    // --- Gözat kurulumu ---

    private fun setupBrowse() {
        categoryAdapter = CategoryAdapter { index, _ -> showCategory(index) }
        categoryList.layoutManager = LinearLayoutManager(this)
        categoryList.adapter = categoryAdapter

        viewModeButton.setOnClickListener {
            // Üç mod arasında sırayla dolaş; TV'de menü açmaktan hızlı.
            viewMode = ViewMode.values()[(viewMode.ordinal + 1) % ViewMode.values().size]
            viewModeButton.text = viewMode.label
            showCategory(currentCategoryIndex)
        }
        sortButton.setOnClickListener {
            sortMode = SortMode.values()[(sortMode.ordinal + 1) % SortMode.values().size]
            sortButton.text = sortMode.label
            showCategory(currentCategoryIndex)
        }
        viewModeButton.text = viewMode.label
        sortButton.text = sortMode.label
    }

    // --- Yükleme ---

    /**
     * Katalogları sunucudan baştan indirir.
     *
     * Sağlayıcıda kategori/kanal eklenip çıkarıldığında uygulama bunu
     * kendiliğinden fark etmiyor; katalog yalnız açılışta çekiliyor.
     */
    private fun refresh() {
        if (Repository.sources().isEmpty()) {
            Toast.makeText(this, "Yenilenecek kaynak yok", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "İçerik yenileniyor…", Toast.LENGTH_SHORT).show()
        catalog = Catalog()
        load()
    }

    private fun load() {
        val sources = Repository.sources()
        if (sources.isEmpty()) {
            showEmptyState()
            return
        }

        loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val errors = ArrayList<String>()
            catalog = CatalogLoader.loadAll(sources) { source, error ->
                errors += "${source.name}: ${error.message ?: error.javaClass.simpleName}"
            }
            loading.visibility = View.GONE
            render()
            if (errors.isNotEmpty()) {
                Toast.makeText(this@MainActivity, errors.joinToString("\n"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showEmptyState() {
        showHomeChrome(true)
        emptyHint.visibility = View.VISIBLE
        // Ana sayfada "Kaynak Ekle" düğmesi yok; kaynak yönetimi Ayarlar'da.
        emptyHint.text = "Henüz kaynak yok. Sol alttaki ⚙ Ayarlar'dan M3U, Xtream veya NAS ekleyin."
    }

    // --- Çizim ---

    private fun render() {
        if (section == Section.HOME) {
            showHomeChrome(true)
            renderHome()
        } else {
            showHomeChrome(false)
            renderBrowse()
        }
    }

    /** Ana Sayfa ile Gözat arasında geçiş. */
    private fun showHomeChrome(home: Boolean) {
        homeScroll.visibility = if (home) View.VISIBLE else View.GONE
        browsePane.visibility = if (home) View.GONE else View.VISIBLE
    }

    private fun renderHome() {
        railsContainer.removeAllViews()
        emptyHint.visibility = View.GONE

        // İzlemeye devam et en üstte: kullanıcının çoğu zaman aradığı şey bu.
        val resumable = (catalog.vod + catalog.series)
            .filter { Repository.position(it.id) > 0 }
            .take(20)
        if (resumable.isNotEmpty()) {
            addRail("İzlemeye Devam Et", resumable, CardStyle.WIDE)
        }

        addSectionTiles()

        // Favoriler türlerine göre ayrı raylarda; boşken de başlık görünsün ki
        // özelliğin var olduğu belli olsun.
        val favorites = Repository.favorites()
        addFavoriteRail("Favori Kanallar", favorites.filter { it.kind == MediaKind.LIVE }, CardStyle.CHANNEL)
        addFavoriteRail("Favori Filmler", favorites.filter { it.kind == MediaKind.VOD }, CardStyle.POSTER)
        addFavoriteRail("Favori Diziler", favorites.filter { it.kind == MediaKind.SERIES }, CardStyle.POSTER)

        if (catalog.isEmpty && Repository.sources().isEmpty()) {
            emptyHint.visibility = View.VISIBLE
            emptyHint.text = getString(R.string.empty_hint)
        }
    }

    /** Favori rayı; boşsa başlık ve yönlendirme metniyle gösterilir. */
    private fun addFavoriteRail(title: String, items: List<MediaItem>, style: CardStyle) {
        if (items.isNotEmpty()) {
            addRail(title, items, style)
            return
        }
        val rail = layoutInflater.inflate(R.layout.view_rail, railsContainer, false)
        rail.findViewById<TextView>(R.id.rail_title).text = title
        rail.findViewById<RecyclerView>(R.id.rail_list).visibility = View.GONE

        val hint = TextView(this).apply {
            text = "Henüz favori yok — bir karta uzun basarak ekleyin."
            setTextColor(getColor(R.color.on_surface_variant))
            textSize = 9f
        }
        (rail as android.view.ViewGroup).addView(hint)
        railsContainer.addView(rail)
    }

    /**
     * Bölüm kutuları: kenar çubuğundaki bölümlerin büyük, renkli karşılıkları.
     * Uzaktan bakıldığında nereye gidileceği tek bakışta anlaşılsın diye
     * her bölüm ayrı renkte.
     */
    private fun addSectionTiles() {
        val tiles = listOf(
            Tile(getString(R.string.row_live), R.drawable.ic_live, 0xFF4B8EFF.toInt(),
                "${catalog.live.size} kanal") { show(Section.LIVE) },
            Tile(getString(R.string.row_vod), R.drawable.ic_movie, 0xFF3834B6.toInt(),
                "${catalog.vod.size} film") { show(Section.MOVIES) },
            Tile(getString(R.string.row_series), R.drawable.ic_series, 0xFFEF6719.toInt(),
                "${catalog.series.size} dizi") { show(Section.SERIES) },
            Tile(getString(R.string.row_music), R.drawable.ic_music, 0xFF00A97F.toInt(),
                "NAS · USB") { startActivity(Intent(this, MusicBrowseActivity::class.java)) },
            Tile(getString(R.string.row_nas), R.drawable.ic_nas, 0xFFB0348A.toInt(),
                "Video · Foto") { openNas() },
            Tile(getString(R.string.row_settings), R.drawable.ic_settings, 0xFF5A6270.toInt(),
                "Kaynak · Dil") { startActivity(Intent(this, SettingsActivity::class.java)) }
        )

        val rail = layoutInflater.inflate(R.layout.view_rail, railsContainer, false)
        rail.findViewById<TextView>(R.id.rail_title).text = "Bölümler"
        val list = rail.findViewById<RecyclerView>(R.id.rail_list)
        list.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        list.clipChildren = false
        (rail as android.view.ViewGroup).clipChildren = false
        list.adapter = SectionTileAdapter(tiles)
        railsContainer.addView(rail)
    }

    private fun renderBrowse() {
        val items = when (section) {
            Section.LIVE -> catalog.live
            Section.MOVIES -> catalog.vod
            Section.SERIES -> catalog.series
            Section.HOME -> emptyList()
        }
        // Kısa başlıklar: kategori sütunu dar, uzun adlar kırpılıyor.
        sectionTitle.text = when (section) {
            Section.LIVE -> "Canlı"
            Section.MOVIES -> "Filmler"
            Section.SERIES -> "Diziler"
            Section.HOME -> ""
        }

        // Kategoriler playlist'in grup başlıklarından geliyor; grupsuz öğeler
        // sona "Diğer" olarak toplanıyor.
        val named = items
            .filter { !it.group.isNullOrBlank() }
            .groupBy { it.group!! }
            .toList()
            .sortedBy { it.first.lowercase() }
        val ungrouped = items.filter { it.group.isNullOrBlank() }

        groups = buildList {
            if (items.isNotEmpty() && named.size != 1) {
                add("Tümü" to items)
            }
            addAll(named)
            if (ungrouped.isNotEmpty()) add("Diğer" to ungrouped)
        }

        categoryAdapter.submit(groups.map { Category(it.first, it.second.size) })
        currentCategoryIndex = 0
        showCategory(0)
    }

    private var currentCategoryIndex = 0

    private fun showCategory(index: Int) {
        currentCategoryIndex = index
        val group = groups.getOrNull(index)
        val items = group?.second.orEmpty().let { list ->
            when (sortMode) {
                SortMode.ORIGINAL -> list
                SortMode.NAME -> list.sortedBy { it.title.lowercase() }
            }
        }

        contentTitle.text = group?.let { "${it.first}  ·  ${it.second.size}" }.orEmpty()
        contentEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        contentEmpty.text = "Bu kategoride içerik yok"

        val style = when (viewMode) {
            ViewMode.LIST -> CardStyle.LIST
            ViewMode.TILE -> CardStyle.CHANNEL
            ViewMode.GRID -> if (section == Section.LIVE) CardStyle.CHANNEL else CardStyle.POSTER
        }

        // Kanal kartları sütunu doldursun: sabit 160dp'de "SD: beIN Sports 1"
        // gibi adlar "SD: b…" diye kırpılıyor.
        val fillWidth = style == CardStyle.CHANNEL

        contentGrid.layoutManager = if (style == CardStyle.LIST) {
            LinearLayoutManager(this)
        } else {
            // Kart genişliği sabit; kaç sütun sığdığını ölçülen genişlikten bul.
            GridLayoutManager(this, spanCountFor(style))
        }
        // Odaklanan kart 1.1 ölçekte büyürken komşularına taşabilsin.
        contentGrid.clipChildren = false

        val adapter = MediaAdapter(
            style = style,
            fillWidth = fillWidth,
            // Sırayı da geçiriyoruz: oynatıcıda önceki/sonraki ve kanal
            // tuşları bu liste üzerinde gezinsin.
            onClick = { openFromList(items, it) },
            onLongClick = { item ->
                val added = Repository.toggleFavorite(item)
                Toast.makeText(
                    this,
                    if (added) "${item.title} favorilere eklendi"
                    else "${item.title} favorilerden çıkarıldı",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        contentAdapter = adapter
        contentGrid.adapter = adapter
        adapter.submit(items)
        contentGrid.scrollToPosition(0)
    }

    /** Ölçülen genişliğe kaç kart sığıyor; en az 1. */
    private fun spanCountFor(style: CardStyle): Int {
        val cardWidthDp = when (style) {
            CardStyle.POSTER -> 110 + 16
            CardStyle.CHANNEL -> 160 + 16
            else -> 200 + 16
        }
        val density = resources.displayMetrics.density
        val availablePx = contentGrid.width.takeIf { it > 0 }
            ?: (resources.displayMetrics.widthPixels - (260 * density).toInt())
        return (availablePx / (cardWidthDp * density)).toInt().coerceAtLeast(1)
    }

    /**
     * Yatay bir raf ekler.
     *
     * Lint uyarısı bastırılıyor: kontrol, düzendeki wrap_content'i kaydırma
     * yönü sanıyor. Raf YATAY kaydırıyor, o yöndeki ölçü match_parent;
     * wrap_content yalnız yükseklikte ve kart boyları sabit.
     */
    @android.annotation.SuppressLint("InvalidSetHasFixedSize")
    private fun addRail(title: String, items: List<MediaItem>, style: CardStyle) {
        val rail = layoutInflater.inflate(R.layout.view_rail, railsContainer, false)
        rail.findViewById<TextView>(R.id.rail_title).text =
            if (items.size > 1) "$title  ·  ${items.size}" else title

        val list = rail.findViewById<RecyclerView>(R.id.rail_list)
        list.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        list.setHasFixedSize(true)
        list.clipChildren = false
        (rail as? android.view.ViewGroup)?.clipChildren = false

        val adapter = MediaAdapter(
            style = style,
            onClick = { openFromList(items, it) },

            onLongClick = { item ->
                val added = Repository.toggleFavorite(item)
                Toast.makeText(
                    this,
                    if (added) "${item.title} favorilere eklendi"
                    else "${item.title} favorilerden çıkarıldı",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        list.adapter = adapter
        adapter.submit(items)

        railsContainer.addView(rail)
    }

    // --- Gezinme ---

    /**
     * Öğeyi içinde bulunduğu listeyle birlikte açar; oynatıcı önceki/sonraki
     * için bu sırayı kullanır.
     */
    private fun openFromList(list: List<MediaItem>, item: MediaItem) {
        // Film ve diziler önce detay sayfası açar: özet, puan ve dizilerde
        // sezon/bölüm seçimi oradan yapılıyor. Canlı kanallar doğrudan açılır,
        // çünkü orada gösterilecek bir ayrıntı yok.
        if (item.kind == MediaKind.VOD || item.kind == MediaKind.SERIES) {
            startActivity(
                Intent(this, DetailsActivity::class.java)
                    .putExtra(DetailsActivity.EXTRA_ITEM, item)
            )
            return
        }
        if (item.kind == MediaKind.PHOTO) {
            open(item)
            return
        }
        val index = list.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        PlaybackActivity.start(this, list, index)
    }

    private fun open(item: MediaItem) {
        when (item.kind) {
            // Xtream dizileri bölüm listesi açar. M3U listelerinde her bölüm
            // zaten ayrı bir oynatılabilir öğe olduğu için doğrudan başlatılır.
            MediaKind.SERIES ->
                if (item.seriesId != null) {
                    startActivity(
                        Intent(this, DetailsActivity::class.java)
                            .putExtra(DetailsActivity.EXTRA_ITEM, item)
                    )
                } else {
                    PlaybackActivity.start(this, item)
                }
            MediaKind.PHOTO -> PhotoViewerActivity.start(this, listOf(item), 0)
            else -> PlaybackActivity.start(this, item)
        }
    }

    private fun openAddSource() = startActivity(Intent(this, AddSourceActivity::class.java))

    private fun openNas() {
        val nasSources = Repository.sources().filter { it.type == SourceType.SMB }
        if (nasSources.isEmpty()) {
            Toast.makeText(this, "Önce bir NAS kaynağı ekleyin", Toast.LENGTH_LONG).show()
            openAddSource()
            return
        }
        startActivity(
            Intent(this, NasBrowseActivity::class.java)
                .putExtra(NasBrowseActivity.EXTRA_SOURCE, nasSources.first())
        )
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            // İçerikteyken önce kategori sütununa dön.
            section != Section.HOME && contentGrid.hasFocus() -> categoryList.requestFocus()
            section != Section.HOME -> show(Section.HOME)
            else -> super.onBackPressed()
        }
    }

    /**
     * Kumandanın gezinme dışı tuşları.
     *
     * Sony kumandasında SEARCH tuşu yok; arama renkli tuşa ve GUIDE'a
     * bağlanıyor. Renkli tuşlar referans IPTV uygulamalarındaki alışkanlığı
     * izliyor: kırmızı arama, yeşil görünüm, sarı sıralama.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_SEARCH,
            android.view.KeyEvent.KEYCODE_PROG_RED -> {
                openSearchForSection()
                return true
            }

            android.view.KeyEvent.KEYCODE_PROG_GREEN -> {
                if (section != Section.HOME) { viewModeButton.performClick(); return true }
            }

            android.view.KeyEvent.KEYCODE_PROG_YELLOW -> {
                if (section != Section.HOME) { sortButton.performClick(); return true }
            }

            android.view.KeyEvent.KEYCODE_PROG_BLUE -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }

            // Kanal tuşlarıyla bölümler arasında dolaş.
            android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                show(Section.values()[(section.ordinal + 1) % Section.values().size])
                return true
            }

            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                val values = Section.values()
                show(values[(section.ordinal - 1 + values.size) % values.size])
                return true
            }

            android.view.KeyEvent.KEYCODE_GUIDE -> {
                show(Section.LIVE)
                return true
            }

            android.view.KeyEvent.KEYCODE_MENU -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        /** Ana sayfada her bölümden gösterilen tadımlık öğe sayısı. */
        const val PREVIEW_COUNT = 20
    }
}
