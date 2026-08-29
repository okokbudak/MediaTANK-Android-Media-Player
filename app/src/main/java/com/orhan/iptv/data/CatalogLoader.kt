package com.orhan.iptv.data

import com.orhan.iptv.data.m3u.M3uParser
import com.orhan.iptv.data.xtream.XtreamClient
import com.orhan.iptv.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.BufferedReader
import java.io.File

/** Bir kaynağın çözümlenmiş içeriği. */
data class Catalog(
    val live: List<MediaItem> = emptyList(),
    val vod: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList()
) {
    operator fun plus(other: Catalog) =
        Catalog(live + other.live, vod + other.vod, series + other.series)

    val isEmpty: Boolean get() = live.isEmpty() && vod.isEmpty() && series.isEmpty()
}

/** Kaynakları ağdan çekip [Catalog]'a çevirir. */
object CatalogLoader {

    /**
     * Tüm kaynakları yükler. Bir kaynak hata verirse diğerleri yüklenmeye
     * devam eder; hatalar [onSourceError] ile bildirilir.
     */
    suspend fun loadAll(
        sources: List<Source>,
        onSourceError: (Source, Throwable) -> Unit = { _, _ -> }
    ): Catalog = withContext(Dispatchers.IO) {
        var catalog = Catalog()
        for (source in sources) {
            try {
                catalog += load(source)
            } catch (e: Exception) {
                onSourceError(source, e)
            }
        }
        Repository.live = catalog.live
        Repository.vod = catalog.vod
        Repository.series = catalog.series
        catalog
    }

    suspend fun load(source: Source): Catalog = withContext(Dispatchers.IO) {
        when (source.type) {
            SourceType.M3U -> loadM3u(source)
            SourceType.XTREAM -> loadXtream(source)
            // NAS ayrı ekranda, isteğe bağlı olarak gezilir; önden yüklenmez.
            SourceType.SMB -> Catalog()
        }
    }

    private fun loadM3u(source: Source): Catalog {
        val url = source.url.trim()
        val reader: BufferedReader = if (url.startsWith("http", ignoreCase = true)) {
            Http.stream(Request.Builder().url(url).build()).bufferedReader()
        } else {
            File(url).bufferedReader()
        }

        val items = reader.use { M3uParser.parse(it) }
        return Catalog(
            live = items.filter { it.kind == MediaKind.LIVE },
            vod = items.filter { it.kind == MediaKind.VOD },
            series = items.filter { it.kind == MediaKind.SERIES }
        )
    }

    private fun loadXtream(source: Source): Catalog {
        val client = XtreamClient(source)
        // Her uç nokta tek tek korunuyor: sağlayıcı VOD satmıyorsa canlı
        // kanallar yine de yüklensin.
        val live = runCatching { client.liveStreams() }.getOrDefault(emptyList())
        val vod = runCatching { client.vodStreams() }.getOrDefault(emptyList())
        val series = runCatching { client.seriesList() }.getOrDefault(emptyList())
        if (live.isEmpty() && vod.isEmpty() && series.isEmpty()) {
            // Hiçbiri gelmediyse gerçek nedeni öğrenmek için kimlik doğrula.
            client.authenticate()
        }
        return Catalog(live, vod, series)
    }
}
