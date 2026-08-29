package com.orhan.iptv.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Kaynakların, profillerin ve profil başına tercihlerin tutulduğu depo.
 * SharedPreferences + Gson yeterli: veri miktarı küçük.
 *
 * Ad alanı kuralı: kaynaklar ve profil listesi cihaz genelinde; favoriler,
 * kaldığı yer ve motor tercihi ise etkin profile göre anahtarlanır
 * (`p_<profilId>_...`). Böylece aynı televizyonda herkesin listesi ayrı kalır.
 */
object Repository {

    private const val PREFS = "mediatank"
    private const val KEY_SOURCES = "sources"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ACTIVE_PROFILE = "active_profile"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    /** Son yüklenen katalog; UI ve oynatıcı arasında paylaşılır. */
    @Volatile var live: List<MediaItem> = emptyList()
    @Volatile var vod: List<MediaItem> = emptyList()
    @Volatile var series: List<MediaItem> = emptyList()

    /**
     * Oynatıcının "önceki / sonraki" için kullandığı sıra.
     *
     * Bellekte tutuluyor çünkü Intent ile taşınamaz: bir kategori 60 bin öğe
     * içerebiliyor ve Binder işlemi 1 MB sınırını aşar.
     */
    @Volatile var queue: List<MediaItem> = emptyList()

    /** Müzik çaların sırası; video sırasından ayrı tutuluyor. */
    @Volatile var musicQueue: List<MediaItem> = emptyList()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun newId(): String = UUID.randomUUID().toString()

    // --- Profiller ---

    fun profiles(): List<Profile> = read(KEY_PROFILES)

    fun addProfile(profile: Profile) {
        write(KEY_PROFILES, profiles().filterNot { it.id == profile.id } + profile)
    }

    /** Profili ve ona ait tüm tercihleri siler. */
    fun removeProfile(id: String) {
        write(KEY_PROFILES, profiles().filterNot { it.id == id })
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("p_${id}_") }.forEach { editor.remove(it) }
        editor.apply()
        if (activeProfileId() == id) prefs.edit().remove(KEY_ACTIVE_PROFILE).apply()
    }

    fun activeProfileId(): String? = prefs.getString(KEY_ACTIVE_PROFILE, null)

    fun activeProfile(): Profile? =
        activeProfileId()?.let { id -> profiles().firstOrNull { it.id == id } }

    fun setActiveProfile(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, id).apply()
    }

    /** Etkin profile ait anahtar öneki. Profil yoksa ortak ad alanı kullanılır. */
    private fun scope(): String = activeProfileId()?.let { "p_${it}_" } ?: "p_default_"

    // --- Kaynaklar (cihaz geneli) ---

    fun sources(): List<Source> = read(KEY_SOURCES)

    fun addSource(source: Source) {
        write(KEY_SOURCES, sources().filterNot { it.id == source.id } + source)
    }

    fun removeSource(id: String) = write(KEY_SOURCES, sources().filterNot { it.id == id })

    fun clearSources() = write<Source>(KEY_SOURCES, emptyList())

    // --- Favoriler (profil başına) ---

    /**
     * Favoriler öğenin kendisiyle birlikte saklanıyor; kaynak listesi
     * yenilendiğinde kaybolmasın ve favori rayı ağ beklemeden çizilebilsin.
     */
    fun favorites(): List<MediaItem> = read(scope() + "favorites")

    /**
     * Favori kimliklerinin bellek içi kopyası.
     *
     * [isFavorite] her kart çiziminde çağrılıyor; 40 binlik bir kanal listesinde
     * her seferinde JSON ayrıştırmak kaydırmayı gözle görülür biçimde takardı.
     * Anahtar profil kapsamını içeriyor, böylece profil değişince küme
     * kendiliğinden tazeleniyor.
     */
    private var favoriteIdsScope: String? = null
    private var favoriteIds: MutableSet<String> = HashSet()

    private fun favoriteIdSet(): MutableSet<String> {
        val current = scope()
        if (favoriteIdsScope != current) {
            favoriteIds = favorites().mapTo(HashSet()) { it.id }
            favoriteIdsScope = current
        }
        return favoriteIds
    }

    fun isFavorite(itemId: String): Boolean = favoriteIdSet().contains(itemId)

    /** Favoriyi ekler veya çıkarır; yeni durumu döndürür (true = favoride). */
    fun toggleFavorite(item: MediaItem): Boolean {
        val current = favorites()
        val existing = current.any { it.id == item.id }
        val updated = if (existing) current.filterNot { it.id == item.id } else current + item
        write(scope() + "favorites", updated)

        val ids = favoriteIdSet()
        if (existing) ids.remove(item.id) else ids.add(item.id)
        return !existing
    }

    // --- İzlendi işareti (profil başına) ---

    /**
     * İzlenmiş sayılan öğelerin kimlikleri.
     *
     * Kaldığı yer kaydından ayrı tutuluyor: bir filmi sonuna kadar izleyip
     * "devam et" konumunu silmek ile onu "izledim" diye işaretlemek farklı
     * şeyler; kullanıcı yarıda bıraktığı bir filmi de elle izlendi
     * işaretleyebilmeli.
     */
    private var watchedIdsScope: String? = null
    private var watchedIds: MutableSet<String> = HashSet()

    private fun watchedSet(): MutableSet<String> {
        val current = scope()
        if (watchedIdsScope != current) {
            watchedIds = prefs.getStringSet(current + "watched", emptySet())
                ?.toMutableSet() ?: HashSet()
            watchedIdsScope = current
        }
        return watchedIds
    }

    fun isWatched(itemId: String): Boolean = watchedSet().contains(itemId)

    fun setWatched(itemId: String, watched: Boolean) {
        val ids = watchedSet()
        if (watched) ids.add(itemId) else ids.remove(itemId)
        // StringSet'i kopyalayarak yaz: SharedPreferences kendi verdiği kümenin
        // değiştirilmesini desteklemiyor, aynı örneği geri yazmak sessizce
        // kayboluyor.
        prefs.edit().putStringSet(scope() + "watched", HashSet(ids)).apply()
        // İzlendi işaretlenen içerik "izlemeye devam et" listesinden düşmeli.
        if (watched) prefs.edit().remove(scope() + "pos_" + itemId).apply()
    }

    /** Yeni durumu döndürür (true = izlendi). */
    fun toggleWatched(itemId: String): Boolean {
        val next = !isWatched(itemId)
        setWatched(itemId, next)
        return next
    }

    // --- Kaldığı yer (profil başına) ---

    fun savePosition(itemId: String, positionMs: Long, durationMs: Long) {
        val key = scope() + "pos_" + itemId
        // Son bir dakikaya girildiyse içerik bitmiş sayılır: kaldığı yer
        // kaydı silinir ve öğe izlendi olarak işaretlenir.
        if (durationMs > 0 && positionMs > durationMs - WATCHED_THRESHOLD_MS) {
            prefs.edit().remove(key).apply()
            setWatched(itemId, true)
        } else if (positionMs > 10_000) {
            prefs.edit().putLong(key, positionMs).apply()
        }
    }

    /** Bitmiş sayılmak için sona kalan süre. */
    const val WATCHED_THRESHOLD_MS = 60_000L

    fun position(itemId: String): Long = prefs.getLong(scope() + "pos_" + itemId, 0L)

    /** Kaldığı yer kaydını siler; "baştan izle" bunu kullanıyor. */
    fun clearPosition(itemId: String) {
        prefs.edit().remove(scope() + "pos_" + itemId).apply()
    }

    // --- TMDB (cihaz geneli) ---

    /**
     * TMDB API anahtarı. Boşsa dış bilgi çekilmez ve yalnız sağlayıcının
     * verdiği bilgi gösterilir.
     */
    fun tmdbApiKey(): String = prefs.getString("tmdb_key", "").orEmpty()

    fun setTmdbApiKey(key: String) {
        prefs.edit().putString("tmdb_key", key.trim()).apply()
    }

    // --- Dil tercihleri (profil başına) ---

    /**
     * Tercih edilen ses ve altyazı dilleri, ISO 639 kodlarıyla ("tr", "en").
     * Boş dize "kapalı" (altyazı) veya "seçme" (ses) anlamına gelir.
     *
     * Oynatıcı, içerikte bu dilde bir kanal varsa onu kendiliğinden seçer.
     */
    fun preferredAudioLanguage(): String =
        prefs.getString(scope() + "lang_audio", "tr").orEmpty()

    fun setPreferredAudioLanguage(code: String) {
        prefs.edit().putString(scope() + "lang_audio", code).apply()
    }

    fun preferredSubtitleLanguage(): String =
        prefs.getString(scope() + "lang_subtitle", "tr").orEmpty()

    fun setPreferredSubtitleLanguage(code: String) {
        prefs.edit().putString(scope() + "lang_subtitle", code).apply()
    }

    // --- Motor tercihi ---

    fun defaultEngine(): Engine =
        if (prefs.getString(scope() + "engine", "EXOPLAYER") == "VLC") Engine.VLC
        else Engine.EXOPLAYER

    fun setDefaultEngine(engine: Engine) {
        prefs.edit().putString(scope() + "engine", engine.name).apply()
    }

    /**
     * Bu öğe ExoPlayer'da patladıysa bir dahaki sefere doğrudan VLC ile aç.
     * Cihaz geneli: bir codec'in desteklenmemesi profile bağlı değil.
     */
    fun markExoFailed(playUrl: String) {
        prefs.edit().putBoolean("exofail_" + playUrl.hashCode(), true).apply()
    }

    fun exoFailedBefore(playUrl: String): Boolean =
        prefs.getBoolean("exofail_" + playUrl.hashCode(), false)

    // --- Ortak JSON yardımcıları ---

    private inline fun <reified T> read(key: String): List<T> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<T>>() {}.type
        return runCatching { gson.fromJson<List<T>>(json, type) }.getOrNull() ?: emptyList()
    }

    private fun <T> write(key: String, list: List<T>) {
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }
}
