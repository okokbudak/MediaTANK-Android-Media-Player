package com.orhan.iptv.data.meta

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.orhan.iptv.data.xtream.ContentDetail
import com.orhan.iptv.net.Http
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.util.Locale

/**
 * TMDB'den film ve dizi bilgisi çeker.
 *
 * Sağlayıcının kataloğu çoğu zaman özet ve afiş vermiyor; TMDB bunları
 * Türkçe olarak sağlıyor. Puan TMDB'nin kendi oylaması (IMDb değil) —
 * ekranda bu ayrım belirtiliyor.
 *
 * Kota koruması: sonuç [MetadataCache] üzerinde kalıcı olarak saklanıyor,
 * aynı içerik ikinci kez sorulmuyor.
 */
class TmdbClient(private val apiKey: String) {

    private val gson = Gson()

    private data class SearchResponse(val results: List<Result>?) {
        data class Result(
            val id: Int?,
            val title: String?,          // film
            val name: String?,           // dizi
            val overview: String?,
            @SerializedName("poster_path") val posterPath: String?,
            @SerializedName("backdrop_path") val backdropPath: String?,
            @SerializedName("vote_average") val voteAverage: Double?,
            @SerializedName("release_date") val releaseDate: String?,
            @SerializedName("first_air_date") val firstAirDate: String?
        )
    }

    private data class DetailResponse(
        val genres: List<Genre>?,
        val runtime: Int?,
        @SerializedName("episode_run_time") val episodeRunTime: List<Int>?,
        val credits: Credits?
    ) {
        data class Genre(val name: String?)
        data class Credits(val cast: List<Person>?, val crew: List<CrewMember>?)
        data class Person(val name: String?)
        data class CrewMember(val name: String?, val job: String?)
    }

    /**
     * [rawTitle] listedeki ham başlık; kalite/kategori önekleri ve sondaki yıl
     * temizlenip aranıyor. [isSeries] doğruysa dizi uç noktası kullanılıyor.
     */
    @Throws(IOException::class)
    fun lookup(rawTitle: String, isSeries: Boolean): ContentDetail? {
        val (title, year) = cleanTitle(rawTitle)
        if (title.isBlank()) return null

        val kind = if (isSeries) "tv" else "movie"
        val searchUrl = "https://api.themoviedb.org/3/search/$kind".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("api_key", apiKey)
            ?.addQueryParameter("query", title)
            ?.addQueryParameter("language", "tr-TR")
            ?.also { builder ->
                // Yıl bilgisi varsa aynı adlı yeniden çevrimlerden ayırt eder.
                year?.let {
                    builder.addQueryParameter(
                        if (isSeries) "first_air_date_year" else "year",
                        it
                    )
                }
            }
            ?.build()?.toString() ?: return null

        val searchBody = Http.string(Request.Builder().url(searchUrl).build())
        val match = runCatching { gson.fromJson(searchBody, SearchResponse::class.java) }
            .getOrNull()?.results?.firstOrNull() ?: return null

        val detail = match.id?.let { fetchDetail(kind, it) }

        val runtimeMinutes = detail?.runtime ?: detail?.episodeRunTime?.firstOrNull()
        val director = detail?.credits?.crew
            ?.firstOrNull { it.job.equals("Director", ignoreCase = true) }?.name
        val cast = detail?.credits?.cast?.take(6)?.mapNotNull { it.name }
            ?.takeIf { it.isNotEmpty() }?.joinToString(", ")

        return ContentDetail(
            title = match.title ?: match.name ?: title,
            plot = match.overview?.takeIf { it.isNotBlank() },
            cast = cast,
            director = director,
            genre = detail?.genres?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }
                ?.joinToString(", "),
            year = (match.releaseDate ?: match.firstAirDate)?.take(4)?.takeIf { it.isNotBlank() },
            rating = match.voteAverage?.takeIf { it > 0 },
            posterUrl = match.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                ?: match.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
            durationText = runtimeMinutes?.takeIf { it > 0 }?.let { "$it dk" }
        )
    }

    private fun fetchDetail(kind: String, id: Int): DetailResponse? {
        val url = "https://api.themoviedb.org/3/$kind/$id".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("api_key", apiKey)
            ?.addQueryParameter("language", "tr-TR")
            ?.addQueryParameter("append_to_response", "credits")
            ?.build()?.toString() ?: return null

        return runCatching {
            gson.fromJson(Http.string(Request.Builder().url(url).build()), DetailResponse::class.java)
        }.getOrNull()
    }

    /**
     * IPTV başlıklarını aranabilir hale getirir.
     *
     * Sağlayıcı başlıkları "SD: ", "4K ", "TR: " gibi önekler ve sonda yıl
     * taşıyor ("... Adana Yollarinda 2016"). Bunlar temizlenmezse TMDB
     * eşleşme bulamıyor. Yıl ayrı döndürülüp arama filtresi olarak kullanılıyor.
     */
    private fun cleanTitle(raw: String): Pair<String, String?> {
        var text = raw.trim()

        // "SD:", "HD:", "FULL HD:", "4K:", "TR:", "DIZI:" gibi önekler
        text = text.replace(Regex("""^\s*(\p{L}|\d|\s|\+){1,12}:\s*"""), "")

        // Kalite etiketleri
        text = text.replace(
            Regex("""\b(4K|UHD|FHD|FULL\s*HD|HD|SD|1080P|720P|X265|HEVC)\b""", RegexOption.IGNORE_CASE),
            " "
        )

        // Sondaki yıl
        val yearMatch = Regex("""\b(19|20)\d{2}\b""").findAll(text).lastOrNull()
        val year = yearMatch?.value
        if (year != null) text = text.removeRange(yearMatch.range)

        text = text.replace(Regex("""[\[\](){}]"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', '-', '·', '.')

        return text to year
    }

    companion object {
        /** Anahtarın biçimsel olarak geçerli görünüp görünmediği. */
        fun looksValid(key: String): Boolean = key.trim().length >= 20
    }
}

/** Kaynaktan bağımsız, kullanıcıya gösterilecek bilgi kaynağı etiketi. */
enum class MetadataSource(val label: String) {
    PROVIDER("Sağlayıcı"),
    TMDB("TMDB")
}
