package com.orhan.iptv.data.meta

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.orhan.iptv.data.xtream.ContentDetail

/**
 * TMDB sonuçlarının kalıcı önbelleği.
 *
 * Aynı film ikinci kez açıldığında ağa gidilmiyor: hem kota korunuyor hem de
 * detay sayfası anında doluyor. Sonuç bulunamadıysa bu da kaydediliyor,
 * yoksa eşleşmeyen her başlık her açılışta yeniden sorgulanırdı.
 *
 * Profilden bağımsız: aynı filmin bilgisi herkes için aynı.
 */
object MetadataCache {

    private const val PREFS = "mediatank_meta"
    private const val MISS = "__miss__"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun key(itemId: String) = "m_$itemId"

    /**
     * Önbellekteki kayıt.
     * `null` = hiç sorulmamış, `Miss` = sorulmuş ama bulunamamış.
     */
    sealed interface Entry {
        data class Hit(val detail: ContentDetail) : Entry
        object Miss : Entry
    }

    fun get(itemId: String): Entry? {
        val raw = prefs.getString(key(itemId), null) ?: return null
        if (raw == MISS) return Entry.Miss
        val detail = runCatching { gson.fromJson(raw, ContentDetail::class.java) }.getOrNull()
        return detail?.let { Entry.Hit(it) }
    }

    fun put(itemId: String, detail: ContentDetail?) {
        val value = if (detail == null) MISS else gson.toJson(detail)
        prefs.edit().putString(key(itemId), value).apply()
    }

    /** Kullanıcı bilgileri tazelemek isterse. */
    fun clear() = prefs.edit().clear().apply()
}
