package com.orhan.iptv.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.orhan.iptv.R
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Repository
import com.orhan.iptv.player.PlaybackActivity
import com.orhan.iptv.ui.CardStyle
import com.orhan.iptv.ui.MediaAdapter
import com.orhan.iptv.ui.detail.DetailsActivity
import java.util.Locale

/**
 * Arama ekranı.
 *
 * Kapsam çağıran ekrandan geliyor: Canlı/Film/Dizi bölümlerinden açılınca
 * yalnız o bölümde, Ana Sayfa'dan açılınca hepsinde arar.
 *
 * Katalog [Repository] üzerinde bellekte durduğu için arama ağ beklemeden,
 * her tuşta anında sonuç veriyor.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var results: RecyclerView
    private lateinit var summary: TextView
    private lateinit var adapter: MediaAdapter

    private var scope: Scope = Scope.ALL

    /** Aranacak havuz. */
    enum class Scope { ALL, LIVE, VOD, SERIES }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        scope = runCatching {
            Scope.valueOf(intent.getStringExtra(EXTRA_SCOPE) ?: Scope.ALL.name)
        }.getOrDefault(Scope.ALL)

        findViewById<TextView>(R.id.search_scope).text = when (scope) {
            Scope.ALL -> "Her Yerde Ara"
            Scope.LIVE -> "Canlı Kanallarda Ara"
            Scope.VOD -> "Filmlerde Ara"
            Scope.SERIES -> "Dizilerde Ara"
        }

        summary = findViewById(R.id.result_summary)
        results = findViewById(R.id.results)
        results.layoutManager = LinearLayoutManager(this)

        adapter = MediaAdapter(
            style = CardStyle.LIST,
            onClick = { open(it) },
            onLongClick = { item ->
                val added = Repository.toggleFavorite(item)
                android.widget.Toast.makeText(
                    this,
                    if (added) "${item.title} favorilere eklendi"
                    else "${item.title} favorilerden çıkarıldı",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
        results.adapter = adapter

        findViewById<EditText>(R.id.search_field).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = search(s?.toString().orEmpty())
        })
    }

    private fun pool(): List<MediaItem> = when (scope) {
        Scope.LIVE -> Repository.live
        Scope.VOD -> Repository.vod
        Scope.SERIES -> Repository.series
        Scope.ALL -> Repository.live + Repository.vod + Repository.series
    }

    private fun search(rawQuery: String) {
        val query = normalize(rawQuery)
        if (query.length < MIN_QUERY) {
            adapter.submit(emptyList())
            summary.text = getString(R.string.search_prompt)
            return
        }

        // Başlıkta geçenler önce, grup adında geçenler sonra: kullanıcı
        // genelde adı arıyor, kategori eşleşmesi ikincil.
        val pool = pool()
        val byTitle = ArrayList<MediaItem>()
        val byGroup = ArrayList<MediaItem>()
        for (item in pool) {
            when {
                normalize(item.title).contains(query) -> byTitle += item
                normalize(item.group.orEmpty()).contains(query) -> byGroup += item
            }
            if (byTitle.size >= MAX_RESULTS) break
        }

        val found = (byTitle + byGroup).take(MAX_RESULTS)
        adapter.submit(found)
        summary.text = if (found.isEmpty()) {
            getString(R.string.search_empty)
        } else {
            "${found.size} sonuç" + if (found.size >= MAX_RESULTS) " (ilk $MAX_RESULTS)" else ""
        }
    }

    /**
     * Türkçe'ye duyarlı normalleştirme.
     *
     * Kullanıcı "ic" yazınca "İç" bulunmalı: Türkçe'de büyük/küçük harf
     * dönüşümü İ/ı yüzünden özel, ayrıca aksanlı harfler sadeleştiriliyor.
     */
    private fun normalize(text: String): String {
        val lower = text.lowercase(Locale("tr"))
        val builder = StringBuilder(lower.length)
        for (ch in lower) {
            builder.append(
                when (ch) {
                    'ı', 'î' -> 'i'
                    'ğ' -> 'g'
                    'ü', 'û' -> 'u'
                    'ş' -> 's'
                    'ö' -> 'o'
                    'ç' -> 'c'
                    'â' -> 'a'
                    else -> ch
                }
            )
        }
        return builder.toString()
    }

    private fun open(item: MediaItem) {
        if (item.kind == MediaKind.SERIES && item.seriesId != null) {
            startActivity(
                Intent(this, DetailsActivity::class.java)
                    .putExtra(DetailsActivity.EXTRA_ITEM, item)
            )
        } else {
            PlaybackActivity.start(this, item)
        }
    }

    companion object {
        private const val EXTRA_SCOPE = "scope"
        private const val MIN_QUERY = 2
        /** TV'de binlerce satırı kaydırmak kullanışsız; ilk eşleşmeler yeterli. */
        private const val MAX_RESULTS = 200

        fun start(context: Context, scope: Scope) {
            context.startActivity(
                Intent(context, SearchActivity::class.java).putExtra(EXTRA_SCOPE, scope.name)
            )
        }
    }
}
