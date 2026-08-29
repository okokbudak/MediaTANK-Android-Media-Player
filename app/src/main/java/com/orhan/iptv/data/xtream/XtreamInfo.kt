package com.orhan.iptv.data.xtream

import com.google.gson.annotations.SerializedName

/**
 * Xtream'in içerik ayrıntısı uç noktalarının modelleri.
 *
 * Puan, özet, oyuncular gibi bilgiler sağlayıcının kendi kataloğundan geliyor.
 * IMDb'nin herkese açık ve anahtarsız bir arayüzü olmadığı için otomatik
 * IMDb çekimi yapılmıyor; sağlayıcı bu alanları zaten IMDb/TMDB'den
 * doldurduğu için pratikte aynı bilgi görünüyor.
 */

data class XtreamVodInfo(
    val info: Info?,
    @SerializedName("movie_data") val movieData: MovieData?
) {
    data class Info(
        val plot: String?,
        val cast: String?,
        val director: String?,
        val genre: String?,
        @SerializedName("releasedate") val releaseDate: String?,
        @SerializedName("release_date") val releaseDateAlt: String?,
        val rating: String?,
        val duration: String?,
        @SerializedName("movie_image") val movieImage: String?,
        @SerializedName("backdrop_path") val backdrop: List<String>?
    )

    data class MovieData(
        val name: String?,
        @SerializedName("container_extension") val containerExtension: String?
    )
}

data class XtreamSeriesDetail(
    val info: Info?,
    val seasons: List<Season>?,
    val episodes: Map<String, List<XtreamSeriesInfo.Episode>>?
) {
    data class Info(
        val name: String?,
        val plot: String?,
        val cast: String?,
        val director: String?,
        val genre: String?,
        @SerializedName("releaseDate") val releaseDate: String?,
        @SerializedName("release_date") val releaseDateAlt: String?,
        val rating: String?,
        val cover: String?,
        @SerializedName("backdrop_path") val backdrop: List<String>?
    )

    data class Season(
        @SerializedName("season_number") val seasonNumber: Int?,
        val name: String?,
        @SerializedName("episode_count") val episodeCount: String?
    )
}

/** Ekranda gösterilecek, kaynaktan bağımsız içerik ayrıntısı. */
data class ContentDetail(
    val title: String,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val year: String?,
    /** 0–10 arası; bilinmiyorsa null. */
    val rating: Double?,
    val posterUrl: String?,
    val durationText: String?
)
