package com.orhan.iptv.data.xtream

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.orhan.iptv.data.MediaItem
import com.orhan.iptv.data.MediaKind
import com.orhan.iptv.data.Source
import com.orhan.iptv.net.Http
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException

/**
 * Xtream Codes panel API istemcisi.
 *
 * Uç noktalar:
 *   player_api.php?username=&password=&action=get_live_streams
 *   player_api.php?...&action=get_vod_streams
 *   player_api.php?...&action=get_series
 *   player_api.php?...&action=get_series_info&series_id=
 *
 * Oynatma URL şeması:
 *   Canlı : {base}/live/{user}/{pass}/{stream_id}.ts    (m3u8 de olabilir)
 *   Film  : {base}/movie/{user}/{pass}/{stream_id}.{ext}
 *   Bölüm : {base}/series/{user}/{pass}/{episode_id}.{ext}
 */
class XtreamClient(private val source: Source) {

    private val gson = Gson()

    /** http(s):// öneki yoksa ekler, sondaki / işaretini atar. */
    private val base: String = source.url.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }

    private val user = source.username.orEmpty()
    private val pass = source.password.orEmpty()

    @Throws(IOException::class)
    fun authenticate(): XtreamAuth = get("", XtreamAuth::class.java)
        ?: throw IOException("Sunucu kimlik doğrulamaya yanıt vermedi")

    @Throws(IOException::class)
    fun liveCategories(): List<XtreamCategory> = getList("get_live_categories")

    @Throws(IOException::class)
    fun vodCategories(): List<XtreamCategory> = getList("get_vod_categories")

    @Throws(IOException::class)
    fun seriesCategories(): List<XtreamCategory> = getList("get_series_categories")

    @Throws(IOException::class)
    fun liveStreams(): List<MediaItem> {
        val categories = runCatching { liveCategories() }.getOrDefault(emptyList())
            .associate { it.categoryId to it.categoryName }
        val streams: List<XtreamLiveStream> = getList("get_live_streams")
        return streams.mapNotNull { s ->
            val id = s.streamId ?: return@mapNotNull null
            MediaItem(
                id = "x_live_$id",
                title = s.name.orEmpty().ifBlank { "Kanal $id" },
                kind = MediaKind.LIVE,
                playUrl = "$base/live/$user/$pass/$id.ts",
                logoUrl = s.streamIcon?.takeIf { it.isNotBlank() },
                group = categories[s.categoryId]
            )
        }
    }

    @Throws(IOException::class)
    fun vodStreams(): List<MediaItem> {
        val categories = runCatching { vodCategories() }.getOrDefault(emptyList())
            .associate { it.categoryId to it.categoryName }
        val streams: List<XtreamVodStream> = getList("get_vod_streams")
        return streams.mapNotNull { s ->
            val id = s.streamId ?: return@mapNotNull null
            val ext = s.containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"
            MediaItem(
                id = "x_vod_$id",
                title = s.name.orEmpty().ifBlank { "Film $id" },
                kind = MediaKind.VOD,
                playUrl = "$base/movie/$user/$pass/$id.$ext",
                logoUrl = s.streamIcon?.takeIf { it.isNotBlank() },
                group = categories[s.categoryId],
                description = s.plot
            )
        }
    }

    @Throws(IOException::class)
    fun seriesList(): List<MediaItem> {
        val categories = runCatching { seriesCategories() }.getOrDefault(emptyList())
            .associate { it.categoryId to it.categoryName }
        val list: List<XtreamSeries> = getList("get_series")
        return list.mapNotNull { s ->
            val id = s.seriesId ?: return@mapNotNull null
            MediaItem(
                id = "x_series_$id",
                title = s.name.orEmpty().ifBlank { "Dizi $id" },
                kind = MediaKind.SERIES,
                playUrl = "",              // Dizinin kendisi oynatılmaz; bölümleri oynatılır
                logoUrl = s.cover?.takeIf { it.isNotBlank() },
                group = categories[s.categoryId],
                description = s.plot,
                seriesId = id
            )
        }
    }

    /** Bir filmin ayrıntısı: özet, puan, oyuncular. */
    @Throws(IOException::class)
    fun vodDetail(streamId: String, fallbackTitle: String): ContentDetail? {
        val url = apiUrl("get_vod_info") + "&vod_id=$streamId"
        val body = Http.string(Request.Builder().url(url).build())
        val info = runCatching { gson.fromJson(body, XtreamVodInfo::class.java) }
            .getOrNull()?.info ?: return null

        return ContentDetail(
            title = fallbackTitle,
            plot = info.plot?.takeIf { it.isNotBlank() },
            cast = info.cast?.takeIf { it.isNotBlank() },
            director = info.director?.takeIf { it.isNotBlank() },
            genre = info.genre?.takeIf { it.isNotBlank() },
            year = (info.releaseDate ?: info.releaseDateAlt)?.take(4)?.takeIf { it.isNotBlank() },
            rating = info.rating?.trim()?.toDoubleOrNull(),
            posterUrl = info.movieImage?.takeIf { it.isNotBlank() }
                ?: info.backdrop?.firstOrNull(),
            durationText = info.duration?.takeIf { it.isNotBlank() }
        )
    }

    /** Bir dizinin ayrıntısı ve sezonlara ayrılmış bölümleri. */
    @Throws(IOException::class)
    fun seriesDetail(seriesId: String, fallbackTitle: String): Pair<ContentDetail?, Map<Int, List<MediaItem>>> {
        val url = apiUrl("get_series_info") + "&series_id=$seriesId"
        val body = Http.string(Request.Builder().url(url).build())
        val parsed = runCatching { gson.fromJson(body, XtreamSeriesDetail::class.java) }.getOrNull()
            ?: return null to emptyMap()

        val detail = parsed.info?.let { info ->
            ContentDetail(
                title = info.name?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                plot = info.plot?.takeIf { it.isNotBlank() },
                cast = info.cast?.takeIf { it.isNotBlank() },
                director = info.director?.takeIf { it.isNotBlank() },
                genre = info.genre?.takeIf { it.isNotBlank() },
                year = (info.releaseDate ?: info.releaseDateAlt)?.take(4)?.takeIf { it.isNotBlank() },
                rating = info.rating?.trim()?.toDoubleOrNull(),
                posterUrl = info.cover?.takeIf { it.isNotBlank() }
                    ?: info.backdrop?.firstOrNull(),
                durationText = null
            )
        }

        // Sezon numarasına göre grupla; sezon seçici bu haritayı kullanıyor.
        val bySeason = LinkedHashMap<Int, List<MediaItem>>()
        parsed.episodes.orEmpty()
            .entries
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .forEach { (season, eps) ->
                val number = season.toIntOrNull() ?: return@forEach
                bySeason[number] = eps
                    .sortedBy { it.episodeNum?.toIntOrNull() ?: Int.MAX_VALUE }
                    .mapNotNull { ep ->
                        val id = ep.id ?: return@mapNotNull null
                        val ext = ep.containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"
                        MediaItem(
                            id = "x_ep_$id",
                            title = "${ep.episodeNum.orEmpty()}. ${ep.title.orEmpty()}".trim(),
                            kind = MediaKind.VOD,
                            playUrl = "$base/series/$user/$pass/$id.$ext",
                            logoUrl = ep.info?.movieImage,
                            group = "Sezon $number",
                            description = ep.info?.plot
                        )
                    }
            }

        return detail to bySeason
    }

    /** Bir dizinin bölümlerini sezon sırasına göre düz liste olarak döndürür. */
    @Throws(IOException::class)
    fun episodes(seriesId: String): List<MediaItem> {
        val url = apiUrl("get_series_info") + "&series_id=$seriesId"
        val body = Http.string(Request.Builder().url(url).build())
        val info = runCatching { gson.fromJson(body, XtreamSeriesInfo::class.java) }.getOrNull()
        val seasons = info?.episodes ?: return emptyList()

        return seasons.entries
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .flatMap { (season, eps) ->
                eps.sortedBy { it.episodeNum?.toIntOrNull() ?: Int.MAX_VALUE }.map { ep ->
                    val id = ep.id.orEmpty()
                    val ext = ep.containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"
                    MediaItem(
                        id = "x_ep_$id",
                        title = "S%s·B%s  %s".format(
                            season,
                            ep.episodeNum.orEmpty(),
                            ep.title.orEmpty()
                        ).trim(),
                        kind = MediaKind.VOD,
                        playUrl = "$base/series/$user/$pass/$id.$ext",
                        logoUrl = ep.info?.movieImage,
                        group = "Sezon $season",
                        description = ep.info?.plot
                    )
                }
            }
    }

    // --- Alt seviye ---

    private fun apiUrl(action: String): String {
        val builder = "$base/player_api.php".toHttpUrlOrNull()?.newBuilder()
            ?: throw IOException("Geçersiz sunucu adresi: $base")
        builder.addQueryParameter("username", user)
        builder.addQueryParameter("password", pass)
        if (action.isNotEmpty()) builder.addQueryParameter("action", action)
        return builder.build().toString()
    }

    private fun <T> get(action: String, cls: Class<T>): T? {
        val body = Http.string(Request.Builder().url(apiUrl(action)).build())
        return runCatching { gson.fromJson(body, cls) }.getOrNull()
    }

    /**
     * Xtream sunucuları hata durumunda dizi yerine `[]`, `{}` veya düz metin
     * döndürebiliyor; bu yüzden ayrıştırma hatası boş listeye çevriliyor.
     */
    private inline fun <reified T> getList(action: String): List<T> {
        val body = Http.string(Request.Builder().url(apiUrl(action)).build())
        val type = object : TypeToken<List<T>>() {}.type
        return try {
            gson.fromJson<List<T>>(body, type) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }
}
